package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ClusterSharding
import common.SharedMessages.{AvatarCreated, CreateAvatar, NumeratedMessage, TunnelEndpoint}
import pipe.AvatarResender.WorkWithQueue

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

    case n: NumeratedMessage if clients.contains(n.id) =>
      shard ! n

    case n: NumeratedMessage if sender() == tunnelManager =>
      shard ! n

    case c: AvatarCreated =>
      tunnelManager ! c

    case c: CreateAvatar =>
      tunnelManager ! c

    case other =>
      log.error("Not a numerated message or id unknown: [{}]", other)
  }
}
