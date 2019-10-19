package io.kirill.helloakkahttp.lowlevel

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.concurrent.Future

object SimpleHttps extends App {
  implicit val system = ActorSystem("low-level-server")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val ksFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  val ksPassword = "akka-https".toCharArray
  ks.load(ksFile, ksPassword)

  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
  keyManagerFactory.init(ks, ksPassword)

  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  val sslContext: SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)

  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)

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

  Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081, httpsConnectionContext)
}
