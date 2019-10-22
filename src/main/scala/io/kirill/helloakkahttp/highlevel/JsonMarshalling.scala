package io.kirill.helloakkahttp.highlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import GameAreaMap._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPLayerByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(nickname: String)
  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {
  var players: Map[String, Player] = Map()
  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("getting all players")
      sender ! players.values.toList
    case GetPlayer(nickname) =>
      log.info(s"getting player with nickname $nickname")
      sender ! players.get(nickname)
    case GetPLayerByClass(characterClass) =>
      log.info(s"getting all players by class $characterClass")
      sender ! players.values.filter(_.characterClass == characterClass).toList
    case AddPlayer(player) =>
      log.info(s"adding new player $player")
      players = players + (player.nickname -> player)
      sender ! OperationSuccess
    case RemovePlayer(nickname) =>
      log.info(s"removing player $nickname")
      players = players - nickname
      sender ! OperationSuccess
  }
}

trait PlayerJsonProtocol extends DefaultJsonProtocol {
  implicit val playerJsonFormat = jsonFormat3(Player)
}

object JsonMarshalling extends App with PlayerJsonProtocol with SprayJsonSupport {
  implicit val system = ActorSystem("json-marshalling")
  implicit val materializer = ActorMaterializer()
  implicit val defaultTimeout = Timeout(2 seconds)
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  val game = system.actorOf(Props[GameAreaMap], "the-game")
  List(
    Player("boris", "druid", 9000),
    Player("alice", "warrior", 70),
    Player("bob", "shaman", 75),
    Player("charlie", "elf", 80),
  ).foreach(game ! AddPlayer(_))

  val gameServerRoute = pathPrefix("api" / "players") {
    get {
      (path(Segment) | parameter("nickname".as[String])) { nickname =>
        val player = (game ? GetPlayer(nickname)).mapTo[Option[Player]]
        complete(player)
      } ~
      parameter("class".as[String]) { characterClass =>
        val playerByClassFuture = (game ? GetPLayerByClass(characterClass)).mapTo[List[Player]]
        complete(playerByClassFuture)
      } ~
      pathEndOrSingleSlash {
        val players = (game ? GetAllPlayers).mapTo[List[Player]]
        complete(players)
      }
    }~
    post {
      entity(as[Player]) { player =>
        val response = (game ? AddPlayer(player)).map(_ => StatusCodes.Created)
        complete(response)
      }
    } ~
    delete {
      path(Segment) { nickname =>
        val response = (game ? RemovePlayer(nickname)).map(_ => StatusCodes.NoContent)
        complete(response)
      }
    }
  }

  Http().bindAndHandle(gameServerRoute, "localhost", 8080)
}
