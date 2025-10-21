package com.pipeline.performance

import com.pipeline.core.{ExtractStep, Pipeline, TransformStep}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

/**
 * Latency-focused performance tests.
 *
 * Tests p50, p95, p99 latencies for critical operations
 * to ensure consistent performance and detect outliers.
 */
@RunWith(classOf[JUnitRunner])
class LatencyPerformanceTest extends PerformanceTestBase {

  behavior of "Latency Performance"

  it should "measure p50/p95/p99 latencies for pipeline execution" in {
    val df = createLargeTestDataFrame(50000)
    df.createOrReplaceTempView("latency_test_table")

    val pipeline = Pipeline(
      name = "latency-test-pipeline",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "id % 2 = 0"),
          nextStep = None,
        ),
      ),
    )

    // Run multiple iterations to collect latency distribution
    val iterations = 10
    logger.info(s"Running $iterations iterations to measure latency percentiles...")

    (1 to iterations).foreach { i =>
      benchmark(s"pipeline-latency-iter-$i") {
        pipeline.execute(spark) match {
          case Right(_) => ()
          case Left(ex) => fail(s"Pipeline failed on iteration $i: ${ex.getMessage}")
        }
      }
    }

    // Calculate percentiles from collected metrics
    val metrics = performanceMetrics.filter(_._1.startsWith("pipeline-latency-iter-"))
    metrics should not be empty

    val durations = metrics.values.flatMap(_.getMeasurements).toSeq.sorted
    val p50 = percentile(durations, 50)
    val p95 = percentile(durations, 95)
    val p99 = percentile(durations, 99)

    logger.info(s"Latency Percentiles:")
    logger.info(s"  p50: ${p50}ms")
    logger.info(s"  p95: ${p95}ms")
    logger.info(s"  p99: ${p99}ms")

    // Assert reasonable latency bounds
    p50 should be < 3000L // p50 should be under 3s
    p95 should be < 5000L // p95 should be under 5s
    p99 should be < 10000L // p99 should be under 10s
  }

  it should "measure filter operation latency distribution" in {
    val df = createLargeTestDataFrame(100000)

    val iterations = 20

    (1 to iterations).foreach { i =>
      benchmark(s"filter-latency-$i") {
        df.filter("id % 10 = 0").count()
      }
    }

    val metrics = performanceMetrics.filter(_._1.startsWith("filter-latency-"))
    val durations = metrics.values.flatMap(_.getMeasurements).toSeq.sorted

    val p50 = percentile(durations, 50)
    val p95 = percentile(durations, 95)

    logger.info(s"Filter Operation Latency:")
    logger.info(s"  p50: ${p50}ms")
    logger.info(s"  p95: ${p95}ms")

    // p95 should be within 2x of p50 for consistent performance
    p95 should be < (p50 * 2)
  }

  it should "measure aggregation latency with consistent p95" in {
    val df = createLargeTestDataFrame(200000)

    val iterations = 15

    (1 to iterations).foreach { i =>
      benchmark(s"agg-latency-$i") {
        df.groupBy("category")
          .count()
          .collect()
      }
    }

    val metrics = performanceMetrics.filter(_._1.startsWith("agg-latency-"))
    val durations = metrics.values.flatMap(_.getMeasurements).toSeq.sorted

    val p50 = percentile(durations, 50)
    val p95 = percentile(durations, 95)
    val p99 = percentile(durations, 99)

    logger.info(s"Aggregation Latency:")
    logger.info(s"  p50: ${p50}ms")
    logger.info(s"  p95: ${p95}ms")
    logger.info(s"  p99: ${p99}ms")

    // Verify stable performance (p95 not too far from p50)
    val p95_to_p50_ratio = p95.toDouble / p50
    logger.info(f"  p95/p50 ratio: $p95_to_p50_ratio%.2f")

    p95_to_p50_ratio should be < 3.0 // p95 should be less than 3x p50
  }

  it should "measure transform chain latency consistency" in {
    val df = createLargeTestDataFrame(100000)
    df.createOrReplaceTempView("transform_latency_test")

    val pipeline = Pipeline(
      name = "transform-chain-latency",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "id % 5 = 0"),
          nextStep = None,
        ),
        TransformStep(
          method = "enrichData",
          config = Map(
            "columns" -> Map(
              "is_high" -> "value1 > 500",
            ),
          ),
          nextStep = None,
        ),
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "is_high = true"),
          nextStep = None,
        ),
      ),
    )

    val iterations = 10

    (1 to iterations).foreach { i =>
      benchmark(s"transform-chain-$i") {
        pipeline.execute(spark) match {
          case Right(context) =>
            context.getPrimaryDataFrame.count()
          case Left(ex) =>
            fail(s"Pipeline failed: ${ex.getMessage}")
        }
      }
    }

    val metrics = performanceMetrics.filter(_._1.startsWith("transform-chain-"))
    val durations = metrics.values.flatMap(_.getMeasurements).toSeq.sorted

    val p50 = percentile(durations, 50)
    val p95 = percentile(durations, 95)

    logger.info(s"Transform Chain Latency:")
    logger.info(s"  p50: ${p50}ms")
    logger.info(s"  p95: ${p95}ms")

    // All measurements should be relatively consistent
    durations.foreach { duration =>
      duration should be < (p50 * 3) // No outliers beyond 3x p50
    }
  }

  /**
   * Helper to calculate percentile from sorted sequence.
   */
  private def percentile(sorted: Seq[Long], p: Int): Long = {
    if (sorted.isEmpty) return 0L
    val index = ((p / 100.0) * sorted.size).toInt
    sorted(math.min(index, sorted.size - 1))
  }
}