import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import avatar.ClusterMain
import messages.Messages.{Api, AvatarCreated, Command, CreateAvatar}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._


/**
  * Created by dda on 8/3/16.
  */

class ShardTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("AvatarSystem"))

  implicit val timeout: Timeout = 3.seconds
  val shardMaster = system.actorOf(Props[ClusterMain], "ClusterMain")
  val mediator = DistributedPubSub(system).mediator
  val pipe = {
    val inner = TestProbe("TunnelManager")
    mediator ! Put(inner.ref)
    inner
  }

  "Shard" must {
    "Create avatar" in {
      val uuid = UUID.randomUUID()
      val api = Api(List(Command("TestCommand", Option.empty)))
      mediator.tell(Send (
        path = "/user/ClusterMain/ShardMaster",
        msg = CreateAvatar(uuid, api, pipe.ref),
        localAffinity = false
      ), pipe.ref)
      val messages = pipe.receiveWhile(timeout.duration) { case smthg => smthg }
      println(messages)
      assert(messages.exists {
        case a: AvatarCreated => true
        case _ => false
      })
    }
  }

}
