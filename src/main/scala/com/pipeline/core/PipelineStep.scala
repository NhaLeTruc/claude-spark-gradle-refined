package com.pipeline.core

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.slf4j.{Logger, LoggerFactory}
import com.pipeline.operations.{ExtractMethods, LoadMethods, UserMethods}

/**
 * Sealed trait for pipeline steps implementing Chain of Responsibility pattern.
 *
 * Implements FR-006: Chain of Responsibility for pipeline steps.
 * Validates Constitution Section I: SOLID principles (Open/Closed Principle).
 *
 * Each step can execute its logic and optionally chain to the next step.
 */
sealed trait PipelineStep {

  /** Method name to execute (e.g., "fromPostgres", "filterRows", "toS3") */
  def method: String

  /** Configuration for this step */
  def config: Map[String, Any]

  /** Next step in the chain (None if this is the last step) */
  def nextStep: Option[PipelineStep]

  /**
   * Executes this step's logic.
   *
   * @param context Current pipeline context
   * @param spark   SparkSession
   * @return Updated pipeline context
   */
  def execute(context: PipelineContext, spark: SparkSession): PipelineContext

  /**
   * Executes this step and chains to next step if present.
   *
   * Implements Chain of Responsibility pattern.
   *
   * @param context Current pipeline context
   * @param spark   SparkSession
   * @return Final pipeline context after chain execution
   */
  def executeChain(context: PipelineContext, spark: SparkSession): PipelineContext = {
    val logger = LoggerFactory.getLogger(getClass)

    logger.info(s"Executing step: ${this.getClass.getSimpleName}, method: $method")

    val updatedContext = execute(context, spark)

    nextStep match {
      case Some(next) =>
        logger.info(s"Chaining to next step: ${next.getClass.getSimpleName}")
        next.executeChain(updatedContext, spark)

      case None =>
        logger.info("End of chain reached")
        updatedContext
    }
  }
}

/**
 * Extract step for reading data from sources.
 *
 * Implements FR-003: Extract from data sources.
 * Supports PostgreSQL, MySQL, Kafka, S3, DeltaLake (FR-023).
 *
 * @param method   Extract method name (e.g., "fromPostgres")
 * @param config   Configuration including table/query, credentials
 * @param nextStep Optional next step in chain
 */
case class ExtractStep(
    method: String,
    config: Map[String, Any],
    nextStep: Option[PipelineStep],
) extends PipelineStep {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    logger.info(s"Extract step executing: method=$method")

    // Extract logic will be implemented by ExtractMethods
    // For now, return context unchanged (will be implemented in ExtractMethods)
    val df = extractData(spark)

    // Register DataFrame if registerAs is specified
    val updatedContext = config.get("registerAs") match {
      case Some(name) =>
        logger.info(s"Registering DataFrame as: $name")
        context.register(name.toString, df).updatePrimary(Right(df))
      case None =>
        context.updatePrimary(Right(df))
    }

    updatedContext
  }

  /**
   * Delegates to ExtractMethods based on method name.
   */
  private def extractData(spark: SparkSession): DataFrame = {
    method match {
      case "fromPostgres" => ExtractMethods.fromPostgres(config, spark)
      case "fromMySQL" => ExtractMethods.fromMySQL(config, spark)
      case "fromKafka" => ExtractMethods.fromKafka(config, spark)
      case "fromS3" => ExtractMethods.fromS3(config, spark)
      case "fromDeltaLake" => ExtractMethods.fromDeltaLake(config, spark)
      case "fromAvro" => ExtractMethods.fromAvro(config, spark)
      case _ => throw new IllegalArgumentException(s"Unknown extract method: $method")
    }
  }
}

/**
 * Transform step for data transformation operations.
 *
 * Implements FR-004: Transform operations.
 * Supports filterRows, joinDataFrames, aggregateData, etc. (FR-024).
 *
 * @param method   Transform method name (e.g., "filterRows")
 * @param config   Configuration including transformation logic
 * @param nextStep Optional next step in chain
 */
case class TransformStep(
    method: String,
    config: Map[String, Any],
    nextStep: Option[PipelineStep],
) extends PipelineStep {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    logger.info(s"Transform step executing: method=$method")

    // Resolve input DataFrames if specified (multi-DataFrame support)
    val enrichedConfig = config.get("inputDataFrames") match {
      case Some(names: List[_]) =>
        val dfNames = names.map(_.toString)
        logger.info(s"Resolving input DataFrames: ${dfNames.mkString(", ")}")

        val resolvedDFs = dfNames.flatMap { name =>
          context.get(name).map(df => name -> df)
        }.toMap

        if (resolvedDFs.size != dfNames.size) {
          val missing = dfNames.filterNot(resolvedDFs.contains)
          throw new IllegalStateException(s"Missing required DataFrames: ${missing.mkString(", ")}")
        }

        config ++ Map("resolvedDataFrames" -> resolvedDFs)

      case _ =>
        config
    }

    // Transform logic will be implemented by UserMethods
    val transformedDf = transformData(context.getPrimaryDataFrame, enrichedConfig, spark)

    // Register result if registerAs is specified
    val updatedContext = config.get("registerAs") match {
      case Some(name) =>
        logger.info(s"Registering transformed DataFrame as: $name")
        context.register(name.toString, transformedDf).updatePrimary(Right(transformedDf))
      case None =>
        context.updatePrimary(Right(transformedDf))
    }

    updatedContext
  }

  /**
   * Delegates to UserMethods based on method name.
   */
  private def transformData(df: DataFrame, cfg: Map[String, Any], spark: SparkSession): DataFrame = {
    method match {
      case "filterRows" => UserMethods.filterRows(df, cfg, spark)
      case "enrichData" => UserMethods.enrichData(df, cfg, spark)
      case "joinDataFrames" => UserMethods.joinDataFrames(df, cfg, spark)
      case "aggregateData" => UserMethods.aggregateData(df, cfg, spark)
      case "reshapeData" => UserMethods.reshapeData(df, cfg, spark)
      case "unionDataFrames" => UserMethods.unionDataFrames(df, cfg, spark)
      case "toAvroSchema" => UserMethods.toAvroSchema(df, cfg, spark)
      case "evolveAvroSchema" => UserMethods.evolveAvroSchema(df, cfg, spark)
      case _ => throw new IllegalArgumentException(s"Unknown transform method: $method")
    }
  }
}

/**
 * Validate step for data quality validation.
 *
 * Implements FR-010: Data validation.
 * Supports schema, nulls, ranges, referential integrity, business rules (FR-025).
 *
 * @param method   Validation method name (e.g., "validateSchema")
 * @param config   Configuration including validation rules
 * @param nextStep Optional next step in chain
 */
case class ValidateStep(
    method: String,
    config: Map[String, Any],
    nextStep: Option[PipelineStep],
) extends PipelineStep {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    logger.info(s"Validate step executing: method=$method")

    val df = context.getPrimaryDataFrame

    // Resolve referenced DataFrames if needed (for referential integrity)
    val enrichedConfig = config.get("referencedDataFrame") match {
      case Some(refName: String) =>
        logger.info(s"Resolving referenced DataFrame: $refName")
        val refDf = context.get(refName).getOrElse(
          throw new IllegalStateException(s"Referenced DataFrame '$refName' not found in context")
        )
        config ++ Map("resolvedReferencedDataFrame" -> refDf)
      case _ =>
        config
    }

    // Validation logic will be implemented by UserMethods
    validateData(df, enrichedConfig, spark)

    // Validation doesn't modify data, just checks it
    context
  }

  /**
   * Delegates to UserMethods validation methods based on method name.
   * Throws exception if validation fails.
   */
  private def validateData(df: DataFrame, cfg: Map[String, Any], spark: SparkSession): Unit = {
    method match {
      case "validateSchema" => UserMethods.validateSchema(df, cfg, spark)
      case "validateNulls" => UserMethods.validateNulls(df, cfg, spark)
      case "validateRanges" => UserMethods.validateRanges(df, cfg, spark)
      case "validateReferentialIntegrity" => UserMethods.validateReferentialIntegrity(df, cfg, spark)
      case "validateBusinessRules" => UserMethods.validateBusinessRules(df, cfg, spark)
      case _ => throw new IllegalArgumentException(s"Unknown validation method: $method")
    }
  }
}

/**
 * Load step for writing data to sinks.
 *
 * Implements FR-005: Load to data sinks.
 * Supports PostgreSQL, MySQL, Kafka, S3, DeltaLake (FR-023).
 *
 * @param method   Load method name (e.g., "toS3")
 * @param config   Configuration including destination, credentials
 * @param nextStep Optional next step in chain (rarely used for Load)
 */
case class LoadStep(
    method: String,
    config: Map[String, Any],
    nextStep: Option[PipelineStep],
) extends PipelineStep {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    logger.info(s"Load step executing: method=$method")

    val df = context.getPrimaryDataFrame

    // Load logic will be implemented by LoadMethods
    loadData(df, spark)

    // Load doesn't modify context, just writes data
    context
  }

  /**
   * Delegates to LoadMethods based on method name.
   */
  private def loadData(df: DataFrame, spark: SparkSession): Unit = {
    method match {
      case "toPostgres" => LoadMethods.toPostgres(df, config, spark)
      case "toMySQL" => LoadMethods.toMySQL(df, config, spark)
      case "toKafka" => LoadMethods.toKafka(df, config, spark)
      case "toS3" => LoadMethods.toS3(df, config, spark)
      case "toDeltaLake" => LoadMethods.toDeltaLake(df, config, spark)
      case "toAvro" => LoadMethods.toAvro(df, config, spark)
      case _ => throw new IllegalArgumentException(s"Unknown load method: $method")
    }
  }
}
