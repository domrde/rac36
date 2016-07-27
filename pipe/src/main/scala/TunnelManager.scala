package pipe
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.ConfigFactory

/**
  * Created by dda on 24.04.16.
  */

object TunnelManager {
  @SerialVersionUID(2131232L)
  case class CreateTunnel(target: ActorRef)
  @SerialVersionUID(2131233L)
  case class TunnelCreated(uuid: String, in: ActorRef, out: ActorRef)
}

class TunnelManager extends Actor with ActorLogging {
  import TunnelManager._

  val config = ConfigFactory.load()

  // Pair of actors for each pair of ports with their current load that is 0
  var tunnels = (config.getInt("my.own.ports.lower") to config.getInt("my.own.ports.upper")).grouped(2).flatMap {
    case Seq(a, b) => Some {
      (context.system.actorOf(QueueToActor(a), String.valueOf(a)), context.system.actorOf(ActorToQueue(b), String.valueOf(b)))
    }
    case _ => None
  } map (a => (a, 0)) toMap

  override def receive: Receive = {
    case CreateTunnel(target) =>
      val uuid = UUID.randomUUID().toString
      val withLowestLoad = tunnels.min(Ordering.by((pair: ((ActorRef, ActorRef), Int)) => pair._2))._1
      tunnels += withLowestLoad -> (tunnels(withLowestLoad) + 1)
      withLowestLoad._1 ! QueueToActor.ReadFromQueue(uuid, target)
      withLowestLoad._2 ! ActorToQueue.WriteToQueue(target, uuid)
      log.info("Creating tunnel with topic {} to actor {}. {} -> {}", uuid, target, withLowestLoad._1.path.name, withLowestLoad._2.path.name)
      sender() ! TunnelCreated(uuid, withLowestLoad._1, withLowestLoad._2)
    case _ =>
  }
}
