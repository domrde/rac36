package avatar
import akka.actor.{Actor, ActorRef}
import messages.Messages.{AvatarCreated, Command, TunnelEndpoint, YourApi}

/**
  * Created by dda on 8/2/16.
  */

class Avatar extends Actor {
  override def receive = behaviour(ActorRef.noSender, List.empty)

  def behaviour(tunnel: ActorRef, commands: List[Command]): Receive = {
    case YourApi(id, api) =>
      context.become(behaviour(tunnel, api.commands))
      sender() ! AvatarCreated
    case TunnelEndpoint => context.become(behaviour(sender(), commands))
    case c: Command if commands.contains(c) => tunnel ! c
    case _ =>
  }
}
