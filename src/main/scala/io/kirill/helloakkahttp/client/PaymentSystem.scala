package io.kirill.helloakkahttp.client

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import spray.json.DefaultJsonProtocol
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object PaymentSystemDomain {
  case class CreditCard(number: String, cvv: String, account: String)
  case class PaymentRequest(creditCard: CreditCard, receiverAccount: String, amount: BigDecimal)
  case object PaymentAccepted
  case object PaymentRejected
}

trait PaymentJsonProtocol extends DefaultJsonProtocol {
  import PaymentSystemDomain._
  implicit val creditCardFormat = jsonFormat3(CreditCard)
  implicit val paymentRequestFormat = jsonFormat3(PaymentRequest)
}

class PaymentValidator extends Actor with ActorLogging {
  import PaymentSystemDomain._

  override def receive: Receive = {
    case PaymentRequest(CreditCard(number, _, senderAccount), receiverAccount, amount) =>
      log.info(s"$senderAccount is trying to send $amount to $receiverAccount")
      val response = if (number == "1234-1234-1234-1234") PaymentRejected else PaymentAccepted
      sender ! response
  }
}

object PaymentSystem extends App with PaymentJsonProtocol with SprayJsonSupport {
  implicit val actorSystem = ActorSystem("payment-system")
  implicit val materializer = ActorMaterializer()
  implicit val defaultTimeout = Timeout(2 seconds)
  import actorSystem.dispatcher
  import PaymentSystemDomain._

  val paymentValidator = actorSystem.actorOf(Props[PaymentValidator])

  val paymentRoute = path("api" / "payments") {
    post {
      entity(as[PaymentRequest]) { request =>
        val response = (paymentValidator ? request).map {
          case PaymentRejected => StatusCodes.Forbidden
          case PaymentAccepted => StatusCodes.OK
          case _ => StatusCodes.BadRequest
        }
        complete(response)
      }
    }
  }

  Http().bindAndHandle(paymentRoute, "localhost", 9000)
}
