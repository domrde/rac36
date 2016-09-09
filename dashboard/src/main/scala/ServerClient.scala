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
  case class LaunchCommand(role: String, t: String = "Launch")
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
  implicit val shardingStatsWrite = Json.writes[ShardingStats]
  implicit val nodeUpWrite = Json.writes[ClusterMain.NodeUp]
  implicit val nodeDownWrite = Json.writes[ClusterMain.NodeDown]

  override def receive: Receive = {
    case Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join
  }


  //todo: organize metrics in trait
  def connected(connection: ActorRef): Receive = {
      case IncomingMessage(text) =>
        Json.parse(text).validate[LaunchCommand] match {
          case JsSuccess(value, _) =>
            context.parent ! value

          case JsError(_) =>
            log.error("Failed to validate json [{}]", text)
        }

      case c: ClusterMain.NodeUp =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case c: ClusterMain.NodeDown =>
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
