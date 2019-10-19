package io.kirill.helloakkahttp.lowlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import io.kirill.helloakkahttp.lowlevel.GuitarDB.{CreateGuitar, FindAllGuitars, FindGuitar, GuitarCreated}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
}

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._
  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive: Receive = {
    case FindAllGuitars =>
      log.info("searching for all guitar")
      sender ! guitars.values.toList
    case FindGuitar(id) =>
      log.info(s"searching guitar by $id")
      sender ! guitars.get(id)
    case CreateGuitar(guitar: Guitar) =>
      log.info(s"adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
  implicit val guitarCreatedFormat = jsonFormat1(GuitarCreated)
}

object SimpleRest extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("simple-server")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleGuitar = Guitar("fender", "stratocaster")
  println(simpleGuitar.toJson.prettyPrint)
  val guitarJson =
    """
      |{
      |  "make": "fender",
      |  "model": "stratocaster"
      |}
      |""".stripMargin
  println(guitarJson.parseJson.convertTo[Guitar])

  val guitarDbActor = system.actorOf(Props[GuitarDB], "guitar-db")
  val guitars = List(
    Guitar("fender", "stratocaster"),
    Guitar("gibson", "les paul"),
    Guitar("martin", "lx1"),
    Guitar("ibanez", "7"),
  )
  guitars.foreach(guitarDbActor ! CreateGuitar(_))

  /**
   * server code
   */
  implicit val defaultTimeout = Timeout(2 seconds)
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, uri @ Uri.Path("/api/guitars"), _, _, _) =>
      val query = uri.query()
      val guitarsFuture: Future[List[Guitar]] =
        if (query.isEmpty) (guitarDbActor ? FindAllGuitars).mapTo[List[Guitar]]
        else query.get("id").map(_.toInt).map(guitarDbActor ? FindGuitar(_))
          .map(_.mapTo[Option[Guitar]].map(_.map(List(_)).getOrElse(List())))
          .getOrElse(Future{List()})
      guitarsFuture.map{ guitars =>
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        )
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitars"), _, entity, _) =>
      val strictEntityFuture = entity.toStrict(10 seconds)
      strictEntityFuture
        .map(_.data.utf8String.parseJson.convertTo[Guitar])
        .map(guitarDbActor ? CreateGuitar(_))
        .flatMap(_.mapTo[GuitarCreated])
        .map{guitarCreatedEvent =>
          HttpResponse(
            StatusCodes.Created,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitarCreatedEvent.toJson.prettyPrint
            )
          )
        }
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future{HttpResponse(status = StatusCodes.NotFound)}
  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)
}
