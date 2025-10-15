package com.pipeline.metrics

import org.slf4j.{Logger, LoggerFactory}

/**
 * Exports pipeline metrics in Prometheus text exposition format.
 *
 * Produces metrics compatible with Prometheus scraping.
 * Supports gauge and counter metric types.
 */
object PrometheusExporter {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Exports metrics in Prometheus text format.
   *
   * @param metrics The pipeline metrics to export
   * @return Prometheus-formatted metrics string
   */
  def export(metrics: PipelineMetrics): String = {
    val lines = scala.collection.mutable.ListBuffer[String]()

    // Pipeline-level metrics
    val safePipelineName = sanitizeLabelValue(metrics.pipelineName)
    val safeMode = sanitizeLabelValue(metrics.mode)
    val safeStatus = sanitizeLabelValue(metrics.status)

    // Duration gauge
    lines += s"""# HELP pipeline_duration_ms Pipeline execution duration in milliseconds"""
    lines += s"""# TYPE pipeline_duration_ms gauge"""
    lines += s"""pipeline_duration_ms{pipeline="$safePipelineName",mode="$safeMode",status="$safeStatus"} ${metrics.durationMs}"""
    lines += ""

    // Records read counter
    lines += s"""# HELP pipeline_records_read_total Total records read by pipeline"""
    lines += s"""# TYPE pipeline_records_read_total counter"""
    lines += s"""pipeline_records_read_total{pipeline="$safePipelineName",mode="$safeMode"} ${metrics.totalRecordsRead}"""
    lines += ""

    // Records written counter
    lines += s"""# HELP pipeline_records_written_total Total records written by pipeline"""
    lines += s"""# TYPE pipeline_records_written_total counter"""
    lines += s"""pipeline_records_written_total{pipeline="$safePipelineName",mode="$safeMode"} ${metrics.totalRecordsWritten}"""
    lines += ""

    // Bytes read counter
    lines += s"""# HELP pipeline_bytes_read_total Total bytes read by pipeline"""
    lines += s"""# TYPE pipeline_bytes_read_total counter"""
    lines += s"""pipeline_bytes_read_total{pipeline="$safePipelineName",mode="$safeMode"} ${metrics.totalBytesRead}"""
    lines += ""

    // Bytes written counter
    lines += s"""# HELP pipeline_bytes_written_total Total bytes written by pipeline"""
    lines += s"""# TYPE pipeline_bytes_written_total counter"""
    lines += s"""pipeline_bytes_written_total{pipeline="$safePipelineName",mode="$safeMode"} ${metrics.totalBytesWritten}"""
    lines += ""

    // Step-level metrics
    metrics.getStepMetrics.foreach { step =>
      val safeStepType = sanitizeLabelValue(step.stepType)
      val safeMethod = sanitizeLabelValue(step.method)
      val safeStepStatus = sanitizeLabelValue(step.status)

      // Step duration
      lines += s"""# HELP pipeline_step_duration_ms Pipeline step execution duration in milliseconds"""
      lines += s"""# TYPE pipeline_step_duration_ms gauge"""
      lines += s"""pipeline_step_duration_ms{pipeline="$safePipelineName",step_index="${step.stepIndex}",step_type="$safeStepType",method="$safeMethod",status="$safeStepStatus"} ${step.durationMs}"""
      lines += ""

      // Step records read
      lines += s"""# HELP pipeline_step_records_read_total Total records read by pipeline step"""
      lines += s"""# TYPE pipeline_step_records_read_total counter"""
      lines += s"""pipeline_step_records_read_total{pipeline="$safePipelineName",step_index="${step.stepIndex}",step_type="$safeStepType",method="$safeMethod"} ${step.recordsRead}"""
      lines += ""

      // Step records written
      lines += s"""# HELP pipeline_step_records_written_total Total records written by pipeline step"""
      lines += s"""# TYPE pipeline_step_records_written_total counter"""
      lines += s"""pipeline_step_records_written_total{pipeline="$safePipelineName",step_index="${step.stepIndex}",step_type="$safeStepType",method="$safeMethod"} ${step.recordsWritten}"""
      lines += ""
    }

    val result = lines.mkString("\n")
    logger.debug(s"Exported Prometheus metrics for pipeline: ${metrics.pipelineName}")
    result
  }

  /**
   * Sanitizes a label value for Prometheus format.
   * Replaces quotes and backslashes with escaped versions.
   *
   * @param value The label value to sanitize
   * @return Sanitized label value
   */
  private def sanitizeLabelValue(value: String): String = {
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
  }

  /**
   * Exports metrics and writes to a file.
   *
   * @param metrics    The pipeline metrics to export
   * @param outputPath Path to write the metrics file
   */
  def exportToFile(metrics: PipelineMetrics, outputPath: String): Unit = {
    try {
      val metricsText = export(metrics)
      val writer = new java.io.PrintWriter(new java.io.File(outputPath))
      try {
        writer.write(metricsText)
        logger.info(s"Exported Prometheus metrics to file: $outputPath")
      } finally {
        writer.close()
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Failed to export Prometheus metrics to file: $outputPath", ex)
        throw ex
    }
  }
}
