package utils.zmqHelpers

import akka.actor.{ActorRef, Props}
import org.zeromq.ZMQ

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 23.04.16.
  *
  * Clients of PIPE must use DEALER sockets to connect and interact with PIPE's ROUTER socket.
  *
  */

object ZmqRouter {
  def apply(url: String, port: Int, validator: Props, stringifier: Props, receiver: ActorRef) = {
    Props(classOf[ZmqRouter], url, port, validator, stringifier, receiver)
  }
}

class ZmqRouter(url: String, port: Int, validator: Props, stringifier: Props, receiver: ActorRef)
  extends ZmqActor(validator, stringifier, receiver) {

  def bindWithFallback(port: Int): ZMQ.Socket = {
    Try {
      ZeroMQHelper(context.system).bindRouterSocket(url + ":" + port)
    } match {
      case Failure(exception) => bindWithFallback(port + 1)
      case Success(value) => value
    }
  }

  val router = if (port == 0) bindWithFallback(30000) else bindWithFallback(port)

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive: Receive = super.receive.orElse {
    case other =>
      log.error("[-] ZmqRouter: other [{}] from [{}]", other, sender())
  }

  override def getSocket = router

  override def sendToSocket(topic: String, text: String) = {
    router.sendMore(topic.toString.getBytes)
    router.send("|" + text)
  }
}