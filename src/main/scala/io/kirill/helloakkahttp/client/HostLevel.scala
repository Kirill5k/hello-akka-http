package io.kirill.helloakkahttp.client

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import io.kirill.helloakkahttp.client.PaymentSystemDomain._
import spray.json._

import scala.util.{Failure, Success}

object HostLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("host-level")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")

  Source(1 to 10)
    .map(i => (HttpRequest(), i))
    .via(poolFlow)
    .map {
      case (Success(response), value) =>
        response.discardEntityBytes()
        s"request $value has received response: $response"
      case (Failure(ex), value) =>
        s"request $value has failed with $ex"
    }
    .runWith(Sink.foreach[String](println))


  def postRequestToPayments(body: String): HttpRequest = HttpRequest(
    HttpMethods.POST,
    uri = Uri("/api/payments"),
    entity = HttpEntity(ContentTypes.`application/json`, body)
  )

  val creditCards = List(
    CreditCard("1235-6322-8731-7788", "122", "rd-23-gb-67"),
    CreditCard("1234-1234-1234-1234", "151", "rd-23-gb-67"),
    CreditCard("1234-1234-4321-4321", "010", "tx-test-card"),
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
  )

  val paymentRequests = creditCards.map(PaymentRequest(_, "io-kirill-1", 9.99))
  val serverRequests = paymentRequests.map(payment => (postRequestToPayments(payment.toJson.prettyPrint), UUID.randomUUID().toString))

  Source(serverRequests)
    .via(Http().cachedHostConnectionPool[String]("localhost", 8080))
    .runForeach {
      case (Success(response @ HttpResponse(StatusCodes.Forbidden, _, _, _)), orderId) =>
        response.discardEntityBytes()
        s"order $orderId was not allowed to proceed: $response"
      case (Success(response), orderId) =>
        response.discardEntityBytes()
        s"order $orderId was successful: $response"
      case (Failure(ex), orderId) =>
        s"order $orderId has failed with $ex"
    }
}
