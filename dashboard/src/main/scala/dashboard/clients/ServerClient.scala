package dashboard.clients

import akka.NotUsed
import akka.actor.{ActorRef, PoisonPill}
import akka.http.scaladsl.model.ws.TextMessage.{Streamed, Strict}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import dashboard.MetricsAggregator.NodeMetrics
import upickle.default.{read, write}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Created by dda on 05.04.17.
  */
object ServerClient {
  case class Connected(connection: ActorRef)

  sealed trait WsIncoming
  sealed trait WsOutgoing

  sealed trait AvatarClientMessages
  case class ChangeAvatarState(id: String, newState: String) extends AvatarClientMessages with WsIncoming
  case class AvatarStatus(id: String, connectedToTunnel: Boolean, brainStarted: Boolean) extends AvatarClientMessages
  case class AvatarsStatuses(statuses: List[AvatarStatus]) extends AvatarClientMessages with WsOutgoing

  sealed trait MetricsClientMessages
  case class LaunchCommand(role: String, t: String = "Launch") extends MetricsClientMessages with WsIncoming
  case class CollectedMetrics(metrics: List[NodeMetrics], t: String = "CollectedMetrics") extends MetricsClientMessages with WsOutgoing


  def newServerUser(avatarClient: ActorRef)(implicit materializer: ActorMaterializer, ec: ExecutionContext): Flow[Message, Message, NotUsed] = {
    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case tm: TextMessage => tm.textStream
      }.mapAsync(4) { partsOfInput =>
        partsOfInput
          .runFold("")(_ + _)
          .flatMap { completeInput =>
            Future.fromTry(Try{read[WsIncoming](completeInput)})
          }
      }.to(Sink.actorRef[WsIncoming](avatarClient, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[WsOutgoing](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          avatarClient ! ServerClient.Connected(outActor)
          NotUsed
        }.map((outMsg: WsOutgoing) => TextMessage(write(outMsg)))

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}
