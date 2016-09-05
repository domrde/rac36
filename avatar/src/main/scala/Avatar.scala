package avatar
import java.util.UUID

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import avatar.Avatar.AvatarState
import messages.Messages._

/**
  * Created by dda on 8/2/16.
  */

// todo: auto-kill if client disconnected
// todo: how often take snapshots? is there automatic call to take snapshot?
// todo: snapshots and journal are stored locally, so state won't be recovered during migration, use shared db
// todo: avatar must have local storage of sensory info so it's only updates the difference http://www.lightbend.com/activator/template/akka-sample-distributed-data-scala
object Avatar {
  case class AvatarState(uuid: UUID, commands: List[Command], tunnel: ActorRef)

  def apply(cache: ActorRef) = Props(classOf[Avatar], cache)
}

class Avatar(cache: ActorRef) extends PersistentActor with ActorLogging {
  log.info("\nAVATAR CREATED")

  override val persistenceId: String = "Avatar" + self.path.name

  var state = AvatarState(null, List.empty, ActorRef.noSender)

  override def receiveRecover: Receive = {
    case CreateAvatar(id, api) =>
      state = AvatarState(id, api.commands, state.tunnel)

    case TunnelEndpoint(id, endpoint) =>
      state = AvatarState(id, state.commands, endpoint)

    case SnapshotOffer(_, snapshot: AvatarState) =>
      state = snapshot
  }

  override def receiveCommand: Receive = {
    case CreateAvatar(id, api) =>
      persist(AvatarState(id, api.commands, state.tunnel)) (newState => state = newState)
      saveSnapshot(state)
      sender() ! AvatarCreated(id)

    case TunnelEndpoint(id, endpoint) =>
      persist(AvatarState(id, state.commands, endpoint)) (newState => state = newState)
      saveSnapshot(state)

    case s: SaveSnapshotSuccess =>

    case s: SaveSnapshotFailure =>

    case p: GetState =>
      sender() ! state

    case Sensory(id, sensoryPayload) =>
      cache ! ReplicatedSet.AddAll(sensoryPayload)

    // Control from API
    case c @ Control(id, command) if state.commands.contains(command) =>
      state.tunnel ! c

    case other =>
      log.error("\nAvatar: other [{}] from [{}]", other, sender())
  }

}
