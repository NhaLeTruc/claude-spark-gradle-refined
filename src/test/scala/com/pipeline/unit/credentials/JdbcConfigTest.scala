package com.pipeline.unit.credentials

import com.pipeline.credentials.{JdbcConfig, VaultClient}
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

import scala.util.Success

/**
 * Unit tests for JdbcConfig.
 *
 * Tests FR-011: Secure JDBC credential retrieval from Vault.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class JdbcConfigTest extends AnyFunSuite with Matchers {

  test("JdbcConfig should require host, port, database, username, password") {
    val requiredKeys = List("host", "port", "database", "username", "password")

    requiredKeys.foreach { key =>
      assert(JdbcConfig.RequiredKeys.contains(key), s"$key should be required")
    }
  }

  test("JdbcConfig should construct JDBC URL for PostgreSQL") {
    val config = JdbcConfig(
      host = "localhost",
      port = 5432,
      database = "testdb",
      username = "user",
      password = "pass",
      credentialType = "postgres",
    )

    config.jdbcUrl should include("jdbc:postgresql://")
    config.jdbcUrl should include("localhost:5432")
    config.jdbcUrl should include("testdb")
  }

  test("JdbcConfig should construct JDBC URL for MySQL") {
    val config = JdbcConfig(
      host = "localhost",
      port = 3306,
      database = "testdb",
      username = "user",
      password = "pass",
      credentialType = "mysql",
    )

    config.jdbcUrl should include("jdbc:mysql://")
    config.jdbcUrl should include("localhost:3306")
    config.jdbcUrl should include("testdb")
  }

  test("JdbcConfig should validate required fields") {
    an[IllegalArgumentException] should be thrownBy {
      JdbcConfig(
        host = "",
        port = 5432,
        database = "testdb",
        username = "user",
        password = "pass",
        credentialType = "postgres",
      )
    }
  }

  test("JdbcConfig should parse from Vault secret data") {
    val secretData = Map(
      "host"     -> "db.example.com",
      "port"     -> "5432",
      "database" -> "production",
      "username" -> "dbuser",
      "password" -> "securepass",
    )

    val config = JdbcConfig.fromVaultData(secretData, "postgres")

    config.host shouldBe "db.example.com"
    config.port shouldBe 5432
    config.database shouldBe "production"
    config.username shouldBe "dbuser"
    config.password shouldBe "securepass"
  }

  test("JdbcConfig should fail if required keys are missing") {
    val incompleteData = Map(
      "host" -> "localhost",
      "port" -> "5432",
      // missing database, username, password
    )

    an[IllegalArgumentException] should be thrownBy {
      JdbcConfig.fromVaultData(incompleteData, "postgres")
    }
  }

  test("JdbcConfig should handle integer port from string") {
    val secretData = Map(
      "host"     -> "localhost",
      "port"     -> "5432", // String, not Int
      "database" -> "testdb",
      "username" -> "user",
      "password" -> "pass",
    )

    val config = JdbcConfig.fromVaultData(secretData, "postgres")

    config.port shouldBe 5432
  }

  test("JdbcConfig should support connection properties") {
    val config = JdbcConfig(
      host = "localhost",
      port = 5432,
      database = "testdb",
      username = "user",
      password = "pass",
      credentialType = "postgres",
      properties = Map("ssl" -> "true", "sslmode" -> "require"),
    )

    config.properties should contain key "ssl"
    config.properties("ssl") shouldBe "true"
  }
}
