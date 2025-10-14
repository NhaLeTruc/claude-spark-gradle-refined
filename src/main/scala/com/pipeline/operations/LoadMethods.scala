package com.pipeline.operations

import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}
import org.slf4j.{Logger, LoggerFactory}
import com.pipeline.credentials.{CredentialConfigFactory, IAMConfig, JdbcConfig, OtherConfig, VaultClient}
import scala.util.{Failure, Success}

/**
 * Static methods for loading data to various sinks.
 *
 * Implements FR-005: Load to data sinks.
 * Implements FR-023: Support at least 5 load methods.
 * Validates Constitution Section V: Library-First Architecture.
 */
object LoadMethods {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Loads data to PostgreSQL.
   *
   * @param df     DataFrame to load
   * @param config Configuration including table, mode, credentials
   * @param spark  SparkSession
   */
  def toPostgres(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    logger.info("Loading to PostgreSQL")

    require(config.contains("table"), "'table' is required")

    val jdbcConfig = resolveJdbcCredentials(config, "postgres")
    val saveMode = parseSaveMode(config.getOrElse("mode", "append").toString)

    val writer = df.write
      .format("jdbc")
      .option("url", jdbcConfig.jdbcUrl)
      .option("user", jdbcConfig.username)
      .option("password", jdbcConfig.password)
      .option("dbtable", config("table").toString)
      .option("driver", "org.postgresql.Driver")
      .mode(saveMode)

    // Add batch size for performance
    val batchSize = config.getOrElse("batchSize", 1000).toString.toInt
    writer.option("batchsize", batchSize.toString)

    // Add additional JDBC properties if provided
    val finalWriter = jdbcConfig.properties.foldLeft(writer) { case (w, (key, value)) =>
      w.option(key, value)
    }

    finalWriter.save()
    logger.info(s"Loaded ${df.count()} rows to PostgreSQL table ${config("table")}")
  }

  /**
   * Loads data to MySQL.
   *
   * @param df     DataFrame to load
   * @param config Configuration including table, mode, credentials
   * @param spark  SparkSession
   */
  def toMySQL(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    logger.info("Loading to MySQL")

    require(config.contains("table"), "'table' is required")

    val jdbcConfig = resolveJdbcCredentials(config, "mysql")
    val saveMode = parseSaveMode(config.getOrElse("mode", "append").toString)

    val writer = df.write
      .format("jdbc")
      .option("url", jdbcConfig.jdbcUrl)
      .option("user", jdbcConfig.username)
      .option("password", jdbcConfig.password)
      .option("dbtable", config("table").toString)
      .option("driver", "com.mysql.cj.jdbc.Driver")
      .mode(saveMode)

    // Add batch size for performance
    val batchSize = config.getOrElse("batchSize", 1000).toString.toInt
    writer.option("batchsize", batchSize.toString)

    // Add additional JDBC properties if provided
    val finalWriter = jdbcConfig.properties.foldLeft(writer) { case (w, (key, value)) =>
      w.option(key, value)
    }

    finalWriter.save()
    logger.info(s"Loaded ${df.count()} rows to MySQL table ${config("table")}")
  }

  /**
   * Loads data to Kafka.
   *
   * @param df     DataFrame to load
   * @param config Configuration including topic, credentials
   * @param spark  SparkSession
   */
  def toKafka(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    logger.info("Loading to Kafka")

    require(config.contains("topic"), "'topic' is required")

    val kafkaConfig = resolveKafkaCredentials(config)

    // DataFrame must have 'key' and 'value' columns for Kafka
    // If not present, create them
    val kafkaDF = if (df.columns.contains("key") && df.columns.contains("value")) {
      df
    } else {
      import org.apache.spark.sql.functions._
      df.withColumn("key", lit(null).cast("string"))
        .withColumn("value", to_json(struct(df.columns.map(col): _*)))
    }

    val writer = kafkaDF.select("key", "value").write
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaConfig.getOrElse("bootstrap.servers", "localhost:9092").toString)
      .option("topic", config("topic").toString)

    // Add additional Kafka properties if provided
    val finalWriter = kafkaConfig.foldLeft(writer) { case (w, (key, value)) =>
      if (key != "bootstrap.servers") w.option(s"kafka.$key", value.toString)
      else w
    }

    finalWriter.save()
    logger.info(s"Loaded data to Kafka topic ${config("topic")}")
  }

  /**
   * Loads data to S3.
   *
   * @param df     DataFrame to load
   * @param config Configuration including bucket, path, format, credentials
   * @param spark  SparkSession
   */
  def toS3(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    logger.info("Loading to S3")

    require(config.contains("bucket"), "'bucket' is required")
    require(config.contains("path"), "'path' is required")

    val iamConfig = resolveS3Credentials(config)

    // Configure S3A credentials
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.access.key", iamConfig.accessKeyId)
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.secret.key", iamConfig.secretAccessKey)
    iamConfig.sessionToken.foreach { token =>
      spark.sparkContext.hadoopConfiguration.set("fs.s3a.session.token", token)
    }
    spark.sparkContext.hadoopConfiguration.set("fs.s3a.aws.credentials.provider", "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider")

    val format = config.getOrElse("format", "parquet").toString
    val saveMode = parseSaveMode(config.getOrElse("mode", "append").toString)
    val s3Path = s"s3a://${config("bucket")}${config("path")}"

    val writer = df.write.format(format).mode(saveMode)

    // Add partitioning if specified
    val finalWriter = config.get("partitionBy") match {
      case Some(columns: List[_]) => writer.partitionBy(columns.map(_.toString): _*)
      case Some(column: String) => writer.partitionBy(column)
      case _ => writer
    }

    finalWriter.save(s3Path)
    logger.info(s"Loaded data to S3 at $s3Path in $format format")
  }

  /**
   * Loads data to DeltaLake.
   *
   * @param df     DataFrame to load
   * @param config Configuration including path, mode
   * @param spark  SparkSession
   */
  def toDeltaLake(df: DataFrame, config: Map[String, Any], spark: SparkSession): Unit = {
    logger.info("Loading to DeltaLake")

    require(config.contains("path"), "'path' is required")

    val saveMode = parseSaveMode(config.getOrElse("mode", "append").toString)
    val path = config("path").toString

    val writer = df.write.format("delta").mode(saveMode)

    // Add partitioning if specified
    val partitionedWriter = config.get("partitionBy") match {
      case Some(columns: List[_]) => writer.partitionBy(columns.map(_.toString): _*)
      case Some(column: String) => writer.partitionBy(column)
      case _ => writer
    }

    // Add merge schema option if specified
    val finalWriter = config.get("mergeSchema") match {
      case Some(merge: Boolean) => partitionedWriter.option("mergeSchema", merge.toString)
      case Some(merge: String) => partitionedWriter.option("mergeSchema", merge)
      case _ => partitionedWriter
    }

    // Add overwrite schema option if specified
    val overwriteWriter = config.get("overwriteSchema") match {
      case Some(overwrite: Boolean) => finalWriter.option("overwriteSchema", overwrite.toString)
      case Some(overwrite: String) => finalWriter.option("overwriteSchema", overwrite)
      case _ => finalWriter
    }

    overwriteWriter.save(path)
    logger.info(s"Loaded data to DeltaLake at $path")
  }

  /**
   * Resolves JDBC credentials from Vault or config.
   */
  private def resolveJdbcCredentials(config: Map[String, Any], credentialType: String): JdbcConfig = {
    config.get("credentialPath") match {
      case Some(path) =>
        val vaultClient = VaultClient.fromEnv()
        vaultClient.readSecret(path.toString) match {
          case Success(data) =>
            CredentialConfigFactory.create(credentialType, data).asInstanceOf[JdbcConfig]
          case Failure(ex) =>
            throw new RuntimeException(s"Failed to read credentials from Vault: ${ex.getMessage}", ex)
        }
      case None =>
        // Fallback to credentials in config (not recommended for production)
        logger.warn("Using credentials from config file - not recommended for production")
        JdbcConfig(
          host = config.getOrElse("host", "localhost").toString,
          port = config.getOrElse("port", if (credentialType == "postgres") 5432 else 3306).toString.toInt,
          database = config.getOrElse("database", "").toString,
          username = config.getOrElse("username", "").toString,
          password = config.getOrElse("password", "").toString,
          credentialType = credentialType,
          properties = config.getOrElse("properties", Map.empty[String, String]).asInstanceOf[Map[String, String]]
        )
    }
  }

  /**
   * Resolves S3/IAM credentials from Vault or config.
   */
  private def resolveS3Credentials(config: Map[String, Any]): IAMConfig = {
    config.get("credentialPath") match {
      case Some(path) =>
        val vaultClient = VaultClient.fromEnv()
        vaultClient.readSecret(path.toString) match {
          case Success(data) =>
            CredentialConfigFactory.create("iam", data).asInstanceOf[IAMConfig]
          case Failure(ex) =>
            throw new RuntimeException(s"Failed to read S3 credentials from Vault: ${ex.getMessage}", ex)
        }
      case None =>
        // Fallback to credentials in config (not recommended for production)
        logger.warn("Using S3 credentials from config file - not recommended for production")
        IAMConfig(
          accessKeyId = config.getOrElse("accessKeyId", "").toString,
          secretAccessKey = config.getOrElse("secretAccessKey", "").toString,
          sessionToken = config.get("sessionToken").map(_.toString),
          region = config.getOrElse("region", "us-east-1").toString
        )
    }
  }

  /**
   * Resolves Kafka credentials from Vault or config.
   */
  private def resolveKafkaCredentials(config: Map[String, Any]): Map[String, Any] = {
    config.get("credentialPath") match {
      case Some(path) =>
        val vaultClient = VaultClient.fromEnv()
        vaultClient.readSecret(path.toString) match {
          case Success(data) => data
          case Failure(ex) =>
            throw new RuntimeException(s"Failed to read Kafka credentials from Vault: ${ex.getMessage}", ex)
        }
      case None =>
        // Use Kafka config directly
        config
    }
  }

  /**
   * Parses SaveMode from string.
   */
  private def parseSaveMode(mode: String): SaveMode = mode.toLowerCase match {
    case "append" => SaveMode.Append
    case "overwrite" => SaveMode.Overwrite
    case "errorifexists" => SaveMode.ErrorIfExists
    case "ignore" => SaveMode.Ignore
    case _ => throw new IllegalArgumentException(s"Invalid save mode: $mode. Must be one of: append, overwrite, errorifexists, ignore")
  }
}
