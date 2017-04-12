package dashboard.clients

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.dda.brain.BrainMessages
import com.typesafe.config.ConfigFactory
import dashboard.Server
import vivarium.ReplicatedSet.LookupResult
import vivarium.{Avatar, ReplicatedSet}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

/**
  * Created by dda on 9/6/16.
  */
object AvatarClient {
  def apply(avatarIdStorage: ActorRef, shard: ActorRef): Props = Props(classOf[AvatarClient], avatarIdStorage, shard)
}

class AvatarClient(avatarIdStorage: ActorRef, shard: ActorRef) extends Actor with ActorLogging {

  private implicit val timeout: Timeout = 5.seconds
  private implicit val executionContext = context.dispatcher
  private val config = ConfigFactory.load()

  override def receive: Receive = {
    case ServerClient.Connected(connection) =>
      context.become(connected(connection))
      context.parent ! Server.Join(Server.AvatarClient)
      avatarIdStorage ! ReplicatedSet.Lookup
      if (config.getBoolean("application.testData")) {
        context.system.scheduler.schedule(0 second, 5 second) {
          val statuses = (0 to 10).map { _ =>
            ServerClient.AvatarStatus(Random.shuffle(List("Red", "Green", "Blue", "Banana", "Apple")).mkString(""), Random.nextBoolean(), Random.nextString(10))
          }.toList.sortBy(_.id)
          connection ! ServerClient.AvatarsStatuses(statuses)
        }
      }
  }

  def connected(connection: ActorRef): Receive = {
    case ServerClient.ChangeAvatarState(id, state) =>
      log.info("[-] dashboard.AvatarClient: Changing avatar [{}] state [{}]", id, state)
      shard ! Avatar.ChangeState(id, if (state == "Start") BrainMessages.Start else BrainMessages.Stop)

    case LookupResult(Some(data: Set[String])) =>
      Future.sequence(data.map(id => shard ? Avatar.GetState(id))).onComplete {

        case Success(value: Avatar.State) =>
          val statuses = value.map { case Avatar.State(id, tunnel, brain) =>
            ServerClient.AvatarStatus(id, tunnel.isDefined, brain.map(_.path.name).getOrElse("None"))}.toList.sortBy(_.id)
          connection ! ServerClient.AvatarsStatuses(statuses)

        case Failure(_) =>
      }

    case LookupResult(_) =>
      connection ! ServerClient.AvatarsStatuses(List.empty)

    case other =>
      log.error("[-] dashboard.AvatarClient: other {} from {}", other, sender())
  }
}
