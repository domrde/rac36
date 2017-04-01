import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import messages.SensoryInformation.Sensory
import messages.{RobotMessages, SensoryInformation}
import pipe.TunnelManager
import play.api.libs.json.{JsValue, Json, Reads}
import utils.test.CameraStub
import utils.test.CameraStub.GetInfo
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar
import vivarium.Avatar.Create

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by dda on 10/4/16.
  */

class ValidatorImpl extends JsonValidator {
  private implicit val tunnelCreatedReads = Json.reads[TunnelManager.TunnelCreated]
  private implicit val failedToCreateTunnelReads = Json.reads[TunnelManager.FailedToCreateTunnel]
  private implicit val controlReads = Json.reads[RobotMessages.Control]
  override val getReads: List[Reads[_ <: AnyRef]] = List(controlReads, tunnelCreatedReads, failedToCreateTunnelReads)
}

class StringifierImpl extends JsonStringifier {
  private implicit val createAvatarWrites = Json.writes[Avatar.Create]
  private implicit val sensoryReadsWrites = Json.writes[SensoryInformation.Sensory]
  override def toJson(msg: AnyRef): Option[JsValue] = {
    msg match {
      case a: Avatar.Create => Some(createAvatarWrites.writes(a))
      case a: SensoryInformation.Sensory => Some(sensoryReadsWrites.writes(a))
      case _ => None
    }
  }
}

class Control extends Actor with ActorLogging {

  val config = ConfigFactory.load()
  val camera = context.actorOf(Props(classOf[CameraStub], config.getString("robotapp.map")))
  val classes = config.getStringList("robotapp.robots").zipWithIndex

  val helper = ZeroMQHelper(context.system)

  private val robots = classes.map { case (clazz, idx) => (
    idx,
    clazz,
    helper.connectDealerActor(
      id = idx.toString,
      url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
      port = 34671,
      validator = Props[ValidatorImpl],
      stringifier = Props[StringifierImpl],
      targetAddress = self)
  )}

  private val cameraDealer = helper.connectDealerActor(
    id = "robotapp.camera",
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    port = 34671,
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = self)

  log.info("[-] RobotApp started and connected to [{}]", "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + 34671)

  implicit val timeout: Timeout = 3.second
  Future.sequence(robots.map { case (idx, clazz, robot) =>
    robot ? Create(idx.toString, "brain-assembly-1.0.jar", clazz)
  }).onComplete(_ => context.system.scheduler.schedule(3.second, 3.second, camera, GetInfo))

  cameraDealer ! Create("robotapp.camera", "brain-assembly-1.0.jar", "com.dda.brain.SensorBrain")

  override def receive: Receive = {
    case Sensory(_, payload) =>
      cameraDealer ! Sensory("robotapp.camera", payload)
      log.info("[-] RobotApp: Sensory sended")

    case other =>
      log.error("[-] RobotApp: Control received other: [{}]", other)
  }
}
