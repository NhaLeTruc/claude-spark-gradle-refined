package com.pipeline.config

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import org.slf4j.{Logger, LoggerFactory}

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

/**
 * Parser for pipeline JSON configuration files.
 *
 * Implements FR-014: JSON configuration with external references.
 * Validates Constitution Section II: Test-First Development (TDD).
 */
object PipelineConfigParser {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private val mapper: ObjectMapper with ClassTagExtensions = new ObjectMapper() with ClassTagExtensions
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  /**
   * Parses JSON string into PipelineConfig.
   *
   * @param json JSON string
   * @return Parsed PipelineConfig
   * @throws Exception if parsing fails
   */
  def parse(json: String): PipelineConfig =
    Try {
      val rawConfig = mapper.readValue[Map[String, Any]](json)
      parseRawConfig(rawConfig)
    } match {
      case Success(config)    =>
        logger.info(s"Successfully parsed pipeline configuration: ${config.name}")
        config
      case Failure(exception) =>
        logger.error(s"Failed to parse pipeline configuration: ${exception.getMessage}")
        throw exception
    }

  /**
   * Parses JSON file into PipelineConfig.
   *
   * @param filePath Path to JSON file
   * @return Parsed PipelineConfig
   * @throws Exception if file reading or parsing fails
   */
  def parseFile(filePath: String): PipelineConfig = {
    val json = new String(Files.readAllBytes(Paths.get(filePath)))
    parse(json)
  }

  /**
   * Validates a pipeline configuration.
   *
   * @param config Pipeline configuration to validate
   * @throws IllegalArgumentException if configuration is invalid
   */
  def validate(config: PipelineConfig): Unit =
    PipelineConfig.validate(config)

  /**
   * Parses raw map into PipelineConfig.
   *
   * Handles type conversions from Jackson's Any types.
   *
   * @param rawConfig Raw configuration map
   * @return Parsed PipelineConfig
   */
  private def parseRawConfig(rawConfig: Map[String, Any]): PipelineConfig = {
    val name = rawConfig.getOrElse("name", throw new IllegalArgumentException("Missing required field: name")).toString

    val mode = rawConfig.getOrElse("mode", throw new IllegalArgumentException("Missing required field: mode")).toString

    val stepsRaw = rawConfig.getOrElse("steps", throw new IllegalArgumentException("Missing required field: steps"))

    val steps = stepsRaw match {
      case list: java.util.List[_] =>
        list.asScala.map {
          case stepMap: java.util.Map[_, _] =>
            parseStepConfig(stepMap.asScala.toMap.asInstanceOf[Map[String, Any]])
          case other                        =>
            throw new IllegalArgumentException(s"Invalid step format: $other")
        }.toList
      case list: Seq[_]            =>
        list.map {
          case stepMap: Map[_, _] =>
            parseStepConfig(stepMap.asInstanceOf[Map[String, Any]])
          case other              =>
            throw new IllegalArgumentException(s"Invalid step format: $other")
        }.toList
      case other                   =>
        throw new IllegalArgumentException(s"Steps must be an array, got: $other")
    }

    val config = PipelineConfig(name, mode, steps)

    // Validate the configuration
    validate(config)

    config
  }

  /**
   * Parses raw map into StepConfig.
   *
   * @param stepMap Raw step configuration map
   * @return Parsed StepConfig
   */
  private def parseStepConfig(stepMap: Map[String, Any]): StepConfig = {
    val stepType = stepMap
      .getOrElse("type", throw new IllegalArgumentException("Missing required field in step: type"))
      .toString

    val method = stepMap
      .getOrElse("method", throw new IllegalArgumentException("Missing required field in step: method"))
      .toString

    val configRaw = stepMap.getOrElse("config", Map.empty[String, Any])

    val config = configRaw match {
      case javaMap: java.util.Map[_, _] =>
        convertJavaMapToScala(javaMap.asScala.toMap.asInstanceOf[Map[String, Any]])
      case scalaMap: Map[_, _]          =>
        scalaMap.asInstanceOf[Map[String, Any]]
      case other                        =>
        throw new IllegalArgumentException(s"Config must be an object, got: $other")
    }

    StepConfig(stepType, method, config)
  }

  /**
   * Recursively converts Java collections to Scala collections.
   *
   * Jackson returns Java collections, we need Scala types.
   *
   * @param map Map to convert
   * @return Converted map with Scala collections
   */
  private def convertJavaMapToScala(map: Map[String, Any]): Map[String, Any] =
    map.map {
      case (key, value: java.util.Map[_, _]) =>
        key -> convertJavaMapToScala(value.asScala.toMap.asInstanceOf[Map[String, Any]])
      case (key, value: java.util.List[_])   =>
        key -> value.asScala.toList.map {
          case item: java.util.Map[_, _] =>
            convertJavaMapToScala(item.asScala.toMap.asInstanceOf[Map[String, Any]])
          case item                      => item
        }
      case (key, value)                      =>
        key -> value
    }
}
