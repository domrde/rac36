import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.zeromq.ZMQ
import pipe.ZeroMQ

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

/**
  * Created by dda on 7/27/16.
  */

class ZeroMQTests(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ZeroMQ.system)
  implicit val timeout: Timeout = 2 second

  "ZeroMQ" must {

    def syncServerToClient(serverGetter: String => ZMQ.Socket, clientGetter: String => ZMQ.Socket,
                           clientsAmount: Int, messagesAmount: Int) = {
      val address = "tcp://localhost:" + (31000 + Random.nextInt(1000))
      val server = serverGetter(address)

      (1 to clientsAmount).map(_ => UUID.randomUUID().toString).foreach { uuid =>
        val client = clientGetter(address)
        client.setIdentity(uuid.getBytes)
        client.send("|StartingMessage")
        ZeroMQ.receiveMessage(server) shouldBe (uuid + "|StartingMessage")
        (1 to messagesAmount).foreach { i =>
          server.sendMore(uuid.getBytes)
          server.send("|Message" + i)
          //Client ignores identity of himself
          ZeroMQ.receiveMessage(client) shouldBe ("|Message" + i)
        }
      }
    }

    def syncClientToServer(serverGetter: String => ZMQ.Socket, clientGetter: String => ZMQ.Socket,
                           clientsAmount: Int, messagesAmount: Int) = {
      val address = "tcp://localhost:" + (31000 + Random.nextInt(1000))
      val server = serverGetter(address)

      (1 to clientsAmount).map(_ => UUID.randomUUID().toString).foreach { uuid =>
        val client = clientGetter(address)
        client.setIdentity(uuid.getBytes)
        (1 to messagesAmount).foreach { i =>
          client.send("|Message" + i)
          ZeroMQ.receiveMessage(server) shouldBe (uuid + "|Message" + i)
        }
      }
    }

    def asyncClientToServer(serverGetter: String => ZMQ.Socket, clientGetter: String => ZMQ.Socket,
                            clientsAmount: Int, messagesAmount: Int) = {
      val address = "tcp://localhost:" + (31000 + Random.nextInt(1000))
      val server = serverGetter(address)

      val futures = (1 to clientsAmount).map(_ => UUID.randomUUID().toString).map (uuid => Future {
        val client = clientGetter(address)
        client.setIdentity(uuid.getBytes)
        (1 to messagesAmount).map { i =>
          val msg = "|" + uuid + "Message" + i
          client.send(msg)
          uuid + msg
        }
      })

      val results = Await.result(Future.sequence(futures), 10 seconds).flatten
      val messages = (1 to results.length).map(_ => ZeroMQ.receiveMessage(server))
      messages.foreach(message => assert(results.contains(message)))
    }

    "router-server dealer-client client->server" in {
      syncClientToServer( (addr) => ZeroMQ.bindRouterSocket(addr), (addr) => ZeroMQ.connectDealerToPort(addr), 20, 20 )
    }

    "router-server dealer-client server->client" in {
      syncServerToClient( (addr) => ZeroMQ.bindRouterSocket(addr), (addr) => ZeroMQ.connectDealerToPort(addr), 20, 20 )
    }

    "async router-server dealer-client client->server" in {
      asyncClientToServer( (addr) => ZeroMQ.bindRouterSocket(addr), (addr) => ZeroMQ.connectDealerToPort(addr), 5, 5 )
    }
  }

}