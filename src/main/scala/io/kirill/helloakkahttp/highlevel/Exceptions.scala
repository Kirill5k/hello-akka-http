package io.kirill.helloakkahttp.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ExceptionHandler, MissingQueryParamRejection, Rejection, RejectionHandler}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._

object Exceptions extends App {
  implicit val system = ActorSystem("exceptions")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val route = pathPrefix("api" / "endpoint") {
    get {
      path(IntNumber) { id =>
        throw new NoSuchElementException(s"item with $id does not exist")
      } ~
      pathEndOrSingleSlash {
        complete(StatusCodes.OK)
      }
    } ~
    (put & path(IntNumber)) { id =>
      throw new NoSuchElementException(s"item with $id does not exist")
    } ~
    post {
      throw new NotImplementedError("not implemented")
    }
  }

  val notImplErrorHandler: ExceptionHandler = ExceptionHandler {
    case e: NotImplementedError => complete(StatusCodes.Forbidden, e.getMessage)
  }

  val noSuchElExceptionHandler: ExceptionHandler = ExceptionHandler {
    case e: NoSuchElementException => complete(StatusCodes.NotFound, e.getMessage)
  }

  val routeWithExceptionHandlers = (handleExceptions(notImplErrorHandler) & handleExceptions(noSuchElExceptionHandler)) {
    route
  }

//  implicit val implicitCustomExceptionHandler: ExceptionHandler = notImplErrorHandler orElse noSuchElExceptionHandler

  Http().bindAndHandle(routeWithExceptionHandlers, "localhost", 8080)
}
