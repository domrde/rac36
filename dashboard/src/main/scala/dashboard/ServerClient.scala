package dashboard

import akka.actor.{Actor, ActorLogging, ActorRef, Address}
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
  implicit val launchCommandReads = Json.reads[LaunchCommand]
  implicit val metricWrites = Json.writes[MetricsAggregator.NodeMetrics]
  implicit val metricsWrites = Json.writes[MetricsAggregator.CollectedMetrics]

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
            log.error("[-] dashboard.ServerClient: Failed to validate json [{}]", text)
        }

      case c: MetricsAggregator.CollectedMetrics =>
        connection ! OutgoingMessage(Json.stringify(Json.toJson(c)))

      case other =>
        log.error("[-] dashboard.ServerClient: other {} from {}", other, sender())
  }
}
