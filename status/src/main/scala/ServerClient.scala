import akka.actor.{Actor, ActorLogging, ActorRef, Address}
import messages.Messages.CoordinateWithType
import play.api.libs.json.Json

/**
  * Created by dda on 9/6/16.
  */
object ServerClient {
  case class Connected(connection: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
}

class ServerClient extends Actor with ActorLogging {
  import ServerClient._

  implicit val addressWrite = Json.writes[Address]
  implicit val memoryMetricsWrite = Json.writes[ClusterMetricsListener.MemoryMetrics]
  implicit val cpuMetricsWrite = Json.writes[ClusterMetricsListener.CpuMetrics]
  implicit val coordWrite = Json.writes[CoordinateWithType]
  implicit val ddataStatusWrite = Json.writes[DdataListener.DdataStatus]
  implicit val shardMetricWrite = Json.writes[ShardingStatsListener.ShardMetric]
  implicit val shardMetricsWrite = Json.writes[ShardingStatsListener.ShardMetrics]

  override def receive: Receive = {
    case Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join
  }

  def connected(connection: ActorRef): Receive = {
      case IncomingMessage(text) =>
        connection ! OutgoingMessage(text)

      case c: ClusterMetricsListener.MemoryMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ClusterMetricsListener.CpuMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: DdataListener.DdataStatus =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ShardingStatsListener.ShardMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case other =>
        log.error("ServerClient: other {} from {}", other, sender())
  }
}
