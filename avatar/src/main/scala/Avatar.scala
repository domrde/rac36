package avatar
import akka.actor.{Actor, ActorLogging, ActorRef}
import messages.Messages._

/**
  * Created by dda on 8/2/16.
  */

class Avatar extends Actor with ActorLogging {
  log.info("\nAVATAR CREATED")

  override def receive = behaviour(ActorRef.noSender, List.empty)

  def behaviour(tunnel: ActorRef, commands: List[Command]): Receive = {
    case CreateAvatar(id, api, returnAddress) =>
      context.become(behaviour(tunnel, api.commands))
      returnAddress ! AvatarCreated(id, self)

    case TunnelEndpoint =>
      context.become(behaviour(sender(), commands))

    case c: Command if commands.contains(c) =>
      tunnel ! c

    case other =>
      log.error("Avatar: other [{}] from [{}]", other, sender())
  }
}
