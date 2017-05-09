package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import common.messages.{NumeratedMessage, SensoryInformation}
import pipe.LowestLoadFinder.{IncrementClients, ToReturnAddress, ToTmWithLowestLoad}
import pipe.TunnelManager.{FailedToCreateTunnel, TunnelCreated}
import play.api.libs.json.{JsValue, Json, Reads}
import utils.zmqHelpers.{JsonStringifier, JsonValidator, ZeroMQHelper}
import vivarium.Avatar

/**
  * Created by dda on 24.04.16.
  */
//todo: check that tunnel is active only if robot connected to appointed PIPE
//todo: check balancing really works
//todo: use cluster metrics-based selection of lowest loaded TM
//todo: use pool of ZmqRouter to lower socket load http://doc.akka.io/docs/akka/current/scala/routing.html
object TunnelManager {
  trait TunnelCreateResponse extends NumeratedMessage
  @SerialVersionUID(101L) case class TunnelCreated(url: String, port: Int, id: String) extends TunnelCreateResponse
  @SerialVersionUID(101L) case class FailedToCreateTunnel(id: String, reason: String) extends TunnelCreateResponse
}

class ValidatorImpl extends JsonValidator {
  private implicit val createAvatarReads = Json.reads[Avatar.Create]
  private implicit val fromRobotToAvatarReads = Json.reads[Avatar.FromRobotToAvatar]
  private implicit val positionReads = Json.reads[SensoryInformation.Position]
  private implicit val sensoryReads = Json.reads[SensoryInformation.Sensory]
  override val getReads: List[Reads[_ <: AnyRef]] = List(createAvatarReads, sensoryReads, fromRobotToAvatarReads)
}

class StringifierImpl extends JsonStringifier {
  private implicit val tunnelCreatedWrites = Json.writes[TunnelManager.TunnelCreated]
  private implicit val failedToCreateTunnelWrites = Json.writes[TunnelManager.FailedToCreateTunnel]
  private implicit val controlWrites = Json.writes[Avatar.FromAvatarToRobot]
  override def toJson(msg: AnyRef): Option[JsValue] = {
    msg match {
      case a: TunnelManager.TunnelCreated => Some(tunnelCreatedWrites.writes(a))
      case a: TunnelManager.FailedToCreateTunnel => Some(failedToCreateTunnelWrites.writes(a))
      case a: Avatar.FromAvatarToRobot => Some(controlWrites.writes(a))
      case _ => None
    }
  }
}

class TunnelManager extends Actor with ActorLogging {

  private val config = ConfigFactory.load()

  private val url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname")
  private val port = config.getInt("pipe.ports.input")

  private val avatarResender = context.actorOf(AvatarResender(self), name = "AvatarResender")

  private val worker = ZeroMQHelper(context.system).bindRouterActor(
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    port = port,
    validator = Props[ValidatorImpl],
    stringifier = Props[StringifierImpl],
    targetAddress = avatarResender
  )

  private val lowestFinder = context.actorOf(Props[LowestLoadFinder], "Sharer")
  lowestFinder ! IncrementClients(url)

  override def receive: Receive = receiveWithClientsStorage(Map.empty)

  def receiveWithClientsStorage(clients: Map[String, ActorRef]): Receive = {
    case ctr: Avatar.Create =>
      log.info("[-] TunnelManager: Tunnel create request, sending to lowest load")
      lowestFinder ! ToTmWithLowestLoad(ctr, self)

    case ToTmWithLowestLoad(ctr, returnAddress) =>
      log.info("[-] TunnelManager: I'm with lowest load, requesting avatar")
      avatarResender ! ctr
      context.become(receiveWithClientsStorage(clients + (ctr.id -> returnAddress)))

    case ac @ Avatar.AvatarCreated(id) =>
      clients(id) ! ToReturnAddress(ac, url, port)
      avatarResender ! AvatarResender.WorkWithQueue(id, worker)
      lowestFinder ! IncrementClients(url)
      context.become(receiveWithClientsStorage(clients - id))
      log.info("[-] TunnelManager: Avatar and tunnel created with id [{}], sending result to original sender [{}]", id, sender())

    case fac @ Avatar.FailedToCreateAvatar(id, _) =>
      clients(id) ! ToReturnAddress(fac, url, port)
      context.become(receiveWithClientsStorage(clients - id))
      log.info("[-] TunnelManager: Failed to create avatar with id [{}], sending result to original sender [{}]", id, sender())

    case ToReturnAddress(Avatar.AvatarCreated(id), tunnelUrl, tunnelPort) =>
      worker ! TunnelCreated(tunnelUrl, tunnelPort, id.toString)
      log.info("[-] TunnelManager: I'm the original sender. Printing tunnel info with topic [{}] to client.", id)

    case ToReturnAddress(Avatar.FailedToCreateAvatar(id, ex), _, _) =>
      worker ! FailedToCreateTunnel(id.toString, ex)
      log.info("[-] TunnelManager: I'm the original sender. Failed to create tunnel, printing info with topic [{}] to client.", id)

    case other =>
      log.error("[-] TunnelManager: other {} from {}", other, sender())
  }

  log.info("[-] TunnelManager: initialized for parent [{}] and listens on [{}]",
    context.parent, url)
}
