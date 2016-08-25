package pipe
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import com.typesafe.config.ConfigFactory
import messages.Messages._
import org.zeromq.ZMQ
import pipe.TunnelManager.{CreateTunnel, TunnelCreated}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads, Writes}

import scala.concurrent.duration._

/**
  * Created by dda on 23.04.16.
  *
  * Clients of PIPE must use DEALER sockets to connect and interact with PIPE's ROUTER socket.
  *
  */
object ZeroMQActor {
  val config = ConfigFactory.load()

  def apply(port: Int) = {
    Props(classOf[ZeroMQActor], "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + port)
  }

  case class WorkWithQueue(topic: String, target: ActorRef)
  case object Poll
}

class ZeroMQActor(url: String) extends Actor with ActorLogging {
  import ZeroMQActor._

  import scala.concurrent.ExecutionContext.Implicits.global

  val router = ZeroMQ.bindRouterSocket(url)
  var clients: Map[ActorRef, String] = Map.empty

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive: Receive = {
    case WorkWithQueue(topic, target) =>
      clients += target -> topic
      context.watch(target)
      target ! TunnelEndpoint
      router.sendMore(topic.getBytes)
      router.send("|" + Json.stringify(Json.toJson(TunnelCreated(topic))))
//      log.debug("Sending tunnel created: " + topic + "|" + Json.stringify(Json.toJson(TunnelCreated(topic))))

    case Terminated => clients -= sender()

    case Poll => val received = router.recv(ZMQ.DONTWAIT); if (received != null) readQueue(received)

    case ZMQMessage(data) if clients.contains(sender()) =>
      router.sendMore(clients(sender()))
      router.send("|" + data)
//      log.debug("Sending message [{}] to zmq", data)

    case other => log.error("Other {} from {}", other, sender())
  }

  def readQueue(readed: Array[Byte]): Unit =
    if (router.hasReceiveMore) readQueue(readed ++ router.recv())
    else workWithMessage(readed)

  // todo: do something better
  def workWithMessage(bytes: Array[Byte]) = {
    val bytesAsString = ByteString(bytes).utf8String
    if (bytesAsString.contains("api")) {
      try {
        Json.parse(bytesAsString).validate[CreateTunnel].foreach { ct =>
          context.parent ! ct
        }
      } catch {
        case e: JsonParseException => log.warning("Malformed json: {}", e.getMessage)
      }
    } else {
      val (topic, message) = bytesAsString.splitAt(bytesAsString.indexOf("|"))
      val target = clients.find { case (actor, uuid) => uuid == topic }
      if (target.isDefined) {
        target.get._1 ! ZMQMessage(message.drop(1))
//        log.debug("Sending message [{}] to target [{}]", message.drop(1), target.get._1)
      } else {
        log.info("Undefined target for message [{}]", bytesAsString)
      }
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

  implicit val createTunnelReads: Reads[CreateTunnel] = (
    (JsPath \ "uuid").read[String] and
      (JsPath \ "api").read[Api]
    )(CreateTunnel.apply _)

  implicit val tunnelInfoWrites = new Writes[TunnelCreated] {
    def writes(tunnel: TunnelCreated) = Json.obj(
      "topic" -> tunnel.topic
    )
  }
}
