package pipe
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.util.ByteString
import org.zeromq.ZMQ

import scala.language.postfixOps

/**
  * Created by dda on 23.04.16.
  */
object ZeroMQ {
  val system = ActorSystem("PipeSystem")
  val mediator = DistributedPubSub(system).mediator
  private val zmqContext = ZMQ.context(1)

  system.registerOnTermination {
    zmqContext.term()
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

  def bindSubSocket(url: String): ZMQ.Socket = {
    val router = zmqContext.socket(ZMQ.SUB)
    router.bind(url)
    router
  }

  def bindPubSocket(url: String): ZMQ.Socket = {
    val dealer = zmqContext.socket(ZMQ.PUB)
    dealer.bind(url)
    dealer
  }

  def connectSubToPort(url: String): ZMQ.Socket = {
    val router = zmqContext.socket(ZMQ.SUB)
    router.connect(url)
    router
  }

  def connectPubToPort(url: String): ZMQ.Socket = {
    val dealer = zmqContext.socket(ZMQ.PUB)
    dealer.connect(url)
    dealer
  }

  def receiveMessage(socket: ZMQ.Socket): String = {
    var data = socket.recv(ZMQ.NOBLOCK)
    while (socket.hasReceiveMore) {
      data = data ++ socket.recv()
    }
    ByteString(data).utf8String
  }

}
