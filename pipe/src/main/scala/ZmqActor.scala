package pipe
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import messages.Messages._
import org.zeromq.ZMQ
import pipe.TunnelManager.CreateTunnelRequest
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 23.04.16.
  *
  * Clients of PIPE must use DEALER sockets to connect and interact with PIPE's ROUTER socket.
  *
  */
object ZmqActor {
  val config = ConfigFactory.load()

  def apply(port: Int) = {
    Props(classOf[ZmqActor], "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + port)
  }

  case class WorkWithQueue(topic: UUID)
  case object Poll
  case object HowManyClients
  case class ClientsInfo(url: String, amount: Int)
  @SerialVersionUID(1L) case class TunnelCreated(url: String, topic: String) extends Serializable
}


// todo: remove dead clients
class ZmqActor(url: String) extends Actor with ActorLogging {
  import ZmqActor._

  import scala.concurrent.ExecutionContext.Implicits.global

  val router = ZeroMQ.bindRouterSocket(url)
  val mediator = ZeroMQ.mediator
  var clients: Set[UUID] = Set.empty
  val avatarAddress = config.getString("application.avatarAddress")

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive = receiveWithoutSharer

  def sendToAvatar(msg: AnyRef) = {
    mediator ! Send(avatarAddress, msg, localAffinity = false)
    log.debug("Sending message [{}] to avatar [{}]", msg, avatarAddress)
  }

  val receiveWithoutSharer: Receive = {
    case HowManyClients =>
      context.become(receiveWithSharer(sender()))
      sender() ! ClientsInfo(url, clients.size)

    case WorkWithQueue(topic) =>
      clients = clients + topic
      sendToAvatar(TunnelEndpoint(topic))
      router.sendMore(topic.toString.getBytes)
      router.send("|" + Json.stringify(Json.toJson(TunnelCreated(url, topic.toString))))

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case ZMQMessage(uuid, data) if clients.contains(uuid) =>
      router.sendMore(uuid.toString.getBytes)
      router.send("|" + data)

    case other => log.error("Other {} from {}", other, sender())
  }

  def receiveWithSharer(sharer: ActorRef): Receive = {
    case WorkWithQueue(topic) =>
      clients = clients + topic
      sendToAvatar(TunnelEndpoint(topic))
      router.sendMore(topic.toString.getBytes)
      router.send("|" + Json.stringify(Json.toJson(TunnelCreated(url, topic.toString))))
      sharer ! ClientsInfo(url, clients.size)

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case ZMQMessage(uuid, data) if clients.contains(uuid) =>
      router.sendMore(uuid.toString.getBytes)
      router.send("|" + data)

    case other => log.error("Other {} from {}", other, sender())
  }

  def readQueue(readed: Array[Byte]): Unit =
    if (router.hasReceiveMore) readQueue(readed ++ router.recv())
    else processBytes(readed)

  def processBytes(bytes: Array[Byte]) = {
    val bytesAsString = ByteString(bytes).utf8String
    val (topic, data) = bytesAsString.splitAt(bytesAsString.indexOf("|"))
    Try(Json.parse(data.drop(1))) match {
      case Failure(exception) => processMessage(topic, data.drop(1))
      case Success(parsedJson) =>
        parsedJson.validate[CreateTunnelRequest] match {
          case JsSuccess(value, path) =>
            log.debug("CreateTunnelRequest on [{}]", self)
            context.parent ! value
          case JsError(errors) => processMessage(topic, data.drop(1))
        }
    }
  }

  def processMessage(topic: String, data: String) = {
    val uuid = UUID.fromString(topic)
    if (clients.contains(uuid)) {
      sendToAvatar(ZMQMessage(uuid, data))
    } else {
      log.error("Undefined target [{}] for message [{}]", topic, data)
    }
  }

  context.system.scheduler.schedule(0.second, 1.millis, self, Poll)

  log.debug("ZeroMQActor initialized for parent {}", context.parent)



  /* Play json parsing reads */

  implicit val rangeReads: Reads[ArgumentRange] = (
    (JsPath \ "lower").read[Long] and
      (JsPath \ "upper").read[Long]
    )(ArgumentRange.apply _)

  implicit val commandReads: Reads[Command] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "range").readNullable[ArgumentRange]
    )(Command.apply _)

  implicit val apiReads: Reads[Api] =
    (JsPath \ "commands").read[List[Command]].map { commands => Api(commands) }

  implicit val createTunnelReads: Reads[CreateTunnelRequest] = (
    (JsPath \ "uuid").read[String] and
      (JsPath \ "api").read[Api]
    )(CreateTunnelRequest.apply _)

  implicit val tunnelInfoWrites = new Writes[TunnelCreated] {
    def writes(tunnel: TunnelCreated) = Json.obj(
      "url" -> tunnel.url,
      "topic" -> tunnel.topic
    )
  }
}
