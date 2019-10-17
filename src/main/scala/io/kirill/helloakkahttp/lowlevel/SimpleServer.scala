package io.kirill.helloakkahttp.lowlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object SimpleServer extends App {

  implicit val system = ActorSystem("low-level-server")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val serverSource = Http().bind("localhost", 8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"accepted connection from ${connection.remoteAddress}")
  }

  val serverBindingFuture = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete {
    case Success(binding) =>
      println(s"server binding success $binding")
      binding.unbind()
    case Failure(exception) => println(s"server binding failed $exception")
  }

  // handle requests in sync
  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"hello": "world"}"""
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.BadRequest,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"error": "bad request"}"""
        )
      )
  }

  val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

//  Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
  Http().bindAndHandleSync(requestHandler, "localhost", 8080)

  // handle requests in async
  val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(
          StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            """{"hello": "async world"}"""
          )
        ))
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"message": "async not found"}"""
        )
      ))
    case request: HttpRequest =>
      request.discardEntityBytes()
      Future(HttpResponse(
        StatusCodes.BadRequest,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"message": "async bad request"}"""
        )
      ))
  }

  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)

  // async via akka stream
  val streamsBasedRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map{
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"hello": "streams world"}"""
        )
      )
    case HttpRequest(HttpMethods.GET, _, _, _, _) =>
      HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"message": "streams not found"}"""
        )
      )
    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.BadRequest,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"message": "streams bad request"}"""
        )
      )
  }

//  Http().bind("localhost", 8082).runForeach { connection =>
//    connection.handleWith(streamsBasedRequestHandler)
//  }
  Http().bindAndHandle(streamsBasedRequestHandler, "localhost", 8082)
}
