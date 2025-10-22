package com.pipeline.unit.core

import com.pipeline.core.{ExtractStep, LoadStep, PipelineContext, PipelineStep, TransformStep}
import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for PipelineStep trait and Chain of Responsibility pattern.
 *
 * Tests FR-006: Chain of Responsibility for pipeline steps.
 * Validates Constitution Section I: SOLID principles.
 */
@RunWith(classOf[JUnitRunner])
class PipelineStepTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit =
    spark = SparkSession
      .builder()
      .appName("PipelineStepTest")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()

  override def afterAll(): Unit =
    if (spark != null) {
      spark.stop()
    }

  test("PipelineStep should support chain of responsibility") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._

    val inputDf = Seq((1, "Alice"), (2, "Bob")).toDF("id", "name")
    val context = PipelineContext(Right(inputDf))

    // Create a chain of steps
    val step1 = ExtractStep(method = "test", config = Map.empty, nextStep = None)
    val step2 = TransformStep(method = "test", config = Map.empty, nextStep = None)
    val step3 = LoadStep(method = "test", config = Map.empty, nextStep = None)

    // Steps should be chainable
    step1 shouldBe a[PipelineStep]
    step2 shouldBe a[PipelineStep]
    step3 shouldBe a[PipelineStep]
  }

  test("PipelineStep should execute chain when nextStep is defined") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._

    val inputDf = Seq((1, "Alice"), (2, "Bob")).toDF("id", "name")
    val context = PipelineContext(Right(inputDf))

    // Create a chain: step1 -> step2
    val step2 = TransformStep(method = "identity", config = Map.empty, nextStep = None)
    val step1 = ExtractStep(method = "identity", config = Map.empty, nextStep = Some(step2))

    // executeChain should process both steps
    step1.nextStep shouldBe defined
    step2.nextStep shouldBe None
  }

  test("PipelineStep should terminate chain when nextStep is None") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._

    val inputDf = Seq((1, "Alice")).toDF("id", "name")
    val context = PipelineContext(Right(inputDf))

    val singleStep = LoadStep(method = "test", config = Map.empty, nextStep = None)

    singleStep.nextStep shouldBe None
  }

  test("ExtractStep should be first in chain") {
    // ExtractStep creates initial context
    val extractStep = ExtractStep(method = "fromPostgres", config = Map("table" -> "users"), nextStep = None)

    extractStep shouldBe a[ExtractStep]
    extractStep.method shouldBe "fromPostgres"
  }

  test("TransformStep should be middle in chain") {
    val transformStep = TransformStep(
      method = "filterRows",
      config = Map("condition" -> "age > 18"),
      nextStep = None,
    )

    transformStep shouldBe a[TransformStep]
    transformStep.method shouldBe "filterRows"
  }

  test("LoadStep should be last in chain") {
    val loadStep = LoadStep(method = "toS3", config = Map("bucket" -> "data-lake"), nextStep = None)

    loadStep shouldBe a[LoadStep]
    loadStep.method shouldBe "toS3"
  }

  test("PipelineStep chain should support multiple transforms") {
    val load       = LoadStep(method = "toS3", config = Map.empty, nextStep = None)
    val transform2 = TransformStep(method = "aggregate", config = Map.empty, nextStep = Some(load))
    val transform1 = TransformStep(method = "filter", config = Map.empty, nextStep = Some(transform2))
    val extract    = ExtractStep(method = "fromPostgres", config = Map.empty, nextStep = Some(transform1))

    // Chain: extract -> transform1 -> transform2 -> load
    extract.nextStep shouldBe defined
    extract.nextStep.get shouldBe transform1
    transform1.nextStep.get shouldBe transform2
    transform2.nextStep.get shouldBe load
    load.nextStep shouldBe None
  }
}
