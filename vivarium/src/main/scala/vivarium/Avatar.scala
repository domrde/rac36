package vivarium

import java.net.{URL, URLClassLoader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ClusterSharding
import vivarium.ReplicatedSet.LookupResult
import com.typesafe.config.ConfigFactory
import common.SharedMessages._

/**
  * Created by dda on 8/2/16.
  */

// todo: auto-kill if client disconnected
// todo: avatars should interact with each other
// todo: send and load jars through NFS
// todo: interaction with services
object Avatar {
  @SerialVersionUID(101L) case class AvatarState(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef])
  @SerialVersionUID(101L) case class AvatarMessage(id: String, from: String, message: String) extends NumeratedMessage
}

class Avatar extends Actor with ActorLogging {
  import Avatar._

  log.info("\nAVATAR CREATED {}", self)

  val config = ConfigFactory.load()

  val cache = context.actorOf(ReplicatedSet())

  val shard = ClusterSharding(context.system).shardRegion("Avatar")

  override def receive: Receive = receiveWithState(null, None, None, Set.empty)

  def receiveWithState(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef], buffer: Set[Position]): Receive = {
    case CreateAvatar(_id, jarName, className) =>
      context.become(receiveWithState(_id, tunnel, Some(startChildFromJar(jarName, className)), buffer))
      sender() ! AvatarCreated(_id)

    case TunnelEndpoint(_id, endpoint) =>
      context.become(receiveWithState(_id, Some(endpoint), brain, buffer))

    case LookupResult(Some(data)) =>
      self ! Sensory(id, data)

    case LookupResult(None) =>
      // empty

    case p: GetState => // for tests
      sender() ! AvatarState(id, tunnel, brain)

    case Sensory(_id, sensoryPayload) =>
      cache ! ReplicatedSet.RemoveAll(buffer diff sensoryPayload)
      cache ! ReplicatedSet.AddAll(sensoryPayload diff buffer)
      val brainPositions = sensoryPayload.map { case Position(name, row, col, angle) =>
        com.dda.brain.BrainMessages.Position(name, row, col, angle)
      }
      brain.foreach(_ ! com.dda.brain.BrainMessages.Sensory(_id, brainPositions))
      context.become(receiveWithState(id, tunnel, brain, sensoryPayload))

    case com.dda.brain.BrainMessages.FromAvatarToRobot(command) =>
      tunnel.foreach(_ ! Control(id, command))

    case com.dda.brain.BrainMessages.TellToOtherAvatar(to, from, message) =>
      shard ! Avatar.AvatarMessage(to, from, message)

    case Avatar.AvatarMessage(to, from, message) =>
      brain.foreach(_ ! com.dda.brain.BrainMessages.TellToOtherAvatar(to, from, message))

    case other =>
      log.error("\nAvatar: other [{}] from [{}]", other, sender())
  }

  def startChildFromJar(jarName: String, className: String) = {
    val url = new URL("jar:file:" + config.getString("jars.nfs-directory") + jarName + "!/")
    // may be Thread.currentThread().getContextClassLoader() will be better
    val classLoader = URLClassLoader.newInstance(Array(url), this.getClass.getClassLoader)
    val clazz = classLoader.loadClass(className)
    context.actorOf(Props(clazz.asInstanceOf[Class[Actor]]), "Brain")
  }
}
