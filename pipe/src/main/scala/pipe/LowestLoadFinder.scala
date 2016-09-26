package pipe

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.client.ClusterClient.Publish
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import common.Constants._
import common.SharedMessages.{AvatarCreated, CreateAvatar}

/**
  * Created by dda on 8/25/16.
  */
object LowestLoadFinder {
  @SerialVersionUID(101L) case class PipeInfo(tm: ActorRef, url: String, load: Int)
  @SerialVersionUID(101L) case class ToTmWithLowestLoad(ctr: CreateAvatar, returnAddress: ActorRef)
  @SerialVersionUID(101L) case class ToReturnAddress(at: AvatarCreated, url: String)
  @SerialVersionUID(101L) case class IncrementClients(url: String)
}

class LowestLoadFinder extends Actor with ActorLogging {
  import LowestLoadFinder._
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(PIPE_SUBSCRIPTION, self)

  override def receive = initial

  //todo: проверить, что паб/саб не перемешиваются и не мешают друг другу
  val initial: Receive = {
    case IncrementClients(url) =>
      mediator ! Publish(PIPE_SUBSCRIPTION, PipeInfo(context.parent, url, 0))
      context.become(receiveWithLoadInfo(Map(context.parent -> (url, 0))))

    case PipeInfo(tm, url, load) =>
      context.become(receiveWithLoadInfo(Map(tm -> (url, load))))

    case s: SubscribeAck =>

    case other =>
      log.error("LowestLoadFinder: other {} from {}", other, sender())
  }


  def receiveWithLoadInfo(info: Map[ActorRef, (String, Int)]): Receive = {
    case IncrementClients(url) =>
      mediator ! Publish(PIPE_SUBSCRIPTION, PipeInfo(context.parent, url, info(context.parent)._2 + 1))

    case PipeInfo(tm, url, load) =>
      context.become(receiveWithLoadInfo(info + (tm -> (url, load))))

    case to @ ToTmWithLowestLoad(ctr, returnAddress) =>
      info.minBy { case (tm, (url, load)) => load }._1 ! to

    case s: SubscribeAck =>

    case other => log.error("LowestLoadFinder: other {} from {}", other, sender())
  }
}
