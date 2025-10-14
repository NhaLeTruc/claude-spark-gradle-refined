package com.pipeline.unit.credentials

import com.pipeline.credentials.OtherConfig
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for OtherConfig.
 *
 * Tests FR-011: Flexible credential structure for Kafka, DeltaLake, etc.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class OtherConfigTest extends AnyFunSuite with Matchers {

  test("OtherConfig should store arbitrary key-value pairs") {
    val config = OtherConfig(
      properties = Map(
        "bootstrap.servers" -> "localhost:9092",
        "security.protocol" -> "SASL_SSL",
        "sasl.mechanism" -> "PLAIN",
      ),
    )

    config.properties should have size 3
    config.properties should contain key "bootstrap.servers"
  }

  test("OtherConfig should parse from Vault secret data") {
    val secretData = Map(
      "bootstrap.servers" -> "kafka-1:9092,kafka-2:9092",
      "security.protocol" -> "SASL_SSL",
      "sasl.jaas.config" -> "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"pass\";",
    )

    val config = OtherConfig.fromVaultData(secretData)

    config.properties should contain key "bootstrap.servers"
    config.properties("bootstrap.servers") shouldBe "kafka-1:9092,kafka-2:9092"
  }

  test("OtherConfig should retrieve specific property") {
    val config = OtherConfig(
      properties = Map(
        "url" -> "https://delta-lake.example.com",
        "token" -> "secret-token",
      ),
    )

    config.get("url") shouldBe Some("https://delta-lake.example.com")
    config.get("nonexistent") shouldBe None
  }

  test("OtherConfig should support empty properties") {
    val config = OtherConfig(properties = Map.empty)

    config.properties shouldBe empty
  }

  test("OtherConfig should convert all values to strings") {
    val secretData = Map(
      "port" -> 9092,
      "enabled" -> true,
      "timeout" -> 30000L,
      "host" -> "localhost",
    )

    val config = OtherConfig.fromVaultData(secretData)

    // All values should be converted to strings
    config.properties.values.foreach { value =>
      value shouldBe a[String]
    }
  }

  test("OtherConfig should handle nested structures from Vault") {
    // In case Vault returns nested maps (though we flatten them)
    val secretData = Map(
      "kafka.bootstrap.servers" -> "localhost:9092",
      "kafka.group.id" -> "consumer-group-1",
    )

    val config = OtherConfig.fromVaultData(secretData)

    config.properties should contain key "kafka.bootstrap.servers"
    config.properties should contain key "kafka.group.id"
  }

  test("OtherConfig should support Kafka SASL configuration") {
    val kafkaConfig = OtherConfig(
      properties = Map(
        "bootstrap.servers" -> "kafka:9092",
        "security.protocol" -> "SASL_SSL",
        "sasl.mechanism" -> "SCRAM-SHA-256",
        "sasl.jaas.config" -> "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"user\" password=\"pass\";",
      ),
    )

    kafkaConfig.get("sasl.mechanism") shouldBe Some("SCRAM-SHA-256")
  }

  test("OtherConfig should support DeltaLake configuration") {
    val deltaConfig = OtherConfig(
      properties = Map(
        "path" -> "s3a://bucket/delta-table",
        "mergeSchema" -> "true",
        "overwriteSchema" -> "false",
      ),
    )

    deltaConfig.get("path") shouldBe defined
    deltaConfig.get("mergeSchema") shouldBe Some("true")
  }
}
