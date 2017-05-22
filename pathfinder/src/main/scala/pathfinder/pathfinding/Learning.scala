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
    val epsilonRanges = List(2e-1, 2e1, 2e3)
    val costRanges = List(2e11, 2e13, 2e15).reverse
    val gammaRanges = List(2e-1, 2e0, 2e1)

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

    radialModels.map { modelFuture =>
      modelFuture.map { case (model, description) =>
        RunResults(buildPath(model, minDim * 0.01, 90.0, start, finish, dims), s"step=${minDim * 0.01} " + description)
      }
    }
  }

  private def checkPathCorrect(roughPath: List[Point], obstacles: List[Example], dims: Point, start: Point,
                               finish: Point, eps: Double)(runResults: RunResults): Boolean = {
    val smoothPath = runResults.path

    //    lazy val intersectsExample =
    //      smoothPath.sliding(2).exists {
    //        case a :: b :: Nil =>
    //          obstacles.exists { example =>
    //            example.intersects(a, b)
    //          }
    //
    //        case other =>
    //          throw new RuntimeException("Illegal list sequence in Learning: " + other)
    //      }

    lazy val pointOutsideField =
      smoothPath.exists(point => point.x < 0 || point.y < 0 || point.x > dims.x || point.y > dims.y)

    lazy val isAwayFromStart = distance(smoothPath.head, start) > eps && distance(smoothPath.last, start) > eps

    lazy val isAwayFromFinish = distance(smoothPath.head, finish) > eps && distance(smoothPath.last, finish) > eps

    val isCorrect = !(pointOutsideField || isAwayFromFinish || isAwayFromStart)

    isCorrect
  }

  def smoothPath(dims: Point, start: Point, finish: Point, roughPathFuture: FutureO[List[Point]]): FutureO[RunResults] = {
    roughPathFuture.flatMap { roughPath =>
      if (roughPath.length < 3) {
        println("A* path is too short for smoothing")
        FutureO(Future.successful(Some(RunResults(roughPath, "A* path is too short for smoothing"))))
      } else {
        val height = dims.y
        val width = dims.x
        val minDim = Math.min(width, height)
        val distanceOfExampleFromRoughPath = minDim * 0.03
        val stepOfExampleGeneration = minDim * 0.03

        val examples = roughPath.sliding(2).flatMap {
          case a :: b :: Nil =>
            val part = stepOfExampleGeneration / distance(a, b) + 0.05
            (0.25 to 0.75 by part)
              .map(t => pointInBetween(a, b)(t))
              .flatMap { p =>
                (1.0 to 5.0 by 0.5).map(c => InputMapper.getPivotPoints(a, b, p, c * distanceOfExampleFromRoughPath))
              }
              .flatMap { case (Point(ay, ax), Point(by, bx)) => List(Example(ay, ax, 1.0), Example(by, bx, -1.0)) }

          case other =>
            throw new RuntimeException("Illegal list sequence in Learning: " + other)
        }.toList

        val inRanges = examples.filter { case Example(y, x, _, _) =>
          0 < y && y < height && 0 < x && x < width
        }

        val normalized = inRanges.map { case Example(y, x, c, _) =>
          Example(y / height, x / width, c)
        }

        val results = Learning.runSVM(normalized, dims, start, finish)

        val result: Future[Option[RunResults]] =
          Future.find(results)(checkPathCorrect(roughPath, inRanges, dims, start, finish, distanceOfExampleFromRoughPath / 2.0))

        FutureO(result)
      }
    }
  }

}
