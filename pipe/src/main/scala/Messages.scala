package pipe
import java.io.Serializable

/**
  * Created by dda on 24.04.16.
  */
object Messages {
  @SerialVersionUID(2131231L)
  case class Message(data: String) extends Serializable
}
