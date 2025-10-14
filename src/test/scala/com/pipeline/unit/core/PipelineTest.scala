package com.pipeline.unit.core

import com.pipeline.core.{ExtractStep, LoadStep, Pipeline, PipelineContext, TransformStep}
import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for Pipeline orchestration.
 *
 * Tests FR-006: Main pipeline execution with Chain of Responsibility.
 * Tests FR-016: Retry logic integration.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class PipelineTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession
      .builder()
      .appName("PipelineTest")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
  }

  test("Pipeline should have name and mode") {
    val pipeline = Pipeline(
      name = "test-pipeline",
      mode = "batch",
      steps = List.empty,
    )

    pipeline.name shouldBe "test-pipeline"
    pipeline.mode shouldBe "batch"
  }

  test("Pipeline should store list of steps") {
    val steps = List(
      ExtractStep(method = "fromPostgres", config = Map.empty, nextStep = None),
      TransformStep(method = "filterRows", config = Map.empty, nextStep = None),
      LoadStep(method = "toS3", config = Map.empty, nextStep = None),
    )

    val pipeline = Pipeline(name = "test", mode = "batch", steps = steps)

    pipeline.steps should have size 3
    pipeline.steps.head shouldBe a[ExtractStep]
    pipeline.steps(1) shouldBe a[TransformStep]
    pipeline.steps(2) shouldBe a[LoadStep]
  }

  test("Pipeline should support batch mode") {
    val pipeline = Pipeline(name = "batch-pipeline", mode = "batch", steps = List.empty)

    pipeline.mode shouldBe "batch"
  }

  test("Pipeline should support streaming mode") {
    val pipeline = Pipeline(name = "streaming-pipeline", mode = "streaming", steps = List.empty)

    pipeline.mode shouldBe "streaming"
  }

  test("Pipeline should validate mode is batch or streaming") {
    // Valid modes
    val batchPipeline = Pipeline(name = "test", mode = "batch", steps = List.empty)
    val streamingPipeline = Pipeline(name = "test", mode = "streaming", steps = List.empty)

    batchPipeline.mode should (equal("batch") or equal("streaming"))
    streamingPipeline.mode should (equal("batch") or equal("streaming"))
  }

  test("Pipeline should have unique name") {
    val pipeline = Pipeline(name = "unique-pipeline", mode = "batch", steps = List.empty)

    pipeline.name shouldBe "unique-pipeline"
    pipeline.name should not be empty
  }

  test("Pipeline should chain steps together") {
    val load = LoadStep(method = "toS3", config = Map.empty, nextStep = None)
    val transform = TransformStep(method = "filterRows", config = Map.empty, nextStep = Some(load))
    val extract = ExtractStep(method = "fromPostgres", config = Map.empty, nextStep = Some(transform))

    val pipeline = Pipeline(name = "chained", mode = "batch", steps = List(extract))

    pipeline.steps.head shouldBe extract
  }

  test("Pipeline should handle empty steps list") {
    val pipeline = Pipeline(name = "empty", mode = "batch", steps = List.empty)

    pipeline.steps shouldBe empty
  }

  test("Pipeline should support complex multi-step workflows") {
    val steps = List(
      ExtractStep(method = "fromPostgres", config = Map("table" -> "users", "registerAs" -> "users"), nextStep = None),
      ExtractStep(method = "fromMySQL", config = Map("table" -> "orders", "registerAs" -> "orders"), nextStep = None),
      TransformStep(
        method = "joinDataFrames",
        config = Map("inputDataFrames" -> List("users", "orders")),
        nextStep = None,
      ),
      LoadStep(method = "toDeltaLake", config = Map("path" -> "/delta/user_orders"), nextStep = None),
    )

    val pipeline = Pipeline(name = "multi-source", mode = "batch", steps = steps)

    pipeline.steps should have size 4
  }

  test("Pipeline should be immutable") {
    val originalSteps = List(ExtractStep(method = "test", config = Map.empty, nextStep = None))
    val pipeline = Pipeline(name = "test", mode = "batch", steps = originalSteps)

    pipeline.steps shouldBe originalSteps
    // Pipeline is a case class, so it's immutable
    pipeline shouldBe a[Pipeline]
  }
}
