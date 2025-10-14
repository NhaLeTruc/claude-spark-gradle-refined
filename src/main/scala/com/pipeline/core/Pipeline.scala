package com.pipeline.core

import com.pipeline.retry.RetryStrategy
import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory, MDC}

import scala.util.{Failure, Success}

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

  require(name.trim.nonEmpty, "Pipeline name cannot be empty")
  require(mode == "batch" || mode == "streaming", s"Invalid mode: $mode. Must be 'batch' or 'streaming'")

  /**
   * Check if this pipeline is configured for streaming mode.
   *
   * @return true if mode is "streaming", false otherwise
   */
  def isStreamingMode: Boolean = mode == "streaming"

  /**
   * Executes the pipeline with retry logic.
   *
   * @param spark         SparkSession
   * @param maxAttempts   Maximum retry attempts (default: 3)
   * @param delayMillis   Delay between retries in milliseconds (default: 5000)
   * @return Success or Failure
   */
  def execute(
      spark: SparkSession,
      maxAttempts: Int = 3,
      delayMillis: Long = 5000,
  ): Either[Throwable, PipelineContext] = {

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
        operation = () => Success(executeSteps(spark)),
        maxAttempts = maxAttempts,
        delayMillis = delayMillis,
      )

      val durationMs = System.currentTimeMillis() - startTime

      result match {
        case Success(context) =>
          if (isStreamingMode) {
            logger.info(s"Streaming pipeline setup completed: name=$name, setupTime=${durationMs}ms")
            logger.info(s"Streaming pipeline is now running continuously...")
          } else {
            logger.info(s"Batch pipeline completed successfully: name=$name, duration=${durationMs}ms")
          }
          Right(context)

        case Failure(exception) =>
          logger.error(s"Pipeline failed after $maxAttempts attempts: name=$name, mode=$mode, duration=${durationMs}ms", exception)
          Left(exception)
      }

    } finally {
      MDC.clear()
    }
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

    logger.info(s"Executing ${steps.size} pipeline steps")

    // Build the chain by linking steps
    val chainedSteps = steps.foldRight[Option[PipelineStep]](None) { (step, nextOpt) =>
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
    val initialContext = PipelineContext(Right(spark.emptyDataFrame))

    chainedSteps match {
      case Some(firstStep) =>
        firstStep.executeChain(initialContext, spark)
      case None =>
        throw new IllegalStateException("Failed to build pipeline chain")
    }
  }
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
