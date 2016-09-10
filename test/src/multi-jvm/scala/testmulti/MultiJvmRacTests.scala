package testmulti

import _root_.pipetest.TunnelCreator
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by dda on 9/10/16.
  */

object MultiNodeRacTestsConfig extends MultiNodeConfig {
  val first  = role("Avatar")
  val second = role("Pipe")

  commonConfig(ConfigFactory.parseString(
    """
      akka.loggers = ["akka.event.slf4j.Slf4jLogger"]
      akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
      akka.cluster.sharding.state-store-mode = "persistence"
      akka.cluster.sharding.guardian-name = "AvatarSharding"
      akka.cluster.auto-down-unreachable-after = 1s
      akka.cluster.distributed-data.name = ddataReplicator
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/example/snapshots"
      akka.loglevel = "INFO"
      akka.cluster.metrics.enabled = off
      akka.cluster.metrics.collector.enabled = off
      akka.cluster.metrics.periodic-tasks-initial-delay = 10m
      kamon.sigar.folder = ""
      application.avatarAddress = "/system/AvatarSharding/Avatar"
    """))
}

class SampleMultiJvmRacSpecNode1 extends MultiJvmRacTests {

  "Avatar" must {
    "create tunnel" in {

    }
  }

}

class SampleMultiJvmRacSpecNode2 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  val tunnelCreator = new TunnelCreator(system)

  "Pipe" must {
    "create tunnel" in {
      runOn(second) {
        (1 to 5).foreach { i =>
          tunnelCreator.createTunnel(TestProbe().ref)
        }
      }
    }
  }
}

abstract class MultiJvmRacTests extends MultiNodeSpec(MultiNodeRacTestsConfig) with WordSpecLike
  with Matchers with BeforeAndAfterAll {

  import MultiNodeRacTestsConfig._

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  override def initialParticipants = roles.size

  implicit val timeout: Timeout = 10.seconds

  val firstAddress = node(first).address
  val secondAddress = node(second).address

  "Multiple nodes" must {

    "start cluster on each node" in {

      testConductor.enter("Starting ClusterMain")

      runOn(first) {
        system.actorOf(Props[avatar.ClusterMain], "ClusterMain")
      }

      runOn(second) {
        system.actorOf(Props[pipe.ClusterMain], "ClusterMain")
      }

      Thread.sleep(1000)

      val clusters = Await.result(Future.sequence(List(
        system.actorSelection(firstAddress + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(secondAddress + "/user/ClusterMain").resolveOne(timeout.duration)
      )), timeout.duration)

      assert(clusters.map(_.path.toString).count(_.contains("/user/ClusterMain")) == roles.size)

      testConductor.enter("ClusterMain started")

      runOn(first, second) {
        Cluster(system) join firstAddress
      }

      val expected =
        Set(firstAddress, secondAddress)

      awaitCond(
        Cluster(system).state.members.
          map(_.address) == expected)

      awaitCond(
        Cluster(system).state.members.
          forall(_.status == Up))

      testConductor.enter("Nodes connected in cluster")
    }
  }

}