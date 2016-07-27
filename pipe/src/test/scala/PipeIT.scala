import akka.actor.ActorSystem

import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import org.zeromq.ZMQ
import pipe.{Messages, TunnelManager, ZeroMQ}

import concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.language.postfixOps

/**
  * Created by dda on 7/27/16.
  */
class PipeIT(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(pipe.ZeroMQ.system)
  implicit val timeout: Timeout = 1 second

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Tunnel" must {
    "send messages from zeromq to targer actor" in {
      val zmqContext = ZMQ.context(1)
      val targetActor = TestProbe()
      val tunnel = system.actorOf(Props[TunnelManager])
      val result: TunnelManager.TunnelCreated = Await.result(tunnel ? TunnelManager.CreateTunnel(targetActor.ref), timeout.duration)
        .asInstanceOf[TunnelManager.TunnelCreated]
      val dealer = zmqContext.socket(ZMQ.DEALER)
      dealer.connect("tcp://localhost:" + result.in.path.name)
      1 to 10 foreach { i =>
        dealer.send(result.uuid + " Hello " + i)
        targetActor.expectMsg(Messages.Message("Hello " + i))
        Thread.sleep(1.milliseconds.toMillis)
      }
      dealer.close()
    }

  }
}