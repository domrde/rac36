package pipe

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.client.ClusterClient.Publish
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import messages.Constants._
import messages.Messages.{AvatarCreated, CreateAvatar}

/**
  * Created by dda on 8/25/16.
  */
object LowestLoadFinder {
  @SerialVersionUID(1L) case class PipeInfo(tm: ActorRef, url: String, load: Int)
  @SerialVersionUID(1L) case class ToTmWithLowestLoad(ctr: CreateAvatar, returnAddress: ActorRef)
  @SerialVersionUID(1L) case class ToReturnAddress(at: AvatarCreated, url: String)
}

class LowestLoadFinder extends Actor with ActorLogging {
  import LowestLoadFinder._
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(PIPE_SUBSCRIPTION, self)

  override def receive = initial

  //todo: проверить, что паб/саб не перемешиваются и не мешают друг другу
  val initial: Receive = {
    case ZmqActor.ClientsInfo(url, amount) =>
      mediator ! Publish(PIPE_SUBSCRIPTION, PipeInfo(context.parent, url, amount))
      context.become(receiveWithLoadInfo(Map(context.parent -> (url, amount))))

    case PipeInfo(tm, url, load) =>
      context.become(receiveWithLoadInfo(Map(tm -> (url, load))))

    case s: SubscribeAck =>

    case other =>
      log.error("LowestLoadFinder: other {} from {}", other, sender())
  }


  def receiveWithLoadInfo(info: Map[ActorRef, (String, Int)]): Receive = {
    case ZmqActor.ClientsInfo(url, amount) =>
      mediator ! Publish(PIPE_SUBSCRIPTION, PipeInfo(context.parent, url, amount))

    case PipeInfo(tm, url, load) =>
      context.become(receiveWithLoadInfo(info + (tm -> (url, load))))

    case to @ ToTmWithLowestLoad(ctr, returnAddress) =>
      info.minBy { case (tm, (url, load)) => load }._1 ! to

    case s: SubscribeAck =>

    case other => log.error("LowestLoadFinder: other {} from {}", other, sender())
  }
}