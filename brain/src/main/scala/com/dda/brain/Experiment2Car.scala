package com.dda.brain

import com.dda.brain.PathfinderBrain.PathPoint

/**
  * Created by dda on 21.05.17.
  */
class Experiment2Car(id: String) extends CarBrain(id) {
  override val target = PathPoint(5.0, 9.0)
}
