import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import common.zmqHelpers.ZeroMQHelper
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.time.Span
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import pipe.ClusterMain
import pipetest.TunnelCreator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Created by dda on 7/27/16.
  */
object PipeTests {
  val staticSystem = ActorSystem("ClusterSystem")
  val tunnelCreator = new TunnelCreator(staticSystem)
  val config = ConfigFactory.load()
  staticSystem.actorOf(Props[ClusterMain])
  val zmqHelpers = ZeroMQHelper(staticSystem)
}

class PipeTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers
  with BeforeAndAfterAll  with TimeLimitedTests {
  import PipeTests._

  def this() = this(PipeTests.staticSystem)

  val timeLimit: Span = 20 seconds

  implicit val timeout: Timeout = 3 seconds

  "Tunnel" must {

    "find avatar master and request avatar creation" in {
      tunnelCreator.createTunnel(TestProbe().ref, "123")
    }

    "create multiple tunnels" in {
      (1 to 5).foreach { i =>
        println(i)
        tunnelCreator.createTunnel(TestProbe().ref, "abc" + i)
        println("Done")
      }
    }

    "async create multiple tunnels" in {
      val futures = (1 to 3).map(i => Future {
        println(i)
        tunnelCreator.createTunnel(TestProbe().ref, "cde" + i)
        println("Done")
      })
      Await.result(Future.sequence(futures), 10 seconds)
    }

    //doesn't really works
    "work correctly if getting malformed json as tunnel created message" in {
      val dealer = zmqHelpers.connectDealerToPort("tcp://localhost:" + config.getInt("application.ports.input"))
      dealer.send("|malformed message 1")
      dealer.send("malformed message 2")
      dealer.setIdentity(UUID.randomUUID().toString.getBytes())
      dealer.send("|malformed message 3")
      dealer.send("malformed message 4")
    }
  }

}