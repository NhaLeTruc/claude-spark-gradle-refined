package com.pipeline.unit.credentials

import com.pipeline.credentials.IAMConfig
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for IAMConfig.
 *
 * Tests FR-011: Secure AWS IAM credential retrieval from Vault.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class IAMConfigTest extends AnyFunSuite with Matchers {

  test("IAMConfig should require accessKeyId and secretAccessKey") {
    val requiredKeys = List("accessKeyId", "secretAccessKey")

    requiredKeys.foreach { key =>
      assert(IAMConfig.RequiredKeys.contains(key), s"$key should be required")
    }
  }

  test("IAMConfig should support optional sessionToken") {
    val config = IAMConfig(
      accessKeyId = "AKIAIOSFODNN7EXAMPLE",
      secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      sessionToken = Some("temporary-session-token"),
      region = "us-west-2",
    )

    config.sessionToken shouldBe defined
    config.sessionToken.get shouldBe "temporary-session-token"
  }

  test("IAMConfig should have optional region") {
    val config = IAMConfig(
      accessKeyId = "AKIAIOSFODNN7EXAMPLE",
      secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      sessionToken = None,
      region = "eu-west-1",
    )

    config.region shouldBe "eu-west-1"
  }

  test("IAMConfig should parse from Vault secret data without sessionToken") {
    val secretData = Map(
      "accessKeyId" -> "AKIAIOSFODNN7EXAMPLE",
      "secretAccessKey" -> "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      "region" -> "us-east-1",
    )

    val config = IAMConfig.fromVaultData(secretData)

    config.accessKeyId shouldBe "AKIAIOSFODNN7EXAMPLE"
    config.secretAccessKey shouldBe "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    config.sessionToken shouldBe None
    config.region shouldBe "us-east-1"
  }

  test("IAMConfig should parse from Vault secret data with sessionToken") {
    val secretData = Map(
      "accessKeyId" -> "ASIAIOSFODNN7EXAMPLE",
      "secretAccessKey" -> "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      "sessionToken" -> "FwoGZXIvYXdzEBYaD...",
      "region" -> "ap-south-1",
    )

    val config = IAMConfig.fromVaultData(secretData)

    config.sessionToken shouldBe defined
    config.sessionToken.get should startWith("FwoGZ")
  }

  test("IAMConfig should fail if required keys are missing") {
    val incompleteData = Map(
      "accessKeyId" -> "AKIAIOSFODNN7EXAMPLE",
      // missing secretAccessKey
    )

    an[IllegalArgumentException] should be thrownBy {
      IAMConfig.fromVaultData(incompleteData)
    }
  }

  test("IAMConfig should validate accessKeyId format") {
    an[IllegalArgumentException] should be thrownBy {
      IAMConfig(
        accessKeyId = "", // empty
        secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
        sessionToken = None,
        region = "us-west-2",
      )
    }
  }

  test("IAMConfig should validate secretAccessKey format") {
    an[IllegalArgumentException] should be thrownBy {
      IAMConfig(
        accessKeyId = "AKIAIOSFODNN7EXAMPLE",
        secretAccessKey = "", // empty
        sessionToken = None,
        region = "us-west-2",
      )
    }
  }

  test("IAMConfig should default region to us-east-1 if not provided") {
    val secretData = Map(
      "accessKeyId" -> "AKIAIOSFODNN7EXAMPLE",
      "secretAccessKey" -> "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
    )

    val config = IAMConfig.fromVaultData(secretData)

    config.region shouldBe "us-east-1"
  }
}
