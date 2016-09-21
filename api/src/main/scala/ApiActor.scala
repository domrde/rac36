import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.ddata.Replicator.{Get, GetSuccess, NotFound, ReadLocal}
import akka.cluster.ddata.{DistributedData, ORSet}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Send
import com.typesafe.config.ConfigFactory
import common.Constants._
import common.SharedMessages._

/**
  * Created by dda on 9/13/16.
  */
object ApiActor {
  @SerialVersionUID(1L) case object GetInfoFromSharedStorage
  @SerialVersionUID(1L) case class GetInfoFromSharedStorageResult(info: AnyRef)

  @SerialVersionUID(1L) case class GetAvailableCommands(robotId: String)
  @SerialVersionUID(1L) case class GetAvailableCommandsResult(robotId: String, commands: List[Command])

  @SerialVersionUID(1L) case class SendCommandToAvatar(id: String, command: Command)
}

class ApiActor extends Actor with ActorLogging {
  import ApiActor._

  val mediator = DistributedPubSub(context.system).mediator
  val replicator = DistributedData(context.system).replicator
  val config = ConfigFactory.load()
  val avatarAddress = config.getString("application.avatarAddress")
  val url = "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + config.getInt("application.ports.input")

  def sendToAvatar(msg: AnyRef) = {
    mediator ! Send(avatarAddress, msg, localAffinity = false)
  }

  override def receive: Receive = receiveWithClients(Map.empty)

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

    case SendCommandToAvatar(id, command) =>
      sendToAvatar(Control(id, command))

    case other =>
      log.error("\nAvatarControl: other [{}] from [{}]", other, sender())
  }
}
