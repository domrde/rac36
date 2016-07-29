package pipe
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.typesafe.config.ConfigFactory
import messages.Messages._
import messages.Constants._

import scala.util.Random

/**
  * Created by dda on 24.04.16.
  */

object TunnelManager {
  @SerialVersionUID(2131232L)
  case class CreateTunnel(target: ActorRef)
  @SerialVersionUID(2131233L)
  case class TunnelCreated(uuid: String, inputPort: String, outputPort: String, in: ActorRef, out: ActorRef) {
    def this(uuid: String, info: TunnelInfo) = {
      this(uuid, info.inputPort, info.outputPort, info.in, info.out)
    }
  }

  case class TunnelInfo(inputPort: String, outputPort: String, in: ActorRef, out: ActorRef)
}

class TunnelManager extends Actor with ActorLogging {
  import TunnelManager._

  val config = ConfigFactory.load()

  // Pair of actors for each pair of ports with their current load that is 0
  var tunnels: Map[TunnelInfo, Int] = (config.getInt("my.own.ports.lower") to config.getInt("my.own.ports.upper")).grouped(2).flatMap {
    case Seq(a, b) => Some(TunnelInfo(
      String.valueOf(a),
      String.valueOf(b),
      context.system.actorOf(QueueToActor(a), "QueueToActor" + Random.nextLong()),
      context.system.actorOf(ActorToQueue(b), "ActorToQueue" + Random.nextLong())
    ))
    case _ => None
  } map (a => (a, 0)) toMap

  override def receive: Receive = {
    case CreateTunnel(target) =>
      ZeroMQ.mediator ! Publish(ACTOR_CREATION_SUBSCRIPTION, CreateAvatar)
    case AvatarCreated(avatar) =>
      val uuid = UUID.randomUUID().toString
      val withLowestLoad = tunnels.min(Ordering.by((pair: (TunnelInfo, Int)) => pair._2))._1
      tunnels += withLowestLoad -> (tunnels(withLowestLoad) + 1)
      withLowestLoad.in ! QueueToActor.ReadFromQueue(uuid, avatar)
      withLowestLoad.out ! ActorToQueue.WriteToQueue(avatar, uuid)
      log.info("Creating tunnel with topic {} to actor {}. {} -> {}", uuid, avatar, withLowestLoad.in, withLowestLoad.out)
      sender() ! new TunnelCreated(uuid, withLowestLoad)
    case _ =>
  }
}
