package com.pipeline.performance

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

/**
 * Base class for performance tests.
 *
 * Provides infrastructure for benchmarking pipeline operations,
 * measuring throughput, latency, and resource utilization.
 */
trait PerformanceTestBase extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  protected val logger: Logger = LoggerFactory.getLogger(getClass)

  // Spark session for tests
  protected var spark: SparkSession = _

  // Performance metrics collector
  protected val performanceMetrics = mutable.Map[String, PerformanceMetric]()

  /**
   * Sets up Spark before all tests.
   */
  override def beforeAll(): Unit = {
    super.beforeAll()
    logger.info("Setting up performance test environment...")

    // Create SparkSession with performance-friendly settings
    spark = SparkSession
      .builder()
      .appName("PerformanceTest")
      .master("local[*]")
      .config("spark.sql.shuffle.partitions", "8")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .config("spark.driver.memory", "2g")
      .config("spark.executor.memory", "2g")
      .config("spark.driver.bindAddress", "127.0.0.1")
      .config("spark.driver.host", "localhost")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    logger.info("SparkSession created for performance testing")
  }

  /**
   * Cleans up Spark after all tests.
   */
  override def afterAll(): Unit = {
    logger.info("Tearing down performance test environment...")

    // Print performance summary
    printPerformanceSummary()

    // Stop SparkSession
    if (spark != null) {
      spark.stop()
      logger.info("SparkSession stopped")
    }

    super.afterAll()
  }

  /**
   * Measures execution time of a code block.
   *
   * @param name Operation name
   * @param operation Code block to measure
   * @tparam T Return type
   * @return Tuple of (result, duration in milliseconds)
   */
  protected def measureTime[T](name: String)(operation: => T): (T, Long) = {
    val startTime = System.currentTimeMillis()
    val result = operation
    val duration = System.currentTimeMillis() - startTime

    logger.info(s"[$name] Completed in ${duration}ms")
    result -> duration
  }

  /**
   * Measures and records execution time.
   *
   * @param name Operation name
   * @param operation Code block to measure
   * @tparam T Return type
   * @return Operation result
   */
  protected def benchmark[T](name: String)(operation: => T): T = {
    val (result, duration) = measureTime(name)(operation)

    // Record metric
    performanceMetrics.get(name) match {
      case Some(metric) =>
        metric.addMeasurement(duration)
      case None =>
        val metric = new PerformanceMetric(name)
        metric.addMeasurement(duration)
        performanceMetrics(name) = metric
    }

    result
  }

  /**
   * Measures throughput (records per second).
   *
   * @param name Operation name
   * @param recordCount Number of records processed
   * @param operation Code block to measure
   * @tparam T Return type
   * @return Operation result
   */
  protected def benchmarkThroughput[T](name: String, recordCount: Long)(operation: => T): T = {
    val (result, duration) = measureTime(name)(operation)

    val throughput = if (duration > 0) {
      (recordCount * 1000.0) / duration
    } else {
      0.0
    }

    logger.info(f"[$name] Throughput: $throughput%.2f records/sec ($recordCount records in ${duration}ms)")

    // Record metric with throughput
    performanceMetrics.get(name) match {
      case Some(metric) =>
        metric.addMeasurement(duration, throughput)
      case None =>
        val metric = new PerformanceMetric(name)
        metric.addMeasurement(duration, throughput)
        performanceMetrics(name) = metric
    }

    result
  }

  /**
   * Asserts that operation completes within time limit.
   *
   * @param name Operation name
   * @param maxDurationMs Maximum allowed duration in milliseconds
   * @param operation Code block to measure
   * @tparam T Return type
   * @return Operation result
   */
  protected def assertPerformance[T](name: String, maxDurationMs: Long)(operation: => T): T = {
    val (result, duration) = measureTime(name)(operation)

    duration should be <= maxDurationMs

    logger.info(s"[$name] Performance assertion passed: ${duration}ms <= ${maxDurationMs}ms")

    result
  }

  /**
   * Asserts minimum throughput.
   *
   * @param name Operation name
   * @param recordCount Number of records
   * @param minThroughput Minimum records per second
   * @param operation Code block to measure
   * @tparam T Return type
   * @return Operation result
   */
  protected def assertThroughput[T](
      name: String,
      recordCount: Long,
      minThroughput: Double,
  )(operation: => T): T = {
    val (result, duration) = measureTime(name)(operation)

    val actualThroughput = if (duration > 0) {
      (recordCount * 1000.0) / duration
    } else {
      Double.MaxValue
    }

    actualThroughput should be >= minThroughput

    logger.info(
      f"[$name] Throughput assertion passed: $actualThroughput%.2f >= $minThroughput%.2f records/sec",
    )

    result
  }

  /**
   * Creates a test DataFrame with specified size.
   *
   * @param rows Number of rows
   * @param columns Number of columns
   * @return Test DataFrame
   */
  protected def createTestDataFrame(rows: Int, columns: Int = 5): DataFrame = {
    val data = (1 to rows).map { i =>
      val rowValues = (0 until columns).map(c => s"value_${i}_$c").toSeq
      org.apache.spark.sql.Row.fromSeq(i +: rowValues)
    }

    val schema = org.apache.spark.sql.types.StructType(
      org.apache.spark.sql.types.StructField("id", org.apache.spark.sql.types.IntegerType, nullable = false) +:
        (0 until columns).map(i =>
          org.apache.spark.sql.types.StructField(s"col$i", org.apache.spark.sql.types.StringType, nullable = true)
        )
    )

    spark.createDataFrame(spark.sparkContext.parallelize(data), schema)
  }

  /**
   * Creates a large test DataFrame for performance testing.
   *
   * @param rows Number of rows (default: 1 million)
   * @return Test DataFrame
   */
  protected def createLargeTestDataFrame(rows: Int = 1000000): DataFrame = {
    spark.range(rows)
      .selectExpr(
        "id",
        "cast(id % 1000 as string) as category",
        "cast(rand() * 1000 as double) as value1",
        "cast(rand() * 100 as double) as value2",
        "cast(current_timestamp() as timestamp) as created_at",
      )
  }

  /**
   * Prints performance summary.
   */
  protected def printPerformanceSummary(): Unit = {
    if (performanceMetrics.nonEmpty) {
      logger.info("=" * 80)
      logger.info("PERFORMANCE TEST SUMMARY")
      logger.info("=" * 80)

      performanceMetrics.toSeq.sortBy(_._1).foreach { case (name, metric) =>
        logger.info(f"$name:")
        logger.info(f"  Runs: ${metric.count}")
        logger.info(f"  Avg: ${metric.average}%.2fms")
        logger.info(f"  Min: ${metric.min}ms")
        logger.info(f"  Max: ${metric.max}ms")
        logger.info(f"  Total: ${metric.total}ms")
        if (metric.hasThroughput) {
          logger.info(f"  Avg Throughput: ${metric.averageThroughput}%.2f records/sec")
        }
        logger.info("")
      }

      logger.info("=" * 80)
    }
  }

  /**
   * Clears performance metrics.
   */
  protected def clearMetrics(): Unit = {
    performanceMetrics.clear()
  }
}

/**
 * Performance metric tracker.
 */
class PerformanceMetric(val name: String) {
  private val measurements = mutable.ListBuffer[Long]()
  private val throughputs = mutable.ListBuffer[Double]()

  def addMeasurement(durationMs: Long, throughput: Double = 0.0): Unit = {
    measurements += durationMs
    if (throughput > 0) {
      throughputs += throughput
    }
  }

  def count: Int = measurements.size

  def total: Long = measurements.sum

  def average: Double = if (measurements.nonEmpty) measurements.sum.toDouble / measurements.size else 0.0

  def min: Long = if (measurements.nonEmpty) measurements.min else 0L

  def max: Long = if (measurements.nonEmpty) measurements.max else 0L

  def hasThroughput: Boolean = throughputs.nonEmpty

  def averageThroughput: Double = if (throughputs.nonEmpty) throughputs.sum / throughputs.size else 0.0

  def p50: Long = percentile(50)

  def p95: Long = percentile(95)

  def p99: Long = percentile(99)

  def getMeasurements: Seq[Long] = measurements.toSeq

  private def percentile(p: Int): Long = {
    if (measurements.isEmpty) return 0L
    val sorted = measurements.sorted
    val index = (p / 100.0 * sorted.size).toInt
    sorted(math.min(index, sorted.size - 1))
  }
}
