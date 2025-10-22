package com.pipeline.metrics

import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths, StandardOpenOption}
import scala.util.{Failure, Success, Try}

/**
 * Exports pipeline metrics to JSON files.
 *
 * Supports both single-file and append modes for metrics collection.
 */
object JsonFileExporter {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Exports metrics to a JSON file.
   *
   * @param metrics    The pipeline metrics to export
   * @param outputPath Path to write the JSON file
   * @param append     If true, appends to existing file; if false, overwrites
   */
  def export(
      metrics: PipelineMetrics,
      outputPath: String,
      append: Boolean = false,
  ): Unit =
    Try {
      val jsonString = metrics.toJson
      val file       = new File(outputPath)

      // Create parent directories if needed
      Option(file.getParentFile).foreach { parent =>
        if (!parent.exists()) {
          parent.mkdirs()
          logger.debug(s"Created parent directories: ${parent.getAbsolutePath}")
        }
      }

      if (append && file.exists()) {
        // Append mode: add as new line in JSONL format
        val path = Paths.get(outputPath)
        Files.write(
          path,
          (jsonString + "\n").getBytes("UTF-8"),
          StandardOpenOption.APPEND,
        )
        logger.info(s"Appended metrics to JSON file: $outputPath")
      } else {
        // Overwrite mode: write pretty-printed JSON
        val writer = new PrintWriter(file)
        try {
          writer.write(jsonString)
          writer.write("\n")
          logger.info(s"Exported metrics to JSON file: $outputPath")
        } finally writer.close()
      }
    } match {
      case Success(_)  =>
        logger.debug(s"Successfully exported metrics for pipeline: ${metrics.pipelineName}")
      case Failure(ex) =>
        logger.error(s"Failed to export metrics to JSON file: $outputPath", ex)
        throw ex
    }

  /**
   * Exports metrics to a timestamped JSON file.
   *
   * Creates files with names like: metrics-2025-10-15-143025-123.json
   *
   * @param metrics     The pipeline metrics to export
   * @param baseDir     Base directory for metrics files
   * @param pipelineName Pipeline name (used in filename)
   * @return The path to the created file
   */
  def exportWithTimestamp(
      metrics: PipelineMetrics,
      baseDir: String,
      pipelineName: String,
  ): String = {
    val timestamp     = new java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss-SSS")
      .format(new java.util.Date(metrics.startTime))
    val sanitizedName = pipelineName.replaceAll("[^a-zA-Z0-9-_]", "_")
    val filename      = s"metrics-$sanitizedName-$timestamp.json"
    val outputPath    = s"$baseDir/$filename"

    export(metrics, outputPath, append = false)
    outputPath
  }

  /**
   * Exports metrics to a JSON Lines (JSONL) file.
   *
   * Each pipeline execution appends a single line to the file.
   * Useful for collecting metrics from multiple runs.
   *
   * @param metrics    The pipeline metrics to export
   * @param outputPath Path to the JSONL file
   */
  def exportToJsonLines(metrics: PipelineMetrics, outputPath: String): Unit =
    export(metrics, outputPath, append = true)

  /**
   * Reads all metrics from a JSON Lines file.
   *
   * @param inputPath Path to the JSONL file
   * @return List of metrics maps
   */
  def readJsonLines(inputPath: String): List[Map[String, Any]] =
    Try {
      import org.json4s._
      import org.json4s.jackson.JsonMethods._
      implicit val formats: Formats = DefaultFormats

      val source = scala.io.Source.fromFile(inputPath)
      try source
        .getLines()
        .filter(_.trim.nonEmpty)
        .map { line =>
          parse(line).extract[Map[String, Any]]
        }
        .toList
      finally source.close()
    } match {
      case Success(metrics) =>
        logger.info(s"Read ${metrics.size} metrics from JSONL file: $inputPath")
        metrics
      case Failure(ex)      =>
        logger.error(s"Failed to read metrics from JSONL file: $inputPath", ex)
        throw ex
    }
}
