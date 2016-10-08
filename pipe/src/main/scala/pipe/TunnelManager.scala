package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import common.SharedMessages.{AvatarCreated, _}
import common.zmqHelpers.ZeroMQHelper
import pipe.LowestLoadFinder.{IncrementClients, ToReturnAddress, ToTmWithLowestLoad}

/**
  * Created by dda on 24.04.16.
  */
//todo: check balancing really works
//todo: use cluster metrics-based selection of lowest loaded TM
//todo: use pool of ZmqRouter to lower socket load http://doc.akka.io/docs/akka/current/scala/routing.html
class TunnelManager extends Actor with ActorLogging {

  val config = ConfigFactory.load()

  val url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + config.getInt("pipe.ports.input")
  val port = config.getInt("pipe.ports.input")

  val zmqReceiver = context.actorOf(AvatarResender(self))
  val worker = ZeroMQHelper(context.system).bindRouterActor(
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    port = port,
    zmqReceiver
  )

  val lowestFinder = context.actorOf(Props[LowestLoadFinder], "Sharer")
  lowestFinder ! IncrementClients(url)

  override def receive = receiveWithClientsStorage(Map.empty)

  def receiveWithClientsStorage(clients: Map[String, ActorRef]): Receive = {
    case ctr: CreateAvatar =>
      lowestFinder ! ToTmWithLowestLoad(ctr, self)
      log.info("Tunnel create request, sending to lowest load")

    case ToTmWithLowestLoad(ctr, returnAddress) =>
      zmqReceiver ! ctr
      log.info("I'm with lowest load, requesting avatar")
      context.become(receiveWithClientsStorage(clients + (ctr.id -> returnAddress)))

    case ac @ AvatarCreated(id) =>
      clients(id) ! ToReturnAddress(ac, url)
      zmqReceiver ! AvatarResender.WorkWithQueue(id, worker)
      lowestFinder ! IncrementClients(url)
      context.become(receiveWithClientsStorage(clients - id))
      log.info("Avatar and tunnel created with id [{}], sending result to original sender [{}]", id, sender())

    case ToReturnAddress(ac, tunnelUrl) =>
      worker ! TunnelCreated(tunnelUrl, ac.id.toString)
      log.info("I'm the original sender. Printing tunnel info with topic [{}] to client.", ac.id)

    case other =>
      log.error("TunnelManager: other {} from {}", other, sender())
  }

  log.info("TunnelManager initialized for parent [{}] and listens on [{}]",
    context.parent, url)
}
