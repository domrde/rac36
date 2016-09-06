import akka.actor.{Actor, ActorLogging, Props}

/**
  * Created by dda on 9/6/16.
  */
class MetricsAggregator extends Actor with ActorLogging {
  val listeners = Set (
    context.actorOf(Props[ClusterMetricsListener], "ClusterMetricsListener"),
    context.actorOf(Props[DdataListener], "DdataListener"),
    context.actorOf(Props[ShardingStatsListener], "ShardingStatsListener")
  )

  val server = context.actorOf(Props[Server], "Server")


  override def receive: Receive = {
    case anything if listeners.contains(sender()) =>
      server ! anything

    case other =>
      log.error("MetricsAggregator: other {} from {}", other, sender())
  }
}
