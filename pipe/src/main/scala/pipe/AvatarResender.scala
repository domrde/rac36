package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ClusterSharding
import common.messages.NumeratedMessage
import pipe.AvatarResender.WorkWithQueue
import vivarium.Avatar._

/**
  * Created by dda on 9/21/16.
  */
// todo: remove dead clients
object AvatarResender {
  case class WorkWithQueue(topic: String, zmqActor: ActorRef)

  def apply(tunnelManager: ActorRef) = {
    Props(classOf[AvatarResender], tunnelManager)
  }
}

class AvatarResender(tunnelManager: ActorRef) extends Actor with ActorLogging {

  override def receive = receiveWithClients(Set.empty)

  val shard = ClusterSharding(context.system).shardRegion("Avatar")

  def receiveWithClients(clients: Set[String]): Receive = {
    case WorkWithQueue(topic, zmqActor) =>
      context.become(receiveWithClients(clients + topic))
      shard ! TunnelEndpoint(topic, zmqActor)

    // Tunnel manager creates tunnel
    case n: NumeratedMessage if sender() == tunnelManager =>
      shard ! n

    case n: NumeratedMessage if clients.contains(n.id) =>
      shard ! n

    // This happens when robot sends first messages to router, since router knows only about resender,
    // resender must redirect that first communication to tunnel manager.
    // Further communication will match under clients.contains branch.
    case c: AvatarMessage =>
      tunnelManager ! c
      log.info("[-] AvatarResender: AvatarMessage [{}] from [{}]", c, sender())

    case other =>
      log.error("[-] AvatarResender: Not a numerated message or id unknown: [{}] from [{}]", other, sender())
  }
}
