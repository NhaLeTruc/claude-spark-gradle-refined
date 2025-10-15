package com.pipeline.integration

import org.apache.spark.sql.SparkSession
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}
import org.testcontainers.containers.{GenericContainer, PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName

/**
 * Base class for integration tests with Testcontainers support.
 *
 * Implements Sprint 1-2 Task 1.2: Integration Testing Suite.
 * Provides shared infrastructure for E2E pipeline tests.
 */
trait IntegrationTestBase extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  // Spark session for tests
  protected var spark: SparkSession = _

  // Testcontainers
  protected var postgresContainer: PostgreSQLContainer[_] = _
  protected var vaultContainer: GenericContainer[_] = _

  /**
   * Sets up Spark and containers before all tests.
   */
  override def beforeAll(): Unit = {
    super.beforeAll()
    logger.info("Setting up integration test environment...")

    // Create SparkSession
    spark = SparkSession
      .builder()
      .appName("IntegrationTest")
      .master("local[*]")
      .config("spark.sql.shuffle.partitions", "4")
      .config("spark.sql.warehouse.dir", "/tmp/spark-warehouse")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.driver.host", "localhost")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    logger.info("SparkSession created")

    // Start Postgres container
    startPostgresContainer()

    // Start Vault container
    startVaultContainer()

    logger.info("Integration test environment ready")
  }

  /**
   * Cleans up Spark and containers after all tests.
   */
  override def afterAll(): Unit = {
    logger.info("Tearing down integration test environment...")

    // Stop SparkSession
    if (spark != null) {
      spark.stop()
      logger.info("SparkSession stopped")
    }

    // Stop containers
    stopPostgresContainer()
    stopVaultContainer()

    super.afterAll()
    logger.info("Integration test environment cleaned up")
  }

  /**
   * Cleans up test data before each test.
   */
  override def beforeEach(): Unit = {
    super.beforeEach()
    cleanupTestData()
  }

  /**
   * Starts PostgreSQL container for testing.
   */
  private def startPostgresContainer(): Unit = {
    try {
      logger.info("Starting PostgreSQL container...")
      postgresContainer = new PostgreSQLContainer(
        DockerImageName.parse("postgres:15-alpine")
      )
      postgresContainer.withDatabaseName("testdb")
      postgresContainer.withUsername("testuser")
      postgresContainer.withPassword("testpass")
      postgresContainer.start()

      logger.info(s"PostgreSQL container started: ${postgresContainer.getJdbcUrl}")
    } catch {
      case ex: Exception =>
        logger.warn(s"Could not start PostgreSQL container: ${ex.getMessage}")
        logger.warn("Integration tests requiring PostgreSQL will be skipped")
    }
  }

  /**
   * Starts Vault container for testing.
   */
  private def startVaultContainer(): Unit = {
    try {
      logger.info("Starting Vault container...")
      vaultContainer = new GenericContainer(DockerImageName.parse("hashicorp/vault:1.15"))
      vaultContainer.withEnv("VAULT_DEV_ROOT_TOKEN_ID", "dev-token")
      vaultContainer.withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
      vaultContainer.withCommand("server", "-dev")
      vaultContainer.withExposedPorts(8200)
      vaultContainer.start()

      val vaultAddr = s"http://${vaultContainer.getHost}:${vaultContainer.getMappedPort(8200)}"
      logger.info(s"Vault container started: $vaultAddr")

      // Set environment variables for tests
      System.setProperty("VAULT_ADDR", vaultAddr)
      System.setProperty("VAULT_TOKEN", "dev-token")
    } catch {
      case ex: Exception =>
        logger.warn(s"Could not start Vault container: ${ex.getMessage}")
        logger.warn("Integration tests requiring Vault will be skipped")
    }
  }

  /**
   * Stops PostgreSQL container.
   */
  private def stopPostgresContainer(): Unit = {
    if (postgresContainer != null) {
      try {
        postgresContainer.stop()
        logger.info("PostgreSQL container stopped")
      } catch {
        case ex: Exception =>
          logger.warn(s"Error stopping PostgreSQL container: ${ex.getMessage}")
      }
    }
  }

  /**
   * Stops Vault container.
   */
  private def stopVaultContainer(): Unit = {
    if (vaultContainer != null) {
      try {
        vaultContainer.stop()
        logger.info("Vault container stopped")
      } catch {
        case ex: Exception =>
          logger.warn(s"Error stopping Vault container: ${ex.getMessage}")
      }
    }
  }

  /**
   * Cleans up test data between tests.
   */
  private def cleanupTestData(): Unit = {
    // Clean up temp directories
    val tempDirs = Seq(
      "/tmp/test-output",
      "/tmp/test-delta",
      "/tmp/test-checkpoints",
    )

    tempDirs.foreach { dir =>
      try {
        val file = new java.io.File(dir)
        if (file.exists()) {
          deleteRecursively(file)
        }
      } catch {
        case ex: Exception =>
          logger.warn(s"Could not clean up directory $dir: ${ex.getMessage}")
      }
    }
  }

  /**
   * Recursively deletes a directory.
   */
  private def deleteRecursively(file: java.io.File): Unit = {
    if (file.isDirectory) {
      file.listFiles().foreach(deleteRecursively)
    }
    file.delete()
  }

  /**
   * Gets PostgreSQL JDBC URL for tests.
   */
  protected def getPostgresJdbcUrl: String = {
    if (postgresContainer != null && postgresContainer.isRunning) {
      postgresContainer.getJdbcUrl
    } else {
      throw new IllegalStateException("PostgreSQL container is not running")
    }
  }

  /**
   * Gets PostgreSQL connection properties.
   */
  protected def getPostgresProperties: Map[String, Any] = {
    if (postgresContainer != null && postgresContainer.isRunning) {
      Map(
        "host" -> postgresContainer.getHost,
        "port" -> postgresContainer.getMappedPort(5432).toString,
        "database" -> postgresContainer.getDatabaseName,
        "username" -> postgresContainer.getUsername,
        "password" -> postgresContainer.getPassword,
      )
    } else {
      throw new IllegalStateException("PostgreSQL container is not running")
    }
  }

  /**
   * Gets Vault address for tests.
   */
  protected def getVaultAddress: String = {
    if (vaultContainer != null && vaultContainer.isRunning) {
      s"http://${vaultContainer.getHost}:${vaultContainer.getMappedPort(8200)}"
    } else {
      throw new IllegalStateException("Vault container is not running")
    }
  }

  /**
   * Gets Vault token for tests.
   */
  protected def getVaultToken: String = "dev-token"

  /**
   * Creates a test table in PostgreSQL.
   */
  protected def createTestTable(tableName: String, schema: String): Unit = {
    if (postgresContainer != null && postgresContainer.isRunning) {
      import java.sql.DriverManager

      val connection = DriverManager.getConnection(
        getPostgresJdbcUrl,
        postgresContainer.getUsername,
        postgresContainer.getPassword,
      )

      try {
        val statement = connection.createStatement()
        statement.execute(s"DROP TABLE IF EXISTS $tableName")
        statement.execute(schema)
        logger.info(s"Created test table: $tableName")
      } finally {
        connection.close()
      }
    }
  }

  /**
   * Inserts test data into PostgreSQL.
   */
  protected def insertTestData(tableName: String, data: Seq[Map[String, Any]]): Unit = {
    if (postgresContainer != null && postgresContainer.isRunning && data.nonEmpty) {
      import java.sql.DriverManager

      val connection = DriverManager.getConnection(
        getPostgresJdbcUrl,
        postgresContainer.getUsername,
        postgresContainer.getPassword,
      )

      try {
        val columns = data.head.keys.toSeq
        val placeholders = columns.map(_ => "?").mkString(", ")
        val sql = s"INSERT INTO $tableName (${columns.mkString(", ")}) VALUES ($placeholders)"

        val statement = connection.prepareStatement(sql)

        data.foreach { row =>
          columns.zipWithIndex.foreach { case (col, idx) =>
            statement.setObject(idx + 1, row(col))
          }
          statement.addBatch()
        }

        statement.executeBatch()
        logger.info(s"Inserted ${data.size} rows into $tableName")
      } finally {
        connection.close()
      }
    }
  }

  /**
   * Stores a secret in Vault for testing.
   */
  protected def storeVaultSecret(path: String, data: Map[String, Any]): Unit = {
    if (vaultContainer != null && vaultContainer.isRunning) {
      import com.pipeline.credentials.VaultClient

      val vaultClient = VaultClient(getVaultAddress, getVaultToken)
      vaultClient.writeSecret(path, data) match {
        case scala.util.Success(_) =>
          logger.info(s"Stored Vault secret: $path")
        case scala.util.Failure(ex) =>
          logger.error(s"Failed to store Vault secret: $path", ex)
          throw ex
      }
    }
  }

  /**
   * Checks if Docker is available for testing.
   */
  protected def isDockerAvailable: Boolean = {
    try {
      val process = Runtime.getRuntime.exec("docker info")
      process.waitFor() == 0
    } catch {
      case _: Exception => false
    }
  }

  /**
   * Skips test if Docker is not available.
   */
  protected def requireDocker(): Unit = {
    assume(isDockerAvailable, "Docker is required for this test")
  }
}
