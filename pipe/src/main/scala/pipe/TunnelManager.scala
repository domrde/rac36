package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import com.typesafe.config.ConfigFactory
import messages.Messages.{AvatarCreated, _}
import pipe.LowestLoadFinder.{ToReturnAddress, ToTmWithLowestLoad}

import scala.util.Random

/**
  * Created by dda on 24.04.16.
  */
//todo: use cluster metrics-based selection of lowest loaded TM
//todo: use pool of ZmqActor to lower socket load http://doc.akka.io/docs/akka/current/scala/routing.html
class TunnelManager extends Actor with ActorLogging {

  val config = ConfigFactory.load()
  val url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + config.getInt("application.ports.input")
  val worker = context.actorOf(ZmqActor(url), "QueueToActor" + Random.nextLong())
  val lowestFinder = context.actorOf(Props[LowestLoadFinder], "Sharer")
  val avatarAddress = config.getString("application.avatarAddress")
  val mediator = DistributedPubSub(context.system).mediator
  worker.tell(ZmqActor.HowManyClients, lowestFinder)

  override def receive = receiveWithClientsStorage(Map.empty)

  def receiveWithClientsStorage(clients: Map[String, ActorRef]): Receive = {
    case ctr: CreateAvatar =>
      lowestFinder ! ToTmWithLowestLoad(ctr, self)
      log.info("Tunnel create request, sending to lowest load")

    case ToTmWithLowestLoad(ctr, returnAddress) =>
      mediator ! Send(avatarAddress, ctr, localAffinity = false)
      log.info("I'm with lowest load, requesting avatar")
      context.become(receiveWithClientsStorage(clients + (ctr.id -> returnAddress)))

    case ac @ AvatarCreated(id) =>
      worker ! ZmqActor.WorkWithQueue(id)
      clients(id) ! ToReturnAddress(ac, url)
      context.become(receiveWithClientsStorage(clients - id))
      log.info("Avatar and tunnel created with id [{}], sending result to original sender [{}]", id, sender())

    case ToReturnAddress(ac, tunnelUrl) =>
      val id = ac.id
      worker ! ZmqActor.TunnelCreated(tunnelUrl, id.toString)
      log.info("I'm the original sender. Printing tunnel info with topic [{}] to client.", id)

    case other =>
      log.error("TunnelManager: other {} from {}", other, sender())
  }

  log.info("TunnelManager initialized for parent [{}] with avatarAddress [{}] and mediator [{}]",
    context.parent, avatarAddress, mediator)
}
