package pipe
import Messages.Message
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.typesafe.config.ConfigFactory

/**
  * Created by dda on 23.04.16.
  */
object ActorToQueue {
  val config = ConfigFactory.load()

  def apply(port: Int) = {
    Props(classOf[ActorToQueue], "tcp://" + config.getString("akka.remote.netty.tcp.hostname") + ":" + port)
  }

  case class WriteToQueue(target: ActorRef, topic: String)
}

class ActorToQueue(url: String) extends Actor with ActorLogging {
  import ActorToQueue._

  val dealer = ZeroMQ.getDealerSocket(url)
  var clients: Map[ActorRef, String] = Map.empty

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    dealer.close()
    super.postStop()
  }

  override def receive: Receive = {
    case WriteToQueue(target, topic) => clients += target -> topic; context.watch(target)
    case Terminated => clients -= sender()
    case m: Message =>
      if (clients.contains(sender())) {
        log.info("Message {} with topic {} from {}", m.data, clients(sender()), sender())
        dealer.send(clients(sender()) + " " + m.data)
      }
    case _ =>
  }
}
