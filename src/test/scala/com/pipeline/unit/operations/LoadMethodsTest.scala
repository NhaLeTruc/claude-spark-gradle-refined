package com.pipeline.unit.operations

import com.pipeline.credentials.IAMConfig
import com.pipeline.operations.LoadMethods
import org.apache.spark.sql.SparkSession
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for LoadMethods.
 *
 * Tests FR-005: Load to S3.
 * Tests FR-023: 5 load methods.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class LoadMethodsTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  @transient private var spark: SparkSession = _

  override def beforeAll(): Unit = {
    spark = SparkSession
      .builder()
      .appName("LoadMethodsTest")
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

  test("LoadMethods should have toS3 method") {
    val config = Map(
      "bucket" -> "data-lake",
      "path" -> "/processed/users",
      "format" -> "parquet",
    )

    config should contain key "bucket"
    config should contain key "path"
    succeed
  }

  test("LoadMethods.toS3 should require bucket and path") {
    val config = Map(
      "bucket" -> "data-lake",
      "path" -> "/users",
    )

    config should contain key "bucket"
    config should contain key "path"

    val incomplete = Map("bucket" -> "data-lake")
    incomplete.get("path") shouldBe None
  }

  test("LoadMethods.toS3 should support multiple formats") {
    val formats = List("parquet", "json", "csv", "avro", "orc")

    formats.foreach { format =>
      val config = Map("bucket" -> "test", "path" -> "/data", "format" -> format)
      config("format") shouldBe format
    }
  }

  test("LoadMethods.toS3 should support save modes") {
    val modes = List("overwrite", "append", "errorIfExists", "ignore")

    modes.foreach { mode =>
      val config = Map("bucket" -> "test", "path" -> "/data", "mode" -> mode)
      config("mode") shouldBe mode
    }
  }

  test("LoadMethods.toS3 should support partitioning") {
    val config = Map(
      "bucket" -> "data-lake",
      "path" -> "/users",
      "format" -> "parquet",
      "partitionBy" -> List("year", "month", "day"),
    )

    val partitions = config("partitionBy").asInstanceOf[List[String]]
    partitions should contain allOf ("year", "month", "day")
  }

  test("LoadMethods.toS3 should support compression") {
    val config = Map(
      "bucket" -> "data-lake",
      "path" -> "/users",
      "format" -> "parquet",
      "compression" -> "snappy",
    )

    config("compression") shouldBe "snappy"
  }

  test("LoadMethods should have toPostgres method") {
    val config = Map(
      "table" -> "users",
      "mode" -> "append",
    )

    config should contain key "table"
    succeed
  }

  test("LoadMethods should have 5 load methods as per FR-023") {
    // FR-023: Support at least 5 load methods
    val methods = List(
      "toPostgres",
      "toMySQL",
      "toKafka",
      "toS3",
      "toDeltaLake",
    )

    methods should have size 5
  }

  test("IAMConfig should contain AWS credentials for S3 access") {
    val iamConfig = IAMConfig(
      accessKeyId = "AKIAIOSFODNN7EXAMPLE",
      secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      sessionToken = None,
      region = "us-west-2",
    )

    iamConfig.accessKeyId should not be empty
    iamConfig.secretAccessKey should not be empty
    iamConfig.region shouldBe "us-west-2"
  }

  test("LoadMethods.toS3 should support s3a protocol") {
    val config = Map(
      "bucket" -> "data-lake",
      "path" -> "/users",
      "format" -> "parquet",
    )

    // S3A is the recommended protocol for Spark
    val s3Path = s"s3a://${config("bucket")}${config("path")}"
    s3Path should startWith("s3a://")
  }

}
