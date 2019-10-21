package io.kirill.helloakkahttp.highlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import io.kirill.helloakkahttp.highlevel.Basics.simplePath
import io.kirill.helloakkahttp.highlevel.GuitarDB.{CreateGuitar, GuitarCreated}
import io.kirill.helloakkahttp.highlevel.PersonDB.PersonCreated
import io.kirill.helloakkahttp.lowlevel.Guitar
import io.kirill.helloakkahttp.lowlevel.GuitarDB.CreateGuitar
import io.kirill.helloakkahttp.lowlevel.SimpleRest.guitarDbActor
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class CreatePersonRequest(name: String)
case class Person(pin: Int, name: String)

object PersonDB {
  case class CreatePerson(name: String)
  case class PersonCreated(pin: Int)
  case class FindPerson(pin: Int)
  case object FindAllPeople
}

class PersonDB extends Actor with ActorLogging {
  import PersonDB._
  var people: List[Person] = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie"),
  )
  var currentPersonId: Int = 4

  override def receive: Receive = {
    case FindAllPeople =>
      log.info("searching for all people")
      sender ! people
    case FindPerson(pin) =>
      log.info(s"searching for person by pin $pin")
      sender ! people.find(_.pin == pin)
    case CreatePerson(name: String) =>
      log.info(s"adding person $name with id $currentPersonId")
      people = people :+ Person(currentPersonId, name)
      sender ! PersonCreated(currentPersonId)
      currentPersonId += 1
  }
}

trait PersonJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val personFormat = jsonFormat2(Person)
  implicit val personCreatedFormat = jsonFormat1(PersonCreated)
  implicit val createPersonFormat = jsonFormat1(CreatePersonRequest)
}

object Server2 extends App with PersonJsonSupport {
  implicit val system = ActorSystem("people-server")
  implicit val materializer = ActorMaterializer()
  implicit val defaultTimeout = Timeout(2 seconds)
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._
  import PersonDB._
  import akka.pattern.ask

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val personDbActor = system.actorOf(Props[PersonDB], "person-db")

  val serverRoute = pathPrefix("api" / "people") {
    get {
      (path(IntNumber) | parameter("pin".as[Int])) { id =>
        val responseFuture = (personDbActor ? FindPerson(id))
          .mapTo[Option[Person]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(responseFuture)
      } ~
      pathEndOrSingleSlash {
        val responseFuture = (personDbActor ? FindAllPeople)
          .mapTo[List[Person]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(responseFuture)
      }
    } ~
    post {
      (pathEndOrSingleSlash & entity(as[CreatePersonRequest])) { request =>
        val responseFuture = (personDbActor ? CreatePerson(request.name))
          .mapTo[PersonCreated]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity)
        complete(responseFuture)
      }
    }
  }

  Http().bindAndHandle(serverRoute, "localhost", 8080)
}
