package pipetest

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorDSL._
import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestProbe
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory
import common.SharedMessages._
import common.zmqHelpers.ZeroMQHelper
import org.zeromq.ZMQ
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Created by dda on 9/10/16.
  */
class TunnelCreator(actorSystem: ActorSystem) {
  import common.Implicits._

  implicit val system = actorSystem

  val config = ConfigFactory.load()

  implicit val timeout: Timeout = 3 seconds

  val zmqHelpers = ZeroMQHelper(system)

  def apiJson(id: String) = Json.stringify(Json.toJson(
    CreateAvatar(id, "brain-assembly-1.0.jar", "com.dda.brain.Brain")
  ))

  case class ManageConnection(id: String, port: Int)

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
      case "poll" =>
        queues.foreach { case (dealer, queue) =>
          val received = dealer.recv(ZMQ.DONTWAIT)
          if (received != null) readQueue(dealer, received, queue)
        }

      case ManageConnection(id, port) =>
        val dealer = zmqHelpers.connectDealerToPort("tcp://localhost:" + port)
        dealer.setIdentity(id.getBytes())
        val storage = new ConcurrentLinkedQueue[String]()
        queues = (dealer, storage) :: queues
        sender ! (dealer, storage)
    }

    context.system.scheduler.schedule(0.seconds, 1.milli, self, "poll")
  })

  def createTunnel(target: ActorRef, id: String) = {
    val future = Future {
      val probe = TestProbe()
      reader.tell(ManageConnection(id, config.getInt("pipe.ports.input")), probe.ref)
      val (dealer, queue) = probe.expectMsgType[(ZMQ.Socket, ConcurrentLinkedQueue[String])]
      dealer.send("|" + apiJson(id))
      var rawMessage = queue.poll()
      while (rawMessage == null) {
        rawMessage = queue.poll()
        Thread.sleep(1)
      }
      val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)

      Json.parse(rawJson).validate[FailedToCreateTunnel] match {
        case JsSuccess(value, path) => throw new Exception("Failed to create avatar: " + value.reason)
        case JsError(errors) =>
      }

      val tunnel: TunnelCreated = Json.parse(rawJson).validate[TunnelCreated].get
      assert(tunnel.id == id)
      (dealer, tunnel.url, tunnel.id, queue)
    }
    Await.result(future, 10.seconds)
  }

}
