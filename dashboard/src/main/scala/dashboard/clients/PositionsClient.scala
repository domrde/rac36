package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import common.messages.SensoryInformation
import dashboard.Server
import vivarium.ReplicatedSet
import vivarium.ReplicatedSet.LookupResult

import scala.concurrent.duration._

/**
  * Created by dda on 03.05.17.
  */
object PositionsClient {
  def apply(positionsStorage: ActorRef): Props = Props(classOf[PositionsClient], positionsStorage)
}

class PositionsClient(positionsStorage: ActorRef) extends Actor with ActorLogging{

  private implicit val timeout: Timeout = 5.seconds
  private implicit val executionContext = context.dispatcher
  private val config = ConfigFactory.load()

  if (config.getBoolean("application.testData")) {
    context.system.scheduler.schedule(0 second, 5 second) {
      val data: Set[SensoryInformation.Position] = Set(
        SensoryInformation.Position("test", -1.7, -2.5, 2, 1.25, 0),
        SensoryInformation.Position("test", -0.45, 0.5, 1, 1.25, 0),
        SensoryInformation.Position("test", 0.8, -1.5, 1, 2.5, 0)
      )
      self ! LookupResult(Some(data))
    }
  }

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join(Server.PositionsClient)
      positionsStorage ! ReplicatedSet.Lookup
  }

  def connected(connection: ActorRef): Receive = {
    case LookupResult(Some(data: Set[SensoryInformation.Position])) =>
      connection ! ServerClient.PositionsData(data)

    case LookupResult(_) =>
      connection ! ServerClient.PositionsData(Set.empty)

    case other =>
      log.error("[-] dashboard.PositionsClient: other {} from {}", other, sender())
  }

}
