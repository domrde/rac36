package pathfinder

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import common.messages.SensoryInformation
import dashboard.clients.ServerClient.ChangeAvatarState
import pipe.TunnelManager
import play.api.libs.json.{JsValue, Json, Reads}
import upickle.default.write
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar
import vivarium.Avatar.Create

import scala.concurrent.Future

/**
  * Created by dda on 02.05.17.
  */
class ValidatorImpl extends JsonValidator {
  private implicit val tunnelCreatedReads = Json.reads[TunnelManager.TunnelCreated]
  private implicit val failedToCreateTunnelReads = Json.reads[TunnelManager.FailedToCreateTunnel]
  private implicit val controlReads = Json.reads[Avatar.FromAvatarToRobot]
  override val getReads: List[Reads[_ <: AnyRef]] = List(controlReads, tunnelCreatedReads, failedToCreateTunnelReads)
}

class StringifierImpl extends JsonStringifier {
  private implicit val createAvatarWrites = Json.writes[Avatar.Create]
  private implicit val sensoryPositionWrites = Json.writes[SensoryInformation.Position]
  private implicit val fromRobotToAvatarWrites = Json.writes[Avatar.FromRobotToAvatar]
  override def toJson(msg: AnyRef): Option[JsValue] = {
    msg match {
      case a: Avatar.Create => Some(createAvatarWrites.writes(a))
      case a: Avatar.FromRobotToAvatar => Some(fromRobotToAvatarWrites.writes(a))
      case _ => None
    }
  }
}

class Control extends Actor with ActorLogging {
  log.info("Pathfinder started")

  val helper = ZeroMQHelper(context.system)

  context.actorOf(Pathfinding(), "Pathfinding")

  override def receive: Receive = {
    case other =>
      log.error("[-] Pathfinder: pathfinder.Control received other: [{}] from [{}]", other, sender())
  }

  // Start brains removed because brains are in working state initially.
}
