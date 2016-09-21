package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import com.typesafe.config.ConfigFactory
import common.SharedMessages.{CreateAvatar, NumeratedMessage, TunnelEndpoint}
import pipe.AvatarResender.WorkWithQueue

/**
  * Created by dda on 9/21/16.
  */
// todo: remove dead clients
object AvatarResender {
  case class WorkWithQueue(topic: String)

  def apply(tunnelManager: ActorRef) = {
    Props(classOf[AvatarResender], tunnelManager)
  }
}

class AvatarResender(tunnelManager: ActorRef) extends Actor with ActorLogging {

  val config = ConfigFactory.load()

  val avatarAddress = config.getString("application.avatarAddress")

  val mediator = DistributedPubSub(context.system).mediator

  def sendToAvatar(msg: NumeratedMessage) = mediator ! Send(avatarAddress, msg, localAffinity = false)

  override def receive = receiveWithClients(Set.empty)

  def receiveWithClients(clients: Set[String]): Receive = {
    case WorkWithQueue(topic) =>
      context.become(receiveWithClients(clients + topic))
      sendToAvatar(TunnelEndpoint(topic, self))

    case c: CreateAvatar =>
      tunnelManager ! c

    case n: NumeratedMessage if clients.contains(n.id) =>
      sendToAvatar(n)

    case other =>
      log.error("Not a numerated message or id unknown: [{}]", other)
  }
}
