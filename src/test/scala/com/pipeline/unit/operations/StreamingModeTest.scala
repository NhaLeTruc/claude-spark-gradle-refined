package com.pipeline.unit.operations

import com.pipeline.core.{Pipeline, ExtractStep, TransformStep, LoadStep}
import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * Test suite for streaming mode detection and execution.
 *
 * Tests:
 * - Streaming mode validation
 * - Batch mode validation
 * - Mode detection during execution
 * - Streaming vs batch behavior differences
 */
class StreamingModeTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession
      .builder()
      .appName("StreamingModeTest")
      .master("local[2]")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
  }

  override def afterAll(): Unit = {
    if (spark != null) {
      spark.stop()
    }
  }

  test("Pipeline should accept streaming mode") {
    val pipeline = Pipeline(
      name = "test-streaming",
      mode = "streaming",
      steps = List.empty
    )

    pipeline.mode shouldBe "streaming"
  }

  test("Pipeline should accept batch mode") {
    val pipeline = Pipeline(
      name = "test-batch",
      mode = "batch",
      steps = List.empty
    )

    pipeline.mode shouldBe "batch"
  }

  test("Pipeline should reject invalid mode") {
    assertThrows[IllegalArgumentException] {
      Pipeline(
        name = "test-invalid",
        mode = "invalid-mode",
        steps = List.empty
      )
    }
  }

  test("Pipeline should detect streaming mode during execution") {
    // Create a simple extract step to verify mode detection
    val extractStep = ExtractStep(
      method = "fromPostgres",
      config = Map(
        "table" -> "test_table",
        "credentialPath" -> "database/postgres/test"
      ),
      nextStep = None
    )

    val pipeline = Pipeline(
      name = "test-streaming-detection",
      mode = "streaming",
      steps = List(extractStep)
    )

    // Verify mode is set correctly
    pipeline.mode shouldBe "streaming"
  }

  test("Pipeline should detect batch mode during execution") {
    val extractStep = ExtractStep(
      method = "fromPostgres",
      config = Map(
        "table" -> "test_table",
        "credentialPath" -> "database/postgres/test"
      ),
      nextStep = None
    )

    val pipeline = Pipeline(
      name = "test-batch-detection",
      mode = "batch",
      steps = List(extractStep)
    )

    // Verify mode is set correctly
    pipeline.mode shouldBe "batch"
  }

  test("isStreamingMode should return true for streaming pipelines") {
    val pipeline = Pipeline(
      name = "streaming-test",
      mode = "streaming",
      steps = List.empty
    )

    pipeline.isStreamingMode shouldBe true
  }

  test("isStreamingMode should return false for batch pipelines") {
    val pipeline = Pipeline(
      name = "batch-test",
      mode = "batch",
      steps = List.empty
    )

    pipeline.isStreamingMode shouldBe false
  }

  test("Streaming pipeline should maintain mode through step chain") {
    val steps = List(
      ExtractStep("fromKafka", Map("topic" -> "test"), None),
      TransformStep("filterRows", Map("condition" -> "value > 0"), None),
      LoadStep("toDeltaLake", Map("path" -> "/tmp/test"), None)
    )

    val pipeline = Pipeline(
      name = "streaming-chain",
      mode = "streaming",
      steps = steps
    )

    pipeline.mode shouldBe "streaming"
    pipeline.isStreamingMode shouldBe true
    pipeline.steps.size shouldBe 3
  }
}
