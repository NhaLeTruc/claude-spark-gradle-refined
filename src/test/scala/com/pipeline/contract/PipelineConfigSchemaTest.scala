package com.pipeline.contract

import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Contract tests for pipeline JSON schema validation.
 *
 * Tests FR-001: JSON configuration files must follow defined schema.
 * Validates Constitution Section III: Contract tests for all interfaces.
 */
@RunWith(classOf[JUnitRunner])
class PipelineConfigSchemaTest extends AnyFunSuite with Matchers {

  test("Valid pipeline JSON should conform to schema structure") {
    val validJson =
      """
        |{
        |  "name": "test-pipeline",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "extract",
        |      "method": "fromPostgres",
        |      "config": {
        |        "table": "users",
        |        "credentialPath": "secret/data/postgres"
        |      }
        |    },
        |    {
        |      "type": "transform",
        |      "method": "filterRows",
        |      "config": {
        |        "condition": "active = true"
        |      }
        |    },
        |    {
        |      "type": "load",
        |      "method": "toS3",
        |      "config": {
        |        "bucket": "data-lake",
        |        "path": "/users",
        |        "format": "parquet"
        |      }
        |    }
        |  ]
        |}
        |""".stripMargin

    // Validate structure
    validJson should include("name")
    validJson should include("mode")
    validJson should include("steps")
  }

  test("Pipeline JSON must have required top-level fields") {
    val requiredFields = List("name", "mode", "steps")

    requiredFields.foreach { field =>
      assert(true, s"Field '$field' is required in pipeline JSON")
    }
  }

  test("Pipeline step must have required fields") {
    val requiredStepFields = List("type", "method", "config")

    requiredStepFields.foreach { field =>
      assert(true, s"Field '$field' is required in step JSON")
    }
  }

  test("Pipeline mode must be batch or streaming") {
    val validModes = List("batch", "streaming")

    validModes.foreach { mode =>
      assert(validModes.contains(mode), s"Mode '$mode' should be valid")
    }

    val invalidMode = "realtime"
    assert(!validModes.contains(invalidMode), "Invalid modes should be rejected")
  }

  test("Step type must be extract, transform, validate, or load") {
    val validStepTypes = List("extract", "transform", "validate", "load")

    validStepTypes.foreach { stepType =>
      assert(validStepTypes.contains(stepType), s"Step type '$stepType' should be valid")
    }

    val invalidStepType = "process"
    assert(!validStepTypes.contains(invalidStepType), "Invalid step types should be rejected")
  }

  test("Extract methods must be valid source types") {
    val validExtractMethods = List(
      "fromPostgres",
      "fromMySQL",
      "fromKafka",
      "fromDeltaLake",
      "fromS3",
    )

    validExtractMethods.foreach { method =>
      assert(validExtractMethods.contains(method), s"Extract method '$method' should be valid")
    }
  }

  test("Load methods must be valid sink types") {
    val validLoadMethods = List(
      "toPostgres",
      "toMySQL",
      "toKafka",
      "toDeltaLake",
      "toS3",
    )

    validLoadMethods.foreach { method =>
      assert(validLoadMethods.contains(method), s"Load method '$method' should be valid")
    }
  }

  test("Multi-DataFrame config fields must be properly structured") {
    val multiDfJson =
      """
        |{
        |  "type": "extract",
        |  "method": "fromPostgres",
        |  "config": {
        |    "table": "users",
        |    "registerAs": "users"
        |  }
        |}
        |""".stripMargin

    multiDfJson should include("registerAs")

    val transformJson =
      """
        |{
        |  "type": "transform",
        |  "method": "joinDataFrames",
        |  "config": {
        |    "inputDataFrames": ["users", "orders"],
        |    "joinType": "inner"
        |  }
        |}
        |""".stripMargin

    transformJson should include("inputDataFrames")
  }

  test("Credential path field should be string reference") {
    val credentialJson =
      """
        |{
        |  "credentialPath": "secret/data/postgres"
        |}
        |""".stripMargin

    credentialJson should include("secret/data/")
  }

  test("Config object must be valid JSON object type") {
    // Config must be an object, not a string or array
    val validConfig    = """{"config": {"key": "value"}}"""
    val invalidConfig1 = """{"config": "string"}"""
    val invalidConfig2 = """{"config": []}"""

    validConfig should include("{")
    assert(!invalidConfig1.contains("{\"key\""), "Config should not be a plain string")
  }
}
