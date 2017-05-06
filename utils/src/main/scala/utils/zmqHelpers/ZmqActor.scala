package utils.zmqHelpers

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import common.messages.NumeratedMessage
import org.zeromq.ZMQ

import scala.concurrent.duration._


/**
  * Created by dda on 01.04.17.
  */
object ZmqActor {
  case object Poll
}

abstract class ZmqActor(validator: Props, stringifier: Props, receiver: ActorRef) extends Actor with ActorLogging {
  private implicit val executionContext = context.dispatcher
  private val pollingCancellable = context.system.scheduler.schedule(0.second, 1.millis, self, ZmqActor.Poll)

  private var readersFan = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(validator)
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  private var stringifiersFan = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(stringifier)
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  def getSocket: ZMQ.Socket

  def sendToSocket(topic: String, text: String): Unit

  private def checkSocketForUpdates() = {
    val socket = getSocket
    val received = socket.recv(ZMQ.DONTWAIT)
    if (received != null) readQueue(socket, received)
  }

  private def readQueue(socket: ZMQ.Socket, readed: Array[Byte]): Unit =
    if (socket.hasReceiveMore) readQueue(socket, readed ++ socket.recv())
    else readersFan.route(JsonValidator.Validate(readed), self)

  override def receive: Receive = {
    case n: NumeratedMessage =>
      stringifiersFan.route(JsonStringifier.Stringify(n, Some(n.id)), self)

    case JsonStringifier.StringifyResult(asText, Some(topic: String)) =>
      sendToSocket(topic, asText)

    case JsonValidator.ValidationResult(result) =>
      result match {
        case n: NumeratedMessage =>
          receiver ! n

        case other =>
          log.error("[-] ZmqActor: ValidationResult is not numerated message: [{}]", other)
      }

    case Terminated(a) =>
      if (readersFan.routees.contains(a)) {
        readersFan = readersFan.removeRoutee(a)
        val r = context.actorOf(validator)
        context watch r
        readersFan = readersFan.addRoutee(r)
      } else if (stringifiersFan.routees.contains(a)) {
        stringifiersFan = stringifiersFan.removeRoutee(a)
        val r = context.actorOf(validator)
        context watch r
        stringifiersFan = stringifiersFan.addRoutee(r)
      } else {
        log.warning("[-] ZmqActor: Unknown terminated [{}]", a)
      }

    case ZmqActor.Poll =>
      checkSocketForUpdates()
  }

  override def postStop(): Unit = {
    pollingCancellable.cancel()
    super.postStop()
  }
}
