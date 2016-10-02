package avatar

import java.net.{URL, URLClassLoader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import avatar.Avatar.AvatarState
import com.typesafe.config.ConfigFactory
import common.SharedMessages._

/**
  * Created by dda on 8/2/16.
  */

// todo: auto-kill if client disconnected
// todo: how often take snapshots? is there automatic call to take snapshot?
// todo: snapshots and journal are stored locally, so state won't be recovered during migration, use shared db
// todo: avatar must have local storage of sensory info so it's only updates the difference http://www.lightbend.com/activator/template/akka-sample-distributed-data-scala
// todo: think about using ddata instead of persistence
// todo: check avatar's childs persist
// todo: avatars should interact with each other
// todo: send and load jars through NFS
object Avatar {
  case class AvatarState(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef])
}

class Avatar extends PersistentActor with ActorLogging {
  log.info("\nAVATAR CREATED {}", self)

  val config = ConfigFactory.load()
  override val persistenceId: String = "Avatar" + self.path.name

  var state = AvatarState(null, None, None)

  val cache = context.actorOf(ReplicatedSet())

  override def receiveRecover: Receive = {
    case CreateAvatar(id, jarName, className) =>
      state = AvatarState(id, state.tunnel, Some(startChildFromJar(jarName, className)))

    case TunnelEndpoint(id, endpoint) =>
      state = AvatarState(id, Some(endpoint), state.brain)

    case SnapshotOffer(_, snapshot: AvatarState) =>
      state = snapshot
  }

  override def receiveCommand: Receive = {
    case CreateAvatar(id, jarName, className) =>
      persist(AvatarState(id, state.tunnel, Some(startChildFromJar(jarName, className))))(newState => state = newState)
      saveSnapshot(state)
      sender() ! AvatarCreated(id)

    case TunnelEndpoint(id, endpoint) =>
      persist(AvatarState(id, Some(endpoint), state.brain))(newState => state = newState)
      saveSnapshot(state)

    case p: GetState => // for tests
      sender() ! state

    case Sensory(id, sensoryPayload) =>
      cache ! ReplicatedSet.AddAll(sensoryPayload)
      val brainPositions = sensoryPayload.map { case Position(name, row, col, angle) =>
        com.dda.brain.BrainMessages.Position(name, row, col, angle)
      }
      state.brain.foreach(_ ! com.dda.brain.BrainMessages.Sensory(brainPositions))

    case com.dda.brain.BrainMessages.Control(command) =>
      state.tunnel.foreach(_ ! Control(state.id, command))

    case s: SaveSnapshotSuccess =>

    case s: SaveSnapshotFailure =>

    case other =>
      log.error("\nAvatar: other [{}] from [{}]", other, sender())
  }

  def startChildFromJar(jarName: String, className: String) = {
    val url = new URL("jar:file:" + config.getString("jars.nfs-directory") + jarName + "!/")
    val classLoader = URLClassLoader.newInstance(Array(url))
    val clazz = classLoader.loadClass(className)
    context.actorOf(Props(clazz.asInstanceOf[Class[Actor]]), "Brain")
  }
}
