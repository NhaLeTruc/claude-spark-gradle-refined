package com.pipeline.unit.retry

import com.pipeline.retry.RetryStrategy
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

import scala.util.{Failure, Success, Try}

/**
 * Unit tests for RetryStrategy.
 *
 * Tests FR-016: Retry up to 3 times with 5-second delays.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
@RunWith(classOf[JUnitRunner])
class RetryStrategyTest extends AnyFunSuite with Matchers {

  test("RetryStrategy should succeed on first attempt if operation succeeds") {
    var attempts = 0
    val operation = () => {
      attempts += 1
      Success("success")
    }

    val result = RetryStrategy.executeWithRetry(operation, maxAttempts = 3, delayMillis = 100)

    result shouldBe a[Success[_]]
    result.get shouldBe "success"
    attempts shouldBe 1
  }

  test("RetryStrategy should retry up to maxAttempts times on failure") {
    var attempts = 0
    val operation = () => {
      attempts += 1
      Failure(new RuntimeException(s"Attempt $attempts failed"))
    }

    val result = RetryStrategy.executeWithRetry(operation, maxAttempts = 3, delayMillis = 100)

    result shouldBe a[Failure[_]]
    attempts shouldBe 3
  }

  test("RetryStrategy should succeed on second attempt after initial failure") {
    var attempts = 0
    val operation = () => {
      attempts += 1
      if (attempts == 1) {
        Failure(new RuntimeException("First attempt failed"))
      } else {
        Success("success on retry")
      }
    }

    val result = RetryStrategy.executeWithRetry(operation, maxAttempts = 3, delayMillis = 100)

    result shouldBe a[Success[_]]
    result.get shouldBe "success on retry"
    attempts shouldBe 2
  }

  test("RetryStrategy should wait delayMillis between retries") {
    var attempts = 0
    val startTime = System.currentTimeMillis()
    val operation = () => {
      attempts += 1
      if (attempts < 3) {
        Failure(new RuntimeException("Not yet"))
      } else {
        Success("finally")
      }
    }

    val result = RetryStrategy.executeWithRetry(operation, maxAttempts = 3, delayMillis = 500)
    val elapsedTime = System.currentTimeMillis() - startTime

    result shouldBe a[Success[_]]
    attempts shouldBe 3
    // Should have waited approximately 1000ms (2 delays of 500ms)
    elapsedTime should be >= 1000L
    elapsedTime should be < 1500L
  }

  test("RetryStrategy should use tail recursion to avoid stack overflow") {
    var attempts = 0
    val operation = () => {
      attempts += 1
      if (attempts < 100) {
        Failure(new RuntimeException("Keep trying"))
      } else {
        Success("done")
      }
    }

    // This should not cause StackOverflowError with tail recursion
    val result = RetryStrategy.executeWithRetry(operation, maxAttempts = 100, delayMillis = 0)

    result shouldBe a[Success[_]]
    attempts shouldBe 100
  }

  test("RetryStrategy should preserve exception details on final failure") {
    val operation = () => {
      Failure(new IllegalArgumentException("Invalid argument"))
    }

    val result = RetryStrategy.executeWithRetry(operation, maxAttempts = 2, delayMillis = 50)

    result shouldBe a[Failure[_]]
    result.failed.get shouldBe an[IllegalArgumentException]
    result.failed.get.getMessage shouldBe "Invalid argument"
  }
}
