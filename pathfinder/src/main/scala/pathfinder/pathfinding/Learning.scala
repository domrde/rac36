package pathfinder.pathfinding

import libsvm._
import libsvm.svm_parameter._
import pathfinder.FutureO
import pathfinder.Globals._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


object Learning {

  svm.svm_set_print_string_function(new svm_print_interface {
    override def print(s: String): Unit = {
      // do nothing
    }
  })

  private def trainSvmModel(svmType: Int, kernel: Int, field: List[Example], gamma: Double, cost: Double, eps: Double) = {
    val prob = new svm_problem
    prob.l = field.size
    prob.y = new Array[Double](prob.l)

    prob.x = Array.fill(prob.l, 2)(new svm_node)
    field.indices.foreach { i =>
      val p = field(i)
      prob.x(i)(0).index = 1
      prob.x(i)(0).value = p.x
      prob.x(i)(1).index = 2
      prob.x(i)(1).value = p.y
      prob.y(i) = p.c
    }

    val param = new svm_parameter
    param.svm_type = svmType
    param.kernel_type = kernel
    param.degree = 3
    param.gamma = gamma
    param.coef0 = 0
    param.nu = 0.5
    param.cache_size = 40
    param.C = cost
    param.eps = eps
    param.p = 0.1
    param.shrinking = 0
    param.probability = 0
    param.nr_weight = 0
    param.weight_label = new Array[Int](0)
    param.weight = new Array[Double](0)

    svm.svm_train(prob, param)
  }

  private def buildPath(model: svm_model, d: Double, angleDelta: Double, from: Point, to: Point, dims: Point): List[Point] = {
    val par = Array.fill(2)(new svm_node)
    par(0) = new svm_node
    par(1) = new svm_node
    par(0).index = 1
    par(1).index = 2

    def findAngleOfSignChange(curPoint: Point, initialAngle: Double, toAngle: Double, step: Double): Double = {
      val par = Array.fill(2)(new svm_node)
      par(0) = new svm_node
      par(1) = new svm_node
      par(0).index = 1
      par(1).index = 2

      val y = Math.sin(Math.toRadians(initialAngle)) * d
      val x = Math.cos(Math.toRadians(initialAngle)) * d
      par(0).value = (curPoint.x + x) / dims.x
      par(1).value = (curPoint.y + y) / dims.y
      val initialGreaterThanZero = svm.svm_predict(model, par) > 0

      @tailrec
      def rec(curAngle: Double): Double = {
        if (Math.abs(curAngle - toAngle) < step) curAngle
        else {
          val y = Math.sin(Math.toRadians(curAngle)) * d
          val x = Math.cos(Math.toRadians(curAngle)) * d
          par(0).value = (curPoint.x + x) / dims.x
          par(1).value = (curPoint.y + y) / dims.y
          val curSignGreaterThanZero = svm.svm_predict(model, par) > 0
          if (curSignGreaterThanZero == initialGreaterThanZero) {
            rec(curAngle + step)
          } else {
            curAngle
          }
        }
      }

      rec(initialAngle + step)
    }

    def buildPath(accumulator: List[Point], limit: Int): List[Point] =
      if (limit < 0 || distance(to, accumulator.head) < d) accumulator else {
        val curPoint = accumulator.head
        val curAngle = Math.atan2(to.y - curPoint.y, to.x - curPoint.x) * 180.0 / Math.PI

        val estimateAngleOfSignChange = findAngleOfSignChange(curPoint, curAngle - angleDelta, curAngle + angleDelta, 10)
        val specificAngleOfSignChange = findAngleOfSignChange(curPoint, estimateAngleOfSignChange - 10, estimateAngleOfSignChange + 10, 1)

        val y = Math.sin(Math.toRadians(specificAngleOfSignChange)) * d
        val x = Math.cos(Math.toRadians(specificAngleOfSignChange)) * d
        par(0).value = (curPoint.x + x) / dims.x
        par(1).value = (curPoint.y + y) / dims.y
        Point(curPoint.y + y, curPoint.x + x)

        buildPath(Point(curPoint.y + y, curPoint.x + x) :: accumulator, limit - 1)
      }

    buildPath(List(from), 150).reverse
  }

  private def runSVM(obstacles: List[Example], dims: Point, start: Point, finish: Point): List[Future[RunResults]] = {

    val minDim = Math.min(dims.x, dims.y)
    val stepRanges =  List(minDim * 0.05, minDim * 0.02, minDim * 0.01)
    val epsilonRanges = List(2e-1, 2e0, 2e1, 2e3, 2e5)
    val costRanges = List(2e-3, 2e0, 2e5, 2e7, 2e9, 2e11, 2e13, 2e15).reverse
    val gammaRanges = List(2e-3, 2e-1, 2e0, 2e1, 2e3, 2e7)

    val radialModels =
      epsilonRanges.flatMap { eps =>
        costRanges.flatMap { cost =>
          gammaRanges.map { gamma =>
            Future {
              (trainSvmModel(svmType = C_SVC, kernel = RBF, field = obstacles, gamma = gamma, cost = cost, eps = eps),
                s"eps=$eps cost=$cost gamma=$gamma")
            }
          }
        }
      }

    radialModels.flatMap { modelFuture =>
      stepRanges.map { step =>
        modelFuture.map { case (model, description) =>
          RunResults(buildPath(model, step, 90.0, start, finish, dims), s"step=$step " + description)
        }
      }
    }
  }

  private def checkPathCorrect(roughPath: List[Point], dims: Point, start: Point,
                               finish: Point, eps: Double)(runResults: RunResults): Boolean = {
    val smoothPath = runResults.path

    val pointOutsideField =
      smoothPath.exists(point => point.x < 0 || point.y < 0 || point.x > dims.x || point.y > dims.y)

    val isAwayFromStart = distance(smoothPath.head, start) > eps && distance(smoothPath.last, start) > eps

    val isAwayFromFinish = distance(smoothPath.head, finish) > eps && distance(smoothPath.last, finish) > eps

    val isCorrect = !(pointOutsideField || isAwayFromFinish || isAwayFromStart)

    isCorrect
  }

  def smoothPath(dims: Point, start: Point, finish: Point, roughPathFuture: FutureO[List[Point]]): FutureO[RunResults] = {
    roughPathFuture.flatMap { roughPath =>
      val height = dims.y
      val width = dims.x
      val minDim = Math.min(width, height)
      val distanceOfExampleFromRoughPath = minDim * 0.05
      val stepOfExampleGeneration = minDim * 0.03

      val examples = roughPath.sliding(2).flatMap { case a :: b :: Nil =>
        val part = stepOfExampleGeneration / distance(a, b) + 0.05
        (0.25 to 0.75 by part)
          .map(t => pointInBetween(a, b)(t))
          .flatMap { p =>
            (1.0 to 5.0 by 0.5).map(c => InputMapper.getPivotPoints(a, b, p, c * distanceOfExampleFromRoughPath))
          }
          .flatMap { case (Point(ay, ax), Point(by, bx)) => List(Example(ay, ax, 1.0), Example(by, bx, -1.0)) }
      }.toList

      val inRanges = examples.filter { case Example(y, x, _) =>
        0 < y && y < height && 0 < x && x < width
      }

      val normalized = inRanges.map { case Example(y, x, c) =>
        Example(y / height, x / width, c)
      }

      val results = Learning.runSVM(normalized, dims, start, finish)

      val result: Future[Option[RunResults]] =
        Future.find(results)(checkPathCorrect(roughPath, dims, start, finish, distanceOfExampleFromRoughPath / 2.0))

      FutureO(result)
    }
  }

}
