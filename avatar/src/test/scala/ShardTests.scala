import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import avatar.ClusterMain
import messages.Constants._
import messages.Messages.{Api, AvatarCreated, Command, YourApi}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


/**
  * Created by dda on 8/3/16.
  */

class ShardTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("AvatarSystem"))

  implicit val timeout: Timeout = 2.seconds
  val shardMaster = system.actorOf(Props[ClusterMain])
  val mediator = DistributedPubSub(system).mediator
  val pipe = {
    val inner = TestProbe("TunnelManager")
    mediator.tell(Subscribe(ACTOR_CREATION_SUBSCRIPTION, inner.ref), inner.ref)
    inner.expectMsgType[SubscribeAck](timeout.duration)
    inner
  }

  "Shard" must {
    "Create avatar" in {
      val uuid = UUID.randomUUID()
      val api = Api(List(Command("TestCommand", Option.empty)))
      mediator.tell(Publish(ACTOR_CREATION_SUBSCRIPTION, YourApi(uuid, api)), pipe.ref)
      val messages = pipe.receiveWhile(timeout.duration) { case smthg => smthg }
      println(messages)
      assert(messages.exists {
        case a: AvatarCreated => true
        case _ => false
      })
    }
  }

}
