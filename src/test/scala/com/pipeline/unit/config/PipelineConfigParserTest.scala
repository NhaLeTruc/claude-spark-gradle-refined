package com.pipeline.unit.config

import com.pipeline.config.PipelineConfigParser
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for PipelineConfigParser.
 *
 * Tests FR-014: Parse JSON configuration files with external references.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class PipelineConfigParserTest extends AnyFunSuite with Matchers {

  test("PipelineConfigParser should parse simple JSON configuration") {
    val json =
      """
        |{
        |  "name": "simple-pipeline",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "extract",
        |      "method": "fromPostgres",
        |      "config": {"table": "users"}
        |    }
        |  ]
        |}
        |""".stripMargin

    val config = PipelineConfigParser.parse(json)

    config.name shouldBe "simple-pipeline"
    config.mode shouldBe "batch"
    config.steps should have size 1
    config.steps.head.stepType shouldBe "extract"
    config.steps.head.method shouldBe "fromPostgres"
  }

  test("PipelineConfigParser should parse multi-step pipeline") {
    val json =
      """
        |{
        |  "name": "multi-step-pipeline",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "extract",
        |      "method": "fromPostgres",
        |      "config": {"table": "users"}
        |    },
        |    {
        |      "type": "transform",
        |      "method": "filterRows",
        |      "config": {"condition": "age > 18"}
        |    },
        |    {
        |      "type": "load",
        |      "method": "toS3",
        |      "config": {"bucket": "data-lake", "path": "/users"}
        |    }
        |  ]
        |}
        |""".stripMargin

    val config = PipelineConfigParser.parse(json)

    config.steps should have size 3
    config.steps(0).stepType shouldBe "extract"
    config.steps(1).stepType shouldBe "transform"
    config.steps(2).stepType shouldBe "load"
  }

  test("PipelineConfigParser should handle registerAs field for DataFrame registration") {
    val json =
      """
        |{
        |  "name": "multi-df-pipeline",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "extract",
        |      "method": "fromPostgres",
        |      "config": {
        |        "table": "users",
        |        "registerAs": "users"
        |      }
        |    }
        |  ]
        |}
        |""".stripMargin

    val config = PipelineConfigParser.parse(json)

    val extractStep = config.steps.head
    extractStep.config should contain key "registerAs"
    extractStep.config("registerAs") shouldBe "users"
  }

  test("PipelineConfigParser should handle inputDataFrames field for multi-DataFrame transforms") {
    val json =
      """
        |{
        |  "name": "join-pipeline",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "transform",
        |      "method": "joinDataFrames",
        |      "config": {
        |        "inputDataFrames": ["users", "orders"],
        |        "joinType": "inner",
        |        "joinColumn": "user_id"
        |      }
        |    }
        |  ]
        |}
        |""".stripMargin

    val config = PipelineConfigParser.parse(json)

    val transformStep = config.steps.head
    transformStep.config should contain key "inputDataFrames"
    val inputDfs = transformStep.config("inputDataFrames").asInstanceOf[List[String]]
    inputDfs should contain allOf ("users", "orders")
  }

  test("PipelineConfigParser should fail on invalid JSON") {
    val invalidJson = """{ invalid json }"""

    an[Exception] should be thrownBy {
      PipelineConfigParser.parse(invalidJson)
    }
  }

  test("PipelineConfigParser should fail on missing required fields") {
    val json =
      """
        |{
        |  "name": "incomplete-pipeline"
        |}
        |""".stripMargin

    an[Exception] should be thrownBy {
      PipelineConfigParser.parse(json)
    }
  }

  test("PipelineConfigParser should handle streaming mode") {
    val json =
      """
        |{
        |  "name": "streaming-pipeline",
        |  "mode": "streaming",
        |  "steps": [
        |    {
        |      "type": "extract",
        |      "method": "fromKafka",
        |      "config": {"topic": "events"}
        |    }
        |  ]
        |}
        |""".stripMargin

    val config = PipelineConfigParser.parse(json)

    config.mode shouldBe "streaming"
  }

  test("PipelineConfigParser should parse credential references") {
    val json =
      """
        |{
        |  "name": "secure-pipeline",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "extract",
        |      "method": "fromPostgres",
        |      "config": {
        |        "table": "users",
        |        "credentialPath": "secret/data/postgres"
        |      }
        |    }
        |  ]
        |}
        |""".stripMargin

    val config = PipelineConfigParser.parse(json)

    val extractStep = config.steps.head
    extractStep.config should contain key "credentialPath"
    extractStep.config("credentialPath") shouldBe "secret/data/postgres"
  }

  test("PipelineConfigParser should validate step types") {
    val json =
      """
        |{
        |  "name": "invalid-step-type",
        |  "mode": "batch",
        |  "steps": [
        |    {
        |      "type": "invalid",
        |      "method": "someMethod",
        |      "config": {}
        |    }
        |  ]
        |}
        |""".stripMargin

    an[IllegalArgumentException] should be thrownBy {
      val config = PipelineConfigParser.parse(json)
      PipelineConfigParser.validate(config)
    }
  }
}
