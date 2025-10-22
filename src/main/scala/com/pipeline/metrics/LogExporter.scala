package com.pipeline.metrics

import org.slf4j.{Logger, LoggerFactory}

/**
 * Exports pipeline metrics via structured logging.
 *
 * Logs metrics at INFO level for successful pipelines and WARN level for failures.
 * Includes step-by-step breakdown for detailed monitoring.
 */
object LogExporter {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Exports metrics via structured logging.
   *
   * @param metrics The pipeline metrics to export
   */
  def export(metrics: PipelineMetrics): Unit = {
    val logLevel = metrics.status match {
      case "COMPLETED" => "INFO"
      case "FAILED"    => "WARN"
      case "CANCELLED" => "WARN"
      case _           => "INFO"
    }

    // Log pipeline summary
    val summaryMsg = formatPipelineSummary(metrics)
    logLevel match {
      case "INFO" => logger.info(summaryMsg)
      case "WARN" => logger.warn(summaryMsg)
      case _      => logger.info(summaryMsg)
    }

    // Log step-by-step details
    if (metrics.getStepMetrics.nonEmpty) {
      logger.info(s"Step-by-step metrics for pipeline: ${metrics.pipelineName}")
      metrics.getStepMetrics.foreach { step =>
        val stepMsg = formatStepMetrics(step)
        logger.info(stepMsg)
      }
    }

    // Log error details if failed
    metrics.errorMessage.foreach { error =>
      logger.error(s"Pipeline error: ${metrics.pipelineName} - $error")
    }
  }

  /**
   * Exports metrics with MDC context for structured logging frameworks.
   *
   * Sets MDC keys that can be used by logging frameworks like Logback or Log4j2.
   *
   * @param metrics The pipeline metrics to export
   */
  def exportWithMDC(metrics: PipelineMetrics): Unit =
    try {
      // Set MDC context
      org.slf4j.MDC.put("pipelineName", metrics.pipelineName)
      org.slf4j.MDC.put("pipelineMode", metrics.mode)
      org.slf4j.MDC.put("pipelineStatus", metrics.status)
      org.slf4j.MDC.put("pipelineDurationMs", metrics.durationMs.toString)
      org.slf4j.MDC.put("totalRecordsRead", metrics.totalRecordsRead.toString)
      org.slf4j.MDC.put("totalRecordsWritten", metrics.totalRecordsWritten.toString)
      org.slf4j.MDC.put("totalBytesRead", metrics.totalBytesRead.toString)
      org.slf4j.MDC.put("totalBytesWritten", metrics.totalBytesWritten.toString)

      // Log with context
      export(metrics)
    } finally {
      // Clear MDC
      org.slf4j.MDC.remove("pipelineName")
      org.slf4j.MDC.remove("pipelineMode")
      org.slf4j.MDC.remove("pipelineStatus")
      org.slf4j.MDC.remove("pipelineDurationMs")
      org.slf4j.MDC.remove("totalRecordsRead")
      org.slf4j.MDC.remove("totalRecordsWritten")
      org.slf4j.MDC.remove("totalBytesRead")
      org.slf4j.MDC.remove("totalBytesWritten")
    }

  /**
   * Formats pipeline summary for logging.
   */
  private def formatPipelineSummary(metrics: PipelineMetrics): String = {
    val throughput = if (metrics.durationMs > 0) {
      val recordsPerSec = (metrics.totalRecordsRead * 1000.0) / metrics.durationMs
      f"$recordsPerSec%.2f records/sec"
    } else {
      "N/A"
    }

    s"""Pipeline Execution Summary:
       |  Pipeline: ${metrics.pipelineName}
       |  Mode: ${metrics.mode}
       |  Status: ${metrics.status}
       |  Duration: ${formatDuration(metrics.durationMs)}
       |  Records: ${metrics.totalRecordsRead} read, ${metrics.totalRecordsWritten} written
       |  Bytes: ${formatBytes(metrics.totalBytesRead)} read, ${formatBytes(metrics.totalBytesWritten)} written
       |  Throughput: $throughput
       |  Steps: ${metrics.getStepMetrics.size}""".stripMargin
  }

  /**
   * Formats step metrics for logging.
   */
  private def formatStepMetrics(step: StepMetrics): String = {
    val errorMsg = step.errorMessage.map(err => s" [ERROR: $err]").getOrElse("")

    s"""  Step ${step.stepIndex}: ${step.stepType}.${step.method}
       |    Status: ${step.status}$errorMsg
       |    Duration: ${formatDuration(step.durationMs)}
       |    Records: ${step.recordsRead} read, ${step.recordsWritten} written
       |    Bytes: ${formatBytes(step.bytesRead)} read, ${formatBytes(step.bytesWritten)} written""".stripMargin
  }

  /**
   * Formats duration in human-readable format.
   */
  private def formatDuration(ms: Long): String =
    if (ms < 1000) {
      s"${ms}ms"
    } else if (ms < 60000) {
      f"${ms / 1000.0}%.2fs"
    } else {
      val minutes = ms / 60000
      val seconds = (ms % 60000) / 1000.0
      f"${minutes}m $seconds%.2fs"
    }

  /**
   * Formats bytes in human-readable format.
   */
  private def formatBytes(bytes: Long): String =
    if (bytes < 1024) {
      s"${bytes}B"
    } else if (bytes < 1024 * 1024) {
      f"${bytes / 1024.0}%.2fKB"
    } else if (bytes < 1024 * 1024 * 1024) {
      f"${bytes / (1024.0 * 1024.0)}%.2fMB"
    } else {
      f"${bytes / (1024.0 * 1024.0 * 1024.0)}%.2fGB"
    }

  /**
   * Exports metrics in JSON format to logs.
   *
   * Useful for log aggregation systems that parse JSON logs.
   *
   * @param metrics The pipeline metrics to export
   */
  def exportAsJson(metrics: PipelineMetrics): Unit = {
    val jsonString = metrics.toJson
    logger.info(s"Pipeline metrics (JSON): $jsonString")
  }
}
