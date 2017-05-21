package playground

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import common.messages.SensoryInformation
import common.messages.SensoryInformation.Sensory
import pipe.TunnelManager
import play.api.libs.json.{JsValue, Json, Reads}
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar
import vivarium.Avatar.{Create, FromAvatarToRobot, FromRobotToAvatar}

class ValidatorImpl extends JsonValidator {
  private implicit val tunnelCreatedReads = Json.reads[TunnelManager.TunnelCreated]
  private implicit val failedToCreateTunnelReads = Json.reads[TunnelManager.FailedToCreateTunnel]
  private implicit val controlReads = Json.reads[Avatar.FromAvatarToRobot]
  override val getReads: List[Reads[_ <: AnyRef]] = List(controlReads, tunnelCreatedReads, failedToCreateTunnelReads)
}

class StringifierImpl extends JsonStringifier {
  private implicit val createAvatarWrites = Json.writes[Avatar.Create]
  private implicit val sensoryPositionWrites = Json.writes[SensoryInformation.Position]
  private implicit val sensoryWrites = Json.writes[SensoryInformation.Sensory]
  override def toJson(msg: AnyRef): Option[JsValue] = {
    msg match {
      case a: Avatar.Create => Some(createAvatarWrites.writes(a))
      case a: SensoryInformation.Sensory => Some(sensoryWrites.writes(a))
      case _ => None
    }
  }
}

class ZMQConnection(id: String) extends Actor with ActorLogging {
  private val helper = ZeroMQHelper(context.system)
  private val config = ConfigFactory.load()

  override def receive: Receive = receiveWithConnection {
    val avatar = helper.connectDealerActor(
      id = id,
      url = "tcp://" + config.getString("playground.zmq-ip"),
      port = config.getInt("playground.zmq-port"),
      validator = Props[ValidatorImpl],
      stringifier = Props[StringifierImpl],
      targetAddress = self)

    if (id == "camera") {
      avatar ! Create(id, config.getString("playground.brain-jar"), config.getString("playground.camera-class"))
    } else {
      avatar ! Create(id, config.getString("playground.brain-jar"), config.getString("playground.brain-class"))
    }

    avatar
  }

  def receiveWithConnection(avatar: ActorRef): Receive = {
    case TunnelManager.TunnelCreated(url, port, _) =>
      log.info("ZMQConnection [{}] got it's avatar on url [{}]", id, s"$url:$port")
      // todo: Here must be reconnection to that url:port, but it's not working

    case f: Sensory =>
      avatar ! f

    case f: FromAvatarToRobot =>
      context.parent ! f

    case f: FromRobotToAvatar =>
      avatar ! f

    case other =>
      log.error("ZMQConnection: unknown message [{}] from [{}]", other, sender())
  }
}