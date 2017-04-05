package dashboard.clients

import akka.actor.ActorRef

/**
  * Created by dda on 05.04.17.
  */
object ServerClient {
  case class Connected(connection: ActorRef)
  case class IncomingMessage(text: String)
  case class OutgoingMessage(text: String)
}
