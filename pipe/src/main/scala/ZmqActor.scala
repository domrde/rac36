package pipe
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import messages.Messages._
import org.zeromq.ZMQ
import pipe.TunnelManager.CreateTunnelRequest
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.duration._

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

  case class WorkWithQueue(topic: String, target: ActorRef)
  case object Poll
  case object HowManyClients
  case class ClientsInfo(url: String, amount: Int)
  @SerialVersionUID(1L) case class TunnelCreated(url: String, topic: String) extends Serializable
}

class ZmqActor(url: String) extends Actor with ActorLogging {
  import ZmqActor._

  import scala.concurrent.ExecutionContext.Implicits.global

  val router = ZeroMQ.bindRouterSocket(url)
  var clients: Map[ActorRef, String] = Map.empty

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive = receiveWithoutSharer

  val receiveWithoutSharer: Receive = {
    case HowManyClients =>
      context.become(receiveWithSharer(sender()))
      sender() ! ClientsInfo(url, clients.size)

    case WorkWithQueue(topic, target) =>
      clients += target -> topic
      context.watch(target)
      target ! TunnelEndpoint
      router.sendMore(topic.getBytes)
      router.send("|" + Json.stringify(Json.toJson(TunnelCreated(url, topic))))

    case Terminated =>
      clients -= sender()

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case ZMQMessage(data) if clients.contains(sender()) =>
      router.sendMore(clients(sender()))
      router.send("|" + data)

    case other => log.error("Other {} from {}", other, sender())
  }

  def receiveWithSharer(sharer: ActorRef): Receive = {
    case WorkWithQueue(topic, target) =>
      clients += target -> topic
      context.watch(target)
      target ! TunnelEndpoint
      router.sendMore(topic.getBytes)
      router.send("|" + Json.stringify(Json.toJson(TunnelCreated(url, topic))))
      sharer ! ClientsInfo(url, clients.size)

    case Terminated =>
      clients -= sender()
      sharer ! ClientsInfo(url, clients.size)

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case ZMQMessage(data) if clients.contains(sender()) =>
      router.sendMore(clients(sender()))
      router.send("|" + data)

    case other => log.error("Other {} from {}", other, sender())
  }

  def readQueue(readed: Array[Byte]): Unit =
    if (router.hasReceiveMore) readQueue(readed ++ router.recv())
    else processBytes(readed)

  def processBytes(bytes: Array[Byte]) = {
    val bytesAsString = ByteString(bytes).utf8String
    val (topic, data) = bytesAsString.splitAt(bytesAsString.indexOf("|"))
    Json.parse(data.drop(1)).validate[CreateTunnelRequest] match {
      case JsSuccess(value, path) => context.parent ! value
      case JsError(errors) => processMessage(topic, data.drop(1))
    }
  }

  def processMessage(topic: String, data: String) = {
    val target = clients.find { case (actor, uuid) => uuid == topic }
    if (target.isDefined) {
      target.get._1 ! ZMQMessage(data)
    } else {
      log.info("Undefined target [{}] for message [{}]", topic, data)
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
