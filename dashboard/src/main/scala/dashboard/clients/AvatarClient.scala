package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.dda.brain.BrainMessages
import com.typesafe.config.ConfigFactory
import common.Constants.AvatarsDdataSetKey
import dashboard.Server
import vivarium.ReplicatedSet.{Lookup, LookupResult}
import vivarium.{Avatar, ReplicatedSet}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

/**
  * Created by dda on 9/6/16.
  */
object AvatarClient {
  def apply(shard: ActorRef): Props = Props(classOf[AvatarClient], shard)
}

class AvatarClient(shard: ActorRef) extends Actor with ActorLogging {

  private implicit val timeout: Timeout = 5.seconds
  private implicit val executionContext = context.dispatcher
  private val config = ConfigFactory.load()
  private val updatePeriod = FiniteDuration(config.getDuration("application.update-period").getSeconds, SECONDS)

  private val avatarsStorage = context.actorOf(ReplicatedSet(AvatarsDdataSetKey))

  context.system.scheduler.schedule(updatePeriod, updatePeriod, avatarsStorage, Lookup)

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection, List.empty))
      context.parent ! Server.Join(Server.AvatarClient)

      if (config.getBoolean("application.test-data")) {
        context.system.scheduler.schedule(updatePeriod, updatePeriod) {
          val statuses = (0 to 10).map { _ =>
            val name = Random.shuffle(List("Red", "Green", "Blue", "Banana", "Apple")).mkString("")
            ServerClient.AvatarStatus(name, Random.nextBoolean(), Random.nextString(10))
          }.toList
          connection ! ServerClient.AvatarsStatuses(statuses)
        }
      }
  }

  def connected(connection: ActorRef, activeAvatars: List[String]): Receive = {
    case ServerClient.ChangeAvatarState(id, state) =>
      log.info("[-] dashboard.AvatarClient: Changing avatar [{}] state [{}]", id, state)
      if (activeAvatars.contains(id))
        shard ! Avatar.ChangeState(id, if (state == "Start") BrainMessages.Start else BrainMessages.Stop)

    case ServerClient.MessageToAvatar(id, message) =>
      log.info("[-] dashboard.AvatarClient: Message to avatar [{}] [{}]", id, message)
      if (activeAvatars.contains(id))
        shard ! Avatar.TellToAvatar(id, "AvatarClient", message)

    case l: LookupResult[String] =>
      l.result match {
        case Some(data) =>
          Future.sequence(data.map(id => shard ? Avatar.GetState(id))).onComplete {

            case Success(value: Set[Any]) =>
              val statuses = value.map { case Avatar.State(id, tunnel, brain) =>
                ServerClient.AvatarStatus(id, tunnel.isDefined, brain.map(_.path.name).getOrElse("None"))}.toList.sortBy(_.id)
              connection ! ServerClient.AvatarsStatuses(statuses)
              context.become(connected(connection, statuses.map(_.id)), discardOld = true)

            case Failure(_) =>
          }

        case None =>
      }

    case other =>
      log.error("[-] dashboard.AvatarClient: other {} from {}", other, sender())
  }
}
