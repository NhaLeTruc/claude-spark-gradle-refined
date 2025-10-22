package com.pipeline.unit.core

import com.pipeline.core.{ExtractStep, PipelineContext}
import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for ExtractStep.
 *
 * Tests FR-003: Extract from PostgreSQL (US1 requirement).
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class ExtractStepTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit =
    spark = SparkSession
      .builder()
      .appName("ExtractStepTest")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()

  override def afterAll(): Unit =
    if (spark != null) {
      spark.stop()
    }

  test("ExtractStep should store method name") {
    val step = ExtractStep(method = "fromPostgres", config = Map("table" -> "users"), nextStep = None)

    step.method shouldBe "fromPostgres"
  }

  test("ExtractStep should store configuration") {
    val config = Map(
      "table"          -> "users",
      "credentialPath" -> "secret/data/postgres",
    )
    val step   = ExtractStep(method = "fromPostgres", config = config, nextStep = None)

    step.config should contain key "table"
    step.config("table") shouldBe "users"
    step.config should contain key "credentialPath"
  }

  test("ExtractStep should support registerAs for DataFrame registration") {
    val config = Map(
      "table"      -> "users",
      "registerAs" -> "users_df",
    )
    val step   = ExtractStep(method = "fromPostgres", config = config, nextStep = None)

    step.config should contain key "registerAs"
    step.config("registerAs") shouldBe "users_df"
  }

  test("ExtractStep should handle missing registerAs") {
    val config = Map("table" -> "orders")
    val step   = ExtractStep(method = "fromPostgres", config = config, nextStep = None)

    step.config.get("registerAs") shouldBe None
  }

  test("ExtractStep should support credentialPath for secure credentials") {
    val config = Map(
      "table"          -> "users",
      "credentialPath" -> "secret/data/postgres/production",
    )
    val step   = ExtractStep(method = "fromPostgres", config = config, nextStep = None)

    step.config("credentialPath") shouldBe "secret/data/postgres/production"
  }

  test("ExtractStep should chain to next step") {
    val nextStep = ExtractStep(method = "fromMySQL", config = Map.empty, nextStep = None)
    val step     = ExtractStep(method = "fromPostgres", config = Map.empty, nextStep = Some(nextStep))

    step.nextStep shouldBe defined
    step.nextStep.get shouldBe nextStep
  }

  test("ExtractStep should support multiple source types") {
    val postgresStep = ExtractStep(method = "fromPostgres", config = Map.empty, nextStep = None)
    val mysqlStep    = ExtractStep(method = "fromMySQL", config = Map.empty, nextStep = None)
    val kafkaStep    = ExtractStep(method = "fromKafka", config = Map.empty, nextStep = None)
    val s3Step       = ExtractStep(method = "fromS3", config = Map.empty, nextStep = None)
    val deltaStep    = ExtractStep(method = "fromDeltaLake", config = Map.empty, nextStep = None)

    postgresStep.method shouldBe "fromPostgres"
    mysqlStep.method shouldBe "fromMySQL"
    kafkaStep.method shouldBe "fromKafka"
    s3Step.method shouldBe "fromS3"
    deltaStep.method shouldBe "fromDeltaLake"
  }

  test("ExtractStep should preserve config immutability") {
    val originalConfig = Map("table" -> "users")
    val step           = ExtractStep(method = "fromPostgres", config = originalConfig, nextStep = None)

    step.config shouldBe originalConfig
    // Config should be immutable
    step.config shouldBe a[Map[_, _]]
  }
}
