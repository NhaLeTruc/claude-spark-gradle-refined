package com.pipeline.unit.core

import com.pipeline.core.LoadStep
import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for LoadStep.
 *
 * Tests FR-005: Load to S3 (US1 requirement).
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class LoadStepTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit =
    spark = SparkSession
      .builder()
      .appName("LoadStepTest")
      .master("local[2]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "2")
      .getOrCreate()

  override def afterAll(): Unit =
    if (spark != null) {
      spark.stop()
    }

  test("LoadStep should store method name") {
    val step = LoadStep(
      method = "toS3",
      config = Map("bucket" -> "data-lake", "path" -> "/users"),
      nextStep = None,
    )

    step.method shouldBe "toS3"
  }

  test("LoadStep should store configuration") {
    val config = Map(
      "bucket" -> "data-lake",
      "path"   -> "/processed/users",
      "format" -> "parquet",
    )
    val step   = LoadStep(method = "toS3", config = config, nextStep = None)

    step.config should contain key "bucket"
    step.config("bucket") shouldBe "data-lake"
    step.config should contain key "path"
    step.config should contain key "format"
  }

  test("LoadStep should support credentialPath for secure credentials") {
    val config = Map(
      "bucket"         -> "data-lake",
      "path"           -> "/users",
      "credentialPath" -> "secret/data/s3/production",
    )
    val step   = LoadStep(method = "toS3", config = config, nextStep = None)

    step.config("credentialPath") shouldBe "secret/data/s3/production"
  }

  test("LoadStep should support multiple sink types") {
    val s3Step       = LoadStep(method = "toS3", config = Map.empty, nextStep = None)
    val postgresStep = LoadStep(method = "toPostgres", config = Map.empty, nextStep = None)
    val mysqlStep    = LoadStep(method = "toMySQL", config = Map.empty, nextStep = None)
    val kafkaStep    = LoadStep(method = "toKafka", config = Map.empty, nextStep = None)
    val deltaStep    = LoadStep(method = "toDeltaLake", config = Map.empty, nextStep = None)

    s3Step.method shouldBe "toS3"
    postgresStep.method shouldBe "toPostgres"
    mysqlStep.method shouldBe "toMySQL"
    kafkaStep.method shouldBe "toKafka"
    deltaStep.method shouldBe "toDeltaLake"
  }

  test("LoadStep should support save modes") {
    val config = Map(
      "table" -> "users",
      "mode"  -> "overwrite",
    )
    val step   = LoadStep(method = "toPostgres", config = config, nextStep = None)

    step.config("mode") shouldBe "overwrite"
  }

  test("LoadStep should support format options") {
    val config = Map(
      "bucket"      -> "data-lake",
      "path"        -> "/users",
      "format"      -> "parquet",
      "compression" -> "snappy",
      "partitionBy" -> List("year", "month"),
    )
    val step   = LoadStep(method = "toS3", config = config, nextStep = None)

    step.config("format") shouldBe "parquet"
    step.config("compression") shouldBe "snappy"
    val partitions = step.config("partitionBy").asInstanceOf[List[String]]
    partitions should contain allOf ("year", "month")
  }

  test("LoadStep should chain to next step") {
    val nextStep = LoadStep(method = "toPostgres", config = Map.empty, nextStep = None)
    val step     = LoadStep(method = "toS3", config = Map.empty, nextStep = Some(nextStep))

    step.nextStep shouldBe defined
    step.nextStep.get shouldBe nextStep
  }

  test("LoadStep should support Kafka-specific configuration") {
    val config = Map(
      "topic"           -> "user-events",
      "credentialPath"  -> "secret/data/kafka",
      "keySerializer"   -> "org.apache.kafka.common.serialization.StringSerializer",
      "valueSerializer" -> "org.apache.kafka.common.serialization.StringSerializer",
    )
    val step   = LoadStep(method = "toKafka", config = config, nextStep = None)

    step.config("topic") shouldBe "user-events"
  }

  test("LoadStep should support DeltaLake merge operations") {
    val config = Map(
      "path"        -> "s3a://bucket/delta-table",
      "mode"        -> "append",
      "mergeSchema" -> "true",
    )
    val step   = LoadStep(method = "toDeltaLake", config = config, nextStep = None)

    step.config("mergeSchema") shouldBe "true"
  }

  test("LoadStep should preserve config immutability") {
    val originalConfig = Map("bucket" -> "data-lake", "path" -> "/users")
    val step           = LoadStep(method = "toS3", config = originalConfig, nextStep = None)

    step.config shouldBe originalConfig
  }

  test("LoadStep typically has no next step (end of chain)") {
    val step = LoadStep(method = "toS3", config = Map.empty, nextStep = None)

    step.nextStep shouldBe None
  }
}
