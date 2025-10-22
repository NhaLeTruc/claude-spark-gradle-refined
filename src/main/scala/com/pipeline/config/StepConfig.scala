package com.pipeline.config

/**
 * Configuration for a single pipeline step.
 *
 * Represents one step in the pipeline chain (extract, transform, validate, or load).
 * Implements FR-001: JSON configuration files for pipeline definition.
 *
 * @param stepType Type of step: "extract", "transform", "validate", or "load"
 * @param method   Method name from ExtractMethods, LoadMethods, or UserMethods (FR-023)
 * @param config   Step-specific configuration parameters
 */
case class StepConfig(
    stepType: String,
    method: String,
    config: Map[String, Any],
)

object StepConfig {

  /**
   * Valid step types per FR-006 Chain of Responsibility pattern.
   */
  val ValidStepTypes: Set[String] = Set("extract", "transform", "validate", "load")

  /**
   * Validates that step type is one of the allowed types.
   *
   * @param stepType Step type to validate
   * @throws IllegalArgumentException if step type is invalid
   */
  def validateStepType(stepType: String): Unit =
    if (!ValidStepTypes.contains(stepType)) {
      throw new IllegalArgumentException(
        s"Invalid step type: '$stepType'. Must be one of: ${ValidStepTypes.mkString(", ")}",
      )
    }
}
