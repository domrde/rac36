package pipetest

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorDSL._
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.testkit.TestProbe
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory
import common.ApiActor.{GetAvailableCommands, GetAvailableCommandsResult, SendCommandToAvatar}
import common.SharedMessages._
import common.zmqHelpers.ZeroMQHelper
import org.zeromq.ZMQ
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * Created by dda on 9/10/16.
  */
class TunnelCreator(actorSystem: ActorSystem) {
  import common.Implicits._

  implicit val system = actorSystem

  val config = ConfigFactory.load()

  implicit val timeout: Timeout = 3 seconds

  val mediator = DistributedPubSub(system).mediator

  val zmqHelpers = ZeroMQHelper(system)


  def apiJson(id: String) = Json.stringify(Json.toJson(
    CreateAvatar(id, Api(List(Command("test", ArgumentRange(1, 10)))))
  ))

  def getAvailableCommands(id: String) = Json.stringify(Json.toJson(GetAvailableCommands(id)))

  val avatarMaster = actor("TestAvatarSharding")(new Act {
    mediator ! Put(self)
    become {
      case CreateAvatar(id, api) => sender.tell(AvatarCreated(id), self)
      case a: TunnelEndpoint =>
      case anything => sender ! anything
    }
  })

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
      val tunnel: TunnelCreated = Json.parse(rawJson).validate[TunnelCreated].get
      assert(tunnel.id == id)
      (dealer, tunnel.url, tunnel.id, queue)
    }
    Await.result(future, 10.seconds)
  }

  def requestRobotCommands(id: String) = {
    val probe = TestProbe()
    reader.tell(ManageConnection(id, config.getInt("api.ports.input")), probe.ref)
    val (dealer, queue) = probe.expectMsgType[(ZMQ.Socket, ConcurrentLinkedQueue[String])]
    dealer.send("|" + getAvailableCommands(id))
    var rawMessage = queue.poll()
    while (rawMessage == null) {
      rawMessage = queue.poll()
      Thread.sleep(1)
    }
    val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)
    val commands = Json.parse(rawJson).validate[GetAvailableCommandsResult].get
    (dealer, queue, commands.commands)
  }

  def sendControlToRobot(id: String, name: String, value: Long, dealer: ZMQ.Socket) = {
    dealer.send("|" + Json.stringify(Json.toJson(SendCommandToAvatar(id, name, value))))
  }

  def readCommandFromQueue(queue: ConcurrentLinkedQueue[String]): Control = {
    var rawMessage = queue.poll()
    while (rawMessage == null) {
      rawMessage = queue.poll()
      Thread.sleep(1)
    }
    val rawJson = rawMessage.splitAt(rawMessage.indexOf("|"))._2.drop(1)
    Json.parse(rawJson).validate[Control].get
  }

}
