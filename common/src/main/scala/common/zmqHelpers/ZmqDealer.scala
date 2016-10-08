package common.zmqHelpers

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import com.typesafe.config.ConfigFactory
import org.zeromq.ZMQ

import scala.concurrent.duration._

/**
  * Created by dda on 10/4/16.
  */

//todo: heavily duplicates ZmqRouter

object ZmqDealer {
  val config = ConfigFactory.load()

  def apply(id: String, url: String, port: Int, validator: Props, stringifier: Props, receiver: ActorRef) = {
    Props(classOf[ZmqDealer], id: String, url, port, validator, stringifier, receiver)
  }

  case object Poll
}

class ZmqDealer(id: String, url: String, port: Int, validator: Props, stringifier: Props, receiver: ActorRef) extends Actor with ActorLogging {
  import ZmqRouter._
  import common.SharedMessages.NumeratedMessage

  import scala.concurrent.ExecutionContext.Implicits.global

  val config = ConfigFactory.load()

  val dealer = ZeroMQHelper(context.system).connectDealerToPort(url + ":" + port)

  dealer.setIdentity(id.getBytes())

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
    dealer.close()
    super.postStop()
  }

  override def receive: Receive = {
    case n: NumeratedMessage =>
      stringifiersFan.route(JsonStringifier.Stringify(n, Some(n.id)), self)

    case JsonStringifier.StringifyResult(asText, Some(topic: String)) =>
      dealer.send("|" + asText)

    case Poll =>
      val received = dealer.recv(ZMQ.DONTWAIT)
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
      log.error("ZmqRouter: other [{}] from [{}]", other, sender())
  }

  def readQueue(readed: Array[Byte]): Unit =
    if (dealer.hasReceiveMore) readQueue(readed ++ dealer.recv())
    else readersFan.route(JsonValidator.Validate(readed), self)

  context.system.scheduler.schedule(0.second, 1.millis, self, Poll)
}