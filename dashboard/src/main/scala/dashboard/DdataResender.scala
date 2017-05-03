package dashboard

import akka.actor.{Actor, ActorLogging, ActorRef}
import dashboard.DdataResender.AddClient
import vivarium.ReplicatedSet.LookupResult

object DdataResender {
  case class AddClient(client: ActorRef)
}

class DdataResender extends Actor with ActorLogging {

  def receiveWithClients(clients: List[ActorRef]): Receive = {
    case AddClient(client) =>
      context.become(receiveWithClients(client :: clients))

    case l: LookupResult =>
      clients.foreach(_ ! l)

    case other =>
      log.error("[-] DdataResender: other {} from {}", other, sender())
  }

  override def receive: Receive = receiveWithClients(List.empty)
}