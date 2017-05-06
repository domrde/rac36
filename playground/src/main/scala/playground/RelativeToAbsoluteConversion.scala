package playground

import org.apache.commons.math3.linear.{LUDecomposition, MatrixUtils}
import vrepapiscala.common.{VRepObject, Vec3}

/**
  * Created by dda on 06.05.17.
  */
object RelativeToAbsoluteConversion {
  case class Vec2(y: Double, x: Double)

//    val p = fromRelativeToAbsolute(Vec2(read.detectedPoint.x, read.detectedPoint.z), sensor, robotPosition, rel)

  def fromRelativeToAbsolute(positionRelativeToSensor: Vec2, sensor: VRepObject, robotGlobal: Vec3, robotRelativeToSensor: Vec3): Vec2 = {
    val sensorGlobal = sensor.absolutePosition
    val sensorRelativeToSensor = sensor.positionRelativeTo(sensor)

    val uMatrix = MatrixUtils.createColumnRealMatrix(Array(robotGlobal.x, robotGlobal.y,
      sensorGlobal.x, sensorGlobal.y))

    val mMatrix = MatrixUtils.createRealMatrix(Array(
      Array(robotRelativeToSensor.z, robotRelativeToSensor.x, 1, 0),
      Array(-robotRelativeToSensor.x, robotRelativeToSensor.z, 0, 1),
      Array(sensorRelativeToSensor.z, sensorRelativeToSensor.x, 1, 0),
      Array(-sensorRelativeToSensor.x, sensorRelativeToSensor.z, 0, 1)
    ))

    val result = new LUDecomposition(mMatrix).getSolver().getInverse().multiply(uMatrix).getColumn(0)

    val newY = result(1) * positionRelativeToSensor.x - result(0) * positionRelativeToSensor.y + result(3)
    val newX = result(0) * positionRelativeToSensor.x - result(1) * positionRelativeToSensor.y + result(2)
    Vec2(newY, newX)
  }
}
