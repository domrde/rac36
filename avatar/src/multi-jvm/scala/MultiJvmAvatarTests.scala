import akka.actor.ActorDSL._
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

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
       akka.cluster.auto-join = off
    """))
}

class MultiJvmAvatarSpecNode1 extends MultiJvmAvatarTests
class MultiJvmAvatarSpecNode2 extends MultiJvmAvatarTests
class MultiJvmAvatarSpecNode3 extends MultiJvmAvatarTests

abstract class MultiJvmAvatarTests extends MultiNodeSpec(MultiNodeAvatarTestsConfig) with WordSpecLike with Matchers with BeforeAndAfterAll {
  import MultiNodeAvatarTestsConfig._

  override def beforeAll() = multiNodeSpecBeforeAll()
  override def afterAll() = multiNodeSpecAfterAll()
  override def initialParticipants = roles.size

  val firstAddress = node(first).address
  val secondAddress = node(second).address
  val thirdAddress = node(third).address

  "Multiple nodes".must {

    "connect in cluster during tests" in within(10 seconds) {
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

      testConductor.enter("all-joined")
    }

    "create actors" in {
      runOn(first, second, third) {
        val created = actor(new Act {
          become {
            case other => sender() ! other
          }
        })

        val probe = TestProbe()
        created.tell("test", probe.ref)
        probe.expectMsg("test")
      }

      testConductor.enter()
    }

    // todo: avatar tests
//    "share data in cluster" in {
//
//    }
//
//    "create avatar by request" in {
//
//    }
//
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

    enterBarrier()

  }

}
