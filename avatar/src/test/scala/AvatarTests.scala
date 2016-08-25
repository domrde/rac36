import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import avatar.Avatar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * Created by dda on 8/3/16.
  */
class AvatarTests() extends TestKit(ActorSystem("AvatarTestsSpec")) with WordSpecLike with Matchers with BeforeAndAfterAll {

  "Avatar" must {
    "Redirect correct command to robot" in {
      val avatar = system.actorOf(Props[Avatar])

    }

    "Ignore incorrect commands" in {

    }
  }

}
