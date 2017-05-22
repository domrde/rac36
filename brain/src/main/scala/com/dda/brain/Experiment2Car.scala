package com.dda.brain

import com.dda.brain.BrainMessages.TellToOtherAvatar
import com.dda.brain.PathfinderBrain.{PathPoint, Request}
import upickle.default.write
import scala.concurrent.duration._

/**
  * Created by dda on 21.05.17.
  */
class Experiment2Car(id: String) extends CarBrain(id) {
  override val target = PathPoint(5.0, 9.0)

  cancellation.cancel()

  context.system.scheduler.scheduleOnce(5.second) {
    avatar ! TellToOtherAvatar("pathfinder", write(Request(target)))
  }
}
