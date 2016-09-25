package api

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.ddata.Replicator.{Get, GetSuccess, NotFound, ReadLocal}
import akka.cluster.ddata.{DistributedData, ORSet}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import com.typesafe.config.ConfigFactory
import common.Constants._
import common.SharedMessages._
import common.zmqHelpers.ZeroMQHelper

/**
  * Created by dda on 9/13/16.
  */
//todo: sensory data not through ddata
//todo: do no set commands to not created avatars
//todo: only one contolling app
class ApiActor extends Actor with ActorLogging {
  import common.ApiActor._

  val mediator = DistributedPubSub(context.system).mediator
  val replicator = DistributedData(context.system).replicator
  val config = ConfigFactory.load()
  val avatarAddress = config.getString("api.avatarAddress")

  val worker = ZeroMQHelper(context.system).start(
    url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname"),
    portLower = config.getInt("api.ports.input"),
    portUpper = config.getInt("api.ports.input"),
    self
  ).head

  def sendToAvatar(msg: AnyRef) = {
    mediator ! Send(avatarAddress, msg, localAffinity = false)
  }

  override def receive: Receive = receiveWithClients(Map.empty)


  //todo: replace actor clients with zmq ids
  def receiveWithClients(clients: Map[String, ActorRef]): Receive = {

    // Shared info handling

    case GetInfoFromSharedStorage =>
      replicator ! Get(DdataSetKey, ReadLocal, Some(sender()))

    case g @ GetSuccess(DdataSetKey, Some(replyTo: ActorRef)) =>
      g.dataValue match {
        case data: ORSet[Position] => replyTo ! GetInfoFromSharedStorageResult(Some(data.elements))
        case _ => replyTo ! GetInfoFromSharedStorageResult(None)
      }

    case NotFound(_, Some(replyTo: ActorRef)) =>
      replyTo ! GetInfoFromSharedStorageResult(None)

    // Commands info handling

    case GetAvailableCommands(id) =>
      sendToAvatar(GetListOfAvailableCommands(id))
      context.become(receiveWithClients(clients + (id -> sender())))

    case ListOfAvailableCommands(id, api) =>
      clients(id) ! GetAvailableCommandsResult(id, api.commands)
      context.become(receiveWithClients(clients - id))

    // Avatar control

    case SendCommandToAvatar(id, name, value) =>
      sendToAvatar(Control(id, name, value))

    case other =>
      log.error("\nApiActor: other [{}] from [{}]", other, sender())
  }
}
