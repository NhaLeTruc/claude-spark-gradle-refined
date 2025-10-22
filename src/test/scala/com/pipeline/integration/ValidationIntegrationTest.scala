package com.pipeline.integration

import com.pipeline.core.{ExtractStep, Pipeline, ValidateStep}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

/**
 * Integration tests for data validation operations.
 *
 * Tests validation failures and edge cases.
 */
@RunWith(classOf[JUnitRunner])
class ValidationIntegrationTest extends IntegrationTestBase {

  behavior of "Validation Integration"

  it should "validate schema and fail on mismatch" in {
    requireDocker()

    // Create source table with inconsistent data
    createTestTable(
      "validation_test",
      """
        |CREATE TABLE validation_test (
        |  id INT,
        |  name VARCHAR(100),
        |  age INT,
        |  email VARCHAR(100)
        |)
      """.stripMargin,
    )

    insertTestData(
      "validation_test",
      Seq(
        Map("id" -> 1, "name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"),
        Map("id" -> 2, "name" -> "Bob", "age"   -> 25, "email" -> "bob@example.com"),
      ),
    )

    // Create pipeline with schema validation
    val props    = getPostgresProperties
    val pipeline = Pipeline(
      name = "validation-test-pipeline",
      mode = "batch",
      steps = List(
        ExtractStep(
          method = "fromPostgres",
          config = Map(
            "host"     -> props("host"),
            "port"     -> props("port"),
            "database" -> props("database"),
            "username" -> props("username"),
            "password" -> props("password"),
            "table"    -> "validation_test",
          ),
          nextStep = None,
        ),
        ValidateStep(
          method = "validateSchema",
          config = Map(
            "expectedColumns" -> List(
              Map("name" -> "id", "type"    -> "integer"),
              Map("name" -> "name", "type"  -> "string"),
              Map("name" -> "age", "type"   -> "integer"),
              Map("name" -> "email", "type" -> "string"),
            ),
          ),
          nextStep = None,
        ),
      ),
    )

    // Execute pipeline
    val result = pipeline.execute(spark)

    result match {
      case Right(_) =>
        logger.info("Schema validation passed as expected")

      case Left(exception) =>
        fail(s"Schema validation should have passed: ${exception.getMessage}")
    }
  }

  it should "validate nulls and fail on null values" in {
    requireDocker()

    // Create table with potential nulls
    createTestTable(
      "null_test",
      """
        |CREATE TABLE null_test (
        |  id INT,
        |  name VARCHAR(100),
        |  required_field VARCHAR(100)
        |)
      """.stripMargin,
    )

    insertTestData(
      "null_test",
      Seq(
        Map("id" -> 1, "name" -> "Alice", "required_field"   -> "value1"),
        Map("id" -> 2, "name" -> "Bob", "required_field"     -> "value2"),
        Map("id" -> 3, "name" -> "Charlie", "required_field" -> null),
      ),
    )

    // Create pipeline with null validation
    val props    = getPostgresProperties
    val pipeline = Pipeline(
      name = "null-validation-pipeline",
      mode = "batch",
      steps = List(
        ExtractStep(
          method = "fromPostgres",
          config = Map(
            "host"     -> props("host"),
            "port"     -> props("port"),
            "database" -> props("database"),
            "username" -> props("username"),
            "password" -> props("password"),
            "table"    -> "null_test",
          ),
          nextStep = None,
        ),
        ValidateStep(
          method = "validateNulls",
          config = Map(
            "notNullColumns" -> List("required_field"),
          ),
          nextStep = None,
        ),
      ),
    )

    // Execute pipeline (should fail due to null value)
    val result = pipeline.execute(spark, maxAttempts = 1)

    result match {
      case Left(exception) =>
        logger.info(s"Null validation failed as expected: ${exception.getMessage}")
        exception.getMessage should include("null")

      case Right(_) =>
        fail("Pipeline should have failed due to null values")
    }
  }

  it should "validate ranges and fail on out-of-range values" in {
    requireDocker()

    // Create table with range violations
    createTestTable(
      "range_test",
      """
        |CREATE TABLE range_test (
        |  id INT,
        |  age INT,
        |  score DECIMAL(5,2)
        |)
      """.stripMargin,
    )

    insertTestData(
      "range_test",
      Seq(
        Map("id" -> 1, "age" -> 25, "score"  -> 85.5),
        Map("id" -> 2, "age" -> 30, "score"  -> 92.3),
        Map("id" -> 3, "age" -> 150, "score" -> 78.1), // Invalid age
      ),
    )

    // Create pipeline with range validation
    val props    = getPostgresProperties
    val pipeline = Pipeline(
      name = "range-validation-pipeline",
      mode = "batch",
      steps = List(
        ExtractStep(
          method = "fromPostgres",
          config = Map(
            "host"     -> props("host"),
            "port"     -> props("port"),
            "database" -> props("database"),
            "username" -> props("username"),
            "password" -> props("password"),
            "table"    -> "range_test",
          ),
          nextStep = None,
        ),
        ValidateStep(
          method = "validateRanges",
          config = Map(
            "ranges"          -> Map(
              "age"   -> Map("min" -> 0, "max" -> 120),
              "score" -> Map("min" -> 0.0, "max" -> 100.0),
            ),
            "failOnViolation" -> true,
          ),
          nextStep = None,
        ),
      ),
    )

    // Execute pipeline (should fail due to age > 120)
    val result = pipeline.execute(spark, maxAttempts = 1)

    result match {
      case Left(exception) =>
        logger.info(s"Range validation failed as expected: ${exception.getMessage}")

      case Right(_) =>
        fail("Pipeline should have failed due to range violations")
    }
  }

  it should "handle empty result sets gracefully" in {
    requireDocker()

    // Create empty table
    createTestTable(
      "empty_table",
      "CREATE TABLE empty_table (id INT, value VARCHAR(50))",
    )

    // Don't insert any data

    // Create pipeline
    val props    = getPostgresProperties
    val pipeline = Pipeline(
      name = "empty-test-pipeline",
      mode = "batch",
      steps = List(
        ExtractStep(
          method = "fromPostgres",
          config = Map(
            "host"     -> props("host"),
            "port"     -> props("port"),
            "database" -> props("database"),
            "username" -> props("username"),
            "password" -> props("password"),
            "table"    -> "empty_table",
          ),
          nextStep = None,
        ),
      ),
    )

    // Execute pipeline
    val result = pipeline.execute(spark)

    result match {
      case Right(context) =>
        val df = context.getPrimaryDataFrame
        df.count() shouldBe 0
        logger.info("Empty result set handled successfully")

      case Left(exception) =>
        fail(s"Pipeline should handle empty results: ${exception.getMessage}")
    }
  }

  it should "enforce business rules validation" in {
    requireDocker()

    // Create table with business rule violations
    createTestTable(
      "business_rules_test",
      """
        |CREATE TABLE business_rules_test (
        |  order_id INT,
        |  customer_type VARCHAR(20),
        |  amount DECIMAL(10,2),
        |  discount DECIMAL(5,2)
        |)
      """.stripMargin,
    )

    insertTestData(
      "business_rules_test",
      Seq(
        Map("order_id" -> 1, "customer_type" -> "VIP", "amount"     -> 100.00, "discount" -> 10.0),
        Map("order_id" -> 2, "customer_type" -> "Regular", "amount" -> 200.00, "discount" -> 5.0),
        Map("order_id" -> 3, "customer_type" -> "VIP", "amount"     -> 50.00, "discount"  -> 50.0), // Discount too high
      ),
    )

    // Create pipeline with business rules
    val props    = getPostgresProperties
    val pipeline = Pipeline(
      name = "business-rules-pipeline",
      mode = "batch",
      steps = List(
        ExtractStep(
          method = "fromPostgres",
          config = Map(
            "host"     -> props("host"),
            "port"     -> props("port"),
            "database" -> props("database"),
            "username" -> props("username"),
            "password" -> props("password"),
            "table"    -> "business_rules_test",
          ),
          nextStep = None,
        ),
        ValidateStep(
          method = "validateBusinessRules",
          config = Map(
            "rules" -> List(
              "discount <= 20", // Discount cannot exceed 20%
              "amount >= 10",   // Order amount must be at least $10
            ),
          ),
          nextStep = None,
        ),
      ),
    )

    // Execute pipeline (should fail due to discount > 20%)
    val result = pipeline.execute(spark, maxAttempts = 1)

    result match {
      case Left(exception) =>
        logger.info(s"Business rules validation failed as expected: ${exception.getMessage}")
        exception.getMessage should include("discount")

      case Right(_) =>
        fail("Pipeline should have failed due to business rule violations")
    }
  }
}
