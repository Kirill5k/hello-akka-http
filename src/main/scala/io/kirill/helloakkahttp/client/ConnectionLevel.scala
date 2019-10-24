package io.kirill.helloakkahttp.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import io.kirill.helloakkahttp.client.PaymentSystemDomain._

import scala.util.{Failure, Success}
import spray.json._

object ConnectionLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("web-sockets")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val connectionFlow = Http().outgoingConnection("www.google.com")

  def oneOfRequest(request: HttpRequest) =
    Source.single(request).via(connectionFlow).runWith(Sink.head)

//  oneOfRequest(HttpRequest()).onComplete {
//    case Success(response) => println(s"got successful response: $response")
//    case Failure(exception) => println(s"error sending request: $exception")
//  }

  val creditCards = List(
    CreditCard("1235-6322-8731-7788", "122", "rd-23-gb-67"),
    CreditCard("1234-1234-1234-1234", "151", "rd-23-gb-67"),
    CreditCard("1234-1234-4321-4321", "010", "tx-test-card"),
    CreditCard("4242-4242-4242-4242", "424", "tx-test-account"),
  )

  val paymentRequests = creditCards.map(PaymentRequest(_, "io-kirill-1", 9.99))
  val serverRequests = paymentRequests.map(payment => HttpRequest(
    HttpMethods.POST,
    uri = Uri("/api/payments"),
    entity = HttpEntity(ContentTypes.`application/json`, payment.toJson.prettyPrint)
  ))

  Source(serverRequests)
    .via(Http().outgoingConnection("localhost", 9000))
    .to(Sink.foreach[HttpResponse](println))
    .run()
}
