import java.util.UUID

import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.cluster.pubsub.DistributedPubSub
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.util.Timeout
import avatar.ClusterMain
import com.typesafe.config.ConfigFactory
import messages.Messages.{Api, Command}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by dda on 8/2/16.
  */

object MultiNodeAvatarTestsConfig extends MultiNodeConfig {
  val first  = role("first")
  val second = role("second")
  val third  = role("third")

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString(
    """
       akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
       akka.cluster.sharding.state-store-mode = ddata
       akka.loglevel = "INFO"
    """))
}

class SampleMultiJvmAvatarSpecNode1 extends MultiJvmAvatarTests
class SampleMultiJvmAvatarSpecNode2 extends MultiJvmAvatarTests
class SampleMultiJvmAvatarSpecNode3 extends MultiJvmAvatarTests

abstract class MultiJvmAvatarTests extends MultiNodeSpec(MultiNodeAvatarTestsConfig) with WordSpecLike with Matchers with BeforeAndAfterAll {
  import MultiNodeAvatarTestsConfig._

  override def beforeAll() = multiNodeSpecBeforeAll()
  override def afterAll() = multiNodeSpecAfterAll()
  override def initialParticipants = roles.size

  implicit val timeout: Timeout = 10.seconds

  val firstAddress = node(first).address
  val secondAddress = node(second).address
  val thirdAddress = node(third).address

  "Multiple nodes" must {

    "start cluster on each node" in {
      runOn(first, second, third) {
        system.actorOf(Props[ClusterMain], "ClusterMain")
      }

      val clusters = Await.result(Future.sequence(List(
        system.actorSelection(firstAddress + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(secondAddress + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(thirdAddress + "/user/ClusterMain").resolveOne(timeout.duration)
      )), timeout.duration)

      assert(clusters.map(_.path.toString).count(_.contains("/user/ClusterMain")) == roles.size)

      testConductor.enter("ClusterMain started")

      runOn(first, second, third) {
        Cluster(system) join firstAddress
      }

      val expected =
        Set(firstAddress, secondAddress, thirdAddress)

      awaitCond(
        Cluster(system).state.members.
          map(_.address) == expected)

      awaitCond(
        Cluster(system).state.members.
          forall(_.status == Up))

      testConductor.enter("Nodes connected in cluster")

      val shardMasters = Await.result(Future.sequence(List(
        system.actorSelection(firstAddress + "/user/ClusterMain/ShardMaster").resolveOne(timeout.duration),
        system.actorSelection(secondAddress + "/user/ClusterMain/ShardMaster").resolveOne(timeout.duration),
        system.actorSelection(thirdAddress + "/user/ClusterMain/ShardMaster").resolveOne(timeout.duration)
      )), timeout.duration)

      assert(shardMasters.map(_.path.toString).count(_.contains("/user/ClusterMain/ShardMaster")) == roles.size)
      testConductor.enter("Shard masters started")
    }

    // todo: avatar tests
//    "share data in cluster" in {
//
//    }

    "create avatar by request" in {
      runOn(first) {
        val uuid = UUID.randomUUID()
        val api = Api(List(Command("TestCommand", Option.empty)))
        val mediator = DistributedPubSub(system).mediator
//        mediator ! Publish(ACTOR_CREATION_SUBSCRIPTION, YourApi(uuid, api))
      }

      testConductor.enter("Waiting avatar creation")
    }

//    "save avatars on nodes shutdown" in {
//
//    }
//
//    "save robot's commands list with arguments ranges" in {
//
//    }
//
//    "work under load of multiple sensors" in {
//
//    }
//
//    "choose one node to react to request" in {
//
//    }
//
//    "migrate avatars on new shard connect"

    enterBarrier()

  }

}
