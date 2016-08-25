import akka.actor.{ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import messages.Constants._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pipe.TunnelManager.TunnelCreated
import pipe.{ClusterMain, ZeroMQ}
import play.api.libs.json.{JsPath, Reads}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by dda on 7/27/16.
  */

class PipeTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ZeroMQ.system)
  system.actorOf(Props[ClusterMain])
  val avatarMaster = TestProbe()
  ZeroMQ.mediator ! Subscribe(ACTOR_CREATION_SUBSCRIPTION, avatarMaster.ref)
  val config = ConfigFactory.load()
  implicit val timeout: Timeout = 3 seconds

  def apiJson(uuid: String) = "{ \"uuid\":\"" + uuid.toString + "\"," +
    " \"api\":{\"commands\":[{\"name\":\"test\", \"range\":{\"lower\":1, \"upper\":10}}]}}"

  implicit val tunnelCreatedReads: Reads[TunnelCreated] =
    (JsPath \ "topic").read[String].map { topic => TunnelCreated(topic) }

  override def afterAll = TestKit.shutdownActorSystem(ZeroMQ.system)

//  def createTunnel(target: ActorRef): TunnelManager.TunnelCreated = {
//    val uuid = UUID.randomUUID()
//    val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("my.own.ports.input"))
//    dealer.send(apiJson(uuid.toString))
//    val msg = avatarMaster.expectMsgType[CreateAvatar](timeout.duration)
//    avatarMaster.lastSender.tell(AvatarCreated(msg.uuid), target)
//    val rawMessage = ZeroMQ.receiveMessage(dealer)
//    val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)
//    val tunnel = Json.parse(rawJson).validate[TunnelCreated].getOrElse(fail("Failed to parse TunnelCreated from json"))
//    tunnel.topic shouldBe uuid.toString
//    tunnel
//  }

  // Можно сделать ddata на pipe, чтоб избавиться от необходимости работы с централизованным
  // распределителем узлов -- клиент будет подключаться к любому pipe и получать аватара и <ip>:<port>,
  // по которому с ним можно связаться
  "Tunnel" must {

//    "find avatar master and request avatar creation" in {
//      createTunnel(TestProbe().ref)
//    }
//
//    "create multiple tunnels" in {
//      val futures = (1 to 100).map(_ => Future(createTunnel(TestProbe().ref)))
//      Await.result(Future.sequence(futures), 10 seconds)
//    }

    //    "work correctly if getting malformed json as tunnel created message" in {
    //      val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("my.own.ports.input"))
    //      dealer.send(config.getString("my.own.topic") + "|malformed message")
    //      dealer.send("one more malformed message")
    //    }
    //
    //    "transmit messages from zeromq to remote actor" in {
    //      val remoteActor = TestProbe()
    //      val tunnel = createTunnel(remoteActor.ref)
    //      remoteActor.expectMsg(timeout.duration, TunnelEndpoint)
    //      val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("my.own.ports.input"))
    //      1 to 10 foreach { i =>
    //        dealer.send(tunnel.topic + "|Hello " + i)
    //        remoteActor.expectMsg(timeout.duration, ZMQMessage("Hello " + i))
    //      }
    //      dealer.close()
    //    }

    //    "transmit messages from remote actor to zeromq" in {
    //      val remoteActor = TestProbe()
    //      val tunnel = createTunnel(remoteActor.ref)
    //      remoteActor.expectMsg(timeout.duration, TunnelEndpoint)
    //      val tunnelEndpoint = remoteActor.lastSender
    //      val router = ZeroMQ.connectRouterToPort("tcp://localhost:" + config.getInt("my.own.ports.output"))
    //      router.setIdentity(tunnel.topic.getBytes)
    //      1 to 10 foreach { i =>
    //        tunnelEndpoint.tell(ZMQMessage("Hello " + i), remoteActor.ref)
    //        ZeroMQ.receiveMessage(router) shouldBe (tunnel.topic + "|Hello " + i)
    //      }
    //      router.close()
    //    }

    //    "transmit messages back and forth" in {
    //      val remoteActor = TestProbe()
    //      val tunnel = createTunnel(remoteActor.ref)
    //      remoteActor.expectMsg(TunnelEndpoint, timeout.duration)
    //      val tunnelEndpoint = remoteActor.lastSender
    //      val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + tunnel.inputPort)
    //      val router = ZeroMQ.connectRouterToPort("tcp://localhost:" + tunnel.outputPort)
    //      1 to 10 foreach { i =>
    //        val msg = remoteActor.ref.path + " " + i
    //        dealer.send(tunnel.topic + " " + msg)
    //        remoteActor.expectMsg(ZMQMessage(msg), timeout.duration)
    //        tunnelEndpoint.tell(ZMQMessage(msg), remoteActor.ref)
    //        ZeroMQ.receiveMessage(router) shouldBe (tunnel.topic + " " + msg)
    //      }
    //      dealer.close()
    //      router.close()
    //    }
    //
    //    "check problem" in {
    //      val remoteActor = TestProbe()
    //      val tunnel = createTunnel(remoteActor.ref)
    //      remoteActor.expectMsg(TunnelEndpoint, timeout.duration)
    //      val tunnelEndpoint = remoteActor.lastSender
    //      val router1 = ZeroMQ.connectRouterToPort("tcp://localhost:" + tunnel.outputPort)
    //      val router2 = ZeroMQ.connectRouterToPort("tcp://localhost:" + tunnel.outputPort)
    //      1 to 10 foreach { i => tunnelEndpoint.tell(ZMQMessage("Hello " + i), remoteActor.ref) }
    //      1 to 10 foreach { i => ZeroMQ.receiveMessage(router1) shouldBe (tunnel.topic + " Hello " + i) }
    //      Await.result(Future(router2.recv()), timeout.duration)
    //      router1.close()
    //      router2.close()
    //    }
    //
    //    "handle transmission from multiple clients" in {
    //      (1 to 50)
    //        .map(_ => TestProbe())
    //        .map { remoteActor =>
    //          val tunnel = createTunnel(remoteActor.ref)
    //          remoteActor.expectMsg(TunnelEndpoint, timeout.duration)
    //          val tunnelEndpoint = remoteActor.lastSender
    //          val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + tunnel.inputPort)
    //          val router = ZeroMQ.connectRouterToPort("tcp://localhost:" + tunnel.outputPort)
    //          (remoteActor, tunnel, tunnelEndpoint, dealer, router)
    //        }
    //        .map { case (remoteActor, tunnel, tunnelEndpoint, dealer, router) => Future {
    //          1 to 50 foreach { i =>
    //            val msg = remoteActor.ref.path + " " + i
    //            dealer.send(tunnel.topic + " " + msg)
    //            remoteActor.expectMsg(ZMQMessage(msg), timeout.duration)
    //            tunnelEndpoint.tell(ZMQMessage(msg), remoteActor.ref)
    //            ZeroMQ.receiveMessage(router) shouldBe (tunnel.topic + " " + msg)
    //          }
    //        }}
    //        .foreach(future => Await.result(future, 1 minute))
    //    }
    //
    //    "provide full registration-sending functionality" in {
    //
    //    }
    //
    //    // dealer sends to everybody, router to only one
    //    "test of router/dealer combination 1" in {
    //      val router = ZeroMQ.bindRouterSocket("tcp://localhost:31111")
    //      val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:31111")
    //      router.setIdentity("Test".getBytes)
    //      dealer.sendMore("Test".getBytes) // replace more with concat
    //      dealer.send("Hello")
    //      ZeroMQ.receiveMessage(router) shouldBe "TestHello"
    //    }
    //
  }

}