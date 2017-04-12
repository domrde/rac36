package playground

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.ConfigFactory
import pipe.TunnelManager
import utils.zmqHelpers.ZeroMQHelper
import vivarium.Avatar.Create

/**
  * Created by dda on 12.04.17.
  */
object Car {

}

class Car extends Actor with ActorLogging {

  private val config = ConfigFactory.load()

  private val helper = ZeroMQHelper(context.system)

  private val id = UUID.randomUUID().toString

  override def receive: Receive = receiveWithConnection {
    val avatar = helper.connectDealerActor(
      id = id,
      url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
      port = 34671,
      validator = Props[ValidatorImpl],
      stringifier = Props[StringifierImpl],
      targetAddress = self)

    avatar ! Create(id, config.getString("playground.brain-jar"), config.getString("playground.car-class"))

    avatar
  }

  def receiveWithConnection(avatar: ActorRef): Receive = {
    case TunnelManager.TunnelCreated(url, port, _) =>
      log.info("Car [{}] got it's avatar on url {[]}", id, url + ":" + port)
      context.become(receiveWithConnection {
        val avatar = helper.connectDealerActor(
          id = id,
          url = url,
          port = port,
          validator = Props[ValidatorImpl],
          stringifier = Props[StringifierImpl],
          targetAddress = self)

        avatar ! Create(id, config.getString("playground.brain-jar"), config.getString("playground.car-class"))

        avatar
      }, discardOld = true)

    case other =>
      log.error("Car: unknown message [{}] from [{}]", other, sender())
  }
}
