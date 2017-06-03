package pathfinder.pathfinding

import java.util.concurrent.Executors

import libsvm._
import libsvm.svm_parameter._
import pathfinder.Globals._
import pathfinder.pathfinding.PathBuilder.buildPath
import scala.concurrent.duration._

import scala.concurrent.{Await, ExecutionContext, Future}


object Learning {

  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(200))

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

  private def checkPathCorrect(roughPath: List[Point], dims: Point, start: Point,
                               finish: Point, eps: Double)(result: SvmResult): Boolean = {
    val smoothPath = result.path

    lazy val pointOutsideField =
      smoothPath.exists(point => point.x < 0 || point.y < 0 || point.x > dims.x || point.y > dims.y)

    !pointOutsideField
  }

  private def runSVM(obstacles: List[Example], dims: Point, start: Point, finish: Point): Future[List[List[Point]]] = {

    val pathStep = Math.min(dims.x, dims.y) / 20.0

    Future {
      trainSvmModel(svmType = C_SVC, kernel = RBF, field = obstacles, gamma = 1, cost = 2e11, eps = 2e-1)
    }.map { model =>
      buildPath(model, pathStep, ANGLE_OF_SEARCH, start, finish, dims)
    }
  }

  private def classify(s: Point, f: Point, target: Point) = {
    if ((f.x - s.x) * (target.y - s.y) < (f.y - s.y) * (target.x - s.x)) 1.0 else -1.0
  }

  private def pathDistance(result: SvmResult): Double = {
    result.path.sliding(2).map { case a :: b :: Nil => distance(a, b) }.sum
  }

  def smoothPath(dims: Point, start: Point, finish: Point, roughPath: List[Point]): List[Point] = {
    if (roughPath.length < 3) {
      roughPath
    } else {
      val height = dims.y
      val width = dims.x
      val minDim = Math.min(width, height)
      val distanceOfExampleFromRoughPath = minDim * 0.07
      val stepOfExampleGeneration = minDim * 0.01

      val examples = roughPath.sliding(2).flatMap {
        case a :: b :: Nil =>
          val part = stepOfExampleGeneration / distance(a, b) + 0.01
          (0.25 to 0.75 by part)
            .map(t => pointInBetween(a, b)(t))
            .flatMap { p =>
              (1.0 to 2.0 by 0.3).map(c => getPivotPoints(a, b, p, c * distanceOfExampleFromRoughPath))
            }
            .flatMap { case (Point(ay, ax), Point(by, bx)) => List(Point(ay, ax), Point(by, bx)) }
            .map { p => Example(p.y, p.x, classify(a, b, p), 0.15) }

        case other =>
          throw new RuntimeException("Illegal list sequence in Learning: " + other)
      }.toList

      val inRanges =
        examples
          .filterNot { case Example(y, x, _, _) =>
            roughPath.exists(p => distance(Point(y, x), p) < distanceOfExampleFromRoughPath)
          }

      val normalized = inRanges.map { case Example(y, x, c, r) =>
        Example(y / height, x / width, c, r)
      }

      val pathResult: Future[List[List[Point]]] = Learning.runSVM(normalized, dims, start, finish)

      val paths =
        Await.result(pathResult, 10.second)
          .filter(_.nonEmpty)
          .map(result => SvmResult(result))
          .filter(checkPathCorrect(roughPath, dims, start, finish, 1.0))

      if (paths.nonEmpty) paths.minBy(pathDistance).path
      else List.empty
    }
  }

}
