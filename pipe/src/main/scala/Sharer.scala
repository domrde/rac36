package pipe
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.client.ClusterClient.Publish
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck}
import messages.Constants._
import messages.Messages.AvatarCreated
import pipe.TunnelManager.CreateTunnelRequest

/**
  * Created by dda on 8/25/16.
  */
object Sharer {
  @SerialVersionUID(1L) case class PipeInfo(tm: ActorRef, url: String, load: Int) extends Serializable
  @SerialVersionUID(1L) case class ToTmWithLowestLoad(ctr: CreateTunnelRequest, returnAddress: ActorRef) extends Serializable
  @SerialVersionUID(1L) case class ToReturnAddress(at: AvatarCreated) extends Serializable
}

class Sharer extends Actor with ActorLogging {
  import Sharer._
  ZeroMQ.mediator ! Subscribe(PIPE_SUBSCRIPTION, self)

  override def receive = initial

  //todo: проверить, что паб/саб не перемешиваются и не мешают друг другу
  val initial: Receive = {
    case ZmqActor.ClientsInfo(url, amount) =>
      ZeroMQ.mediator ! Publish(PIPE_SUBSCRIPTION, PipeInfo(context.parent, url, amount))
      context.become(receiveWithLoadInfo(Map(context.parent -> (url, amount))))

    case PipeInfo(tm, url, load) =>
      log.debug("Info update for subscription")
      context.become(receiveWithLoadInfo(Map(tm -> (url, load))))

    case s: SubscribeAck =>

    case other =>
      log.error("Other {} from {}", other, sender())
  }


  def receiveWithLoadInfo(info: Map[ActorRef, (String, Int)]): Receive = {
    case ZmqActor.ClientsInfo(url, amount) =>
      ZeroMQ.mediator ! Publish(PIPE_SUBSCRIPTION, PipeInfo(context.parent, url, amount))

    case PipeInfo(tm, url, load) =>
      context.become(receiveWithLoadInfo(info + (tm -> (url, load))))

    case to @ ToTmWithLowestLoad(ctr, returnAddress) =>
      info.minBy { case (tm, (url, load)) => load }._1 ! to

    case s: SubscribeAck =>

    case other => log.error("Other {} from {}", other, sender())
  }
}
