package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.dda.brain.BrainMessages
import dashboard.Server
import vivarium.ReplicatedSet.LookupResult
import vivarium.{Avatar, ReplicatedSet}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by dda on 9/6/16.
  */
object AvatarClient {
  def apply(avatarIdStorage: ActorRef, shard: ActorRef): Props = Props(classOf[AvatarClient], avatarIdStorage, shard)
}

class AvatarClient(avatarIdStorage: ActorRef, shard: ActorRef) extends Actor with ActorLogging {

  private implicit val timeout: Timeout = 5.seconds
  private implicit val executionContext = context.dispatcher

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join(Server.AvatarClient)
      avatarIdStorage ! ReplicatedSet.Lookup
  }

  def connected(connection: ActorRef): Receive = {
    case ServerClient.ChangeAvatarState(id, state) =>
      shard ! Avatar.ChangeState(id, if (state == "Start") BrainMessages.Start else BrainMessages.Stop)

    case LookupResult(Some(data: Set[ActorRef])) =>
      Future.sequence(data.map(_ ? Avatar.GetState)).onComplete {

        case Success(value: Avatar.State) =>
          val statuses = value.map { case Avatar.State(id, tunnel, brain) =>
            ServerClient.AvatarStatus(id, tunnel.isDefined, brain.isDefined)}.toList.sortBy(_.id)
          connection ! ServerClient.AvatarsStatuses(statuses)

        case Failure(_) =>
      }

    case LookupResult(_) =>
      connection ! ServerClient.AvatarsStatuses(List.empty)

    case other =>
      log.error("[-] dashboard.AvatarClient: other {} from {}", other, sender())
  }
}
