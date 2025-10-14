package com.pipeline.operations

import com.pipeline.credentials.{CredentialConfigFactory, IAMConfig, JdbcConfig, OtherConfig, VaultClient}
import com.pipeline.avro.AvroConverter
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success}

/**
 * Static methods for extracting data from various sources.
 *
 * Implements FR-003: Extract from data sources.
 * Implements FR-023: Support at least 5 extract methods.
 * Validates Constitution Section V: Library-First Architecture.
 */
object ExtractMethods {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Extracts data from PostgreSQL using JDBC.
   *
   * @param config Configuration including table/query, credentials
   * @param spark  SparkSession
   * @return DataFrame containing extracted data
   */
  def fromPostgres(config: Map[String, Any], spark: SparkSession): DataFrame = {
    logger.info("Extracting from PostgreSQL")

    require(config.contains("table") || config.contains("query"), "Either 'table' or 'query' is required")

    val jdbcConfig = resolveJdbcCredentials(config, "postgres")

    val reader = spark.read
      .format("jdbc")
      .option("url", jdbcConfig.jdbcUrl)
      .option("user", jdbcConfig.username)
      .option("password", jdbcConfig.password)
      .option("driver", "org.postgresql.Driver")

    // Add partitioning if specified
    val partitionedReader = config.get("partitionColumn") match {
      case Some(column) =>
        reader
          .option("partitionColumn", column.toString)
          .option("lowerBound", config.getOrElse("lowerBound", 0).toString)
          .option("upperBound", config.getOrElse("upperBound", 1000000).toString)
          .option("numPartitions", config.getOrElse("numPartitions", 10).toString)
      case None => reader
    }

    // Load by table or query
    val df = config.get("query") match {
      case Some(query) =>
        logger.info(s"Executing query: $query")
        partitionedReader.option("query", query.toString).load()
      case None =>
        val table = config("table").toString
        logger.info(s"Loading table: $table")
        partitionedReader.option("dbtable", table).load()
    }

    logger.info(s"Extracted ${df.count()} rows from PostgreSQL")
    df
  }

  /**
   * Extracts data from MySQL using JDBC.
   *
   * @param config Configuration including table/query, credentials
   * @param spark  SparkSession
   * @return DataFrame containing extracted data
   */
  def fromMySQL(config: Map[String, Any], spark: SparkSession): DataFrame = {
    logger.info("Extracting from MySQL")

    require(config.contains("table") || config.contains("query"), "Either 'table' or 'query' is required")

    val jdbcConfig = resolveJdbcCredentials(config, "mysql")

    val reader = spark.read
      .format("jdbc")
      .option("url", jdbcConfig.jdbcUrl)
      .option("user", jdbcConfig.username)
      .option("password", jdbcConfig.password)
      .option("driver", "com.mysql.cj.jdbc.Driver")

    // Add partitioning if specified
    val partitionedReader = config.get("partitionColumn") match {
      case Some(column) =>
        reader
          .option("partitionColumn", column.toString)
          .option("lowerBound", config.getOrElse("lowerBound", 0).toString)
          .option("upperBound", config.getOrElse("upperBound", 1000000).toString)
          .option("numPartitions", config.getOrElse("numPartitions", 10).toString)
      case None => reader
    }

    // Load by table or query
    val df = config.get("query") match {
      case Some(query) =>
        logger.info(s"Executing query: $query")
        partitionedReader.option("query", query.toString).load()
      case None =>
        val table = config("table").toString
        logger.info(s"Loading table: $table")
        partitionedReader.option("dbtable", table).load()
    }

    logger.info(s"Extracted ${df.count()} rows from MySQL")
    df
  }

  /**
   * Extracts data from Kafka.
   *
   * Supports both batch and streaming reads.
   *
   * @param config Configuration including topic, credentials
   * @param spark  SparkSession
   * @return DataFrame containing extracted data
   */
  def fromKafka(config: Map[String, Any], spark: SparkSession): DataFrame = {
    logger.info("Extracting from Kafka")

    require(config.contains("topic"), "'topic' is required")

    val kafkaConfig = resolveKafkaCredentials(config)
    val topic = config("topic").toString

    val reader = spark.read
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaConfig.get("bootstrap.servers").getOrElse("localhost:9092"))
      .option("subscribe", topic)

    // Add Kafka-specific options
    kafkaConfig.properties.foreach { case (key, value) =>
      if (key != "bootstrap.servers") {
        reader.option(s"kafka.$key", value)
      }
    }

    // Starting offsets
    val startingOffsets = config.getOrElse("startingOffsets", "earliest").toString
    reader.option("startingOffsets", startingOffsets)

    // Ending offsets (for batch reads)
    config.get("endingOffsets").foreach { offsets =>
      reader.option("endingOffsets", offsets.toString)
    }

    val df = reader.load()

    logger.info(s"Extracted data from Kafka topic: $topic")
    df
  }

  /**
   * Extracts data from S3.
   *
   * Supports multiple formats: parquet, json, csv, avro, orc.
   *
   * @param config Configuration including bucket, path, format, credentials
   * @param spark  SparkSession
   * @return DataFrame containing extracted data
   */
  def fromS3(config: Map[String, Any], spark: SparkSession): DataFrame = {
    logger.info("Extracting from S3")

    require(config.contains("bucket"), "'bucket' is required")
    require(config.contains("path"), "'path' is required")

    val iamConfig = resolveS3Credentials(config)

    // Configure S3A with IAM credentials
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", iamConfig.accessKeyId)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", iamConfig.secretAccessKey)
    iamConfig.sessionToken.foreach { token =>
      spark.sparkContext.hadoopConfiguration.set("fs.s3a.session.token", token)
    }
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.endpoint", s"s3.${iamConfig.region}.amazonaws.com")

    val bucket = config("bucket").toString
    val path = config("path").toString
    val format = config.getOrElse("format", "parquet").toString
    val s3Path = s"s3a://$bucket$path"

    logger.info(s"Reading from S3: $s3Path (format: $format)")

    val reader = spark.read.format(format)

    // Add format-specific options
    format.toLowerCase match {
      case "csv" =>
        reader
          .option("header", config.getOrElse("header", "true").toString)
          .option("inferSchema", config.getOrElse("inferSchema", "true").toString)
          .option("delimiter", config.getOrElse("delimiter", ",").toString)
      case "json" =>
        config.get("inferSchema").foreach(v => reader.option("inferSchema", v.toString))
        config.get("multiLine").foreach(v => reader.option("multiLine", v.toString))
      case _ => // parquet, avro, orc use defaults
    }

    val df = reader.load(s3Path)

    logger.info(s"Extracted ${df.count()} rows from S3")
    df
  }

  /**
   * Extracts data from DeltaLake.
   *
   * Supports time travel and version queries.
   *
   * @param config Configuration including path
   * @param spark  SparkSession
   * @return DataFrame containing extracted data
   */
  def fromDeltaLake(config: Map[String, Any], spark: SparkSession): DataFrame = {
    logger.info("Extracting from DeltaLake")

    require(config.contains("path"), "'path' is required")

    val path = config("path").toString

    val reader = spark.read.format("delta")

    // Time travel support
    val df = (config.get("version"), config.get("timestamp")) match {
      case (Some(version), _) =>
        logger.info(s"Reading DeltaLake at version: $version")
        reader.option("versionAsOf", version.toString).load(path)
      case (_, Some(timestamp)) =>
        logger.info(s"Reading DeltaLake at timestamp: $timestamp")
        reader.option("timestampAsOf", timestamp.toString).load(path)
      case _ =>
        logger.info(s"Reading latest DeltaLake version")
        reader.load(path)
    }

    logger.info(s"Extracted ${df.count()} rows from DeltaLake")
    df
  }

  /**
   * Resolves JDBC credentials from Vault or config.
   */
  private def resolveJdbcCredentials(config: Map[String, Any], credentialType: String): JdbcConfig = {
    config.get("credentialPath") match {
      case Some(path) =>
        logger.info(s"Resolving JDBC credentials from Vault: $path")
        val vaultClient = VaultClient.fromEnv()
        vaultClient.readSecret(path.toString) match {
          case Success(data) =>
            CredentialConfigFactory.create(credentialType, data).asInstanceOf[JdbcConfig]
          case Failure(ex) =>
            throw new RuntimeException(s"Failed to read credentials from Vault: ${ex.getMessage}", ex)
        }
      case None =>
        // Credentials provided directly in config (not recommended for production)
        logger.warn("Using credentials from config - not recommended for production")
        JdbcConfig(
          host = config("host").toString,
          port = config("port").toString.toInt,
          database = config("database").toString,
          username = config("username").toString,
          password = config("password").toString,
          credentialType = credentialType,
        )
    }
  }

  /**
   * Resolves S3 IAM credentials from Vault or config.
   */
  private def resolveS3Credentials(config: Map[String, Any]): IAMConfig = {
    config.get("credentialPath") match {
      case Some(path) =>
        logger.info(s"Resolving S3 credentials from Vault: $path")
        val vaultClient = VaultClient.fromEnv()
        vaultClient.readSecret(path.toString) match {
          case Success(data) =>
            CredentialConfigFactory.create("s3", data).asInstanceOf[IAMConfig]
          case Failure(ex) =>
            throw new RuntimeException(s"Failed to read credentials from Vault: ${ex.getMessage}", ex)
        }
      case None =>
        logger.warn("Using S3 credentials from config - not recommended for production")
        IAMConfig(
          accessKeyId = config("accessKeyId").toString,
          secretAccessKey = config("secretAccessKey").toString,
          sessionToken = config.get("sessionToken").map(_.toString),
          region = config.getOrElse("region", "us-east-1").toString,
        )
    }
  }

  /**
   * Resolves Kafka credentials from Vault or config.
   */
  private def resolveKafkaCredentials(config: Map[String, Any]): OtherConfig = {
    config.get("credentialPath") match {
      case Some(path) =>
        logger.info(s"Resolving Kafka credentials from Vault: $path")
        val vaultClient = VaultClient.fromEnv()
        vaultClient.readSecret(path.toString) match {
          case Success(data) =>
            CredentialConfigFactory.create("kafka", data).asInstanceOf[OtherConfig]
          case Failure(ex) =>
            throw new RuntimeException(s"Failed to read credentials from Vault: ${ex.getMessage}", ex)
        }
      case None =>
        // Return config as-is
        OtherConfig(config.map { case (k, v) => k -> v.toString })
    }
  }

  /**
   * Reads data from Avro files.
   *
   * @param config Configuration including path, inferSchema
   * @param spark  SparkSession
   * @return DataFrame containing Avro data
   */
  def fromAvro(config: Map[String, Any], spark: SparkSession): DataFrame = {
    logger.info("Extracting from Avro")

    require(config.contains("path"), "'path' is required")

    val path = config("path").toString
    AvroConverter.readAvro(path, config)(spark)
  }
}
