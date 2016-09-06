import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.concurrent.Await
import scala.concurrent.duration._
/**
  * Created by dda on 9/6/16.
  */
object Server {
  case object Join
}

class Server extends Actor with ActorLogging {
  import Directives._
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  def newUser(): Flow[Message, Message, NotUsed] = {
    val userActor = context.actorOf(Props[ServerClient])

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) => ServerClient.IncomingMessage(text)
      }.to(Sink.actorRef[ServerClient.IncomingMessage](userActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ServerClient.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          userActor ! ServerClient.Connected(outActor)
          NotUsed
        }.map(
        (outMsg: ServerClient.OutgoingMessage) => TextMessage(outMsg.text))

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  val route =
    path("") {
      get {
        getFromResource("dashboard.html")
      }
    } ~
    path("ws") {
      get {
        handleWebSocketMessages(newUser())
      }
    }

  val binding = Await.result(Http().bindAndHandle(route, "127.0.0.1", 8080), 3.seconds)

  override def receive= receiveWithClients(List.empty)

  def receiveWithClients(clients: List[ActorRef]): Receive = {
    case Server.Join =>
      context.become(receiveWithClients(sender() :: clients))

    case anything if sender() == context.parent =>
      clients.foreach(_ ! anything)

    case other =>
      log.error("Server: other {} from {}", other, sender())
  }
}
