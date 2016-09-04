import java.util.UUID

import akka.actor.ActorDSL._
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.pattern.ask
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import messages.Messages._
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pipe.ZmqActor.WorkWithQueue
import pipe.{ZeroMQ, ZmqActor}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

/**
  * Created by dda on 8/24/16.
  */

//todo: fix tests, they don't work
class ZmqActorTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers
  with BeforeAndAfterAll with TimeLimitedTests {
  def this() = this(ZeroMQ.system)
  val config = ConfigFactory.load()
  implicit val timeout: Timeout = 20 seconds
  val timeLimit: Span = 1 minute

  val avatarMaster = actor("TestAvatarSharding")(new Act {
    ZeroMQ.mediator ! Put(self)
    var messages = Set.empty[ZMQMessage]
    become {
      case CreateAvatar(uuid, api) => sender.tell(AvatarCreated(uuid), self)
      case z: ZMQMessage => messages = messages + z
      case "GetMessages" => sender() ! messages
      case a: TunnelEndpoint =>
      case anything => sender ! anything
    }
  })

  "ZeroMQActor" must {
    "Accept messages from zmq" in {
      val clientsAmount = 5
      val messagesAmount = 5
      val port = 31000 + Random.nextInt(1000)
      val address = "tcp://localhost:" + port
      val zeroMqActor = system.actorOf(ZmqActor(port))

      val probes = (1 to clientsAmount).map { i =>
        val client = ZeroMQ.connectDealerToPort(address)
        val id = UUID.randomUUID()
        client.setIdentity(id.toString.getBytes)
        zeroMqActor ! WorkWithQueue(id)
        (client, id)
      }

      def messageFormat(i: Int, id: String) = "(" + i + ") -- [Message]-[" + id + "]"

      val futures = probes.flatMap { case (client, id) =>
        (1 to messagesAmount).map ( i => Future {
          client.send("|" + messageFormat(i, id.toString))
        })
      }

      Await.result(Future.sequence(futures), timeout.duration)
      val received = Await.result(avatarMaster ? "GetMessages", timeout.duration).asInstanceOf[Set[ZMQMessage]]
      probes.foreach { case (client, id) =>
        (1 to messagesAmount).foreach { i =>
          val msg = ZMQMessage(id, messageFormat(i, id.toString))
          assert(received.contains(msg))
        }
      }
    }

    "Propagate messages from actor to zmq" in {
      val clientsAmount = 5
      val messagesAmount = 5
      val port = 31000 + Random.nextInt(1000)
      val address = "tcp://localhost:" + port
      val zeroMqActor = system.actorOf(ZmqActor(port))

      //      val probes = (1 to clientsAmount).map { i =>
      //        val probe = TestProbe()
      //        val client = ZeroMQ.connectDealerToPort(address)
      //        val id = "Id" + i
      //        client.setIdentity(("Id" + i).getBytes)
      //        zeroMqActor ! WorkWithQueue(id, probe.ref)
      //        probe.expectMsg(timeout.duration, TunnelEndpoint)
      //        (probe, client, id)
      //      }
      //
      //      def messageFormat(i: Int, id: String) = "(" + i + ") -- [Message]-[" + id + "]"
      //
      //      val futures = probes.flatMap { case (probe, client, id) =>
      //        (1 to messagesAmount).map ( i => Future {
      //          zeroMqActor.tell(ZMQMessage(messageFormat(i, id)), probe.ref)
      //        })
      //      }

      //      Await.result(Future.sequence(futures), timeout.duration)
      //      Thread.sleep(500)
      //      probes.foreach { case (probe, client, id) =>
      //        print(id)
      //        val msgs = (1 to messagesAmount + 1).map ( _ => ZeroMQ.receiveMessage(client)).toSet
      //        (1 to messagesAmount).foreach (i => assert(msgs.contains("|" + messageFormat(i, id))))
      //        println(" done")
      //      }
    }

    "Work in continuous both end messaging" in {
      val clientsAmount = 5
      val messagesAmount = 5
      val port = 31000 + Random.nextInt(1000)
      val address = "tcp://localhost:" + port
      val zeroMqActor = system.actorOf(ZmqActor(port))

      //      val probes = (1 to clientsAmount).map { i =>
      //        val probe = TestProbe()
      //        val client = ZeroMQ.connectDealerToPort(address)
      //        val id = "Id" + i
      //        client.setIdentity(("Id" + i).getBytes)
      //        zeroMqActor ! WorkWithQueue(id, probe.ref)
      //        probe.expectMsg(timeout.duration, TunnelEndpoint)
      //        client.recv()
      //        (probe, client, id)
      //      }
      //
      //      def messageFormat(i: Int, id: String) = "(" + i + ") -- [Message]-[" + id + "]"
      //
      //      val futures = probes.flatMap { case (probe, client, id) =>
      //        (1 to messagesAmount).map ( i => Future {
      //          val msg = messageFormat(i, id)
      //          client.send("|" + msg)
      //          probe.expectMsg(timeout.duration, ZMQMessage(msg))
      //          zeroMqActor.tell(ZMQMessage(msg), probe.ref)
      //          ZeroMQ.receiveMessage(client) shouldBe ("|" + msg)
      //        })
      //      }
      //
      //      Await.result(Future.sequence(futures), timeout.duration)
    }
  }
}
