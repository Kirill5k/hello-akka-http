package io.kirill.helloakkahttp.highlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import io.kirill.helloakkahttp.highlevel.Basics.simplePath
import io.kirill.helloakkahttp.highlevel.GuitarDB.{CreateGuitar, GuitarCreated}
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

object Server extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("directives")
  implicit val materializer = ActorMaterializer()
  implicit val defaultTimeout = Timeout(2 seconds)
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  import GuitarDB._
  import akka.pattern.ask

  val guitarDbActor = system.actorOf(Props[GuitarDB], "guitar-db")
  val guitars = List(
    Guitar("fender", "stratocaster"),
    Guitar("gibson", "les paul"),
    Guitar("martin", "lx1"),
    Guitar("ibanez", "7"),
  )
  guitars.foreach(guitarDbActor ! CreateGuitar(_))

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val guitarServerRoute = pathPrefix("api" / "guitars") {
    get {
      (path(IntNumber) | parameter("guitarId".as[Int])) { id =>
        val responseFuture = (guitarDbActor ? FindGuitar(id))
          .mapTo[Option[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(responseFuture)
      } ~
      pathEndOrSingleSlash {
        val responseFuture = (guitarDbActor ? FindAllGuitars)
          .mapTo[List[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(responseFuture)
      }
    }
  }

  Http().bindAndHandle(guitarServerRoute, "localhost", 8080)
}
