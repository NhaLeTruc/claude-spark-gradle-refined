package com.pipeline.exceptions

import org.apache.spark.sql.DataFrame

/**
 * Base exception for all pipeline-related errors.
 *
 * Provides context about where the error occurred in the pipeline.
 *
 * @param message     Error message
 * @param cause       Original exception (if any)
 * @param pipelineName Name of the pipeline
 * @param stepIndex   Index of the step where error occurred (0-based)
 * @param stepType    Type of step (extract, transform, validate, load)
 * @param method      Method name that failed
 * @param config      Configuration that was being used
 */
class PipelineException(
    message: String,
    cause: Throwable = null,
    val pipelineName: Option[String] = None,
    val stepIndex: Option[Int] = None,
    val stepType: Option[String] = None,
    val method: Option[String] = None,
    val config: Option[Map[String, Any]] = None,
) extends Exception(message, cause) {

  /**
   * Generate a detailed error message with context.
   */
  override def getMessage: String = {
    val contextParts = Seq(
      pipelineName.map(name => s"Pipeline: $name"),
      stepIndex.map(idx => s"Step: $idx"),
      stepType.map(typ => s"Type: $typ"),
      method.map(m => s"Method: $m"),
    ).flatten

    val context = if (contextParts.nonEmpty) {
      s"[${contextParts.mkString(", ")}] "
    } else {
      ""
    }

    s"$context$message"
  }

  /**
   * Get sanitized config (removes credentials).
   */
  def getSanitizedConfig: Option[Map[String, Any]] = {
    config.map { cfg =>
      cfg.map {
        case (key, _) if key.toLowerCase.contains("password") => key -> "***REDACTED***"
        case (key, _) if key.toLowerCase.contains("secret") => key -> "***REDACTED***"
        case (key, _) if key.toLowerCase.contains("token") => key -> "***REDACTED***"
        case (key, _) if key.toLowerCase.contains("key") && !key.toLowerCase.contains("keyspace") => key -> "***REDACTED***"
        case (key, value) => key -> value
      }
    }
  }
}

/**
 * Exception thrown during pipeline execution.
 *
 * Wraps underlying exceptions that occur during step execution.
 */
class PipelineExecutionException(
    message: String,
    cause: Throwable = null,
    pipelineName: Option[String] = None,
    stepIndex: Option[Int] = None,
    stepType: Option[String] = None,
    method: Option[String] = None,
    config: Option[Map[String, Any]] = None,
) extends PipelineException(message, cause, pipelineName, stepIndex, stepType, method, config)

/**
 * Exception thrown when a DataFrame reference cannot be resolved.
 *
 * This occurs when a step references a DataFrame by name that doesn't exist in context.
 */
class DataFrameResolutionException(
    val referenceName: String,
    val availableNames: Set[String],
    pipelineName: Option[String] = None,
    stepIndex: Option[Int] = None,
    stepType: Option[String] = None,
    method: Option[String] = None,
) extends PipelineException(
      message = s"DataFrame '$referenceName' not found. Available: ${availableNames.mkString(", ")}",
      pipelineName = pipelineName,
      stepIndex = stepIndex,
      stepType = stepType,
      method = method,
    )

/**
 * Exception thrown when data validation fails.
 *
 * Includes samples of invalid data for debugging.
 *
 * @param validationType Type of validation that failed
 * @param failureCount   Number of rows that failed validation
 * @param sampleRecords  Sample of failed records (as JSON strings)
 * @param validationRule The validation rule that was violated
 */
class ValidationException(
    val validationType: String,
    val failureCount: Long,
    val sampleRecords: Seq[String],
    val validationRule: Option[String] = None,
    pipelineName: Option[String] = None,
    stepIndex: Option[Int] = None,
    method: Option[String] = None,
) extends PipelineException(
      message = PipelineException.buildValidationMessage(validationType, failureCount, sampleRecords, validationRule),
      pipelineName = pipelineName,
      stepIndex = stepIndex,
      stepType = Some("validate"),
      method = method,
    )

/**
 * Exception thrown when credentials cannot be loaded or are invalid.
 */
class CredentialException(
    message: String,
    cause: Throwable = null,
    val credentialPath: Option[String] = None,
    val credentialType: Option[String] = None,
    pipelineName: Option[String] = None,
    stepIndex: Option[Int] = None,
) extends PipelineException(
      message = PipelineException.buildCredentialMessage(message, credentialPath, credentialType),
      cause = cause,
      pipelineName = pipelineName,
      stepIndex = stepIndex,
    )

/**
 * Exception thrown when a streaming query fails.
 *
 * @param queryName Name of the streaming query
 * @param queryId   ID of the streaming query
 */
class StreamingQueryException(
    message: String,
    cause: Throwable = null,
    val queryName: Option[String] = None,
    val queryId: Option[String] = None,
    pipelineName: Option[String] = None,
) extends PipelineException(
      message = PipelineException.buildStreamingMessage(message, queryName, queryId),
      cause = cause,
      pipelineName = pipelineName,
    )

/**
 * Exception thrown when pipeline configuration is invalid.
 */
class ConfigurationException(
    message: String,
    cause: Throwable = null,
    val configPath: Option[String] = None,
    pipelineName: Option[String] = None,
) extends PipelineException(
      message = PipelineException.buildConfigurationMessage(message, configPath),
      cause = cause,
      pipelineName = pipelineName,
    )

/**
 * Exception thrown for transient failures that may be retried.
 *
 * Examples: network timeouts, temporary resource unavailability.
 */
class RetryableException(
    message: String,
    cause: Throwable = null,
    val attemptNumber: Int = 1,
    val maxAttempts: Int = 3,
    pipelineName: Option[String] = None,
    stepIndex: Option[Int] = None,
    stepType: Option[String] = None,
    method: Option[String] = None,
) extends PipelineException(
      message = s"$message (attempt $attemptNumber of $maxAttempts)",
      cause = cause,
      pipelineName = pipelineName,
      stepIndex = stepIndex,
      stepType = stepType,
      method = method,
    ) {

  /**
   * Check if we should retry.
   */
  def shouldRetry: Boolean = attemptNumber < maxAttempts

  /**
   * Create a new exception for the next retry attempt.
   */
  def nextAttempt: RetryableException = {
    new RetryableException(
      message = this.message.replaceAll(s"attempt $attemptNumber", s"attempt ${attemptNumber + 1}"),
      cause = this.cause,
      attemptNumber = attemptNumber + 1,
      maxAttempts = maxAttempts,
      pipelineName = pipelineName,
      stepIndex = stepIndex,
      stepType = stepType,
      method = method,
    )
  }
}

/**
 * Exception thrown when a pipeline is cancelled.
 *
 * @param pipelineName Name of the cancelled pipeline
 * @param cancelledAt  Step index where cancellation occurred
 */
class PipelineCancelledException(
    pipelineName: Option[String] = None,
    val cancelledAt: Option[Int] = None,
) extends PipelineException(
      message = "Pipeline execution cancelled",
      pipelineName = pipelineName,
      stepIndex = cancelledAt,
    )

/**
 * Companion object with helper methods for exception detection and message building.
 */
object PipelineException {

  // Helper functions for building error messages

  private[exceptions] def buildValidationMessage(
      validationType: String,
      failureCount: Long,
      sampleRecords: Seq[String],
      validationRule: Option[String],
  ): String = {
    val ruleText = validationRule.map(r => s" Rule: $r").getOrElse("")
    val samples = if (sampleRecords.nonEmpty) {
      s"\nSample failed records:\n${sampleRecords.take(5).mkString("\n")}"
    } else {
      ""
    }

    s"Validation failed: $validationType. Failed rows: $failureCount.$ruleText$samples"
  }

  private[exceptions] def buildCredentialMessage(
      message: String,
      credentialPath: Option[String],
      credentialType: Option[String],
  ): String = {
    val pathText = credentialPath.map(p => s" Path: $p").getOrElse("")
    val typeText = credentialType.map(t => s" Type: $t").getOrElse("")
    s"$message$pathText$typeText"
  }

  private[exceptions] def buildStreamingMessage(
      message: String,
      queryName: Option[String],
      queryId: Option[String],
  ): String = {
    val nameText = queryName.map(n => s" Query: $n").getOrElse("")
    val idText = queryId.map(i => s" (ID: $i)").getOrElse("")
    s"$message$nameText$idText"
  }

  private[exceptions] def buildConfigurationMessage(
      message: String,
      configPath: Option[String],
  ): String = {
    val pathText = configPath.map(p => s" Config: $p").getOrElse("")
    s"$message$pathText"
  }

  /**
   * Determine if an exception is retryable based on its type and message.
   */
  def isRetryable(ex: Throwable): Boolean = {
    ex match {
      case _: RetryableException => true
      case _: java.net.SocketTimeoutException => true
      case _: java.net.ConnectException => true
      case _: java.io.IOException if ex.getMessage != null && ex.getMessage.contains("Connection refused") => true
      case _: org.apache.spark.sql.AnalysisException if ex.getMessage != null && ex.getMessage.contains("timeout") => true
      case _ => false
    }
  }

  /**
   * Wrap an exception with pipeline context.
   */
  def wrapException(
      ex: Throwable,
      pipelineName: Option[String] = None,
      stepIndex: Option[Int] = None,
      stepType: Option[String] = None,
      method: Option[String] = None,
      config: Option[Map[String, Any]] = None,
  ): PipelineException = {
    ex match {
      // Already a pipeline exception - preserve it
      case pe: PipelineException => pe

      // Known Spark exceptions
      case ae: org.apache.spark.sql.AnalysisException =>
        new PipelineExecutionException(
          message = s"Spark analysis error: ${ae.getMessage}",
          cause = ae,
          pipelineName = pipelineName,
          stepIndex = stepIndex,
          stepType = stepType,
          method = method,
          config = config,
        )

      // Network/IO exceptions - make them retryable
      case ioe: java.io.IOException =>
        new RetryableException(
          message = s"I/O error: ${ioe.getMessage}",
          cause = ioe,
          pipelineName = pipelineName,
          stepIndex = stepIndex,
          stepType = stepType,
          method = method,
        )

      // Generic wrapper
      case _ =>
        new PipelineExecutionException(
          message = s"Execution error: ${ex.getMessage}",
          cause = ex,
          pipelineName = pipelineName,
          stepIndex = stepIndex,
          stepType = stepType,
          method = method,
          config = config,
        )
    }
  }
}
