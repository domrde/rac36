package utils.zmqHelpers

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

/**
  * Created by dda on 9/14/16.
  */
object JsonValidator {
  case class Validate(bytes: Array[Byte])
  case class ValidationResult(msg: AnyRef)
}

// Json.validate needs implicit reads, provide them in implementation
//
// override def getReads = List(Json.reads[NumeratedMessageChild])
abstract class JsonValidator extends Actor with ActorLogging {
  import JsonValidator._

  def getReads: List[Reads[_ <: AnyRef]]

  override def receive: Receive = {
    case Validate(bytes) =>
      processBytes(bytes)

    case other =>
      log.error("[-] JsonValidator: other [{}] from [{}]", other, sender())
  }

  private def processBytes(bytes: Array[Byte]) = {
    val bytesAsString = ByteString(bytes).utf8String
    val (_, data) = bytesAsString.splitAt(bytesAsString.indexOf("|"))
    Try(Json.parse(data.drop(1))) match {
      case Success(parsedJson) =>
        validateJson(parsedJson)
      case Failure(exception) =>
        log.error("[-] JsonValidator: Malformed message [{}] caused exception [{}]", bytesAsString, exception.getMessage)
    }
  }

  //todo: come up with a better way
  private def validateJson(json: JsValue) = {
    getReads.map { reads =>
      json.validate(reads) match {
        case JsSuccess(value, _) =>
          Some(ValidationResult(value))

        case JsError(_) =>
          None
      }
    } find { // only one result needed, so traversing all list not necessary
      case Some(_) => true
      case _ => false
    } match {
      case Some(Some(x)) => sender() ! x
      case _ => log.error("[-] JsonValidator: Failed to validate json [{}]", json)
    }
  }
}
