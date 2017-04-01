package vivarium

import java.net.{URL, URLClassLoader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ClusterSharding
import com.typesafe.config.ConfigFactory
import messages.NumeratedMessage
import messages.RobotMessages.Control
import messages.SensoryInformation.{Position, Sensory}
import vivarium.ReplicatedSet.LookupResult

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 8/2/16.
  */

// todo: auto-kill if client disconnected
// todo: avatars should interact with each other
// todo: send and load jars through NFS
// todo: interaction with services
object Avatar {
  trait AvatarMessage extends NumeratedMessage

  @SerialVersionUID(101L) case class Create(id: String, jarName: String, className: String) extends AvatarMessage
  @SerialVersionUID(101L) case class GetState(id: String) extends AvatarMessage // for tests
  @SerialVersionUID(101L) case class State(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef]) extends AvatarMessage
  @SerialVersionUID(101L) case class Message(id: String, from: String, message: String) extends AvatarMessage
  trait AvatarCreateResponse extends AvatarMessage
  @SerialVersionUID(101L) case class AvatarCreated(id: String) extends AvatarCreateResponse
  @SerialVersionUID(101L) case class FailedToCreateAvatar(id: String, reason: String) extends AvatarCreateResponse
  @SerialVersionUID(101L) case class TunnelEndpoint(id: String, endpoint: ActorRef) extends AvatarMessage
}

class Avatar extends Actor with ActorLogging {
  log.info("[-] AVATAR CREATED {}", self)

  val config = ConfigFactory.load()

  val cache = context.actorOf(ReplicatedSet())

  val shard = ClusterSharding(context.system).shardRegion("Avatar")

  override def receive: Receive = receiveWithState(null, None, None, Set.empty)

  def receiveWithState(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef], buffer: Set[Position]): Receive = {
    case Avatar.Create(_id, jarName, className) =>
      Try {
        startChildFromJar(jarName, className)
      } match {
        case Failure(exception) =>
          context.become(receiveWithState(_id, tunnel, None, buffer))
          sender() ! Avatar.FailedToCreateAvatar(_id, exception.getMessage)
        case Success(value) =>
          context.become(receiveWithState(_id, tunnel, Some(value), buffer))
          sender() ! Avatar.AvatarCreated(_id)
      }

    case Avatar.TunnelEndpoint(_id, endpoint) =>
      context.become(receiveWithState(_id, Some(endpoint), brain, buffer))

    case LookupResult(Some(data)) =>
      self ! Sensory(id, data)

    case LookupResult(None) =>
      // empty

    case p: Avatar.GetState => // for tests
      sender() ! Avatar.State(id, tunnel, brain)

    case Sensory(_id, sensoryPayload) =>
      cache ! ReplicatedSet.RemoveAll(buffer diff sensoryPayload)
      cache ! ReplicatedSet.AddAll(sensoryPayload diff buffer)
      val brainPositions = sensoryPayload.map { case Position(name, row, col, angle) =>
        messages.BrainMessages.Position(name, row, col, angle)
      }
      brain.foreach(_ ! messages.BrainMessages.Sensory(_id, brainPositions))
      context.become(receiveWithState(id, tunnel, brain, sensoryPayload))

    case messages.BrainMessages.FromAvatarToRobot(command) =>
      tunnel.foreach(_ ! Control(id, command))

    case messages.BrainMessages.TellToOtherAvatar(to, from, message) =>
      shard ! Avatar.Message(to, from, message)

    case Avatar.Message(to, from, message) =>
      brain.foreach(_ ! messages.BrainMessages.TellToOtherAvatar(to, from, message))

    case other =>
      log.error("[-] Avatar: other [{}] from [{}]", other, sender())
  }

  def startChildFromJar(jarName: String, className: String) = {
    val url = new URL("jar:file:" + config.getString("jars.nfs-directory") + jarName + "!/")
    // may be Thread.currentThread().getContextClassLoader() will be better
    val classLoader = URLClassLoader.newInstance(Array(url), this.getClass.getClassLoader)
    val clazz = classLoader.loadClass(className)
    context.actorOf(Props(clazz.asInstanceOf[Class[Actor]]), "Brain")
  }
}
