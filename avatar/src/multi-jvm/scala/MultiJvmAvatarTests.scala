import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.MemberStatus.Up
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Put, Send}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.TestProbe
import akka.util.Timeout
import avatar.ClusterMain
import com.typesafe.config.ConfigFactory
import messages.Messages._
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

  commonConfig(ConfigFactory.parseString(
    """
       akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
       akka.cluster.sharding.state-store-mode = ddata
       akka.cluster.sharding.guardian-name = "AvatarSharding"
       akka.cluster.auto-down-unreachable-after = 1s
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

  val mediator = DistributedPubSub(system).mediator

  def sendMessageToMediator(msg: AnyRef, from: ActorRef): Unit = {
    mediator.tell(Send(
      path = "/system/AvatarSharding/Avatar",
      msg = msg,
      localAffinity = false
    ), from)
  }

  def createSingleAvatar() = {
    val uuid = UUID.randomUUID()
    val pipe = TestProbe()
    mediator ! Put(pipe.ref)
    val api = Api(List(Command("TestCommand", Option.empty)))

    sendMessageToMediator(CreateAvatar(uuid, api), pipe.ref)
    val a: AvatarCreated = pipe.expectMsgType[AvatarCreated](timeout.duration)
    a.uuid shouldBe uuid

    val messagesSent = (1 to 5).map { i =>
      val msg = ParrotMessage(uuid, "testMessage" + i)
      sendMessageToMediator(msg, pipe.ref)
      msg
    }.toSet

    pipe.receiveN(5).toSet shouldBe messagesSent
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

    "save avatars on nodes shutdown" in {

      runOn (second, third) {
        testConductor.enter("Waiting avatars connection check")
      }

      runOn(first) {
        val avatarUuids = (1 to 10).map(_ => createSingleAvatar())

        def checkAvatarsReachable() = avatarUuids.foreach { uuid =>
          val probe = TestProbe()
          val msg = ParrotMessage(uuid, "Check avatars reacheable")
          sendMessageToMediator(msg, probe.ref)
          probe.expectMsg[ParrotMessage](msg)
        }

        checkAvatarsReachable()

        val currentCondition = Cluster(system).state.members

        testConductor.enter("Waiting avatars connection check")

        runOn(second, third) {
          system.terminate()
        }

        awaitCond(Cluster(system).state.members != currentCondition, 1.minute)

        println("\n\n\n" +
          currentCondition +
          "\n\n\n" +
          Cluster(system).state.members +
          "\n\n\n"
        )

        checkAvatarsReachable()
      }

    }

    //    "share data in cluster" in {
    //
    //    }
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

  }

}
