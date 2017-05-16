package pathfinder

import akka.actor.{Actor, ActorLogging, Props}
import com.dda.brain.PathfinderBrain
import com.typesafe.config.ConfigFactory
import common.messages.SensoryInformation
import pipe.TunnelManager
import play.api.libs.json.{JsValue, Json, Reads}
import upickle.default._
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar
import vivarium.Avatar.Create

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 07.05.17.
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

object Pathfinding {
  def apply(): Props = Props(classOf[Pathfinding])
}

class Pathfinding extends Actor with ActorLogging {
  log.info("Pathfinding actor started")

  private val config = ConfigFactory.load()
  val helper = ZeroMQHelper(context.system)

  val id = "pathfinder"

  private val dealer = helper.connectDealerActor(
    id = id,
    url = "tcp://" + config.getString("pathfinder.zmq-ip"),
    port = config.getInt("pathfinder.zmq-port"),
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = self)


  override def receive: Receive = {
    case TunnelManager.TunnelCreated(_, _, _id) if _id == id =>

    case Avatar.FromAvatarToRobot(_id, message) if _id == id =>
      Try {
        read[PathfinderBrain.FindPath](message)
      } match {
        case Success(value) =>
          context.actorOf(Worker.apply(value))

        case Failure(_) =>
      }

    case pf: PathfinderBrain.PathFound =>
      dealer ! Avatar.FromRobotToAvatar(id, write(pf))

    case other =>
      log.error("[-] Pathfinding: received other: [{}] from [{}]", other, sender())
  }

  dealer ! Create(id, config.getString("pathfinder.brain-jar"), config.getString("pathfinder.brain-class"))

}
