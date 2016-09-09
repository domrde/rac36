import ShardingStatsListener.ShardingStats
import akka.actor.{Actor, ActorLogging, ActorRef, Address}
import messages.Messages.CoordinateWithType
import play.api.libs.json.{JsError, JsSuccess, Json}

/**
  * Created by dda on 9/6/16.
  */
object ServerClient {
  case class Connected(connection: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
  case class LaunchCommand(image: String, t: String = "Launch")
  case class Launched(image: String, t: String = "Launched")
}

class ServerClient extends Actor with ActorLogging {
  import ServerClient._

  implicit val addressWrite = Json.writes[Address]
  implicit val memoryMetricsWrite = Json.writes[ClusterMetricsListener.MemoryMetrics]
  implicit val cpuMetricsWrite = Json.writes[ClusterMetricsListener.CpuMetrics]
  implicit val coordWrite = Json.writes[CoordinateWithType]
  implicit val ddataStatusWrite = Json.writes[DdataListener.DdataStatus]
  implicit val shardMetricWrite = Json.writes[ShardingStatsListener.RegionMetric]
  implicit val shardMetricsWrite = Json.writes[ShardingStatsListener.RegionMetrics]
  implicit val launchCommandReads = Json.reads[LaunchCommand]
  implicit val launchedWrite = Json.writes[Launched]
  implicit val shardingStatsWrite = Json.writes[ShardingStats]
  implicit val nodesInfoWrite = Json.writes[ClusterMetricsListener.NodeInfo]
  implicit val nodesStatusWrite = Json.writes[ClusterMetricsListener.NodesStatus]

  override def receive: Receive = {
    case Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join
  }

  def connected(connection: ActorRef): Receive = {
      case IncomingMessage(text) =>
        Json.parse(text).validate[LaunchCommand] match {
          case JsSuccess(value, _) =>
            context.parent ! value

          case JsError(_) =>
            log.error("Failed to validate json [{}]", text)
        }

      case c: ClusterMetricsListener.NodesStatus =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: Launched =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ShardingStats =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ClusterMetricsListener.MemoryMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ClusterMetricsListener.CpuMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: DdataListener.DdataStatus =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ShardingStatsListener.RegionMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case other =>
        log.error("ServerClient: other {} from {}", other, sender())
  }
}
