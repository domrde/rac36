package common.zmqHelpers

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory
import org.zeromq.ZMQ

import scala.concurrent.duration._

/**
  * Created by dda on 23.04.16.
  *
  * Clients of PIPE must use DEALER sockets to connect and interact with PIPE's ROUTER socket.
  *
  */
object ZmqActor {
  val config = ConfigFactory.load()

  def apply(url: String, validator: Props, stringifier: Props, receiver: ActorRef) = {
    Props(classOf[ZmqActor], url, validator, stringifier, receiver)
  }

  case object Poll
}

class ZmqActor(url: String, validator: Props, stringifier: Props, receiver: ActorRef) extends Actor with ActorLogging {
  import ZmqActor._
  import common.SharedMessages.NumeratedMessage

  import scala.concurrent.ExecutionContext.Implicits.global

  val router = ZeroMQHelper(context.system).bindRouterSocket(url)

  var readersFan = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(validator)
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  var stringifiersFan = {
    val routees = Vector.fill(5) {
      val r = context.actorOf(stringifier)
      context watch r
      ActorRefRoutee(r)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive: Receive = {
    case n: NumeratedMessage =>
      stringifiersFan.route(JsonStringifier.Stringify(n, Some(n.id)), self)

    case JsonStringifier.StringifyResult(asText, Some(topic: String)) =>
      router.sendMore(topic.toString.getBytes)
      router.send("|" + asText)

    case Poll =>
      val received = router.recv(ZMQ.DONTWAIT)
      if (received != null) readQueue(received)

    case JsonValidator.ValidationResult(result) =>
      result match {
        case n: NumeratedMessage =>
          receiver ! result

        case other =>
          log.error("ValidationResult is not numerated message: [{}]", other)
      }

    case Terminated(a) =>
      if (readersFan.routees.contains(a)) {
        readersFan = readersFan.removeRoutee(a)
        val r = context.actorOf(Props[JsonValidator])
        context watch r
        readersFan = readersFan.addRoutee(r)
      } else if (stringifiersFan.routees.contains(a)) {
        stringifiersFan = stringifiersFan.removeRoutee(a)
        val r = context.actorOf(Props[JsonValidator])
        context watch r
        stringifiersFan = stringifiersFan.addRoutee(r)
      } else {
        log.warning("Unknown terminated [{}]", a)
      }

    case other =>
      log.error("ZmqActor: other [{}] from [{}]", other, sender())
  }

  def readQueue(readed: Array[Byte]): Unit =
    if (router.hasReceiveMore) readQueue(readed ++ router.recv())
    else readersFan.route(JsonValidator.Validate(readed), self)

  context.system.scheduler.schedule(0.second, 1.millis, self, Poll)
}