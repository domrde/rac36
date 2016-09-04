package pipe
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import com.typesafe.config.ConfigFactory
import messages.Messages.{AvatarCreated, _}
import pipe.LowestLoadFinder.{ToReturnAddress, ToTmWithLowestLoad}

import scala.util.Random

/**
  * Created by dda on 24.04.16.
  */

object TunnelManager {
  @SerialVersionUID(1L) case class CreateTunnelRequest(uuid: String, api: Api) extends Serializable
}

class TunnelManager extends Actor with ActorLogging {
  import TunnelManager._

  // todo: перейти на пул воркеров для снижения нагрузки на сокет http://doc.akka.io/docs/akka/current/scala/routing.html
  val config = ConfigFactory.load()
  val worker = context.actorOf(ZmqActor(config.getInt("application.ports.input")), "QueueToActor" + Random.nextLong())
  val lowestFinder = context.actorOf(Props[LowestLoadFinder], "Sharer")
  val avatarAddress = config.getString("application.avatarAddress")
  worker.tell(ZmqActor.HowManyClients, lowestFinder)

  override def receive = receiveWithClientsStorage(Map.empty)

  def receiveWithClientsStorage(clients: Map[String, ActorRef]): Receive = {
    case ctr: CreateTunnelRequest =>
      lowestFinder ! ToTmWithLowestLoad(ctr, self)
      log.info("Tunnel create request, sending to lowest load")

    case ToTmWithLowestLoad(ctr, returnAddress) =>
      ZeroMQ.mediator ! Send(avatarAddress, CreateAvatar(UUID.fromString(ctr.uuid), ctr.api), localAffinity = false)
      log.info("I'm with lowest load, requesting avatar")
      context.become(receiveWithClientsStorage(clients + (ctr.uuid -> returnAddress)))

    case ac @ AvatarCreated(uuid) =>
      clients(uuid.toString) ! ToReturnAddress(ac)
      context.become(receiveWithClientsStorage(clients - uuid.toString))
      log.info("Avatar created with uuid [{}], sending it to original sender [{}]", uuid, sender())

    case ToReturnAddress(ac) =>
      val uuid = ac.uuid
      worker ! ZmqActor.WorkWithQueue(uuid)
      log.info("I'm the original sender. Creating tunnel with topic [{}] to actor [{}].", uuid, sender())

    case other =>
      log.error("Other {} from {}", other, sender())
  }

  log.debug("TunnelManager initialized for parent {}", context.parent)
}
