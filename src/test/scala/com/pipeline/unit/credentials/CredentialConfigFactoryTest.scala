package com.pipeline.unit.credentials

import com.pipeline.credentials.{CredentialConfig, CredentialConfigFactory, IAMConfig, JdbcConfig, OtherConfig}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for CredentialConfigFactory.
 *
 * Tests FR-011: Factory pattern for credential type resolution.
 * Validates Constitution Section I: SOLID principles (Factory Pattern).
 */
@RunWith(classOf[JUnitRunner])
class CredentialConfigFactoryTest extends AnyFunSuite with Matchers {

  test("Factory should create JdbcConfig for postgres type") {
    val secretData = Map(
      "host"     -> "localhost",
      "port"     -> "5432",
      "database" -> "testdb",
      "username" -> "user",
      "password" -> "pass",
    )

    val config = CredentialConfigFactory.create("postgres", secretData)

    config shouldBe a[JdbcConfig]
    config.asInstanceOf[JdbcConfig].credentialType shouldBe "postgres"
  }

  test("Factory should create JdbcConfig for mysql type") {
    val secretData = Map(
      "host"     -> "localhost",
      "port"     -> "3306",
      "database" -> "testdb",
      "username" -> "user",
      "password" -> "pass",
    )

    val config = CredentialConfigFactory.create("mysql", secretData)

    config shouldBe a[JdbcConfig]
    config.asInstanceOf[JdbcConfig].credentialType shouldBe "mysql"
  }

  test("Factory should create IAMConfig for s3 type") {
    val secretData = Map(
      "accessKeyId"     -> "AKIAIOSFODNN7EXAMPLE",
      "secretAccessKey" -> "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      "region"          -> "us-west-2",
    )

    val config = CredentialConfigFactory.create("s3", secretData)

    config shouldBe an[IAMConfig]
  }

  test("Factory should create OtherConfig for kafka type") {
    val secretData = Map(
      "bootstrap.servers" -> "localhost:9092",
      "security.protocol" -> "SASL_SSL",
    )

    val config = CredentialConfigFactory.create("kafka", secretData)

    config shouldBe an[OtherConfig]
  }

  test("Factory should create OtherConfig for deltalake type") {
    val secretData = Map(
      "path"        -> "s3a://bucket/delta",
      "mergeSchema" -> "true",
    )

    val config = CredentialConfigFactory.create("deltalake", secretData)

    config shouldBe an[OtherConfig]
  }

  test("Factory should throw exception for unknown type") {
    val secretData = Map("key" -> "value")

    an[IllegalArgumentException] should be thrownBy {
      CredentialConfigFactory.create("unknown", secretData)
    }
  }

  test("Factory should be case-insensitive for types") {
    val secretData = Map(
      "host"     -> "localhost",
      "port"     -> "5432",
      "database" -> "testdb",
      "username" -> "user",
      "password" -> "pass",
    )

    val config1 = CredentialConfigFactory.create("POSTGRES", secretData)
    val config2 = CredentialConfigFactory.create("postgres", secretData)
    val config3 = CredentialConfigFactory.create("PostgreSQL", secretData)

    config1 shouldBe a[JdbcConfig]
    config2 shouldBe a[JdbcConfig]
    config3 shouldBe a[JdbcConfig]
  }

  test("Factory should list supported types") {
    val supportedTypes = CredentialConfigFactory.supportedTypes

    supportedTypes should contain allOf ("postgres", "mysql", "s3", "kafka", "deltalake")
  }
}
