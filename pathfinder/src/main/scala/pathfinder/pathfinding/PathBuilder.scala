package pathfinder.pathfinding

import libsvm.{svm, svm_model, svm_node}
import pathfinder.Globals.{Point, distance}

import scala.annotation.tailrec

/**
  * Created by dda on 03.06.17.
  */
object PathBuilder {

  case class PointWithAngle(p: Point, angle: Double)

  private def findAngleOfSignChange(curPoint: Point, initialAngle: Double, toAngle: Double, step: Double)
                           (implicit dims: Point, model: svm_model, searchRadius: Double): Option[Double] = {
    val par = Array.fill(2)(new svm_node)
    par(0) = new svm_node
    par(1) = new svm_node
    par(0).index = 1
    par(1).index = 2

    val y = Math.sin(Math.toRadians(initialAngle)) * searchRadius
    val x = Math.cos(Math.toRadians(initialAngle)) * searchRadius
    par(0).value = (curPoint.x + x) / dims.x
    par(1).value = (curPoint.y + y) / dims.y
    val initialGreaterThanZero = svm.svm_predict(model, par) > 0

    @tailrec
    def rec(curAngle: Double): Option[Double] = {
      if (Math.abs(curAngle - toAngle) < step) None
      else {
        val y = Math.sin(Math.toRadians(curAngle)) * searchRadius
        val x = Math.cos(Math.toRadians(curAngle)) * searchRadius
        par(0).value = (curPoint.x + x) / dims.x
        par(1).value = (curPoint.y + y) / dims.y
        val curSignGreaterThanZero = svm.svm_predict(model, par) > 0
        if (curSignGreaterThanZero == initialGreaterThanZero) {
          rec(curAngle + step)
        } else {
          Some(curAngle)
        }
      }
    }

    rec(initialAngle + step)
  }

  private def findNearestPointsOfPath(source: Point, pathStep: Double)(implicit dims: Point, model: svm_model): List[Point] = {
    val minDim = Math.min(dims.x, dims.y)
    val angleStep = 20.0

    @tailrec
    def rec(currentSearchRadius: Double): List[Point] = {
      if (currentSearchRadius > minDim * 0.55) {
        List.empty
      } else {
        implicit val searchRadius = currentSearchRadius
        val angleOfFirstPoint = findAngleOfSignChange(source, -180, 180, angleStep)
        if (angleOfFirstPoint.isDefined) {
          val angleOfSecondPoint = findAngleOfSignChange(source, angleOfFirstPoint.get + 20, 180, angleStep)
          if (angleOfSecondPoint.isDefined) {
            List(
              Point(
                source.y + Math.sin(Math.toRadians(angleOfFirstPoint.get)) * currentSearchRadius,
                source.x + Math.cos(Math.toRadians(angleOfFirstPoint.get)) * currentSearchRadius
              ),
              Point(
                source.y + Math.sin(Math.toRadians(angleOfSecondPoint.get)) * currentSearchRadius,
                source.x + Math.cos(Math.toRadians(angleOfSecondPoint.get)) * currentSearchRadius
              )
            )
          } else rec(currentSearchRadius + pathStep)
        } else rec(currentSearchRadius + pathStep)
      }
    }

    rec(pathStep)
  }


  def buildPath(svmModel: svm_model, d: Double, angleDelta: Double, from: Point, to: Point, dimsOfField: Point): List[List[Point]] = {
    implicit val dims: Point = dimsOfField
    implicit val searchRadius: Double = d
    implicit val model: svm_model = svmModel
    val pathStep: Double = d

    val par = Array.fill(2)(new svm_node)
    par(0) = new svm_node
    par(1) = new svm_node
    par(0).index = 1
    par(1).index = 2


    def buildPath(accumulator: List[PointWithAngle], limit: Int): List[PointWithAngle] = {
      val curPoint = accumulator.head.p
      val curAngle = accumulator.head.angle
      if (limit < 0 || distance(to, curPoint) < d) {
        accumulator
      } else {
        val estimateAngleOfSignChange =
          findAngleOfSignChange(curPoint, curAngle - angleDelta, curAngle + angleDelta, 10).getOrElse(curAngle)

        val specificAngleOfSignChange =
          findAngleOfSignChange(curPoint, estimateAngleOfSignChange - 10, estimateAngleOfSignChange + 10, 1).getOrElse(curAngle)

        val y = Math.sin(Math.toRadians(specificAngleOfSignChange)) * d
        val x = Math.cos(Math.toRadians(specificAngleOfSignChange)) * d
        par(0).value = (curPoint.x + x) / dims.x
        par(1).value = (curPoint.y + y) / dims.y
        Point(curPoint.y + y, curPoint.x + x)

        buildPath(PointWithAngle(Point(curPoint.y + y, curPoint.x + x), specificAngleOfSignChange) :: accumulator, limit - 1)
      }
    }

    findNearestPointsOfPath(from, pathStep).flatMap { first =>
      findNearestPointsOfPath(first, pathStep).map { second =>
        val angle = Math.toDegrees(Math.atan2(first.y - second.y, first.x - second.x))
        val firstWithAngle = PointWithAngle(first, angle)
        val secondWithAngle = PointWithAngle(second, angle)
        buildPath(List(firstWithAngle, secondWithAngle), 150).map(_.p).reverse
      }
    }
  }

}
