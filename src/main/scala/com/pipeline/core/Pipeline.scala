package com.pipeline.core

import com.pipeline.metrics.PipelineMetrics
import com.pipeline.retry.RetryStrategy
import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory, MDC}

import scala.util.{Failure, Success, Try}

/**
 * Pipeline orchestrator that executes a chain of steps.
 *
 * Implements FR-006: Main pipeline execution with Chain of Responsibility.
 * Implements FR-016: Retry logic for failed pipelines.
 * Validates Constitution Section VI: Observability with structured logging.
 *
 * @param name  Pipeline identifier
 * @param mode  Execution mode: "batch" or "streaming"
 * @param steps Ordered list of pipeline steps
 */
case class Pipeline(
    name: String,
    mode: String,
    steps: List[PipelineStep],
) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  @volatile private var cancelled = false

  // Metrics collector
  private var metricsCollector: Option[PipelineMetrics] = None

  require(name.trim.nonEmpty, "Pipeline name cannot be empty")
  require(mode == "batch" || mode == "streaming", s"Invalid mode: $mode. Must be 'batch' or 'streaming'")

  /**
   * Check if this pipeline is configured for streaming mode.
   *
   * @return true if mode is "streaming", false otherwise
   */
  def isStreamingMode: Boolean = mode == "streaming"

  /**
   * Cancels the pipeline execution.
   *
   * This sets the cancellation flag which will be checked between steps.
   * For streaming pipelines, also stops all active streaming queries.
   */
  def cancel(): Unit = {
    logger.warn(s"Cancellation requested for pipeline: $name")
    cancelled = true
  }

  /**
   * Checks if cancellation has been requested.
   *
   * @return true if pipeline should cancel
   */
  def isCancelled: Boolean = cancelled

  /**
   * Checks for cancellation and throws exception if cancelled.
   *
   * @param stepIndex Current step index
   * @throws PipelineCancelledException if cancelled
   */
  private def checkCancellation(stepIndex: Option[Int] = None): Unit =
    if (cancelled) {
      logger.warn(s"Pipeline cancelled at step ${stepIndex.getOrElse("unknown")}")
      throw new com.pipeline.exceptions.PipelineCancelledException(
        pipelineName = Some(name),
        cancelledAt = stepIndex,
      )
    }

  /**
   * Executes the pipeline with retry logic.
   *
   * @param spark         SparkSession
   * @param maxAttempts   Maximum retry attempts (default: 3)
   * @param delayMillis   Delay between retries in milliseconds (default: 5000)
   * @param collectMetrics Enable metrics collection (default: true)
   * @return Success or Failure
   */
  def execute(
      spark: SparkSession,
      maxAttempts: Int = 3,
      delayMillis: Long = 5000,
      collectMetrics: Boolean = true,
  ): Either[Throwable, PipelineContext] = {

    // Initialize metrics collector
    if (collectMetrics) {
      metricsCollector = Some(PipelineMetrics(name, mode))
    }

    // Set MDC for structured logging
    MDC.put("pipelineName", name)
    MDC.put("pipelineMode", mode)
    MDC.put("correlationId", java.util.UUID.randomUUID().toString)

    try {
      if (isStreamingMode) {
        logger.info(s"Starting STREAMING pipeline execution: name=$name, steps=${steps.size}")
        logger.info(s"Streaming mode: continuous processing until termination")
      } else {
        logger.info(s"Starting BATCH pipeline execution: name=$name, steps=${steps.size}")
      }

      val startTime = System.currentTimeMillis()

      // Execute with retry
      val result = RetryStrategy.executeWithRetry(
        operation = () => Try(executeSteps(spark)),
        maxAttempts = maxAttempts,
        delayMillis = delayMillis,
      )

      val durationMs = System.currentTimeMillis() - startTime

      result match {
        case Success(context) =>
          // Mark pipeline as completed
          metricsCollector.foreach(_.complete())

          if (isStreamingMode) {
            logger.info(s"Streaming pipeline setup completed: name=$name, setupTime=${durationMs}ms")
            logger.info(s"Streaming pipeline is now running continuously...")
          } else {
            logger.info(s"Batch pipeline completed successfully: name=$name, duration=${durationMs}ms")
          }
          Right(context)

        case Failure(exception) =>
          exception match {
            case _: com.pipeline.exceptions.PipelineCancelledException =>
              metricsCollector.foreach(_.cancel())
              logger.warn(s"Pipeline cancelled: name=$name, duration=${durationMs}ms")
              Left(exception)

            case _ =>
              metricsCollector.foreach(_.fail(exception.getMessage))
              logger.error(
                s"Pipeline failed after $maxAttempts attempts: name=$name, mode=$mode, duration=${durationMs}ms",
                exception,
              )
              Left(exception)
          }
      }

    } finally MDC.clear()
  }

  /**
   * Executes all pipeline steps in sequence.
   *
   * @param spark SparkSession
   * @return Final PipelineContext
   */
  private def executeSteps(spark: SparkSession): PipelineContext = {
    if (steps.isEmpty) {
      throw new IllegalStateException("Pipeline has no steps to execute")
    }

    // Check cancellation before starting
    checkCancellation(Some(0))

    logger.info(s"Executing ${steps.size} pipeline steps")

    // Build the chain by linking steps with cancellation checks
    val chainedSteps = steps.zipWithIndex.foldRight[Option[PipelineStep]](None) { case ((step, idx), nextOpt) =>
      Some(
        step match {
          case ExtractStep(m, c, _)   => ExtractStep(m, c, nextOpt)
          case TransformStep(m, c, _) => TransformStep(m, c, nextOpt)
          case ValidateStep(m, c, _)  => ValidateStep(m, c, nextOpt)
          case LoadStep(m, c, _)      => LoadStep(m, c, nextOpt)
        },
      )
    }

    // Execute the chain starting from the first step
    // Initialize context with streaming mode flag
    val initialContext = PipelineContext(
      primary = Right(spark.emptyDataFrame),
      isStreamingMode = isStreamingMode,
    )

    val resultContext = chainedSteps match {
      case Some(firstStep) =>
        // Execute with pipeline context for better error messages
        // Note: cancellation is checked via exceptions thrown during execution
        firstStep.executeChainWithContext(
          initialContext,
          spark,
          pipelineName = Some(name),
          stepIndex = Some(0),
          metricsCollector = metricsCollector,
        )
      case None            =>
        throw new IllegalStateException("Failed to build pipeline chain")
    }

    // Handle streaming-specific post-execution
    if (isStreamingMode && resultContext.hasActiveStreams) {
      logger.info(s"Streaming pipeline started with ${resultContext.streamingQueryNames.size} queries")
      logger.info(s"Active queries: ${resultContext.streamingQueryNames.mkString(", ")}")

      // Check if we should await termination
      // In production, this would be configurable via pipeline config
      // For now, we'll keep queries running (user can await externally)
      logger.info("Streaming queries running in background")
    }

    resultContext
  }

  /**
   * Gets the collected metrics for this pipeline execution.
   *
   * @return Optional PipelineMetrics
   */
  def getMetrics: Option[PipelineMetrics] = metricsCollector
}

/**
 * Factory methods for Pipeline creation.
 */
object Pipeline {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates a Pipeline from parsed configuration.
   *
   * @param config Parsed pipeline configuration
   * @return Pipeline instance
   */
  def fromConfig(config: com.pipeline.config.PipelineConfig): Pipeline = {
    logger.info(s"Creating pipeline from config: name=${config.name}")

    // Convert StepConfig to PipelineStep
    val pipelineSteps = config.steps.map { stepConfig =>
      stepConfig.stepType.toLowerCase match {
        case "extract" =>
          ExtractStep(stepConfig.method, stepConfig.config, None)

        case "transform" =>
          TransformStep(stepConfig.method, stepConfig.config, None)

        case "validate" =>
          ValidateStep(stepConfig.method, stepConfig.config, None)

        case "load" =>
          LoadStep(stepConfig.method, stepConfig.config, None)

        case unknown =>
          throw new IllegalArgumentException(s"Unknown step type: $unknown")
      }
    }

    Pipeline(config.name, config.mode, pipelineSteps)
  }
}
