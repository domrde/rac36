package playground

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.config.ConfigFactory
import common.messages.SensoryInformation
import common.messages.SensoryInformation.Sensory
import dashboard.clients.ServerClient.ChangeAvatarState
import pipe.TunnelManager
import play.api.libs.json.{JsValue, Json, Reads}
import upickle.default.write
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar
import vivarium.Avatar.Create

import scala.collection.JavaConversions._
import scala.concurrent.Future

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

class Control extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private implicit val materializer = ActorMaterializer()
  private implicit val system = context.system
  private val config = ConfigFactory.load()

  log.info("[-] RobotApp started and connected to [{}]", "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + 34671)

  val helper = ZeroMQHelper(context.system)

  private val vrep = context.actorOf(Props[VRepRemoteControl])

  private val cars: Map[String, ActorRef] = config.getStringList("playground.car-ids").map { id =>
    id -> context.actorOf(Car(id, vrep)) }.toMap

  private val cameraDealer = helper.connectDealerActor(
    id = "camera",
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    port = 34671,
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = self)

  cameraDealer ! Create("camera", config.getString("playground.brain-jar"), "com.dda.brain.ParrotBrain")

  override def receive: Receive = {
    case Sensory(_, payload) =>
      val correctPayload = payload.filterNot { case SensoryInformation.Position(name, y, x, radius, angle) =>
        y.isNaN || y.isInfinity || x.isNaN || x.isInfinity
      }
      cameraDealer ! Sensory("camera", correctPayload)

    case TunnelManager.TunnelCreated(_, _, "camera") =>
      log.info("Starting brains")
      startBrains()

    case other =>
      log.error("[-] Playground: playground.Control received other: [{}]", other)
  }

  private def startBrains(): Unit = {
    val messageSource: Source[Message, NotUsed] =
      Source(("camera" :: cars.keys.toList).map(id => TextMessage(write(ChangeAvatarState(id, "Start")))))

    val printSink: Sink[Message, Future[Done]] =
      Sink.foreach {
        case message: TextMessage.Strict =>
          println(message.text)
      }

    val wsFlow = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:8888/avatar"))
    messageSource
      .viaMat(wsFlow)(Keep.right)
      .toMat(printSink)(Keep.both)
      .run()

    log.info("Brains started")
  }

}
