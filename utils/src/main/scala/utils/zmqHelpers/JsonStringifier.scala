package utils.zmqHelpers

import akka.actor.{Actor, ActorLogging}
import play.api.libs.json.{JsValue, Json}

/**
  * Created by dda on 9/16/16.
  */
object JsonStringifier {
  case class Stringify(msg: AnyRef, request: Option[AnyRef])
  case class StringifyResult(msg: String, request: Option[AnyRef])
}

// Json.stringify needs implicit writes, provide them in implementation
//
//  override def toJson(msg: AnyRef): JsValue = {
//    msg match {
//      case a: TestObject => testObjectWrites.writes(a)
//    }
//  }
abstract class JsonStringifier extends Actor with ActorLogging {
  import JsonStringifier._

  def toJson(msg: AnyRef): Option[JsValue]

  override def receive: Receive = {
    case Stringify(msg, request) =>
      toJson(msg) match {
        case Some(json) => sender() ! StringifyResult(Json.stringify(json), request)
        case None => log.error("[-] JsonStringifier: not found json writer for [{}] from [{}]", msg, sender())
      }

    case other =>
      log.error("[-] JsonStringifier: other [{}] from [{}]", other, sender())
  }

}
