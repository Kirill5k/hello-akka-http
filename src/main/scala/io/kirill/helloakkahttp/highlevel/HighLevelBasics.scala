package io.kirill.helloakkahttp.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object HighLevelBasics extends App {
  implicit val system = ActorSystem("high-level-server")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import akka.http.scaladsl.server.Directives._

  val simplePath: Route =
    path("home") {
      get {
        complete(StatusCodes.OK)
      } ~
      post {
        complete(StatusCodes.Forbidden)
      }
    } ~
    path("about") {
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   <p>Hello, World</p>
            | </body>
            |</html>
            |""".stripMargin
        )
      )
    }

  Http().bindAndHandle(simplePath, "localhost", 8080)
}
