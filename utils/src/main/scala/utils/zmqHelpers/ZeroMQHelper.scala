package utils.zmqHelpers

import akka.actor.{ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}
import akka.util.ByteString
import org.zeromq.ZMQ

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Created by dda on 23.04.16.
  */
object ZeroMQHelper extends ExtensionId[ZeroMQHelper] with ExtensionIdProvider {
  private val zmqContext = ZMQ.context(1)

  override def createExtension(system: ExtendedActorSystem): ZeroMQHelper = new ZeroMQHelper(system, zmqContext)

  override def lookup() = ZeroMQHelper
}

class ZeroMQHelper(system: ActorSystem, zmqContext: ZMQ.Context) extends Extension {

  system.registerOnTermination {
    zmqContext.term()
  }

  def connectDealerActor(id: String,
                         url: String,
                         port: Int,
                         validator: Props,
                         stringifier: Props,
                         targetAddress: ActorRef): ActorRef = {
    system.actorOf(ZmqDealer(id, url, port, validator, stringifier, targetAddress))
  }

  def bindRouterActor(url: String,
                      port: Int,
                      validator: Props,
                      stringifier: Props,
                      targetAddress: ActorRef): ActorRef = {
    system.actorOf(ZmqRouter(url, port, validator, stringifier, targetAddress))
  }

  def bindRouterSocket(url: String): ZMQ.Socket = {
    val router = zmqContext.socket(ZMQ.ROUTER)
    router.bind(url)
    router
  }

  def bindDealerSocket(url: String): ZMQ.Socket = {
    val dealer = zmqContext.socket(ZMQ.DEALER)
    dealer.bind(url)
    dealer
  }

  def connectRouterToPort(url: String): ZMQ.Socket = {
    val router = zmqContext.socket(ZMQ.ROUTER)
    router.connect(url)
    router
  }

  def connectDealerToPort(url: String): ZMQ.Socket = {
    val dealer = zmqContext.socket(ZMQ.DEALER)
    dealer.connect(url)
    dealer
  }

  def receiveMessage(socket: ZMQ.Socket): String = Await.result(Future {
    var data = socket.recv()
    while (socket.hasReceiveMore) {
      data = data ++ socket.recv()
    }
    ByteString(data).utf8String
  }, 5.seconds)

}
