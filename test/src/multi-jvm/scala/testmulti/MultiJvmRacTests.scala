package testmulti

import _root_.pipetest.{CameraStub, TunnelCreator}
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import akka.util.Timeout
import vivarium.ReplicatedSet
import vivarium.ReplicatedSet.{Lookup, LookupResult}
import com.typesafe.config.ConfigFactory
import common.SharedMessages.{Control, Position, Sensory}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pipetest.CameraStub.GetInfo
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by dda on 9/10/16.
  */

object MultiNodeRacTestsConfig extends MultiNodeConfig {
  val first  = role("Pipe-1")
  val second = role("Avatar-1")
  val third = role("Avatar-2")
  val fourth = role("Pipe-2")

  nodeConfig(second, third)(ConfigFactory.parseString(
    """
      akka.cluster.roles = ["Avatar"]
      jars.nfs-directory = brain/target/scala-2.11/
    """))

  nodeConfig(first, fourth)(ConfigFactory.parseString(
    """
      akka.cluster.roles = ["Pipe"]
      pipe.ports.input  = 34671
    """
  ))

  commonConfig(ConfigFactory.parseString(
    """
      akka {
        loglevel = "INFO"
        loggers = ["akka.event.slf4j.Slf4jLogger"]
        actor {
          provider = "akka.cluster.ClusterActorRefProvider"
          serializers {
            kryo = "com.romix.akka.serialization.kryo.KryoSerializer"
          }
          serialization-bindings {
            "common.GlobalMessages" = kryo
            "common.SharedMessages$NumeratedMessage" = kryo
            "java.io.Serializable" = kryo
            "akka.actor.Identify" = akka-misc
            "akka.actor.ActorIdentity" = akka-misc
            "scala.Some" = akka-misc
            "scala.None$" = akka-misc
          }
          kryo.idstrategy = automatic
          kryo.resolve-subclasses = true
        }
        cluster {
          auto-down-unreachable-after = 10s
          sharding {
            guardian-name = "AvatarSharding"
            state-store-mode = "ddata"
            role = "Avatar"
          }
          pub-sub {
            routing-logic = round-robin
          }
          distributed-data.name = ddataReplicator
          metrics {
            enabled = off
            collector.enabled = off
            native-library-extract-folder = "target/"
            periodic-tasks-initial-delay = 10m
          }
        }
        extensions = ["com.romix.akka.serialization.kryo.KryoSerializationExtension$"]
      }
      
      kamon.sigar.folder = akka.cluster.metrics.native-library-extract-folder
    """))
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
  val fourthAddress = node(third).address
  val fifthAddress = node(fourth).address

  "Multiple nodes" must {

    "start cluster on each node" in {

      testConductor.enter("Starting ClusterMain")

      runOn(first) {
        system.actorOf(Props[pipe.ClusterMain], "ClusterMain")
      }

      runOn(second) {

        system.actorOf(Props[vivarium.ClusterMain], "ClusterMain")
      }

      runOn(third) {
        system.actorOf(Props[vivarium.ClusterMain], "ClusterMain")
      }

      runOn(fourth) {
        system.actorOf(Props[pipe.ClusterMain], "ClusterMain")
      }

      Thread.sleep(1000)

      val clusters = Await.result(Future.sequence(List(
        system.actorSelection(firstAddress + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(secondAddress + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(fourthAddress + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(fifthAddress + "/user/ClusterMain").resolveOne(timeout.duration)
      )), timeout.duration)

      assert(clusters.map(_.path.toString).count(_.contains("/user/ClusterMain")) == roles.size)

      testConductor.enter("ClusterMain started")

      runOn(first, second, third, fourth) {
        Cluster(system) join firstAddress
      }

      val expected =
        Set(firstAddress, secondAddress, fourthAddress, fifthAddress)

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

class SampleMultiJvmRacSpecNode2 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Avatar" must {
    "create tunnel" in {
      runOn(second) {
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
        awaitCond({
          val probe = TestProbe()
          replicatedSet.tell(Lookup, probe.ref)
          val result = probe.expectMsgType[LookupResult]
          result.result.contains(sensory)
        }, 10.seconds)

        testConductor.enter("Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
        log.info("\n------>Avatar: Done")
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

class SampleMultiJvmRacSpecNode4 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Avatar2" must {
    "create tunnel" in {
      runOn(third) {
        testConductor.enter("Creating tunnel")
        log.info("\n------>Avatar2: Creating tunnel")
        Thread.sleep(1000)
        testConductor.enter("Tunnel created")
        log.info("\n------>Avatar2: Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
        log.info("\n------>Avatar2: Done")
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

class SampleMultiJvmRacSpecNode5 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Pipe2" must {
    "create tunnel" in {
      runOn(fourth) {
        testConductor.enter("Creating tunnel")
        log.info("\n------>Pipe2: Creating tunnel")
        Thread.sleep(1000)
        testConductor.enter("Tunnel created")
        log.info("\n------>Pipe2: Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
        log.info("\n------>Pipe2: Done")
      }
    }

    "wait 2 sec" in {
      runOn(fourth) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }
}

class SampleMultiJvmRacSpecNode1 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  val tunnelCreator = new TunnelCreator(system)

  implicit val positionWrites = Json.writes[Position]

  implicit val sensoryWrites = Json.writes[Sensory]

  "Pipe" must {
    "create tunnel" in {
      runOn(first) {

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
        awaitCond(p = {
          val probe = TestProbe()
          replicatedSet.tell(Lookup, probe.ref)
          val result = probe.expectMsgType[LookupResult]
          result.result.contains(data)
        }, max = 10.seconds)

        var rawMessage = tunnel._4.poll()
        while (rawMessage == null) {
          rawMessage = tunnel._4.poll()
          Thread.sleep(1)
        }

        val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)

        import common.Implicits._
        Json.parse(rawJson).validate[Control].get shouldBe Control(tunnel._3, "testCommand")

        testConductor.enter("Tunnel created")
        log.info("\n------>Pipe: Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
        log.info("\n------>Pipe: Done")
      }
    }

    "wait 2 sec" in {
      runOn(first) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }
}