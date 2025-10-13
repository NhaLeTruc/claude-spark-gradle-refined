package com.pipeline.unit.core

import com.pipeline.core.PipelineContext
import org.apache.avro.generic.GenericRecord
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for PipelineContext.
 *
 * Tests multi-DataFrame support for TransformStep (FR-007, FR-023).
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class PipelineContextTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession
      .builder()
      .appName("PipelineContextTest")
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

  test("PipelineContext should initialize with primary DataFrame") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val df = Seq((1, "Alice"), (2, "Bob")).toDF("id", "name")

    val context = PipelineContext(Right(df))

    context.primary shouldBe a[Right[_, _]]
    context.primary.right.get.count() shouldBe 2
    context.dataFrames shouldBe empty
  }

  test("PipelineContext should register named DataFrame") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val primaryDf = Seq((1, "Alice")).toDF("id", "name")
    val usersDf = Seq((1, "Alice", 30)).toDF("id", "name", "age")

    val context = PipelineContext(Right(primaryDf))
    val updatedContext = context.register("users", usersDf)

    updatedContext.dataFrames.size shouldBe 1
    updatedContext.get("users") shouldBe defined
    updatedContext.get("users").get.count() shouldBe 1
  }

  test("PipelineContext should retrieve registered DataFrame by name") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val primaryDf = Seq((1, "Alice")).toDF("id", "name")
    val ordersDf = Seq((101, 1, 99.99)).toDF("order_id", "user_id", "amount")

    val context = PipelineContext(Right(primaryDf))
      .register("orders", ordersDf)

    val retrieved = context.get("orders")
    retrieved shouldBe defined
    retrieved.get.count() shouldBe 1
    retrieved.get.columns should contain("order_id")
  }

  test("PipelineContext should return None for non-existent DataFrame") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val df = Seq((1, "Alice")).toDF("id", "name")

    val context = PipelineContext(Right(df))

    context.get("nonexistent") shouldBe None
  }

  test("PipelineContext should update primary DataFrame") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val initialDf = Seq((1, "Alice")).toDF("id", "name")
    val newDf = Seq((2, "Bob"), (3, "Charlie")).toDF("id", "name")

    val context = PipelineContext(Right(initialDf))
    val updatedContext = context.updatePrimary(Right(newDf))

    updatedContext.primary.right.get.count() shouldBe 2
  }

  test("PipelineContext should preserve registered DataFrames when updating primary") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val primaryDf = Seq((1, "Alice")).toDF("id", "name")
    val usersDf = Seq((1, "Alice", 30)).toDF("id", "name", "age")
    val newPrimaryDf = Seq((2, "Bob")).toDF("id", "name")

    val context = PipelineContext(Right(primaryDf))
      .register("users", usersDf)
      .updatePrimary(Right(newPrimaryDf))

    context.primary.right.get.count() shouldBe 1
    context.get("users") shouldBe defined
    context.get("users").get.count() shouldBe 1
  }

  test("PipelineContext should support multiple registered DataFrames") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val primaryDf = Seq((1, "Alice")).toDF("id", "name")
    val usersDf = Seq((1, "Alice", 30)).toDF("id", "name", "age")
    val ordersDf = Seq((101, 1, 99.99)).toDF("order_id", "user_id", "amount")
    val productsDf = Seq(("P1", "Widget", 19.99)).toDF("product_id", "name", "price")

    val context = PipelineContext(Right(primaryDf))
      .register("users", usersDf)
      .register("orders", ordersDf)
      .register("products", productsDf)

    context.dataFrames.size shouldBe 3
    context.get("users") shouldBe defined
    context.get("orders") shouldBe defined
    context.get("products") shouldBe defined
  }

  test("PipelineContext should overwrite DataFrame if registered with same name") {
    val sparkImplicits = spark.implicits
    import sparkImplicits._
    val primaryDf = Seq((1, "Alice")).toDF("id", "name")
    val usersDf1 = Seq((1, "Alice")).toDF("id", "name")
    val usersDf2 = Seq((2, "Bob"), (3, "Charlie")).toDF("id", "name")

    val context = PipelineContext(Right(primaryDf))
      .register("users", usersDf1)
      .register("users", usersDf2)

    context.dataFrames.size shouldBe 1
    context.get("users").get.count() shouldBe 2
  }

  test("PipelineContext should support Avro GenericRecord as primary") {
    // Note: This is a placeholder test since Avro conversion is implemented in Phase 9
    // For now, we test the Either type handling
    val context = PipelineContext(Left(null.asInstanceOf[GenericRecord]))

    context.primary shouldBe a[Left[_, _]]
  }
}
