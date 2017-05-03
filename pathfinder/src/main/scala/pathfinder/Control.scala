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
  private implicit val executionContext = context.dispatcher
  private implicit val materializer = ActorMaterializer()
  private implicit val system = context.system
  private val config = ConfigFactory.load()

  log.info("Pathfinder started")

  val helper = ZeroMQHelper(context.system)

  private val dealer = helper.connectDealerActor(
    id = "camera",
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    port = 34671,
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = self)

  dealer ! Create("pathfinder", config.getString("pathfinder.brain-jar"), "com.dda.brain.PathfinderBrain")

  override def receive: Receive = {
    case TunnelManager.TunnelCreated(_, _, "pathfinder") =>
      startBrains()
      log.info("[-] Pathfinder: TunnelCreated")

    case Avatar.FromAvatarToRobot("pathfinder", message) =>
      log.info("[-] Pathfinder: FromAvatarToRobot")
      val response = "Response"
      dealer ! Avatar.FromRobotToAvatar("pathfinder", response)

    case other =>
      log.error("[-] Pathfinder: pathfinder.Control received other: [{}]", other)
  }

  private def startBrains(): Unit = {
    val messageSource: Source[Message, NotUsed] =
      Source(List(TextMessage(write(ChangeAvatarState("pathfinder", "Start")))))

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
