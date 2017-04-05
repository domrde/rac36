package vivarium

import java.net.{URL, URLClassLoader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.cluster.sharding.ClusterSharding
import com.dda
import com.dda.brain.{BrainActor, BrainMessages}
import com.dda.brain.BrainMessages.BrainState
import com.typesafe.config.ConfigFactory
import common.messages.{NumeratedMessage, SensoryInformation}
import common.Constants.{PositionDdataSetKey, AvatarsDdataSetKey}
import vivarium.ReplicatedSet.LookupResult

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 8/2/16.
  */

// todo: avatar state in dashboard
// todo: auto-kill if client disconnected
// todo: send and load jars through NFS
// todo: interaction with services
object Avatar {

  trait AvatarMessage extends NumeratedMessage

  @SerialVersionUID(101L) case class Init(id: String) extends AvatarMessage
  @SerialVersionUID(101L) case class Create(id: String, jarName: String, className: String) extends AvatarMessage
  @SerialVersionUID(101L) case class GetState(id: String) extends AvatarMessage
  @SerialVersionUID(101L) case class State(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef]) extends AvatarMessage
  @SerialVersionUID(101L) case class TunnelEndpoint(id: String, endpoint: ActorRef) extends AvatarMessage

  trait AvatarCreateResponse extends AvatarMessage
  @SerialVersionUID(101L) case class AvatarCreated(id: String) extends AvatarCreateResponse
  @SerialVersionUID(101L) case class FailedToCreateAvatar(id: String, reason: String) extends AvatarCreateResponse

  trait BrainMessageWrapper extends AvatarMessage
  @SerialVersionUID(101L) case class FromAvatarToRobot(id: String, message: String) extends BrainMessageWrapper
  @SerialVersionUID(101L) case class FromRobotToAvatar(id: String, message: String) extends BrainMessageWrapper
  @SerialVersionUID(101L) case class TellToAvatar(id: String, from: String, message: String) extends BrainMessageWrapper
  @SerialVersionUID(101L) case class ChangeState(id: String, newState: BrainState) extends BrainMessageWrapper

}

class Avatar extends Actor with ActorLogging {
  log.info("[-] AVATAR CREATED {}", self)

  private val config = ConfigFactory.load()

  private val cache = context.actorOf(ReplicatedSet(PositionDdataSetKey))

  private val avatarIdStorage = context.actorOf(ReplicatedSet(AvatarsDdataSetKey))

  private val shard = ClusterSharding(context.system).shardRegion("Avatar")

  override def receive: Receive = notInitialized

  val notInitialized: Receive = {
    case Avatar.Init(id) =>
      avatarIdStorage ! ReplicatedSet.AddAll(Set(id))
      context.become(receiveWithState(id, None, None, Set.empty))

    case Avatar.Create(id, jarName, className) =>
      Try {
        startChildFromJar(id, jarName, className)
      } match {
        case Failure(exception) =>
          sender() ! Avatar.FailedToCreateAvatar(id, exception.getMessage)
        case Success(value) =>
          avatarIdStorage ! ReplicatedSet.AddAll(Set(id))
          sender() ! Avatar.AvatarCreated(id)
          context.become(receiveWithState(id, None, Some(value), Set.empty))
      }

    case other =>
      log.error("[-] Avatar: not initialized, unknown message [{}] from [{}]", other, sender())
  }

  def receiveWithState(id: String, tunnel: Option[ActorRef],
                       brain: Option[ActorRef], buffer: Set[SensoryInformation.Position]): Receive =
    handleAvatarMessages(id, tunnel, brain, buffer) orElse
      handleBrainMessages(id, tunnel, brain, buffer) orElse {
      case s: SubscribeAck =>
      case other => log.error("[-] Avatar: unknown message [{}] from [{}]", other, sender())
    }

  def handleAvatarMessages(id: String, tunnel: Option[ActorRef],
                           brain: Option[ActorRef], buffer: Set[SensoryInformation.Position]): Receive = {
    case Avatar.Create(_id, jarName, className) if brain.isEmpty =>
      Try {
        startChildFromJar(_id, jarName, className)
      } match {
        case Failure(exception) =>
          sender() ! Avatar.FailedToCreateAvatar(_id, exception.getMessage)
        case Success(value) =>
          context.become(receiveWithState(_id, tunnel, Some(value), buffer))
          sender() ! Avatar.AvatarCreated(_id)
      }

    case Avatar.TunnelEndpoint(_id, endpoint) =>
      context.become(receiveWithState(_id, Some(endpoint), brain, buffer))

    case LookupResult(Some(data: Set[SensoryInformation.Position])) if sender() == cache =>
      self ! SensoryInformation.Sensory(id, data)

    case LookupResult(None) if sender() == avatarIdStorage =>

    case LookupResult(None) =>
    // no data in storage

    case p: Avatar.GetState => // for tests
      sender() ! Avatar.State(id, tunnel, brain)
  }

  def handleBrainMessages(id: String, tunnel: Option[ActorRef],
                          brain: Option[ActorRef], buffer: Set[SensoryInformation.Position]): Receive = {
    case SensoryInformation.Sensory(_id, sensoryPayload) =>
      cache ! ReplicatedSet.RemoveAll(buffer diff sensoryPayload)
      cache ! ReplicatedSet.AddAll(sensoryPayload diff buffer)
      val brainPositions = sensoryPayload.map { case SensoryInformation.Position(name, row, col, angle) =>
        BrainMessages.Position(name, row, col, angle)
      }
      brain.foreach(_ ! dda.brain.BrainMessages.Sensory(brainPositions))
      context.become(receiveWithState(id, tunnel, brain, sensoryPayload))

    case BrainMessages.FromAvatarToRobot(message) =>
      tunnel.foreach(_ ! Avatar.FromAvatarToRobot(id, message))

    case Avatar.FromRobotToAvatar(_, message) =>
      brain.foreach(_ ! BrainMessages.FromRobotToAvatar(message))

    case BrainMessages.TellToOtherAvatar(to, message) =>
      shard ! Avatar.TellToAvatar(to, id, message)

    case Avatar.TellToAvatar(_, from, message) =>
      brain.foreach(_ ! BrainMessages.FromOtherAvatar(from, message))

    case Avatar.ChangeState(_, newState) =>
      log.info("[-] Avatar: changing brains state to [{}]", newState)
      brain.foreach(_ ! BrainMessages.ChangeState(newState))

    case Terminated(a) =>
      if (brain.isDefined && brain.get == a)
        context.become(receiveWithState(id, tunnel, None, buffer))
      else if (tunnel.isDefined && tunnel.get == a)
        context.become(receiveWithState(id, None, brain, buffer))
  }

  private def startChildFromJar(id: String, jarName: String, className: String) = {
    val url = new URL("jar:file:" + config.getString("application.jars-nfs-directory") + jarName + "!/")
    // may be Thread.currentThread().getContextClassLoader() will be better
    val classLoader = URLClassLoader.newInstance(Array(url), this.getClass.getClassLoader)
    val clazz = classLoader.loadClass(className)
    context.actorOf(Props(clazz.asInstanceOf[Class[BrainActor]], id), "Brain")
  }
}
