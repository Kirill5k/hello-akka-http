package io.kirill.helloakkahttp.highlevel

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Route

object DirectivesBreakdown extends App {
  implicit val system = ActorSystem("directives")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import akka.http.scaladsl.server.Directives._

  /**
   * Type #1: Filtering directives
   */
  val simpleHttpMethodRoute: Route =
    get { complete(StatusCodes.OK) } ~
    post { complete(StatusCodes.Forbidden) } ~
    put { complete(StatusCodes.Forbidden) } ~
    delete { complete(StatusCodes.Forbidden) }

  val simplePathRoute: Route = path("about") { complete(StatusCodes.OK) }
  val complexPathRoute: Route = path("api" / "resource") { complete(StatusCodes.OK)}
  // will match localhost:8080 or localhost:8080/
  val pathEndRoute = pathEndOrSingleSlash { complete(StatusCodes.OK) }

  /**
   * Type #2: Extraction directives
   */
  val pathExtractionRoute: Route =
    path("api" / "items" / IntNumber) { id =>
      get {
        complete(HttpEntity(
          ContentTypes.`application/json`,
          s"""{"id": ${id}}"""
        ))
      }
    }

  val queryParamsExtractionRoute: Route =
    path("api" / "items") {
      get {
        parameter("type".as[String]) { itemType =>
          complete(HttpEntity(
            ContentTypes.`application/json`,
            s"""{"type": "${itemType}"}"""
          ))
        }
      }
    }

  val requestExtractionRoute: Route =
    path("api" / "items") {
      post {
        extractRequest { request: HttpRequest =>
          extractLog { log =>
            log.info(s"received this: $request")
            complete(StatusCodes.Created)
          }
        }
      }
    }


  /**
   * Type #3: composite directives
   */
  val compactSimpleNestedRoute = (path("api" / "items") & get) {
    complete(StatusCodes.OK)
  }

  val compactExtractRequestRoute = (path("api" / "items") & post & extractRequest & extractLog) { (request, log) =>
    log.info(s"post on api/items s$request")
    complete(StatusCodes.OK)
  }

  val dryRoute = (path("about") | path("about-us")) {
    complete(StatusCodes.OK)
  }

  /**
   * Type #4: actionable directives
   */

  val failedRoute =
    path("not-supported") {
      failWith(new RuntimeException("unsupported!"))
    }

  val rejectRoute =
    path("secret") {
      reject
    }
}
