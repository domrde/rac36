package avatar

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import avatar.Avatar.AvatarState
import common.SharedMessages._

/**
  * Created by dda on 8/2/16.
  */

// todo: auto-kill if client disconnected
// todo: how often take snapshots? is there automatic call to take snapshot?
// todo: snapshots and journal are stored locally, so state won't be recovered during migration, use shared db
// todo: avatar must have local storage of sensory info so it's only updates the difference http://www.lightbend.com/activator/template/akka-sample-distributed-data-scala
// todo: think about using ddata instead of persistence
object Avatar {
  case class AvatarState(id: String, commands: List[Command], tunnel: ActorRef)
}

class Avatar extends PersistentActor with ActorLogging {
  log.info("\nAVATAR CREATED {}", self)

  override val persistenceId: String = "Avatar" + self.path.name

  var state = AvatarState(null, List.empty, ActorRef.noSender)

  val cache = context.actorOf(ReplicatedSet())

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

    case p: GetState => // for tests
      sender() ! state

    case Sensory(id, sensoryPayload) =>
      cache ! ReplicatedSet.AddAll(sensoryPayload)

    case GetListOfAvailableCommands(id) =>
      sender() ! ListOfAvailableCommands(id, Api(state.commands))

    case c: Control =>
      state.tunnel ! c

    case s: SaveSnapshotSuccess =>

    case s: SaveSnapshotFailure =>

    case other =>
      log.error("\nAvatar: other [{}] from [{}]", other, sender())
  }

}
