package com.pipeline.unit.core

import com.pipeline.core.{PipelineContext, TransformStep}
import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for TransformStep.
 *
 * Tests FR-004: Transform operations (US1 requirement).
 * Tests FR-024: Multi-DataFrame support for joins.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class TransformStepTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession
      .builder()
      .appName("TransformStepTest")
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

  test("TransformStep should store method name") {
    val step = TransformStep(
      method = "filterRows",
      config = Map("condition" -> "age > 18"),
      nextStep = None,
    )

    step.method shouldBe "filterRows"
  }

  test("TransformStep should store configuration") {
    val config = Map(
      "condition" -> "active = true",
      "columns" -> List("id", "name", "email"),
    )
    val step = TransformStep(method = "filterRows", config = config, nextStep = None)

    step.config should contain key "condition"
    step.config("condition") shouldBe "active = true"
  }

  test("TransformStep should support inputDataFrames for multi-DataFrame operations") {
    val config = Map(
      "inputDataFrames" -> List("users", "orders"),
      "joinType" -> "inner",
      "joinColumn" -> "user_id",
    )
    val step = TransformStep(method = "joinDataFrames", config = config, nextStep = None)

    step.config should contain key "inputDataFrames"
    val inputDfs = step.config("inputDataFrames").asInstanceOf[List[String]]
    inputDfs should contain allOf ("users", "orders")
  }

  test("TransformStep should handle single DataFrame transforms") {
    val config = Map("condition" -> "status = 'active'")
    val step = TransformStep(method = "filterRows", config = config, nextStep = None)

    step.config.get("inputDataFrames") shouldBe None
  }

  test("TransformStep should support registerAs for output DataFrame") {
    val config = Map(
      "condition" -> "age > 18",
      "registerAs" -> "adults",
    )
    val step = TransformStep(method = "filterRows", config = config, nextStep = None)

    step.config should contain key "registerAs"
    step.config("registerAs") shouldBe "adults"
  }

  test("TransformStep should chain to next step") {
    val nextStep = TransformStep(method = "aggregate", config = Map.empty, nextStep = None)
    val step = TransformStep(method = "filterRows", config = Map.empty, nextStep = Some(nextStep))

    step.nextStep shouldBe defined
    step.nextStep.get shouldBe nextStep
  }

  test("TransformStep should support multiple transform types") {
    val filterStep = TransformStep(method = "filterRows", config = Map.empty, nextStep = None)
    val joinStep = TransformStep(method = "joinDataFrames", config = Map.empty, nextStep = None)
    val aggStep = TransformStep(method = "aggregateData", config = Map.empty, nextStep = None)
    val reshapeStep = TransformStep(method = "reshapeData", config = Map.empty, nextStep = None)
    val unionStep = TransformStep(method = "unionDataFrames", config = Map.empty, nextStep = None)

    filterStep.method shouldBe "filterRows"
    joinStep.method shouldBe "joinDataFrames"
    aggStep.method shouldBe "aggregateData"
    reshapeStep.method shouldBe "reshapeData"
    unionStep.method shouldBe "unionDataFrames"
  }

  test("TransformStep should support complex join configurations") {
    val config = Map(
      "inputDataFrames" -> List("users", "orders", "products"),
      "joinType" -> "left",
      "joinConditions" -> List(
        Map("left" -> "users.id", "right" -> "orders.user_id"),
        Map("left" -> "orders.product_id", "right" -> "products.id"),
      ),
    )
    val step = TransformStep(method = "joinDataFrames", config = config, nextStep = None)

    val inputDfs = step.config("inputDataFrames").asInstanceOf[List[String]]
    inputDfs should have size 3
  }

  test("TransformStep should support SQL expressions") {
    val config = Map(
      "expression" -> "SELECT id, name, age FROM users WHERE age > 18",
    )
    val step = TransformStep(method = "sqlTransform", config = config, nextStep = None)

    step.config should contain key "expression"
  }

  test("TransformStep should preserve config immutability") {
    val originalConfig = Map("condition" -> "active = true")
    val step = TransformStep(method = "filterRows", config = originalConfig, nextStep = None)

    step.config shouldBe originalConfig
  }
}
