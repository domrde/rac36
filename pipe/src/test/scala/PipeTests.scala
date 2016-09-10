import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pipe.{ClusterMain, ZeroMQ}
import pipetest.TunnelCreator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Created by dda on 7/27/16.
  */
// todo: tests doesn't work in parallel
class PipeTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers
  with BeforeAndAfterAll  with TimeLimitedTests {

  val config = ConfigFactory.load()
  def this() = this(ZeroMQ.system)
  system.actorOf(Props[ClusterMain])
  implicit val timeout: Timeout = 3 seconds
  val timeLimit: Span = 20 seconds
  val tunnelCreator = new TunnelCreator(system)


  "Tunnel" must {

    "find avatar master and request avatar creation" in {
      tunnelCreator.createTunnel(TestProbe().ref)
    }

    "create multiple tunnels" in {
      (1 to 5).foreach { i =>
        println(i)
        tunnelCreator.createTunnel(TestProbe().ref)
        println("Done")
      }
    }

    "async create multiple tunnels" in {
      val futures = (1 to 3).map(i => Future {
        println(i)
        tunnelCreator.createTunnel(TestProbe().ref)
        println("Done")
      })
      Await.result(Future.sequence(futures), 10 seconds)
    }

    "work correctly if getting malformed json as tunnel created message" in {
      val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("application.ports.input"))
      dealer.send("|malformed message 1")
      dealer.send("malformed message 2")
      dealer.setIdentity(UUID.randomUUID().toString.getBytes())
      dealer.send("|malformed message 3")
      dealer.send("malformed message 4")
    }
  }

}