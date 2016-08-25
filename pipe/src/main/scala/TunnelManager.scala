package pipe
import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.typesafe.config.ConfigFactory
import messages.Constants._
import messages.Messages.{AvatarCreated, _}

import scala.util.Random

/**
  * Created by dda on 24.04.16.
  */

object TunnelManager {
  @SerialVersionUID(2131232L)
  case class CreateTunnel(uuid: String, api: Api)
  @SerialVersionUID(2131233L)
  case class TunnelCreated(topic: String)
}

class TunnelManager extends Actor with ActorLogging {
  import TunnelManager._

  val config = ConfigFactory.load()
  val worker = context.actorOf(ZeroMQActor(config.getInt("my.own.ports.input")), "QueueToActor" + Random.nextLong())

  override def receive: Receive = {
    case CreateTunnel(uuid, api) =>
      ZeroMQ.mediator ! Publish(ACTOR_CREATION_SUBSCRIPTION, CreateAvatar(UUID.fromString(uuid), api))
      log.debug("Tunnel create request")
    case AvatarCreated(uuid) =>
      worker ! ZeroMQActor.WorkWithQueue(uuid.toString, sender())
      log.info("Creating tunnel with topic {} to actor {}.", uuid, sender())
    case other => log.error("Other {} from {}", other, sender())
  }

  log.debug("TunnelManager initialized for parent {}", context.parent)
}
