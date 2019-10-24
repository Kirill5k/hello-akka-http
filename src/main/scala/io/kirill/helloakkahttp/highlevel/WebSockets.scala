package io.kirill.helloakkahttp.highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.CompactByteString

import scala.concurrent.duration._
import scala.language.postfixOps

object WebSockets extends App {
  implicit val system = ActorSystem("web-sockets")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val textMessage = TextMessage(Source.single("hello via a text message"))
  val binaryMessage = BinaryMessage(Source.single(CompactByteString("hello via a binary message")))


  val html =
    """<html>
      |    <head>
      |        <script>
      |            var socket = new WebSocket("ws://localhost:8080/greeter");
      |            console.log("starting websocket...");
      |            socket.onmessage = function (ev) {
      |                var newChild = document.createElement("div");
      |                newChild.innerText = ev.data;
      |                document.getElementById("1").appendChild(newChild);
      |            };
      |            socket.onopen = function (ev) {
      |                socket.send("socket is open")
      |            };
      |            socket.send("hello server");
      |        </script>
      |    </head>
      |    <body>
      |        <p>Starting websocket...</p>
      |        <div id="1">
      |
      |        </div>
      |    </body>
      |</html>""".stripMargin

  def websocketFlow: Flow[Message, Message, Any] = Flow[Message].map {
    case tm: TextMessage =>
      TextMessage(Source.single("server says back") ++ tm.textStream ++ Source.single("!"))
    case bm: BinaryMessage =>
      bm.dataStream.runWith(Sink.ignore)
      TextMessage(Source.single("server received a binary message"))
  }

  case class SocialPost(owner: String, content: String)
  val feed = Source(List(
    SocialPost("Martin", "Scala 3 has been announced"),
    SocialPost("Martin", "I destroyed java"),
    SocialPost("Donal", "We should buy Greenland")
  ))

  val socialMessages = feed.throttle(1, 2 seconds).map(sp => TextMessage(s"${sp.owner}: ${sp.content}"))

  val socialFlow: Flow[Message, Message, Any] = Flow.fromSinkAndSource(
    Sink.foreach[Message](println),
    socialMessages
  )

  val wsRoute =
    (pathEndOrSingleSlash & get) {
      complete(HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        html
      ))
    } ~
    path("greeter") {
      handleWebSocketMessages(socialFlow)
    }

  Http().bindAndHandle(wsRoute, "localhost", 8080)
}
