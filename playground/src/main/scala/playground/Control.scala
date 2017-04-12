package playground

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.config.ConfigFactory
import common.messages.SensoryInformation
import common.messages.SensoryInformation.Sensory
import dashboard.clients.ServerClient.ChangeAvatarState
import pipe.TunnelManager
import play.api.libs.json.{JsValue, Json, Reads}
import upickle.default.write
import utils.test.CameraStub
import utils.test.CameraStub.{GetInfo, MoveRobot}
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar
import vivarium.Avatar.{Create, FromAvatarToRobot}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by dda on 10/4/16.
  */

class ValidatorImpl extends JsonValidator {
  private implicit val tunnelCreatedReads = Json.reads[TunnelManager.TunnelCreated]
  private implicit val failedToCreateTunnelReads = Json.reads[TunnelManager.FailedToCreateTunnel]
  private implicit val controlReads = Json.reads[Avatar.FromAvatarToRobot]
  override val getReads: List[Reads[_ <: AnyRef]] = List(controlReads, tunnelCreatedReads, failedToCreateTunnelReads)
}

class StringifierImpl extends JsonStringifier {
  private implicit val createAvatarWrites = Json.writes[Avatar.Create]
  private implicit val sensoryPositionReadsWrites = Json.writes[SensoryInformation.Position]
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
  private implicit val executionContext = context.dispatcher
  private implicit val materializer = ActorMaterializer()
  private implicit val system = context.system
  private val config = ConfigFactory.load()
  private val camera = context.actorOf(Props(classOf[CameraStub], config.getString("playground.map")))

  log.info("[-] RobotApp started and connected to [{}]", "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + 34671)

  val helper = ZeroMQHelper(context.system)

  private val cameraDealer = helper.connectDealerActor(
    id = "robotapp.camera",
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    port = 34671,
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = self)

  cameraDealer ! Create("robotapp.camera", "brain-assembly-1.0.jar", "com.dda.brain.ParrotBrain")

  private val cars = config.getStringList("playground.car-ids").map { id => (id, context.actorOf(Car(id))) }.toList

  private val messageSource: Source[Message, NotUsed] =
    Source(("robotapp.camera" :: cars.map(_._1)).map(id => TextMessage(write(ChangeAvatarState(id, "Start")))))

  private val printSink: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        println(message.text)
    }

  override def receive: Receive = {
    case Sensory(_, payload) =>
      cameraDealer ! Sensory("robotapp.camera", payload)
      log.info("[-] Playground: Sensory sended")

    case TunnelManager.TunnelCreated(_, _, "robotapp.camera") =>
      context.system.scheduler.schedule(3.second, 3.second, camera, GetInfo)
      log.info("[-] Playground: camera avatar created")
      val wsFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:8888/avatar"))
      messageSource
        .viaMat(wsFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(printSink)(Keep.both) // also keep the Future[Done]
        .run()

    case FromAvatarToRobot(id, _) =>
      log.info("[-] Playground: MoveRobot [{}]", id)
      camera ! MoveRobot(id, 1, 0, 0)

    case other =>
      log.error("[-] Playground: playground.Control received other: [{}]", other)
  }
}
