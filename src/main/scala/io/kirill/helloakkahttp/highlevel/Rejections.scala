package io.kirill.helloakkahttp.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}
import akka.stream.ActorMaterializer

object Rejections extends App {
  implicit val system = ActorSystem("rejections")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  val route = pathPrefix("api" / "endpoint") {
    get {
      path(IntNumber) { id =>
        complete(StatusCodes.OK)
      } ~
        pathEndOrSingleSlash {
          complete(StatusCodes.OK)
        }
    }
  }

  // Rejection handlers
  val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"encountered rejections: $rejections")
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
    println(s"encountered rejections: $rejections")
    Some(complete(StatusCodes.Forbidden))
  }

  val routeWithHandlers = handleRejections(badRequestHandler) {
    route ~
      pathPrefix("api" / "another-endpoint") {
        post {
          handleRejections(forbiddenHandler) {
            parameter("param") { _ =>
              complete(StatusCodes.OK)
            }
          }
        }
      }
  }

  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case m: MethodRejection =>
        println(s"received method rejection $m")
        complete("Rejected method!")
    }
    .handle {
      case m: MissingQueryParamRejection =>
        println(s"query param rejection $m")
        complete("Rejected query param!")
    }
    .result()

//  Http().bindAndHandle(routeWithHandlers, "localhost", 8080)
  Http().bindAndHandle(route, "localhost", 8080)
}
