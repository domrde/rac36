package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Address}
import dashboard.{MetricsAggregator, Server}
import play.api.libs.json.{JsError, JsSuccess, Json}

/**
  * Created by dda on 9/6/16.
  */
object MetricsClient {
  case class LaunchCommand(role: String, t: String = "Launch")
}

class MetricsClient extends Actor with ActorLogging {
  import MetricsClient._

  implicit val launchCommandReads = Json.reads[LaunchCommand]
  implicit val addressWrite = Json.writes[Address]
  implicit val metricWrites = Json.writes[MetricsAggregator.NodeMetrics]
  implicit val metricsWrites = Json.writes[MetricsAggregator.CollectedMetrics]

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join
  }

  def connected(connection: ActorRef): Receive = {
      case ServerClient.IncomingMessage(text) =>
        Json.parse(text).validate[LaunchCommand] match {
          case JsSuccess(value, _) =>
            context.parent ! value

          case JsError(_) =>
            log.error("[-] dashboard.ServerClient: Failed to validate json [{}]", text)
        }

      case c: MetricsAggregator.CollectedMetrics =>
        connection ! ServerClient.OutgoingMessage(Json.stringify(Json.toJson(c)))

      case other =>
        log.error("[-] dashboard.ServerClient: other {} from {}", other, sender())
  }
}
