package playground

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import utils.zmqHelpers.ZeroMQHelper
import vrepapiscala.VRepAPI

import scala.collection.JavaConversions._

/**
  * Created by dda on 9/10/16.
  */
object Boot extends App {
  val system = ActorSystem("ClusterSystem")
  val helper = ZeroMQHelper(system)
  val config = ConfigFactory.load()
  val api = VRepAPI.connect("127.0.0.1", 19997).get

  config.getStringList("playground.car-ids").map(id => system.actorOf(Car(id, api), "Car" + id.substring(1)))
  api.simulation.start()
  system.registerOnTermination(() => api.simulation.stop())
}