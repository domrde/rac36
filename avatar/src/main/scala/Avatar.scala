package avatar
import akka.actor.{Actor, ActorLogging, ActorRef}
import messages.Messages.{AvatarCreated, Command, TunnelEndpoint, YourApi}

/**
  * Created by dda on 8/2/16.
  */

class Avatar extends Actor with ActorLogging {
  log.info("\nAVATAR CREATED")

  override def receive = behaviour(ActorRef.noSender, List.empty)

  def behaviour(tunnel: ActorRef, commands: List[Command]): Receive = {
    case YourApi(id, api) =>
      context.become(behaviour(tunnel, api.commands))
      sender() ! AvatarCreated(id, self)

    case TunnelEndpoint =>
      context.become(behaviour(sender(), commands))

    case c: Command if commands.contains(c) =>
      tunnel ! c

    case other =>
      log.error("Avatar: other [{}] from [{}]", other, sender())
  }
}
