package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.dda.brain.BrainMessages
import dashboard.Server
import dashboard.clients.AvatarClient.AvatarsStatuses
import play.api.libs.json.{JsError, JsSuccess, Json}
import vivarium.ReplicatedSet.LookupResult
import vivarium.{Avatar, ReplicatedSet}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by dda on 9/6/16.
  */
object AvatarClient {
  case class ChangeAvatarState(id: String, newState: String)

  case class AvatarStatus(id: String, connectedToTunnel: Boolean, brainStarted: Boolean)
  case class AvatarsStatuses(statuses: List[AvatarStatus])
}

class AvatarClient(avatarIdStorage: ActorRef, shard: ActorRef) extends Actor with ActorLogging {

  private implicit val statusWrites = Json.writes[AvatarClient.AvatarStatus]
  private implicit val statusesWrites = Json.writes[AvatarClient.AvatarsStatuses]
  private implicit val changeAvatarStateReads = Json.reads[AvatarClient.ChangeAvatarState]
  private implicit val timeout: Timeout = 5.seconds
  private implicit val executionContext = context.dispatcher

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join
      avatarIdStorage ! ReplicatedSet.Lookup
  }

  def connected(connection: ActorRef): Receive = {
    case ServerClient.IncomingMessage(text) =>
      Json.parse(text).validate[AvatarClient.ChangeAvatarState] match {
        case JsSuccess(AvatarClient.ChangeAvatarState(id, state), _) =>
          shard ! Avatar.ChangeState(id, if (state == "Start") BrainMessages.Start else BrainMessages.Stop)

        case JsError(_) =>
          log.error("[-] dashboard.AvatarClient: Failed to validate json [{}]", text)
      }

    case LookupResult(Some(data: Set[ActorRef])) =>
      Future.sequence(data.map(_ ? Avatar.GetState)).onComplete {

        case Success(value: Avatar.State) =>
          val statuses = value.map { case Avatar.State(id, tunnel, brain) =>
            AvatarClient.AvatarStatus(id, tunnel.isDefined, brain.isDefined)}.toList.sortBy(_.id)
          connection ! ServerClient.OutgoingMessage(Json.stringify(Json.toJson(AvatarsStatuses(statuses))))

        case Failure(_) =>
      }

    case LookupResult(_) =>
      connection ! ServerClient.OutgoingMessage(Json.stringify(Json.toJson(AvatarsStatuses(List.empty))))

    case other =>
      log.error("[-] dashboard.AvatarClient: other {} from {}", other, sender())
  }
}
