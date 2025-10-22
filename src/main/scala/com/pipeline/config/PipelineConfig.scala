package com.pipeline.config

/**
 * Configuration for a complete pipeline.
 *
 * Represents the entire pipeline defined in a JSON file (FR-014).
 * Implements Constitution Section V: Library-First Architecture.
 *
 * @param name  Pipeline identifier
 * @param mode  Execution mode: "batch" or "streaming" (FR-008, FR-009)
 * @param steps Ordered list of pipeline steps (Chain of Responsibility - FR-006)
 */
case class PipelineConfig(
    name: String,
    mode: String,
    steps: List[StepConfig],
)

object PipelineConfig {

  /**
   * Valid execution modes per FR-008 and FR-009.
   */
  val ValidModes: Set[String] = Set("batch", "streaming")

  /**
   * Validates that mode is one of the allowed modes.
   *
   * @param mode Execution mode to validate
   * @throws IllegalArgumentException if mode is invalid
   */
  def validateMode(mode: String): Unit =
    if (!ValidModes.contains(mode)) {
      throw new IllegalArgumentException(
        s"Invalid mode: '$mode'. Must be one of: ${ValidModes.mkString(", ")}",
      )
    }

  /**
   * Validates the entire pipeline configuration.
   *
   * @param config Pipeline configuration to validate
   * @throws IllegalArgumentException if configuration is invalid
   */
  def validate(config: PipelineConfig): Unit = {
    // Validate name
    if (config.name.trim.isEmpty) {
      throw new IllegalArgumentException("Pipeline name cannot be empty")
    }

    // Validate mode
    validateMode(config.mode)

    // Validate steps
    if (config.steps.isEmpty) {
      throw new IllegalArgumentException("Pipeline must have at least one step")
    }

    // Validate each step type
    config.steps.foreach { step =>
      StepConfig.validateStepType(step.stepType)
    }
  }
}
