package testmulti

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
import common.SharedMessages.{Control, Position, Sensory}
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
  val third = role("Api")

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
      api.ports.input  = 34575
      pipe.ports.input = 34670
      extensions = ["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]
      akka.actor.serializers {
        kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
      }
      akka.actor.serialization-bindings {
       "common.GlobalMessages" = kryo
       "common.SharedMessages$NumeratedMessage" = kryo
       "java.io.Serializable" = none
       "akka.actor.Identify" = akka-misc
       "akka.actor.ActorIdentity" = akka-misc
       "scala.Some" = akka-misc
       "scala.None$" = akka-misc
     }
     akka.actor.kryo.idstrategy = automatic
     akka.actor.kryo.resolve-subclasses = true
    """))
}

class SampleMultiJvmRacSpecNode1 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Avatar" must {
    "create tunnel" in {

      testConductor.enter("Creating tunnel")
      log.info("\n------>Avatar: Creating tunnel")

      awaitCond {
        val probe = TestProbe()
        camera.tell(GetInfo, probe.ref)
        probe.expectMsgType[Sensory].sensoryPayload.nonEmpty
      }

      val probe = TestProbe()
      camera.tell(GetInfo, probe.ref)
      val sensory = probe.expectMsgType[Sensory].sensoryPayload

      //waiting for data replication
      awaitCond {
        val probe = TestProbe()
        replicatedSet.tell(Lookup, probe.ref)
        val result = probe.expectMsgType[LookupResult]
        result.result.contains(sensory)
      }

      testConductor.enter("Tunnel created")
      log.info("\n------>Avatar: Tunnel created")
      testConductor.enter("Start sending command")
      log.info("\n------>Avatar: Start sending command")
      testConductor.enter("Robot receiving command")
      log.info("\n------>Avatar: Robot receiving command")
      testConductor.enter("Done")
      log.info("\n------>Avatar: Done")
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

        testConductor.enter("Creating tunnel")
        log.info("\n------>Pipe: Creating tunnel")

        val tunnel = tunnelCreator.createTunnel(TestProbe().ref, "MultiJvmRacTests1")

        def sendCameraDataToAvatar() = {
          val probe = TestProbe()
          camera.tell(GetInfo, probe.ref)
          val sensory = Sensory(tunnel._3, probe.expectMsgType[Sensory].sensoryPayload)
          tunnel._1.send("|" + Json.stringify(Json.toJson(sensory)))
          sensory.sensoryPayload
        }

        val data = sendCameraDataToAvatar()

        //waiting for data replication
        awaitCond {
          val probe = TestProbe()
          replicatedSet.tell(Lookup, probe.ref)
          val result = probe.expectMsgType[LookupResult]
          result.result.contains(data)
        }

        testConductor.enter("Tunnel created")
        log.info("\n------>Pipe: Tunnel created")
        testConductor.enter("Start sending command")
        log.info("\n------>Pipe: Start sending command")
        testConductor.enter("Robot receiving command")
        log.info("\n------>Pipe: Robot receiving command")

        tunnelCreator.readCommandFromQueue(tunnel._4) shouldBe Control("MultiJvmRacTests1", "test", 1)

        testConductor.enter("Done")
        log.info("\n------>Pipe: Done")
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

class SampleMultiJvmRacSpecNode3 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  val tunnelCreator = new TunnelCreator(system)

  "Api" must {
    "create tunnel" in {
      runOn(third) {
        testConductor.enter("Creating tunnel")
        log.info("\n------>Api: Creating tunnel")
        testConductor.enter("Tunnel created")
        log.info("\n------>Api: Tunnel created")
        testConductor.enter("Start sending command")
        log.info("\n------>Api: Requesting list of available commands")

        val requestResult = tunnelCreator.requestRobotCommands("MultiJvmRacTests1")

        log.info("\n------>Api: Commands acquired, sending control")

        tunnelCreator.sendControlToRobot(
          "MultiJvmRacTests1",
          requestResult._3.head.name,
          requestResult._3.head.range.lower,
          requestResult._1
        )

        testConductor.enter("Robot receiving command")
        log.info("\n------>Api: Robot receiving command")
        testConductor.enter("Done")
        log.info("\n------>Api: Done")
      }
    }

    "wait 2 sec" in {
      runOn(third) {
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

  implicit val timeout: Timeout = 30.seconds

  val map =
    "######################\n" +
    "#----1---------------#\n" +
    "-#-------------------#\n" +
    "--#--2---------------#\n" +
    "---#-----------------#\n" +
    "######################\n"

  val camera = system.actorOf(Props(classOf[CameraStub], map))

  val replicatedSet = system.actorOf(ReplicatedSet())

  val firstAddress = node(first).address
  val secondAddress = node(second).address
  val thirdAddress = node(third).address

  "Multiple nodes" must {

    "start cluster on each node" in {

      testConductor.enter("Starting ClusterMain")

      runOn(first) {
        system.actorOf(Props[avatar.ClusterMain], "ClusterMain")
      }

      runOn(second) {
        system.actorOf(Props[pipe.ClusterMain], "ClusterMain")
      }

      runOn(third) {
        system.actorOf(Props[api.ClusterMain], "ClusterMain")
      }

      Thread.sleep(1000)

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
    }
  }

}