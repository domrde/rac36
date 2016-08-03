import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.{ByteString, Timeout}
import messages.Constants._
import messages.Messages.{AvatarCreated, CreateAvatar, TunnelEndpoint}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.zeromq.ZMQ
import pipe.Messages.Message
import pipe.TunnelManager.CreateTunnel
import pipe.{Messages, TunnelManager, ZeroMQ}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps

/**
  * Created by dda on 7/27/16.
  */
class PipeTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ZeroMQ.system)
  implicit val timeout: Timeout = 3 second
  val zmqContext = ZMQ.context(1)
  val tunnelManager = ZeroMQ.system.actorOf(Props[TunnelManager])
  val avatarMaster = TestProbe()
  ZeroMQ.mediator ! Subscribe(ACTOR_CREATION_SUBSCRIPTION, avatarMaster.ref)

  override def afterAll = {
    TestKit.shutdownActorSystem(ZeroMQ.system)
  }

  def connectDealerToPort(port: String) = {
    val dealer = zmqContext.socket(ZMQ.DEALER)
    dealer.connect("tcp://localhost:" + port)
    dealer
  }

  def connectRouterToPort(port: String) = {
    val router = zmqContext.socket(ZMQ.ROUTER)
    router.connect("tcp://localhost:" + port)
    router
  }

  def createTunnel(target: ActorRef): TunnelManager.TunnelCreated = {
    tunnelManager ! TunnelManager.CreateTunnel(target)
    Await.result(tunnelManager ? AvatarCreated(target), timeout.duration)
      .asInstanceOf[TunnelManager.TunnelCreated]
  }

  def receiveMessage(router: ZMQ.Socket) = {
    Await.result(Future(router.recv()), timeout.duration)
    var data = Array.emptyByteArray
    while (router.hasReceiveMore) {
      data = data ++ router.recv()
    }
    data
  }

  "Tunnel" must {
    "find avatar master and request avatar creation" in {
      tunnelManager ! CreateTunnel(TestProbe().ref)
      avatarMaster.expectMsg(CreateAvatar)
    }

    "transmit messages from zeromq to remote actor" in {
      val remoteActor = TestProbe()
      val tunnel = createTunnel(remoteActor.ref)
      remoteActor.expectMsg(TunnelEndpoint)
      val dealer = connectDealerToPort(tunnel.inputPort)
      1 to 10 foreach { i =>
        dealer.send(tunnel.uuid + " Hello " + i)
        remoteActor.expectMsg(Message("Hello " + i))
        remoteActor.lastSender shouldBe tunnel.in
      }
      dealer.close()
    }

    "transmit messages from remote actor to zeromq" in {
      val remoteActor = TestProbe()
      val tunnel = createTunnel(remoteActor.ref)
      remoteActor.expectMsg(TunnelEndpoint)
      remoteActor.lastSender shouldBe tunnel.out
      val router = connectRouterToPort(tunnel.outputPort)
      1 to 10 foreach { i =>
        tunnel.out.tell(Messages.Message("Hello " + i), remoteActor.ref)
        ByteString(receiveMessage(router)).utf8String shouldBe (tunnel.uuid + " Hello " + i)
      }
      router.close()
    }

    "transmit messages back and forth" in {
      val remoteActor = TestProbe()
      val tunnel = createTunnel(remoteActor.ref)
      remoteActor.expectMsg(TunnelEndpoint)
      val dealer = connectDealerToPort(tunnel.inputPort)
      val router = connectRouterToPort(tunnel.outputPort)
      1 to 10 foreach { i =>
        dealer.send(tunnel.uuid + " Hello " + i)
        remoteActor.expectMsg(Message("Hello " + i))
        remoteActor.lastSender shouldBe tunnel.in
        tunnel.out.tell(Messages.Message("Hello " + i), remoteActor.ref)
        ByteString(receiveMessage(router)).utf8String shouldBe (tunnel.uuid + " Hello " + i)
      }
      dealer.close()
      router.close()
    }

    "handle transmission from multiple clients" in {
      val remotes = 1 to 50 map(_ => TestProbe())
      val tunnels = remotes.map(remote => (remote, createTunnel(remote.ref)))
      remotes.foreach(remote => remote.expectMsg(TunnelEndpoint))
      val routerDealerPairs = tunnels.map { case (remote, tunnel) =>
        (remote, tunnel, connectRouterToPort(tunnel.outputPort), connectDealerToPort(tunnel.inputPort))}
      1 to 10 foreach { i =>
        routerDealerPairs foreach { case (remoteActor, tunnel, router, dealer) =>
          dealer.send(tunnel.uuid + " Hello " + i)
          remoteActor.expectMsg(Message("Hello " + i))
          remoteActor.lastSender shouldBe tunnel.in
          tunnel.out.tell(Messages.Message("Hello " + i), remoteActor.ref)
          ByteString(receiveMessage(router)).utf8String shouldBe (tunnel.uuid + " Hello " + i)
        }
      }
      routerDealerPairs.foreach { case (remoteActor, tunnel, router, dealer) => router.close(); dealer.close()}
    }
  }

  "Pipe balancer" must {
    "exist" in {
      fail()
    }
  }

}