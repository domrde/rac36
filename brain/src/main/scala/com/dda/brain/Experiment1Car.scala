package com.dda.brain

import com.dda.brain.PathfinderBrain.PathPoint

/**
  * Created by dda on 21.05.17.
  */
class Experiment1Car(id: String) extends CarBrain(id) {
  override val target = PathPoint(9.2, 9.2)
}
