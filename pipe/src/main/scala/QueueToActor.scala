package pipe
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.zeromq.ZMQ

import scala.concurrent.duration._

/**
  * Created by dda on 23.04.16.
  */
object QueueToActor {
  val config = ConfigFactory.load()

  def apply(port: Int) = {
    Props(classOf[QueueToActor], "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + port)
  }

  case class ReadFromQueue(topic: String, target: ActorRef)
  case object Poll
}

class QueueToActor(url: String) extends Actor with ActorLogging {
  import QueueToActor._

  import scala.concurrent.ExecutionContext.Implicits.global

  val router = ZeroMQ.getRouterSocket(url)
  var clients: Map[String, ActorRef] = Map.empty

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    router.close()
    super.postStop()
  }

  override def receive: Receive = {
    case ReadFromQueue(topic, target) => clients += topic -> target; context.watch(target)
    case Terminated => clients.find(_._2 == sender()).foreach(r => clients -= r._1)
    case Poll => readQueue()
    case _ =>
  }

  def readQueue(): Unit = {
    router.recv(ZMQ.NOBLOCK) match {
      case bytes: Array[Byte] =>
        val data = ByteString(bytes).utf8String
        val topicWithMessage = data.splitAt(data.indexOf(" "))
        if (clients.contains(topicWithMessage._1)) {
//          log.info("Message {} with topic {} for {}", topicWithMessage._2, topicWithMessage._1, clients(topicWithMessage._1))
          clients(topicWithMessage._1) ! Messages.Message(topicWithMessage._2.drop(1))
        }
        if (router.hasReceiveMore) readQueue()
      case null =>
    }
  }

  context.system.scheduler.schedule(0.second, 1.millis, self, Poll)
}
