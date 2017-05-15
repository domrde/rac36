package vivarium

import java.io.{PrintWriter, StringWriter}
import java.net.{URL, URLClassLoader}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.cluster.sharding.ClusterSharding
import com.dda
import com.dda.brain.BrainMessages.BrainState
import com.dda.brain.{BrainActor, BrainMessages}
import com.typesafe.config.ConfigFactory
import common.Constants
import common.Constants.{AvatarsDdataSetKey, PositionDdataSetKey}
import common.messages.{NumeratedMessage, SensoryInformation}

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 8/2/16.
  */

// todo: avatar state in dashboard
// todo: auto-kill if client disconnected
// todo: send and load jars through NFS
// todo: interaction with services
// todo: merge overlapping obstacles into one
object  Avatar {

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
  private val positionStorage = context.actorOf(ReplicatedSet(PositionDdataSetKey))
  private val avatarIdStorage = context.actorOf(ReplicatedSet(AvatarsDdataSetKey))
  private val shard = ClusterSharding(context.system).shardRegion("Avatar")

  override def receive: Receive = notInitialized

  val notInitialized: Receive = {
    case Avatar.Init(id) =>
      avatarIdStorage ! ReplicatedSet.AddAll(Set(id))
      context.become(receiveWithState(id, None, None, Set.empty, Set(id)))

    case Avatar.Create(id, jarName, className) =>
      Try {
        startChildFromJar(id, jarName, className)
      } match {
        case Failure(exception) =>
          val sw = new StringWriter
          exception.printStackTrace(new PrintWriter(sw))
          sender() ! Avatar.FailedToCreateAvatar(id, sw.toString)
        case Success(value) =>
          avatarIdStorage ! ReplicatedSet.AddAll(Set(id))
          sender() ! Avatar.AvatarCreated(id)
          context.become(receiveWithState(id, None, Some(value), Set.empty, Set(id)))
      }

    case other =>
      log.error("[-] Avatar: not initialized, unknown message [{}] from [{}]", other, sender())
  }

  def receiveWithState(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef],
                       buffer: Set[SensoryInformation.Position], aliveAvatars: Set[String]): Receive =
    handleAvatarMessages(id, tunnel, brain, buffer, aliveAvatars) orElse
      handleBrainMessages(id, tunnel, brain, buffer, aliveAvatars) orElse {
      case s: SubscribeAck =>
      case other => log.error("[-] Avatar: unknown message [{}] from [{}]", other, sender())
    }

  def handleAvatarMessages(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef],
                           buffer: Set[SensoryInformation.Position], aliveAvatars: Set[String]): Receive = {
    case Avatar.Create(_id, jarName, className) if brain.isEmpty =>
      Try {
        startChildFromJar(_id, jarName, className)
      } match {
        case Failure(exception) =>
          val sw = new StringWriter
          exception.printStackTrace(new PrintWriter(sw))
          sender() ! Avatar.FailedToCreateAvatar(_id, sw.toString)
        case Success(value) =>
          avatarIdStorage ! ReplicatedSet.AddAll(Set(id))
          sender() ! Avatar.AvatarCreated(_id)
          context.become(receiveWithState(_id, tunnel, Some(value), buffer, aliveAvatars))
      }

    case Avatar.TunnelEndpoint(_id, endpoint) =>
      context.become(receiveWithState(_id, Some(endpoint), brain, buffer, aliveAvatars))

    case l: ReplicatedSet.LookupResult[SensoryInformation.Position] if sender() == positionStorage =>
      l.result match {
        case Some(data) =>
          val brainPositions = data.map { case SensoryInformation.Position(name, y, x, r, angle) =>
            BrainMessages.Position(name, y, x, r, angle)
          }
          brain.foreach(_ ! dda.brain.BrainMessages.Sensory(brainPositions))
          context.become(receiveWithState(id, tunnel, brain, data, aliveAvatars))

        case None =>
      }

    case l: ReplicatedSet.LookupResult[String] if sender() == avatarIdStorage =>
      l.result match {
        case Some(data) =>
          context.become(receiveWithState(id, tunnel, brain, buffer, data))

        case None =>
      }

    case ReplicatedSet.LookupResult(_) =>

    case p: Avatar.GetState =>
      sender() ! Avatar.State(id, tunnel, brain)
  }

  def handleBrainMessages(id: String, tunnel: Option[ActorRef], brain: Option[ActorRef],
                          buffer: Set[SensoryInformation.Position], aliveAvatars: Set[String]): Receive = {
    case SensoryInformation.Sensory(_, sensoryPayload) =>
      val (obstacles, robots) = sensoryPayload.partition(_.name == Constants.OBSTACLE_NAME)

      // we can delete info about robot with current id, because we have updated information about it's position
      // but there may be no information about robot at all, because it's position may have not changed
      if (robots.nonEmpty) {
        val robotToDelete = buffer.filter(_.name == robots.head.name)
        positionStorage ! ReplicatedSet.RemoveAll(robotToDelete)
      }

      val obstaclesNotPresentInBuffer = {
        def intersects(a: SensoryInformation.Position, b: SensoryInformation.Position): Boolean = {
          Math.pow(a.y - b.y, 2.0) + Math.pow(a.x - b.x, 2.0) <= 0.9 * Math.pow(a.radius + b.radius, 2.0)
        }

        obstacles.filterNot { newPosition =>
          buffer.exists(existingPosition => intersects(existingPosition, newPosition))
        }
      }

      // buffer is what currently in replicated cache
      // we need to add obstacles that not intersect with existing thereby new ones
      positionStorage ! ReplicatedSet.AddAll(obstaclesNotPresentInBuffer ++ robots)

      // updating buffer with newest merged info
      positionStorage ! ReplicatedSet.Lookup

    case BrainMessages.FromAvatarToRobot(message) =>
      tunnel.foreach(_ ! Avatar.FromAvatarToRobot(id, message))

    case Avatar.FromRobotToAvatar(_, message) =>
      brain.foreach(_ ! BrainMessages.FromRobotToAvatar(message))

    case BrainMessages.TellToOtherAvatar(to, message) =>
      if (aliveAvatars.contains(to)) shard ! Avatar.TellToAvatar(to, id, message)

    case Avatar.TellToAvatar(_, from, message) =>
      brain.foreach(_ ! BrainMessages.FromOtherAvatar(from, message))

    case Avatar.ChangeState(_, newState) =>
      log.info("[-] Avatar: changing brains state to [{}]", newState)
      brain.foreach(_ ! BrainMessages.ChangeState(newState))

    case Terminated(a) =>
      if (brain.isDefined && brain.get == a)
        context.become(receiveWithState(id, tunnel, None, buffer, aliveAvatars))
      else if (tunnel.isDefined && tunnel.get == a)
        context.become(receiveWithState(id, None, brain, buffer, aliveAvatars))
  }

  private def startChildFromJar(id: String, jarName: String, className: String) = {
    val url = new URL("jar:file:" + config.getString("application.jars-nfs-directory") + jarName + "!/")
    // may be Thread.currentThread().getContextClassLoader() will be better
    val classLoader = URLClassLoader.newInstance(Array(url), this.getClass.getClassLoader)
    val clazz = classLoader.loadClass(className)
    context.actorOf(Props(clazz.asInstanceOf[Class[BrainActor]], id), className)
  }
}
