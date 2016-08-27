import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorDSL._
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.testkit.{TestKit, TestProbe}
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory
import messages.Constants._
import messages.Messages.{Api, AvatarCreated, CreateAvatar, TunnelEndpoint}
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.zeromq.ZMQ
import pipe.ZmqActor.TunnelCreated
import pipe.{ClusterMain, ZeroMQ}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Created by dda on 7/27/16.
  */

class PipeTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers
  with BeforeAndAfterAll  with TimeLimitedTests {
  def this() = this(ZeroMQ.system)
  system.actorOf(Props[ClusterMain])
  val config = ConfigFactory.load()
  implicit val timeout: Timeout = 3 seconds
  val timeLimit: Span = 20 seconds

  def apiJson(uuid: String) = "{\"uuid\":\"" + uuid.toString + "\"," +
    " \"api\":{\"commands\":[{\"name\":\"test\", \"range\":{\"lower\":1, \"upper\":10}}]}}"

  implicit val tunnelCreatedReads: Reads[TunnelCreated] = (
    (JsPath \ "url").read[String] and
      (JsPath \ "topic").read[String]
    )(TunnelCreated.apply _)

  val parrot = actor(new Act {
    become {
      case TunnelEndpoint =>
      case anything => sender ! anything
    }
  })

  val avatarMaster = actor(new Act {
    ZeroMQ.mediator ! Subscribe(ACTOR_CREATION_SUBSCRIPTION, self)
    become { case CreateAvatar(uuid: UUID, api: Api) => sender.tell(AvatarCreated(uuid, parrot), parrot) }
  })

  val reader = actor(new Act {

    var queues = List[(ZMQ.Socket, ConcurrentLinkedQueue[String])]()

    def readQueue(dealer: ZMQ.Socket, readed: Array[Byte], queue: ConcurrentLinkedQueue[String]): Unit = {
      if (dealer.hasReceiveMore) readQueue(dealer, readed ++ dealer.recv(), queue)
      else {
        val bytesAsString = ByteString(readed).utf8String
        queue.offer(bytesAsString)
      }
    }

    become {
      case "new" =>
        val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("my.own.ports.input"))
        val uuid = UUID.randomUUID().toString
        dealer.setIdentity(uuid.getBytes())
        val storage = new ConcurrentLinkedQueue[String]()
        queues = (dealer, storage) :: queues
        sender ! (dealer, storage, uuid)

      case "poll" =>
        queues.foreach { case (dealer, queue) =>
          val received = dealer.recv(ZMQ.DONTWAIT)
          if (received != null) readQueue(dealer, received, queue)
        }
    }

    context.system.scheduler.schedule(0.seconds, 1.nano, self, "poll")
  })

  def createTunnel(target: ActorRef): TunnelCreated = {
    val probe = TestProbe()
    reader.tell("new", probe.ref)
    val (dealer, queue, uuid) = probe.expectMsgType[(ZMQ.Socket, ConcurrentLinkedQueue[String], String)]
    dealer.send("|" + apiJson(uuid))
    var rawMessage = queue.poll()
    while (rawMessage == null) {
      rawMessage = queue.poll()
      Thread.sleep(1)
    }
    val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)
    val tunnel = Json.parse(rawJson).validate[TunnelCreated].getOrElse(fail("Failed to parse TunnelCreated from json"))
    tunnel.topic shouldBe uuid
    tunnel
  }

  "Tunnel" must {

    "find avatar master and request avatar creation" in {
      createTunnel(TestProbe().ref)
    }

    "create multiple tunnels" in {
      (1 to 5).foreach { i =>
        println(i)
        createTunnel(TestProbe().ref)
        println("Done")
      }
    }

    "async create multiple tunnels" in {
      val futures = (1 to 5).map(i => Future {
        println(i)
        createTunnel(TestProbe().ref)
        println("Done")
      })
      Await.result(Future.sequence(futures), 10 seconds)
    }

    "work correctly if getting malformed json as tunnel created message" in {
      val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("my.own.ports.input"))
      dealer.send("|malformed message 1")
      dealer.send("malformed message 2")
      dealer.setIdentity(UUID.randomUUID().toString.getBytes())
      dealer.send("|malformed message 3")
      dealer.send("malformed message 4")
    }
  }

}