package pipetest

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.ActorDSL._
import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Put
import akka.testkit.TestProbe
import akka.util.{ByteString, Timeout}
import com.typesafe.config.ConfigFactory
import messages.Messages.{AvatarCreated, CreateAvatar, TunnelEndpoint}
import org.zeromq.ZMQ
import pipe.ZeroMQ
import pipe.ZmqActor.TunnelCreated
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Created by dda on 9/10/16.
  */
class TunnelCreator(actorSystem: ActorSystem) {

  implicit val system = actorSystem

  val config = ConfigFactory.load()

  implicit val timeout: Timeout = 3 seconds

  val mediator = DistributedPubSub(system).mediator


  def apiJson(uuid: String) = "{\"uuid\":\"" + uuid.toString + "\"," +
    " \"api\":{\"commands\":[{\"name\":\"test\", \"range\":{\"lower\":1, \"upper\":10}}]}}"

  implicit val tunnelCreatedReads: Reads[TunnelCreated] = (
    (JsPath \ "url").read[String] and
      (JsPath \ "topic").read[String]
    )(TunnelCreated.apply _)

  val avatarMaster = actor("TestAvatarSharding")(new Act {
    mediator ! Put(self)
    become {
      case CreateAvatar(uuid, api) => sender.tell(AvatarCreated(uuid), self)
      case a: TunnelEndpoint =>
      case anything => sender ! anything
    }
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
        val dealer = ZeroMQ.connectDealerToPort("tcp://localhost:" + config.getInt("application.ports.input"))
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

  def createTunnel(target: ActorRef) = {
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
    val tunnel: TunnelCreated = Json.parse(rawJson).validate[TunnelCreated].get
    assert(tunnel.topic == uuid)
    (dealer, tunnel.url, tunnel.topic, queue)
  }

}
