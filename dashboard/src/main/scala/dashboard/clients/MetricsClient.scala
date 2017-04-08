package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import dashboard.{MetricsAggregator, Server}

/**
  * Created by dda on 9/6/16.
  */
object MetricsClient {
  def apply(): Props = Props[MetricsClient]
}

class MetricsClient extends Actor with ActorLogging {

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join(Server.MetricsClient)
  }

  def connected(connection: ActorRef): Receive = {
      case lc: ServerClient.LaunchCommand =>
        context.parent ! lc

      case c: MetricsAggregator.CollectedMetrics =>
        connection ! ServerClient.CollectedMetrics(c.metrics)

      case other =>
        log.error("[-] dashboard.ServerClient: other {} from {}", other, sender())
  }
}
