package io.kirill.helloakkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._

import scala.io.StdIn

object Playground extends App {

  implicit val system = ActorSystem("AkkaHttpPlayground")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val simpleRoute =
    pathEndOrSingleSlash {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          | <body>
          |   Hello, Akka Http!
          | </body>
          |</html>
        """.stripMargin
      ))
    }

  val bindingFuture = Http().bindAndHandle(simpleRoute, "localhost", 8080)
  // wait for a new line, then terminate the server
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

  val l = List(2).flatMap(Seq(_))
}
