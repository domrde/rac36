import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import avatar.ClusterMain
import messages.Messages._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


/**
  * Created by dda on 8/3/16.
  */

class ShardTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("AvatarSystem"))

  implicit val timeout: Timeout = 3.seconds
  val shardMaster = system.actorOf(Props[ClusterMain], "ClusterMain")

  "Shard" must {
    "create avatar" in {
      val uuid = UUID.randomUUID()
      val pipe = TestProbe()
      val mediator = DistributedPubSub(system).mediator
      mediator ! Put(pipe.ref)
      val api = Api(List(Command("TestCommand", Option.empty)))

      def sendMessageToMediator(msg: AnyRef, from: ActorRef): Unit = {
        mediator.tell(Send(
          path = "/system/AvatarSharding/Avatar",
          msg = msg,
          localAffinity = false
        ), from)
      }

      sendMessageToMediator(CreateAvatar(uuid, api), pipe.ref)
      val a: AvatarCreated = pipe.expectMsgType[AvatarCreated](timeout.duration)
      a.uuid shouldBe uuid

      val messagesSent = (1 to 5).map { i =>
        val msg = ParrotMessage(uuid, "testMessage" + i)
        sendMessageToMediator(msg, pipe.ref)
        msg
      }

      pipe.receiveN(5) shouldBe messagesSent
    }
  }

}
