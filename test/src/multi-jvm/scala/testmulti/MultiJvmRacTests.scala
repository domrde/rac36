package testmulti

import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.sharding.ClusterSharding
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.dda.brain.BrainMessages
import com.typesafe.config.ConfigFactory
import common.Constants.{AvatarsDdataSetKey, PositionDdataSetKey}
import common.messages.SensoryInformation
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.libs.json.Json
import utils.test.CameraStub
import vivarium.ReplicatedSet.{Lookup, LookupResult}
import vivarium.{Avatar, ReplicatedSet}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by dda on 9/10/16.
  */

object MultiNodeRacTestsConfig extends MultiNodeConfig {
  val pipe1  = role("Pipe-1")
  val avatar1 = role("Avatar-1")
  val avatar2 = role("Avatar-2")
  val pipe2 = role("Pipe-2")

  nodeConfig(avatar1, avatar2)(ConfigFactory.parseString(
    """
      akka.cluster.roles = ["Avatar"]
      application.jars-nfs-directory = brain/target/scala-2.11/
    """))

  nodeConfig(pipe1, pipe2)(ConfigFactory.parseString(
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
            "common.messages.NumeratedMessage" = kryo
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

  private implicit val executionContext = system.dispatcher

  val map =
    "######################\n" +
      "#----1---------------#\n" +
      "-#-------------------#\n" +
      "--#--2---------------#\n" +
      "---#-----------------#\n" +
      "######################\n"

  val camera = system.actorOf(Props(classOf[CameraStub], map))

  val replicatedSet = system.actorOf(ReplicatedSet(PositionDdataSetKey))

  val avatarIdsSet = system.actorOf(ReplicatedSet(AvatarsDdataSetKey))

  val mediator = DistributedPubSub(system).mediator

  val idOfAvatarToCreate = "MultiJvmRacTestsAvatar"

  def awaitAvatarCreation() = {
    awaitCond({
      val probe = TestProbe()
      val shard = ClusterSharding(system).shardRegion("Avatar")
      shard.tell(Avatar.GetState(idOfAvatarToCreate), probe.ref)
      val result = probe.expectMsgType[Avatar.State]
      result.brain.isDefined && result.tunnel.isDefined
    }, 10.seconds)
  }

  def awaitAvatarIdReplication() = {
    awaitCond({
      val probe = TestProbe()
      avatarIdsSet.tell(Lookup, probe.ref)
      val result = probe.expectMsgType[LookupResult[SensoryInformation.Position]]
      result.result.contains(Set(idOfAvatarToCreate))
    }, 10.seconds)
  }

  def awaitCameraDataReplication() = {
    awaitCond({
      val probe = TestProbe()
      camera.tell(CameraStub.GetInfo, probe.ref)
      probe.expectMsgType[SensoryInformation.Sensory].sensoryPayload.nonEmpty
    }, 10.seconds)

    val probe = TestProbe()
    camera.tell(CameraStub.GetInfo, probe.ref)
    val sensory = probe.expectMsgType[SensoryInformation.Sensory].sensoryPayload

    awaitCond({
      val probe = TestProbe()
      replicatedSet.tell(Lookup, probe.ref)
      val result = probe.expectMsgType[LookupResult[SensoryInformation.Position]]
      result.result.contains(sensory)
    }, 10.seconds)
  }

  val pipe1address = node(pipe1).address
  val avatar1address = node(avatar1).address
  val avatar2address = node(avatar2).address
  val pipe2address = node(pipe2).address

  "Multiple nodes" must {

    "start cluster on each node" in {

      testConductor.enter("Starting ClusterMain")

      runOn(pipe1) {
        system.actorOf(Props[pipe.ClusterMain], "ClusterMain")
      }

      runOn(avatar1) {
        system.actorOf(Props[vivarium.ClusterMain], "ClusterMain")
      }

      runOn(avatar2) {
        system.actorOf(Props[vivarium.ClusterMain], "ClusterMain")
      }

      runOn(pipe2) {
        system.actorOf(Props[pipe.ClusterMain], "ClusterMain")
      }

      val clusters = Await.result(Future.sequence(List(
        system.actorSelection(pipe1address + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(avatar1address + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(avatar2address + "/user/ClusterMain").resolveOne(timeout.duration),
        system.actorSelection(pipe2address + "/user/ClusterMain").resolveOne(timeout.duration)
      )), timeout.duration)

      awaitCond(clusters.map(_.path.toString).count(_.contains("/user/ClusterMain")) == roles.size)

      testConductor.enter("ClusterMain started")

      runOn(pipe1, avatar1, avatar2, pipe2) {
        Cluster(system) join pipe1address
      }

      val expected = Set(pipe1address, avatar1address, avatar2address, pipe2address)

      awaitCond(Cluster(system).state.members.toList.map(_.address).toSet == expected)

      awaitCond(Cluster(system).state.members.forall(_.status == Up))

      testConductor.enter("Nodes connected in cluster")
    }
  }
}

class SampleMultiJvmRacSpecNode1 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  val tunnelCreator = new TunnelCreator(system)

  implicit val positionWrites = Json.writes[SensoryInformation.Position]

  implicit val sensoryWrites = Json.writes[SensoryInformation.Sensory]

  "Pipe" must {
    "create tunnel" in {
      runOn(pipe1) {

        testConductor.enter("Creating tunnel")
        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Creating tunnel")

        val tunnel = tunnelCreator.createTunnel(TestProbe().ref, idOfAvatarToCreate)

        testConductor.enter("Waiting for avatar response")
        awaitAvatarCreation()
        testConductor.enter("Avatar responded")

        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Sending ChangeState")

        ClusterSharding(system).shardRegion("Avatar").tell(
          Avatar.ChangeState(idOfAvatarToCreate, BrainMessages.Start), TestProbe().ref)

        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Sending camera data")

        def sendCameraDataToAvatar() = {
          val probe = TestProbe()
          camera.tell(CameraStub.GetInfo, probe.ref)
          val sensory = SensoryInformation.Sensory(tunnel._3, probe.expectMsgType[SensoryInformation.Sensory].sensoryPayload)
          tunnel._1.send("|" + Json.stringify(Json.toJson(sensory)))
          sensory.sensoryPayload
        }

        val data = sendCameraDataToAvatar()

        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Awaiting replication")

        //waiting for data replication
        awaitCond(p = {
          val probe = TestProbe()
          replicatedSet.tell(Lookup, probe.ref)
          val result = probe.expectMsgType[LookupResult[SensoryInformation.Position]]
          result.result.contains(data)
        }, max = 10.seconds)

        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Awaiting command from avatar")

        var rawMessage = tunnel._4.poll()
        while (rawMessage == null) {
          rawMessage = tunnel._4.poll()
          Thread.sleep(1)
        }

        implicit val controlReads = Json.reads[Avatar.FromAvatarToRobot]
        val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)
        Json.parse(rawJson).validate[Avatar.FromAvatarToRobot].get shouldBe Avatar.FromAvatarToRobot(tunnel._3, "testCommand")

        testConductor.enter("Tunnel created")
        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
        log.info("[-] SampleMultiJvmRacSpecNode1 Pipe: Done")
      }
    }

    "wait 2 sec" in {
      runOn(pipe1) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }
}

class SampleMultiJvmRacSpecNode2 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Avatar" must {
    "create avatar" in {
      runOn(avatar1) {
        testConductor.enter("Creating tunnel")
        Thread.sleep(1000)
        testConductor.enter("Waiting for avatar response")
        awaitAvatarCreation()
        awaitAvatarIdReplication()
        testConductor.enter("Avatar responded")
        awaitCameraDataReplication()
        testConductor.enter("Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
      }
    }

    "wait 2 sec" in {
      runOn(avatar1) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }

}

class SampleMultiJvmRacSpecNode3 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Avatar2" must {
    "share data" in {
      runOn(avatar2) {
        testConductor.enter("Creating tunnel")
        Thread.sleep(1000)
        testConductor.enter("Waiting for avatar response")
        awaitAvatarCreation()
        awaitAvatarIdReplication()
        testConductor.enter("Avatar responded")
        awaitCameraDataReplication()
        testConductor.enter("Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
      }
    }

    "wait 2 sec" in {
      runOn(avatar2) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }
}

class SampleMultiJvmRacSpecNode4 extends MultiJvmRacTests {
  import MultiNodeRacTestsConfig._

  "Pipe2" must {
    "share load" in {
      runOn(pipe2) {
        testConductor.enter("Creating tunnel")
        Thread.sleep(1000)
        testConductor.enter("Waiting for avatar response")
        awaitAvatarCreation()
        awaitAvatarIdReplication()
        testConductor.enter("Avatar responded")
        awaitCameraDataReplication()
        testConductor.enter("Tunnel created")
        Thread.sleep(1000)
        testConductor.enter("Done")
      }
    }

    "wait 2 sec" in {
      runOn(pipe2) {
        Thread.sleep(2000)
        testConductor.enter("Two seconds elapsed")
      }
    }
  }
}
