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

object RequestLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("request-level")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))

  responseFuture.onComplete {
    case Success(response) =>
      response.discardEntityBytes()
      println(s"request was success and returned: $response")
    case Failure(exception) =>
      print(s"request failed with: $exception")
  }

  def postRequestToPayments(body: String): HttpRequest = HttpRequest(
    HttpMethods.POST,
    uri = Uri("http://localhost:8080/api/payments"),
    entity = HttpEntity(ContentTypes.`application/json`, body)
  )

  val creditCards = List(
    CreditCard("1235-6322-8731-7788", "122", "rd-23-gb-67"),
    CreditCard("1234-1234-1234-1234", "151", "rd-23-gb-67"),
    CreditCard("1234-1234-4321-4321", "010", "tx-test-card"),
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
  )

  val paymentRequests = creditCards.map(PaymentRequest(_, "io-kirill-1", 9.99))
  val serverRequests = paymentRequests.map(payment => postRequestToPayments(payment.toJson.prettyPrint))

  Source(serverRequests)
    .mapAsync(10)(request => Http().singleRequest(request))
    .runForeach(println)
}
