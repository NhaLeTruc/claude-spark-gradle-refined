package com.pipeline.unit.exceptions

import com.pipeline.exceptions._
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

/**
 * Unit tests for pipeline exception classes.
 *
 * Tests exception context and sanitization features.
 */
@RunWith(classOf[JUnitRunner])
class PipelineExceptionTest extends AnyFunSuite with Matchers {

  test("PipelineException should format message with full context") {
    val ex: PipelineException = new PipelineException(
      message = "Test error occurred",
      cause = null,
      pipelineName = Some("test-pipeline"),
      stepIndex = Some(2),
      stepType = Some("transform"),
      method = Some("filterRows"),
      config = Some(Map("condition" -> "age > 18")),
    )

    val message = ex.getMessage
    message should include("Pipeline: test-pipeline")
    message should include("Step: 2")
    message should include("Type: transform")
    message should include("Method: filterRows")
    message should include("Test error occurred")
  }

  test("PipelineException should format message with partial context") {
    val ex: PipelineException = new PipelineException(
      message = "Partial context error",
      pipelineName = Some("my-pipeline"),
      stepIndex = Some(0),
    )

    val message = ex.getMessage
    message should include("Pipeline: my-pipeline")
    message should include("Step: 0")
    message should not include "Type:"
    message should not include "Method:"
  }

  test("PipelineException should handle no context") {
    val ex = new PipelineException("Simple error")

    val message = ex.getMessage
    message shouldBe "Simple error"
  }

  test("PipelineException should sanitize password in config") {
    val ex = new PipelineException(
      message = "Error with credentials",
      config = Some(
        Map(
          "username" -> "user",
          "password" -> "secret123",
          "host"     -> "localhost",
        ),
      ),
    )

    val sanitized = ex.getSanitizedConfig
    sanitized shouldBe defined
    sanitized.get("password") shouldBe "***REDACTED***"
    sanitized.get("username") shouldBe "user"
    sanitized.get("host") shouldBe "localhost"
  }

  test("PipelineException should sanitize secret in config") {
    val ex = new PipelineException(
      message = "Error",
      config = Some(
        Map(
          "api_secret" -> "abc123",
          "url"        -> "http://example.com",
        ),
      ),
    )

    val sanitized = ex.getSanitizedConfig
    sanitized.get("api_secret") shouldBe "***REDACTED***"
    sanitized.get("url") shouldBe "http://example.com"
  }

  test("PipelineException should sanitize token in config") {
    val ex = new PipelineException(
      message = "Error",
      config = Some(
        Map(
          "access_token" -> "token123",
          "endpoint"     -> "/api",
        ),
      ),
    )

    val sanitized = ex.getSanitizedConfig
    sanitized.get("access_token") shouldBe "***REDACTED***"
    sanitized.get("endpoint") shouldBe "/api"
  }

  test("PipelineException should sanitize key but not keyspace") {
    val ex = new PipelineException(
      message = "Error",
      config = Some(
        Map(
          "api_key"    -> "key123",
          "keyspace"   -> "production", // Should NOT be redacted
          "access_key" -> "access123",
        ),
      ),
    )

    val sanitized = ex.getSanitizedConfig
    sanitized.get("api_key") shouldBe "***REDACTED***"
    sanitized.get("keyspace") shouldBe "production"
    sanitized.get("access_key") shouldBe "***REDACTED***"
  }

  test("PipelineException should handle empty config") {
    val ex = new PipelineException(
      message = "Error",
      config = Some(Map.empty),
    )

    val sanitized = ex.getSanitizedConfig
    sanitized shouldBe defined
    sanitized.get shouldBe empty
  }

  test("PipelineException should handle None config") {
    val ex = new PipelineException(
      message = "Error",
      config = None,
    )

    val sanitized = ex.getSanitizedConfig
    sanitized shouldBe None
  }

  test("PipelineExecutionException should extend PipelineException") {
    val ex = new PipelineExecutionException(
      message = "Execution failed",
      pipelineName = Some("test"),
    )

    ex shouldBe a[PipelineException]
    ex.getMessage should include("Execution failed")
  }

  test("PipelineExecutionException should propagate cause") {
    val cause = new RuntimeException("Original error")
    val ex    = new PipelineExecutionException(
      message = "Wrapped error",
      cause = cause,
    )

    ex.getCause shouldBe cause
  }

  test("DataFrameResolutionException should include reference name") {
    val ex = new DataFrameResolutionException(
      referenceName = "missing_df",
      availableNames = Set("df1", "df2", "df3"),
      pipelineName = Some("test-pipeline"),
      stepIndex = Some(1),
    )

    ex.referenceName shouldBe "missing_df"
    ex.availableNames shouldBe Set("df1", "df2", "df3")
    val message = ex.getMessage
    message should include("missing_df")
    message should include("df1")
    message should include("df2")
    message should include("df3")
  }

  test("DataFrameResolutionException should show empty available names") {
    val ex = new DataFrameResolutionException(
      referenceName = "my_df",
      availableNames = Set.empty,
    )

    val message = ex.getMessage
    message should include("my_df")
    message should include("Available:")
  }

  test("RetryableException should be recognizable") {
    val ex = new RetryableException(
      message = "Temporary failure",
      cause = new java.net.ConnectException("Connection refused"),
    )

    ex shouldBe a[PipelineException]
    PipelineException.isRetryable(ex) shouldBe true
  }

  test("PipelineException.isRetryable should detect network exceptions") {
    val networkEx = new java.net.SocketTimeoutException("Timeout")

    PipelineException.isRetryable(networkEx) shouldBe true
  }

  test("PipelineException.isRetryable should detect connection refused IO exceptions") {
    val ioEx = new java.io.IOException("Connection refused by server")

    PipelineException.isRetryable(ioEx) shouldBe true
  }

  test("PipelineException.isRetryable should not retry generic IO exceptions") {
    val ioEx = new java.io.IOException("Generic IO error")

    PipelineException.isRetryable(ioEx) shouldBe false
  }

  test("PipelineException.isRetryable should not retry illegal arguments") {
    val argEx = new IllegalArgumentException("Bad argument")

    PipelineException.isRetryable(argEx) shouldBe false
  }

  test("PipelineException.wrapException should create RetryableException for retryable errors") {
    val cause   = new java.net.ConnectException("Connection refused")
    val wrapped = PipelineException.wrapException(
      cause,
      pipelineName = Some("test"),
      stepIndex = Some(0),
    )

    wrapped shouldBe a[RetryableException]
    wrapped.getCause shouldBe cause
  }

  test("PipelineException.wrapException should create PipelineExecutionException for non-retryable errors") {
    val cause   = new IllegalArgumentException("Bad config")
    val wrapped = PipelineException.wrapException(
      cause,
      pipelineName = Some("test"),
    )

    wrapped shouldBe a[PipelineExecutionException]
    wrapped should not be a[RetryableException]
  }

  test("ValidationException should include validation type") {
    val ex = new ValidationException(
      validationType = "schema",
      failureCount = 5,
      sampleRecords = Seq("record1", "record2"),
      validationRule = Some("Schema must match expected structure"),
      pipelineName = Some("validator-pipeline"),
    )

    ex.validationType shouldBe "schema"
    ex.failureCount shouldBe 5
    val message = ex.getMessage
    message should include("schema")
  }

  test("ValidationException should track failed record count") {
    val ex = new ValidationException(
      validationType = "null_check",
      failureCount = 100,
      sampleRecords = Seq.empty,
    )

    ex.failureCount shouldBe 100
  }
}
