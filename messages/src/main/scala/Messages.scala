package messages
import java.io.Serializable

import akka.actor.ActorRef

/**
  * Created by dda on 7/28/16.
  */
object Constants {
  val ACTOR_CREATION_SUBSCRIPTION = "ACTOR_CREATION_SUBSCRIPTION"
}

object Messages {
  @SerialVersionUID(1L) case object CreateAvatar extends Serializable
  @SerialVersionUID(1L) case class AvatarCreated(avatar: ActorRef) extends Serializable
  @SerialVersionUID(1L) case object TunnelEndpoint extends Serializable
}
