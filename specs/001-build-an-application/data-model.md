# Data Model & Entity Design

**Feature**: Data Pipeline Orchestration Application
**Date**: 2025-10-13
**Branch**: 001-build-an-application

This document defines the core entities, their responsibilities, relationships, and state management patterns for the pipeline orchestration system.

## Entity Overview

The system consists of 8 core entities organized into 4 functional groups:

1. **Orchestration**: Pipeline, PipelineStep
2. **Configuration**: PipelineConfigParser, StepConfig, ConfigReference
3. **Credentials**: CredentialConfig (trait), JdbcConfig, IAMConfig, OtherConfig, VaultClient
4. **Operations**: ExtractMethods, LoadMethods, UserMethods

## Core Entities

### 1. Pipeline

**Purpose**: Orchestrates the execution of a data pipeline from JSON configuration, manages retry logic, and coordinates the Chain of Responsibility pattern.

**Responsibilities**:
- Parse JSON configuration to instantiate pipeline steps
- Execute steps in sequence (Chain of Responsibility)
- Implement retry logic (max 3 attempts, 5-second delays)
- Emit structured logs for observability
- Manage SparkSession lifecycle

**Scala Definition**:
```scala
package com.pipeline.core

import com.pipeline.config.PipelineConfig
import com.pipeline.retry.RetryStrategy
import org.apache.spark.sql.SparkSession
import org.slf4j.{Logger, LoggerFactory, MDC}
import scala.util.{Try, Success, Failure}

case class Pipeline(config: PipelineConfig, steps: List[PipelineStep]) {
  private val logger: Logger = LoggerFactory.getLogger(classOf[Pipeline])

  /**
   * Main execution method with retry logic
   * @param spark SparkSession for data processing
   * @return Try[Unit] indicating success or failure
   */
  def main(spark: SparkSession): Try[Unit] = {
    val executionId = java.util.UUID.randomUUID().toString
    MDC.put("executionId", executionId)
    MDC.put("pipelineName", config.name)

    logger.info(s"Starting pipeline: ${config.name} (mode: ${config.executionMode})")

    val retryConfig = RetryStrategy.RetryConfig(
      maxRetries = 3,
      delaySeconds = 5,
      exponential = false
    )

    val result = RetryStrategy.retry(0, retryConfig) {
      executeSteps(spark)
    }

    result match {
      case Success(_) =>
        logger.info(s"Pipeline completed successfully: ${config.name}")
      case Failure(ex) =>
        logger.error(s"Pipeline failed after retries: ${config.name}", ex)
    }

    MDC.clear()
    result
  }

  /**
   * Execute all steps in the chain
   * @param spark SparkSession for data processing
   */
  private def executeSteps(spark: SparkSession): Unit = {
    if (steps.isEmpty) {
      throw new IllegalStateException("Pipeline has no steps to execute")
    }

    // Initialize with empty data (first step must be Extract)
    val initialData: Either[GenericRecord, DataFrame] =
      Left(null) // Placeholder, first step ignores this

    steps.head.executeChain(initialData, spark)
  }
}

object Pipeline {
  /**
   * Factory method to create Pipeline from JSON configuration file
   * @param configPath Path to pipeline JSON configuration
   * @param spark SparkSession for data processing
   * @return Pipeline instance
   */
  def fromConfig(configPath: String, spark: SparkSession): Pipeline = {
    val config = PipelineConfigParser.parse(configPath)
    val steps = buildStepChain(config.steps, spark)
    Pipeline(config, steps)
  }

  /**
   * Build chain of PipelineStep instances from configuration
   * @param stepConfigs List of step configurations
   * @param spark SparkSession for credential retrieval context
   * @return List of linked PipelineStep instances
   */
  private def buildStepChain(stepConfigs: List[StepConfig],
                             spark: SparkSession): List[PipelineStep] = {
    if (stepConfigs.isEmpty) return List.empty

    // Build steps in reverse to link them correctly
    stepConfigs.reverse.foldLeft(None: Option[PipelineStep]) { (nextStep, config) =>
      val step = config.stepType match {
        case "extract" => ExtractStep(config.method, config.config, nextStep)
        case "transform" => TransformStep(config.method, config.config, nextStep)
        case "validate" => ValidateStep(config.method, config.config, nextStep)
        case "load" => LoadStep(config.method, config.config, nextStep)
        case other => throw new IllegalArgumentException(s"Unknown step type: $other")
      }
      Some(step)
    }.toList
  }
}
```

**State Transitions**:
```
INITIAL → EXECUTING → (SUCCESS | RETRY | FAILURE)
  └─→ RETRY (attempt < 3) → EXECUTING
  └─→ FAILURE (attempt >= 3)
```

**Validation Rules**:
- Pipeline must have at least one step
- First step must be ExtractStep
- ExecutionMode must be "batch" or "streaming"
- Config name must be non-empty

**Relationships**:
- Contains 1..N PipelineStep instances (composition)
- Uses PipelineConfigParser for initialization
- Uses RetryStrategy for fault tolerance

---

### 2. PipelineStep

**Purpose**: Abstract representation of a single operation in the pipeline, implementing the Chain of Responsibility pattern.

**Responsibilities**:
- Execute a single operation (extract, transform, validate, load)
- Pass result(s) to next step in chain (single or multiple DataFrames)
- Handle data format conversions (Avro ↔ DataFrame)
- Support multi-DataFrame operations (joins, unions, cross-references)
- Maintain named DataFrame registry for complex transformations
- Emit logs for step execution
- Throw exceptions on failure to halt pipeline

**Scala Definition**:
```scala
package com.pipeline.core

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.avro.generic.GenericRecord
import org.slf4j.{Logger, LoggerFactory, MDC}
import scala.collection.mutable

/**
 * Pipeline execution context holding multiple DataFrames
 * Enables complex multi-DataFrame transformations (joins, unions, etc.)
 */
case class PipelineContext(
  primary: Either[GenericRecord, DataFrame],           // Primary data flow
  dataFrames: mutable.Map[String, DataFrame] = mutable.Map.empty  // Named DataFrames registry
) {
  /**
   * Register a DataFrame with a name for later reference
   */
  def register(name: String, df: DataFrame): PipelineContext = {
    dataFrames(name) = df
    this
  }

  /**
   * Retrieve a registered DataFrame by name
   */
  def get(name: String): Option[DataFrame] = dataFrames.get(name)

  /**
   * Update primary data flow
   */
  def updatePrimary(data: Either[GenericRecord, DataFrame]): PipelineContext = {
    copy(primary = data, dataFrames = dataFrames)
  }
}

/**
 * Base trait for all pipeline steps (Chain of Responsibility pattern)
 */
sealed trait PipelineStep {
  def stepType: String
  def method: String
  def config: Map[String, Any]
  def nextStep: Option[PipelineStep]

  protected val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Execute this step's operation
   * @param context Pipeline context with primary data and named DataFrames
   * @param spark SparkSession for processing
   * @return Updated pipeline context
   */
  def execute(context: PipelineContext, spark: SparkSession): PipelineContext

  /**
   * Execute this step and propagate to next step in chain
   * @param context Pipeline context
   * @param spark SparkSession for processing
   * @return Final pipeline context from chain
   */
  def executeChain(context: PipelineContext,
                   spark: SparkSession): PipelineContext = {
    MDC.put("stepType", stepType)
    MDC.put("method", method)

    val startTime = System.currentTimeMillis()
    logger.info(s"Executing step: $stepType.$method")

    try {
      val resultContext = execute(context, spark)

      val duration = System.currentTimeMillis() - startTime
      resultContext.primary match {
        case Right(df) =>
          val recordCount = df.count()
          logger.info(s"Step completed: $recordCount records in ${duration}ms, " +
            s"${resultContext.dataFrames.size} DataFrames in registry")
        case Left(_) =>
          logger.info(s"Step completed (Avro) in ${duration}ms")
      }

      nextStep match {
        case Some(step) => step.executeChain(resultContext, spark)
        case None => resultContext
      }
    } catch {
      case ex: Exception =>
        logger.error(s"Step failed: $stepType.$method", ex)
        throw ex // Halt pipeline execution
    } finally {
      MDC.remove("stepType")
      MDC.remove("method")
    }
  }
}

/**
 * Extract step - retrieves data from external sources
 * Optionally registers extracted data with a name for later reference
 */
case class ExtractStep(
  method: String,
  config: Map[String, Any],
  nextStep: Option[PipelineStep]
) extends PipelineStep {
  override val stepType: String = "extract"

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    // Extract from source (ignores input context)
    val credentials = resolveCredentials(config, spark)
    val configWithCreds = config ++ credentials

    val df = ExtractMethods.invokeMethod(method, configWithCreds, spark)

    // Register extracted DataFrame if name is provided
    val updatedContext = config.get("registerAs") match {
      case Some(name) =>
        context.register(name.toString, df)
      case None => context
    }

    // Update primary data flow
    updatedContext.updatePrimary(Right(df))
  }

  private def resolveCredentials(config: Map[String, Any],
                                 spark: SparkSession): Map[String, String] = {
    config.get("credentialPath") match {
      case Some(path) =>
        val credType = config("credentialType").toString
        val credConfig = CredentialConfigFactory.create(credType)
        val vaultClient = VaultClient.fromEnv()
        credConfig.fetchFromVault(vaultClient, path.toString)
      case None => Map.empty // No credentials required
    }
  }
}

/**
 * Transform step - applies data transformations
 * Supports multi-DataFrame operations via context registry
 */
case class TransformStep(
  method: String,
  config: Map[String, Any],
  nextStep: Option[PipelineStep]
) extends PipelineStep {
  override val stepType: String = "transform"

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    context.primary match {
      case Right(primaryDf) =>
        // Check if method requires additional DataFrames from registry
        val enrichedConfig = config.get("inputDataFrames") match {
          case Some(names: List[String]) =>
            // Resolve named DataFrames from context
            val resolvedDFs = names.flatMap { name =>
              context.get(name).map(df => name -> df)
            }.toMap
            config ++ Map("resolvedDataFrames" -> resolvedDFs)
          case _ => config
        }

        val transformed = UserMethods.invokeMethod(method, primaryDf, enrichedConfig, context)

        // Register output if name is provided
        val updatedContext = config.get("registerAs") match {
          case Some(name) =>
            context.register(name.toString, transformed)
          case None => context
        }

        updatedContext.updatePrimary(Right(transformed))

      case Left(_) =>
        throw new IllegalStateException("Transform step requires DataFrame input")
    }
  }
}

/**
 * Validate step - checks data quality rules
 */
case class ValidateStep(
  method: String,
  config: Map[String, Any],
  nextStep: Option[PipelineStep]
) extends PipelineStep {
  override val stepType: String = "validate"

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    context.primary match {
      case Right(df) =>
        val validated = UserMethods.invokeMethod(method, df, config, context)
        context.updatePrimary(Right(validated)) // Throws exception if validation fails
      case Left(_) =>
        throw new IllegalStateException("Validate step requires DataFrame input")
    }
  }
}

/**
 * Load step - writes data to external sinks
 */
case class LoadStep(
  method: String,
  config: Map[String, Any],
  nextStep: Option[PipelineStep]
) extends PipelineStep {
  override val stepType: String = "load"

  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    context.primary match {
      case Right(df) =>
        val credentials = resolveCredentials(config, spark)
        val configWithCreds = config ++ credentials
        LoadMethods.invokeMethod(method, df, configWithCreds, spark)
        context // Pass through context for potential next step
      case Left(_) =>
        throw new IllegalStateException("Load step requires DataFrame input")
    }
  }

  private def resolveCredentials(config: Map[String, Any],
                                 spark: SparkSession): Map[String, String] = {
    config.get("credentialPath") match {
      case Some(path) =>
        val credType = config("credentialType").toString
        val credConfig = CredentialConfigFactory.create(credType)
        val vaultClient = VaultClient.fromEnv()
        credConfig.fetchFromVault(vaultClient, path.toString)
      case None => Map.empty
    }
  }
}
```

**Validation Rules**:
- method name must exist in corresponding Methods object
- config must contain required parameters for method
- credentialPath (if present) must reference valid Vault path
- Step types: "extract", "transform", "validate", "load"
- registerAs (if present) must be unique DataFrame name
- inputDataFrames (if present) must reference registered DataFrames

**Multi-DataFrame Support**:
- **registerAs**: Optional config parameter to register step output with a name
- **inputDataFrames**: Optional config parameter listing DataFrame names required by transform
- **PipelineContext**: Maintains registry of named DataFrames throughout pipeline execution
- **Use Cases**: Joins (two DataFrames), unions (multiple DataFrames), lookups (reference tables)

**Example Multi-DataFrame Configuration**:
```json
{
  "steps": [
    {
      "stepType": "extract",
      "method": "fromPostgres",
      "config": {
        "table": "orders",
        "registerAs": "orders",
        "credentialPath": "secret/data/postgres/dev",
        "credentialType": "jdbc"
      }
    },
    {
      "stepType": "extract",
      "method": "fromPostgres",
      "config": {
        "table": "customers",
        "registerAs": "customers",
        "credentialPath": "secret/data/postgres/dev",
        "credentialType": "jdbc"
      }
    },
    {
      "stepType": "transform",
      "method": "joinDataFrames",
      "config": {
        "inputDataFrames": ["orders", "customers"],
        "joinKey": "customer_id",
        "joinType": "inner"
      }
    }
  ]
}
```

**Relationships**:
- Links to next PipelineStep (Chain of Responsibility)
- Uses ExtractMethods, LoadMethods, or UserMethods (delegation)
- Uses CredentialConfig for authentication
- Maintains PipelineContext with DataFrame registry

---

### 3. CredentialConfig (Factory Pattern)

**Purpose**: Abstract interface for retrieving credentials from Vault, with specialized implementations for JDBC, IAM, and other credential types.

**Scala Definition**:
```scala
package com.pipeline.credentials

import com.bettercloud.vault.Vault

/**
 * Base trait for credential configuration objects
 */
sealed trait CredentialConfig {
  /**
   * Fetch credentials from Vault and validate required fields
   * @param client Vault client for API access
   * @param path Secret path in Vault
   * @return Map of credential key-value pairs
   */
  def fetchFromVault(client: VaultClient, path: String): Map[String, String]

  protected def validateKeys(creds: Map[String, String], required: Set[String]): Unit = {
    val missing = required.diff(creds.keySet)
    if (missing.nonEmpty) {
      throw new IllegalStateException(
        s"Missing required credential keys: ${missing.mkString(", ")}"
      )
    }
  }
}

/**
 * Factory for creating CredentialConfig instances
 */
object CredentialConfigFactory {
  def create(configType: String): CredentialConfig = configType match {
    case "jdbc" => new JdbcConfig()
    case "iam" => new IAMConfig()
    case "other" => new OtherConfig()
    case _ => throw new IllegalArgumentException(s"Unknown credential type: $configType")
  }
}

/**
 * JDBC credential configuration (PostgreSQL, MySQL)
 */
class JdbcConfig extends CredentialConfig {
  private val requiredKeys = Set("host", "port", "database", "username", "password")

  override def fetchFromVault(client: VaultClient, path: String): Map[String, String] = {
    val creds = client.readSecret(path)
    validateKeys(creds, requiredKeys)
    creds
  }
}

/**
 * IAM credential configuration (AWS S3)
 */
class IAMConfig extends CredentialConfig {
  private val requiredKeys = Set("accessKey", "secretKey", "region")
  private val optionalKeys = Set("sessionToken")

  override def fetchFromVault(client: VaultClient, path: String): Map[String, String] = {
    val creds = client.readSecret(path)
    validateKeys(creds, requiredKeys)
    creds // sessionToken is optional
  }
}

/**
 * Other credential configuration (Kafka, DeltaLake)
 */
class OtherConfig extends CredentialConfig {
  // No fixed required keys - varies by service
  override def fetchFromVault(client: VaultClient, path: String): Map[String, String] = {
    client.readSecret(path)
  }
}
```

**Validation Rules**:
- JdbcConfig: host, port, database, username, password (all required)
- IAMConfig: accessKey, secretKey, region (required), sessionToken (optional)
- OtherConfig: No validation (flexible for Kafka/DeltaLake)

**Relationships**:
- Created by CredentialConfigFactory
- Uses VaultClient for secret retrieval
- Used by ExtractStep and LoadStep for authentication

---

### 4. VaultClient

**Purpose**: Wrapper around Bettercloud Vault Java Driver for credential retrieval.

**Scala Definition**:
```scala
package com.pipeline.credentials

import com.bettercloud.vault.{Vault, VaultConfig, VaultException}
import scala.jdk.CollectionConverters._

/**
 * Client for retrieving secrets from HashiCorp Vault
 */
class VaultClient(vaultAddr: String, token: String) {
  private val config = new VaultConfig()
    .address(vaultAddr)
    .token(token)
    .build()

  private val vault = new Vault(config)

  /**
   * Read secret from Vault KV v2 engine
   * @param path Secret path (e.g., "secret/data/postgres/dev")
   * @return Map of secret key-value pairs
   */
  def readSecret(path: String): Map[String, String] = {
    try {
      val response = vault.logical().read(path)
      if (response == null || response.getData == null) {
        throw new RuntimeException(s"No data found at Vault path: $path")
      }
      response.getData.asScala.toMap.map { case (k, v) => k -> v.toString }
    } catch {
      case e: VaultException =>
        throw new RuntimeException(s"Failed to read secret from Vault at path: $path", e)
    }
  }
}

object VaultClient {
  /**
   * Create VaultClient from environment variables
   * @return VaultClient instance
   */
  def fromEnv(): VaultClient = {
    val addr = sys.env.getOrElse("VAULT_ADDR", "http://localhost:8200")
    val token = sys.env.getOrElse(
      "VAULT_TOKEN",
      throw new RuntimeException("VAULT_TOKEN environment variable not set")
    )
    new VaultClient(addr, token)
  }
}
```

**Validation Rules**:
- VAULT_ADDR must be valid HTTP(S) URL
- VAULT_TOKEN must be non-empty
- Vault path must exist and contain data

**Relationships**:
- Used by CredentialConfig implementations
- Configured via environment variables

---

### 5. ExtractMethods

**Purpose**: Static methods for extracting data from various sources.

**Scala Definition**:
```scala
package com.pipeline.operations

import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 * Static methods for extracting data from external sources
 */
object ExtractMethods {
  def fromPostgres(config: Map[String, Any], spark: SparkSession): DataFrame = {
    val jdbcUrl = s"jdbc:postgresql://${config("host")}:${config("port")}/${config("database")}"

    spark.read
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", config("table").toString)
      .option("user", config("username").toString)
      .option("password", config("password").toString)
      .option("driver", "org.postgresql.Driver")
      .load()
  }

  def fromMySQL(config: Map[String, Any], spark: SparkSession): DataFrame = {
    val jdbcUrl = s"jdbc:mysql://${config("host")}:${config("port")}/${config("database")}"

    spark.read
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", config("table").toString)
      .option("user", config("username").toString)
      .option("password", config("password").toString)
      .option("driver", "com.mysql.cj.jdbc.Driver")
      .load()
  }

  def fromKafka(config: Map[String, Any], spark: SparkSession): DataFrame = {
    val isStreaming = config.getOrElse("streaming", false).asInstanceOf[Boolean]

    val reader = if (isStreaming) spark.readStream else spark.read

    reader
      .format("kafka")
      .option("kafka.bootstrap.servers", config("bootstrapServers").toString)
      .option("subscribe", config("topic").toString)
      .option("startingOffsets", config.getOrElse("startingOffsets", "earliest"))
      .load()
  }

  def fromS3(config: Map[String, Any], spark: SparkSession): DataFrame = {
    // Configure S3 credentials
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", config("accessKey").toString)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", config("secretKey").toString)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint", s"s3.${config("region")}.amazonaws.com")

    val path = s"s3a://${config("bucket")}/${config("path")}"
    val format = config.getOrElse("format", "parquet").toString

    spark.read.format(format).load(path)
  }

  def fromDeltaLake(config: Map[String, Any], spark: SparkSession): DataFrame = {
    val path = config("path").toString
    spark.read.format("delta").load(path)
  }

  /**
   * Invoke extract method by name
   */
  def invokeMethod(methodName: String, config: Map[String, Any],
                   spark: SparkSession): DataFrame = {
    methodName match {
      case "fromPostgres" => fromPostgres(config, spark)
      case "fromMySQL" => fromMySQL(config, spark)
      case "fromKafka" => fromKafka(config, spark)
      case "fromS3" => fromS3(config, spark)
      case "fromDeltaLake" => fromDeltaLake(config, spark)
      case _ => throw new IllegalArgumentException(s"Unknown extract method: $methodName")
    }
  }
}
```

**Validation Rules**:
- Method name must be one of: fromPostgres, fromMySQL, fromKafka, fromS3, fromDeltaLake
- Config must contain required parameters for each method
- Credentials must be valid for authentication

**Relationships**:
- Used by ExtractStep
- Returns DataFrame (Spark SQL)

---

### 6. LoadMethods

**Purpose**: Static methods for loading data to various sinks.

**Scala Definition**:
```scala
package com.pipeline.operations

import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

/**
 * Static methods for loading data to external sinks
 */
object LoadMethods {
  def toPostgres(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    val jdbcUrl = s"jdbc:postgresql://${config("host")}:${config("port")}/${config("database")}"
    val saveMode = SaveMode.valueOf(config.getOrElse("mode", "Append").toString)

    df.write
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", config("table").toString)
      .option("user", config("username").toString)
      .option("password", config("password").toString)
      .option("driver", "org.postgresql.Driver")
      .mode(saveMode)
      .save()
  }

  def toMySQL(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    val jdbcUrl = s"jdbc:mysql://${config("host")}:${config("port")}/${config("database")}"
    val saveMode = SaveMode.valueOf(config.getOrElse("mode", "Append").toString)

    df.write
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", config("table").toString)
      .option("user", config("username").toString)
      .option("password", config("password").toString)
      .option("driver", "com.mysql.cj.jdbc.Driver")
      .mode(saveMode)
      .save()
  }

  def toKafka(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    val isStreaming = df.isStreaming

    val writer = if (isStreaming) {
      df.writeStream
        .format("kafka")
        .option("kafka.bootstrap.servers", config("bootstrapServers").toString)
        .option("topic", config("topic").toString)
        .option("checkpointLocation", config("checkpointLocation").toString)
        .start()
    } else {
      df.write
        .format("kafka")
        .option("kafka.bootstrap.servers", config("bootstrapServers").toString)
        .option("topic", config("topic").toString)
        .save()
    }
  }

  def toS3(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    // Configure S3 credentials
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", config("accessKey").toString)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", config("secretKey").toString)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint", s"s3.${config("region")}.amazonaws.com")

    val path = s"s3a://${config("bucket")}/${config("path")}"
    val format = config.getOrElse("format", "parquet").toString
    val saveMode = SaveMode.valueOf(config.getOrElse("mode", "Append").toString)

    df.write.format(format).mode(saveMode).save(path)
  }

  def toDeltaLake(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    val path = config("path").toString
    val saveMode = SaveMode.valueOf(config.getOrElse("mode", "Append").toString)

    df.write.format("delta").mode(saveMode).save(path)
  }

  /**
   * Invoke load method by name
   */
  def invokeMethod(methodName: String, df: DataFrame,
                   config: Map[String, Any], spark: SparkSession): Unit = {
    methodName match {
      case "toPostgres" => toPostgres(df, config, spark)
      case "toMySQL" => toMySQL(df, config, spark)
      case "toKafka" => toKafka(df, config, spark)
      case "toS3" => toS3(df, config, spark)
      case "toDeltaLake" => toDeltaLake(df, config, spark)
      case _ => throw new IllegalArgumentException(s"Unknown load method: $methodName")
    }
  }
}
```

**Validation Rules**:
- Method name must be one of: toPostgres, toMySQL, toKafka, toS3, toDeltaLake
- SaveMode must be: Append, Overwrite, ErrorIfExists, Ignore
- Streaming writes require checkpointLocation

**Relationships**:
- Used by LoadStep
- Accepts DataFrame input

---

### 7. UserMethods

**Purpose**: Static methods for transforming and validating data (5 of each type per spec).

**Scala Definition Summary** (see research.md for full implementation):
```scala
package com.pipeline.operations

object UserMethods {
  // Transformation methods (5 required)
  def filterRows(df: DataFrame, params: Map[String, Any]): DataFrame
  def aggregateData(df: DataFrame, params: Map[String, Any]): DataFrame
  def joinDataFrames(df: DataFrame, params: Map[String, Any]): DataFrame
  def enrichData(df: DataFrame, params: Map[String, Any]): DataFrame
  def reshapeData(df: DataFrame, params: Map[String, Any]): DataFrame

  // Validation methods (5 required)
  def validateSchema(df: DataFrame, params: Map[String, Any]): DataFrame
  def validateNulls(df: DataFrame, params: Map[String, Any]): DataFrame
  def validateRanges(df: DataFrame, params: Map[String, Any]): DataFrame
  def validateReferentialIntegrity(df: DataFrame, params: Map[String, Any]): DataFrame
  def validateBusinessRules(df: DataFrame, params: Map[String, Any]): DataFrame

  def invokeMethod(methodName: String, df: DataFrame,
                   params: Map[String, Any]): DataFrame
}
```

**Validation Rules**:
- Validation methods throw exceptions on failure (halt pipeline)
- Transformation methods return new DataFrame (immutable)
- Method names must match JSON configuration

**Relationships**:
- Used by TransformStep and ValidateStep
- Operates on DataFrames only (not Avro)

---

### 8. PipelineConfigParser

**Purpose**: Parse JSON configuration files into typed Scala case classes.

**Scala Definition**:
```scala
package com.pipeline.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import java.io.File

case class PipelineConfig(
  name: String,
  executionMode: String, // "batch" or "streaming"
  steps: List[StepConfig]
)

case class StepConfig(
  stepType: String, // "extract", "transform", "validate", "load"
  method: String,
  config: Map[String, Any],
  configRef: Option[String] = None // Path to external JSON file
)

object PipelineConfigParser {
  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  /**
   * Parse pipeline configuration from JSON file
   * @param jsonPath Path to pipeline JSON
   * @return PipelineConfig instance
   */
  def parse(jsonPath: String): PipelineConfig = {
    val file = new File(jsonPath)
    if (!file.exists()) {
      throw new IllegalArgumentException(s"Configuration file not found: $jsonPath")
    }

    val config = mapper.readValue(file, classOf[PipelineConfig])
    validateConfig(config)
    resolveReferences(config, file.getParentFile)
  }

  private def validateConfig(config: PipelineConfig): Unit = {
    require(config.name.nonEmpty, "Pipeline name cannot be empty")
    require(
      config.executionMode == "batch" || config.executionMode == "streaming",
      s"Invalid execution mode: ${config.executionMode}"
    )
    require(config.steps.nonEmpty, "Pipeline must have at least one step")
    require(config.steps.head.stepType == "extract", "First step must be 'extract'")
  }

  private def resolveReferences(config: PipelineConfig, baseDir: File): PipelineConfig = {
    config.copy(
      steps = config.steps.map { step =>
        step.configRef match {
          case Some(refPath) =>
            val refFile = new File(baseDir, refPath)
            val externalConfig = mapper.readValue(refFile, classOf[Map[String, Any]])
            step.copy(config = step.config ++ externalConfig)
          case None => step
        }
      }
    )
  }
}
```

**Validation Rules**:
- name must be non-empty
- executionMode must be "batch" or "streaming"
- steps must be non-empty list
- First step must be "extract"
- External config references must exist

**Relationships**:
- Used by Pipeline.fromConfig
- Produces PipelineConfig and StepConfig case classes

---

## Entity Relationship Diagram

```
┌─────────────────┐
│   Pipeline      │
│  - config       │───────> PipelineConfig
│  - steps        │
│  + main()       │
└────────┬────────┘
         │ 1..N
         │ contains
         ▼
┌─────────────────┐
│  PipelineStep   │◄───────┐
│  - method       │        │ Chain of
│  - config       │        │ Responsibility
│  - nextStep     │────────┘
│  + execute()    │
│  + executeChain()│
└────────┬────────┘
         │
         ├─────► ExtractMethods (static)
         ├─────► LoadMethods (static)
         ├─────► UserMethods (static)
         │
         └─────► CredentialConfig (trait)
                  ├─── JdbcConfig
                  ├─── IAMConfig
                  └─── OtherConfig
                         │
                         └───► VaultClient
```

## Data Flow

```
JSON Config → PipelineConfigParser → PipelineConfig
                                           │
                                           ▼
                                      Pipeline.fromConfig
                                           │
                                           ▼
                                  buildStepChain (Chain of Responsibility)
                                           │
                                           ▼
                          ExtractStep → TransformStep → ValidateStep → LoadStep
                               │              │              │              │
                               ▼              ▼              ▼              ▼
                         ExtractMethods  UserMethods   UserMethods   LoadMethods
                               │                                            │
                               ▼                                            ▼
                          VaultClient                              VaultClient
                               │                                            │
                               ▼                                            ▼
                          [Data Source]                              [Data Sink]
```

## Summary

This data model implements:
- ✅ SOLID principles (SRP, OCP, LSP, ISP, DIP)
- ✅ Chain of Responsibility pattern for pipeline steps
- ✅ Factory pattern for credential configs
- ✅ Immutable case classes for configuration
- ✅ Type-safe Either[Avro, DataFrame] for data interchange
- ✅ Structured logging with MDC for observability
- ✅ Retry strategy with tail recursion
- ✅ All 8 core entities from specification

**Testability**: All entities use dependency injection (SparkSession, VaultClient) and pure functions, enabling comprehensive unit testing with mocks.

**Extensibility**: New step types, extract/load methods, and credential configs can be added without modifying existing code (Open/Closed Principle).
