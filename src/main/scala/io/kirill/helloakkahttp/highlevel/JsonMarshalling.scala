package io.kirill.helloakkahttp.highlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import GameAreaMap._
import akka.http.scaladsl.Http
import io.kirill.helloakkahttp.highlevel.Server2.serverRoute

case class Player(nickname: String, characterClass: String, level: Int)

object GameAreaMap {
  case object GetAllPlayers
  case class GetPlayer(nickname: String)
  case class GetPLayerByClass(characterClass: String)
  case class AddPlayer(player: Player)
  case class RemovePlayer(player: Player)
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
    case RemovePlayer(player) =>
      log.info(s"removing player $player")
      players = players - player.nickname
      sender ! OperationSuccess
  }
}


object JsonMarshalling extends App {
  implicit val system = ActorSystem("json-marshalling")
  implicit val materializer = ActorMaterializer()
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
        reject
      } ~
      parameter("class".as[String]) { characterClass =>
        reject
      } ~
      pathEndOrSingleSlash {
        reject
      }
    }~
    post {
      reject
    } ~
    delete {
      reject
    }
  }

  Http().bindAndHandle(gameServerRoute, "localhost", 8080)
}
