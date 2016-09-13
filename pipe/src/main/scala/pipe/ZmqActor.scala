package pipe

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import messages.Messages._
import org.zeromq.ZMQ
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

  def apply(url: String) = {
    Props(classOf[ZmqActor], url)
  }

  case class WorkWithQueue(topic: String)
  case object Poll
  case object HowManyClients
  case class ClientsInfo(url: String, amount: Int)
  case class TunnelCreated(url: String, topic: String)
}


// todo: remove dead clients
// todo: shouldn't forward messages to avatar that wasn't CREATED
class ZmqActor(url: String) extends Actor with ActorLogging {
  import ZmqActor._

  import scala.concurrent.ExecutionContext.Implicits.global

  val router = ZeroMQ.bindRouterSocket(url)
  val mediator = DistributedPubSub(context.system).mediator
  var clients: Set[String] = Set.empty
  val avatarAddress = config.getString("application.avatarAddress")

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive = receiveWithoutSharer

  def sendToAvatar(msg: AnyRef) = {
    mediator ! Send(avatarAddress, msg, localAffinity = false)
  }

  val receiveWithoutSharer: Receive = {
    case HowManyClients =>
      context.become(receiveWithSharer(sender()))
      sender() ! ClientsInfo(url, clients.size)

    case WorkWithQueue(topic) =>
      clients = clients + topic
      sendToAvatar(TunnelEndpoint(topic, self))

    case t @ TunnelCreated(tunnelUrl, topic) =>
      router.sendMore(topic.toString.getBytes)
      router.send("|" + Json.stringify(Json.toJson(t)))

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case c @ Control(uuid, command) if clients.contains(uuid) =>
      router.sendMore(uuid.toString.getBytes)
      router.send("|" + Json.stringify(Json.toJson(c)))

    case other => log.error("ZmqActor: other {} from {}", other, sender())
  }

  def receiveWithSharer(sharer: ActorRef): Receive = {
    case WorkWithQueue(topic) =>
      clients = clients + topic
      sendToAvatar(TunnelEndpoint(topic, self))
      sharer ! ClientsInfo(url, clients.size)

    case t @ TunnelCreated(tunnelUrl, topic) =>
      router.sendMore(topic.toString.getBytes)
      router.send("|" + Json.stringify(Json.toJson(t)))

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case c @ Control(uuid, command) if clients.contains(uuid) =>
      router.sendMore(uuid.toString.getBytes)
      router.send("|" + Json.stringify(Json.toJson(c)))

    case other => log.error("ZmqActor: other {} from {}", other, sender())
  }

  def readQueue(readed: Array[Byte]): Unit =
    if (router.hasReceiveMore) readQueue(readed ++ router.recv())
    else processBytes(readed)

  def processBytes(bytes: Array[Byte]) = {
    val bytesAsString = ByteString(bytes).utf8String
    val (_, data) = bytesAsString.splitAt(bytesAsString.indexOf("|"))
    Try(Json.parse(data.drop(1))) match {
      case Success(parsedJson) =>
        validateJson(parsedJson)
      case Failure(exception) =>
        log.error("Malformed message [{}] caused exception [{}]", bytesAsString, exception.getMessage)
    }
  }

  def validateJson(json: JsValue) = {
    json.validate[CreateAvatar] match {
      case JsSuccess(value, path) =>
        log.info("CreateAvatar on [{}]", self)
        context.parent ! value

      case JsError(_) =>
        json.validate[Sensory] match {
          case JsSuccess(value, path) =>
            sendToAvatar(value)

          case JsError(_) =>
            log.error("Failed to validate json [{}]", json)
        }
    }
  }

  context.system.scheduler.schedule(0.second, 1.millis, self, Poll)

  log.info("ZeroMQActor initialized for parent {} and avatarAddress {}", context.parent, avatarAddress)



  /* Play json parsing */

  implicit val rangeReads = Json.reads[ArgumentRange]

  implicit val commandReads = Json.reads[Command]

  implicit val apiReads = Json.reads[Api]

  implicit val createAvatarReads = Json.reads[CreateAvatar]

  implicit val posReads = Json.reads[Position]

  implicit val sensoryReads = Json.reads[Sensory]

  implicit val tunnelInfoWrites = Json.writes[TunnelCreated]

  implicit val rangeWrites = Json.writes[ArgumentRange]

  implicit val commandWrites = Json.writes[Command]

  implicit val controlWrites = Json.writes[Control]
}
