package testmulti

import java.util.UUID

import _root_.pipetest.TunnelCreator
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import akka.util.Timeout
import avatar.ReplicatedSet
import avatar.ReplicatedSet.{Lookup, LookupResult}
import com.typesafe.config.ConfigFactory
import messages.Messages.{Position, Sensory}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.libs.json.Json
import test.CameraStub
import test.CameraStub.GetInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

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
  import MultiNodeRacTestsConfig._

  "Avatar" must {
    "create tunnel" in {

      awaitCond {
        val probe = TestProbe()
        camera.tell(GetInfo, probe.ref)
        probe.expectMsgType[Sensory].sensoryPayload.nonEmpty
      }

      val probe = TestProbe()
      camera.tell(GetInfo, probe.ref)
      val sensory = probe.expectMsgType[Sensory].sensoryPayload

      awaitCond {
        val probe = TestProbe()
        replicatedSet.tell(Lookup, probe.ref)
        val result = probe.expectMsgType[LookupResult]
        result.result.contains(sensory)
      }

      testConductor.enter("Creating tunnel")
    }

    "wait 2 sec" in {
      runOn(first) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }

}

class SampleMultiJvmRacSpecNode2 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  val tunnelCreator = new TunnelCreator(system)

  implicit val positionWrites = Json.writes[Position]

  implicit val sensoryWrites = Json.writes[Sensory]

  "Pipe" must {
    "create tunnel" in {
      runOn(second) {
        val tunnel = tunnelCreator.createTunnel(TestProbe().ref)

        def sendCameraDataToAvatar() = {
          val probe = TestProbe()
          camera.tell(GetInfo, probe.ref)
          val sensory = Sensory(UUID.fromString(tunnel._3), probe.expectMsgType[Sensory].sensoryPayload)
          tunnel._1.send("|" + Json.stringify(Json.toJson(sensory)))
          sensory.sensoryPayload
        }

        val data = sendCameraDataToAvatar()

        Thread.sleep(2000) // waiting for data replication

        def checkDataDistributed(data: Set[Position]) = {
          val probe = TestProbe()
          replicatedSet.tell(Lookup, probe.ref)
          val result = probe.expectMsgType[LookupResult]
          result.result.get shouldBe data
        }

        checkDataDistributed(data)

        testConductor.enter("Creating tunnel")
      }
    }

    "wait 2 sec" in {
      runOn(second) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
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

  val map =
    "#----1-\n" +
    "-#-----\n" +
    "--#--2-\n" +
    "---#---\n"

  val camera = system.actorOf(Props(classOf[CameraStub], map))

  val replicatedSet = system.actorOf(ReplicatedSet())

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