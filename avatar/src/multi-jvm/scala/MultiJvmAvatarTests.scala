import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.cluster.ddata.Replicator.{Get, GetSuccess, ReadLocal}
import akka.cluster.ddata.{DistributedData, ORSet}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import akka.util.Timeout
import avatar.Avatar.AvatarState
import avatar.ClusterMain
import com.typesafe.config.ConfigFactory
import common.SharedMessages._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

/**
  * Created by dda on 8/2/16.
  */

object MultiNodeAvatarTestsConfig extends MultiNodeConfig {
  val first  = role("first")
  val second = role("second")
  val third  = role("third")

  commonConfig(ConfigFactory.parseString(
    """
       akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
       akka.cluster.sharding.state-store-mode = "persistence"
       akka.cluster.sharding.guardian-name = "AvatarSharding"
       akka.cluster.auto-down-unreachable-after = 1s
       akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
       akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
       akka.persistence.snapshot-store.local.dir = "target/example/snapshots"
       akka.loglevel = "INFO"
       akka.cluster.metrics.enabled = off
       akka.cluster.metrics.collector.enabled = off
       akka.cluster.metrics.periodic-tasks-initial-delay = 10m
       kamon.sigar.folder = ""
       jars.nfs-directory = brain/target/scala-2.11/
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

  val mediator = DistributedPubSub(system).mediator

  def sendMessageToMediator(msg: AnyRef, from: ActorRef): Unit = {
    mediator.tell(Send(
      path = "/system/AvatarSharding/Avatar",
      msg = msg,
      localAffinity = false
    ), from)
  }

  def createSingleAvatar() = {
    val uuid = UUID.randomUUID().toString
    val tunnelManager = TestProbe()
    mediator ! Put(tunnelManager.ref)

    val zmqActor = TestProbe()

    sendMessageToMediator(CreateAvatar(uuid, "brain-assembly-1.0.jar", "com.dda.brain.Brain"), tunnelManager.ref)
    val a: AvatarCreated = tunnelManager.expectMsgType[AvatarCreated](timeout.duration)
    a.id shouldBe uuid

    (1 to 5).foreach( i => sendMessageToMediator(GetState(uuid), tunnelManager.ref) )

    tunnelManager.receiveN(5).map {
      case a: AvatarState => a.id
    } shouldBe List.fill(5)(uuid)

    sendMessageToMediator(TunnelEndpoint(uuid, zmqActor.ref), zmqActor.ref)

    uuid
  }

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
        system.actorSelection(firstAddress + "/system/AvatarSharding/Avatar").resolveOne(timeout.duration),
        system.actorSelection(secondAddress + "/system/AvatarSharding/Avatar").resolveOne(timeout.duration),
        system.actorSelection(thirdAddress + "/system/AvatarSharding/Avatar").resolveOne(timeout.duration)
      )), timeout.duration)

      assert(shardMasters.map(_.path.toString).count(_.contains("/system/AvatarSharding/Avatar")) == roles.size)
      testConductor.enter("Shard masters started")
    }

    "create avatar by request" in {

      runOn(first) {
        createSingleAvatar()
      }

      runOn(first, second, third) {
        (1 to 5).foreach(_ => createSingleAvatar())
      }

      testConductor.enter("Waiting avatar creation")
    }

    "share data in cluster" in {
      runOn(first, second, third) {
        val probe = TestProbe()
        val uuids = (1 to 3).map(_ => createSingleAvatar())
        val result = uuids.flatMap { uuid =>
          val coords = (1 to 3).map(_ => Position(Random.nextString(5), Random.nextInt(), Random.nextInt(), Random.nextInt())).toSet
          sendMessageToMediator(Sensory(uuids.head, coords), probe.ref)
          coords
        }

        val replicator = DistributedData(system).replicator
        awaitAssert {
          replicator.tell(Get(common.Constants.DdataSetKey, ReadLocal, None), probe.ref)
          val set = probe.expectMsgType[GetSuccess[ORSet[Position]]].dataValue.elements
          result.foreach(coord => assert(set.contains(coord)))
        }
      }

      testConductor.enter("Waiting share check")
    }

    "save avatars with state on nodes shutdown" in {

      runOn (second, third) {
        testConductor.enter("Waiting avatars connection check")
      }

      runOn(first) {
        val avatarsWithProbes = (1 to 10).map { _ =>
          val probe = TestProbe()
          val uuid = createSingleAvatar()
          sendMessageToMediator(TunnelEndpoint(uuid, probe.ref), probe.ref)
          (uuid, probe)
        }

        def checkAvatarsReachable() = avatarsWithProbes.foreach { case (uuid, probe) =>
          sendMessageToMediator(GetState(uuid), probe.ref)
          val state = probe.expectMsgType[AvatarState]
          // todo: that check should be enabled when snapshot and journal problem will be resolved
          // state shouldBe AvatarState(uuid, probe.ref)
        }

        checkAvatarsReachable()

        val currentCondition = Cluster(system).state.members

        testConductor.enter("Waiting avatars connection check")

        runOn(second, third) {
          system.terminate()
        }

        awaitCond(Cluster(system).state.members != currentCondition, 1.minute)

        println("\n\n\n" +
          "Members set changed" +
          "\n\n\n" +
          currentCondition +
          "\n\n\n" +
          Cluster(system).state.members +
          "\n\n\n"
        )

        checkAvatarsReachable()
      }

    }

  }

}
