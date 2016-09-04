package avatar
import akka.actor.{Actor, ActorLogging, ActorRef}
import messages.Messages._

/**
  * Created by dda on 8/2/16.
  */

// todo: auto-kill if client disconnected
// todo: persist tunnel and api
// todo: distributed data
class Avatar extends Actor with ActorLogging {
  log.info("\nAVATAR CREATED")

  override def receive = behaviour(ActorRef.noSender, List.empty)

  def behaviour(tunnel: ActorRef, commands: List[Command]): Receive = {
    case CreateAvatar(id, api) =>
      context.become(behaviour(tunnel, api.commands))
      sender() ! AvatarCreated(id)

    case TunnelEndpoint =>
      context.become(behaviour(sender(), commands))

    case p: ParrotMessage =>
      sender() ! p

    case c: Command if commands.contains(c) =>
      tunnel ! c

    case other =>
      log.error("\nAvatar: other [{}] from [{}]. Parroting it back", other, sender())
      sender() ! other
  }
}
