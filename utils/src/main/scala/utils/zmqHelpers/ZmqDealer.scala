package utils.zmqHelpers

import akka.actor.{ActorRef, Props}

/**
  * Created by dda on 10/4/16.
  */

object ZmqDealer {
  def apply(id: String, url: String, port: Int, validator: Props, stringifier: Props, receiver: ActorRef) = {
    Props(classOf[ZmqDealer], id, url, port, validator, stringifier, receiver)
  }

  case object Poll
}

class ZmqDealer(id: String, url: String, port: Int, validator: Props, stringifier: Props, receiver: ActorRef)
  extends ZmqActor(validator, stringifier, receiver) {

  val dealer = ZeroMQHelper(context.system).connectDealerToPort(url + ":" + port)

  dealer.setIdentity(id.getBytes())

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    dealer.close()
    super.postStop()
  }

  override def receive: Receive = super.receive.orElse {
    case other =>
      log.error("[-] ZmqDealer: other [{}] from [{}]", other, sender())
  }

  override def getSocket = dealer

  override def sendToSocket(topic: String, text: String) = {
    dealer.send("|" + text)
  }
}