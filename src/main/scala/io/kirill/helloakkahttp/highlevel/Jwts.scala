package io.kirill.helloakkahttp.highlevel

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}

import scala.concurrent.duration._
import scala.language.postfixOps
import spray.json._

import scala.util.{Failure, Success}

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)

  implicit val loginRequestFormat = jsonFormat2(LoginRequest)
}

object Jwts extends App with SprayJsonSupport {
  implicit val system = ActorSystem("web-sockets")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  import SecurityDomain._

  val secretPasswordDb = Map(
    "admin" -> "admin",
    "steve" -> "iloveapple"
  )

  val signingAlgorithm = JwtAlgorithm.HS256
  val secretKey = "akka-http-secret"

  def isValidPassword(username: String, password: String): Boolean = {
    secretPasswordDb.contains(username) && secretPasswordDb(password) == password
  }

  def createToken(username: String, daysValid: Int): String = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000  + TimeUnit.DAYS.toSeconds(daysValid)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("kirill")
    )
    JwtSprayJson.encode(claims, secretKey, signingAlgorithm)
  }

  def isTokenExpired(token: String): Boolean =
    JwtSprayJson.decode(token, secretKey, Seq(signingAlgorithm)) match {
      case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
      case Failure(_) => true
    }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(signingAlgorithm))

  val loginRoute = post {
    entity(as[LoginRequest]) {
      case LoginRequest(username, password) if isValidPassword(username, password) =>
        respondWithHeader(RawHeader("Access-Token", createToken(username, 1))) {
          complete(StatusCodes.OK)
        }
      case _ => complete(StatusCodes.Unauthorized)
    }
  }

  val authenticatedRoute = (path("secured-endpoint") & get) {
    optionalHeaderValueByName("Authorization") {
      case Some(token) if isTokenValid(token) => {
        if (isTokenExpired(token)) {
          complete(HttpResponse(StatusCodes.Unauthorized, entity = "token has expired"))
        } else {
          complete("user accessed authorized endpoint")
        }
      }
      case Some(_) => complete(HttpResponse(StatusCodes.Unauthorized, entity = "token has expired or is invalid"))
      case None => complete(HttpResponse(StatusCodes.Forbidden, entity = "missing authorization header"))
    }
  }

  val route = loginRoute ~ authenticatedRoute

  Http().bindAndHandle(route, "localhost", 8080)
}
