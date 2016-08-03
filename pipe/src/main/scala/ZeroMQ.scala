package pipe
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import org.zeromq.ZMQ

/**
  * Created by dda on 23.04.16.
  */
object ZeroMQ {
  val system = ActorSystem("PipeSystem")
  val mediator = DistributedPubSub(system).mediator
  private val zmqContext = ZMQ.context(1)

  system.registerOnTermination {
    zmqContext.term()
  }

  def getRouterSocket(url: String) = {
    val router = zmqContext.socket(ZMQ.ROUTER)
    router.bind(url)
    router
  }

  def getDealerSocket(url: String) = {
    val dealer = zmqContext.socket(ZMQ.DEALER)
    dealer.bind(url)
    dealer
  }

}
