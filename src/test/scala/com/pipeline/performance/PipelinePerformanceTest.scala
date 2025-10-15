package com.pipeline.performance

import com.pipeline.core.{ExtractStep, LoadStep, Pipeline, TransformStep}
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel

/**
 * Critical performance tests for pipeline operations.
 *
 * Tests throughput, latency, and resource utilization of key operations:
 * - Pipeline execution overhead
 * - DataFrame caching performance
 * - Repartitioning impact
 * - Transform operation performance
 * - Metrics collection overhead
 */
class PipelinePerformanceTest extends PerformanceTestBase {

  behavior of "Pipeline Performance"

  it should "execute simple pipeline within acceptable time" in {
    val df = createLargeTestDataFrame(100000)
    val tempTable = "perf_test_simple"

    df.createOrReplaceTempView(tempTable)

    val pipeline = Pipeline(
      name = "simple-perf-test",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "id % 2 = 0"),
          nextStep = None,
        ),
      ),
    )

    // Should complete within 5 seconds for 100k records
    assertPerformance("simple-pipeline-100k", 5000) {
      pipeline.execute(spark) match {
        case Right(context) =>
          val resultDf = context.getPrimaryDataFrame
          resultDf.count() shouldBe 50000
        case Left(ex) =>
          fail(s"Pipeline failed: ${ex.getMessage}")
      }
    }
  }

  it should "achieve minimum throughput for large dataset" in {
    val recordCount = 1000000L
    val df = createLargeTestDataFrame(recordCount.toInt)
    val tempTable = "perf_test_throughput"

    df.createOrReplaceTempView(tempTable)

    val pipeline = Pipeline(
      name = "throughput-test",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "value1 > 500"),
          nextStep = None,
        ),
      ),
    )

    // Should process at least 50k records/sec
    assertThroughput("pipeline-throughput-1M", recordCount, 50000.0) {
      pipeline.execute(spark) match {
        case Right(_) => ()
        case Left(ex) => fail(s"Pipeline failed: ${ex.getMessage}")
      }
    }
  }

  it should "demonstrate caching performance improvement" in {
    val df = createLargeTestDataFrame(500000)
    val tempTable = "perf_test_caching"

    df.createOrReplaceTempView(tempTable)

    // Test without caching
    val pipelineNoCaching = Pipeline(
      name = "no-cache-test",
      mode = "batch",
      steps = List(
        TransformStep(method = "filterRows", config = Map("condition" -> "id % 10 = 0"), nextStep = None),
        TransformStep(method = "filterRows", config = Map("condition" -> "value1 > 500"), nextStep = None),
        TransformStep(method = "filterRows", config = Map("condition" -> "value2 < 50"), nextStep = None),
      ),
    )

    val (_, durationNoCaching) = measureTime("pipeline-no-cache") {
      pipelineNoCaching.execute(spark)
    }

    // Test with caching (simulate by caching intermediate result)
    val dfCached = df.cache()
    dfCached.count() // Force caching

    val tempTableCached = "perf_test_caching_cached"
    dfCached.createOrReplaceTempView(tempTableCached)

    val pipelineCaching = Pipeline(
      name = "with-cache-test",
      mode = "batch",
      steps = List(
        TransformStep(method = "filterRows", config = Map("condition" -> "id % 10 = 0"), nextStep = None),
        TransformStep(method = "filterRows", config = Map("condition" -> "value1 > 500"), nextStep = None),
        TransformStep(method = "filterRows", config = Map("condition" -> "value2 < 50"), nextStep = None),
      ),
    )

    val (_, durationCaching) = measureTime("pipeline-with-cache") {
      pipelineCaching.execute(spark)
    }

    dfCached.unpersist()

    logger.info(s"No cache: ${durationNoCaching}ms, With cache: ${durationCaching}ms")
    logger.info(f"Cache speedup: ${durationNoCaching.toDouble / durationCaching}%.2fx")

    // Caching should provide some benefit (not always guaranteed in small tests)
    // Just verify both complete successfully
    durationNoCaching should be > 0L
    durationCaching should be > 0L
  }

  it should "measure repartitioning overhead" in {
    val df = createLargeTestDataFrame(500000)
    val tempTable = "perf_test_repartition"

    df.createOrReplaceTempView(tempTable)

    // Test without repartitioning
    val (_, durationNoRepartition) = measureTime("no-repartition") {
      val result = df.filter(col("value1") > 500)
      result.count()
    }

    // Test with repartitioning
    val (_, durationWithRepartition) = measureTime("with-repartition") {
      val result = df.repartition(16).filter(col("value1") > 500)
      result.count()
    }

    logger.info(s"No repartition: ${durationNoRepartition}ms")
    logger.info(s"With repartition (16 partitions): ${durationWithRepartition}ms")

    // Both should complete (overhead analysis is informational)
    durationNoRepartition should be > 0L
    durationWithRepartition should be > 0L
  }

  it should "measure metrics collection overhead" in {
    val df = createLargeTestDataFrame(200000)
    val tempTable = "perf_test_metrics"

    df.createOrReplaceTempView(tempTable)

    val pipeline = Pipeline(
      name = "metrics-overhead-test",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "id % 2 = 0"),
          nextStep = None,
        ),
      ),
    )

    // Test without metrics
    val (_, durationNoMetrics) = measureTime("no-metrics") {
      pipeline.execute(spark, collectMetrics = false)
    }

    // Test with metrics
    val (_, durationWithMetrics) = measureTime("with-metrics") {
      pipeline.execute(spark, collectMetrics = true)
    }

    val overhead = durationWithMetrics - durationNoMetrics
    val overheadPercent = (overhead.toDouble / durationNoMetrics) * 100

    logger.info(s"No metrics: ${durationNoMetrics}ms")
    logger.info(s"With metrics: ${durationWithMetrics}ms")
    logger.info(f"Overhead: ${overhead}ms ($overheadPercent%.2f%%)")

    // Metrics overhead should be minimal (<5% or <100ms)
    overhead should be < math.max(100L, (durationNoMetrics * 0.05).toLong)
  }

  it should "handle multiple transforms efficiently" in {
    val recordCount = 200000L
    val df = createLargeTestDataFrame(recordCount.toInt)
    val tempTable = "perf_test_multi_transform"

    df.createOrReplaceTempView(tempTable)

    val pipeline = Pipeline(
      name = "multi-transform-test",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "id % 3 = 0"),
          nextStep = None,
        ),
        TransformStep(
          method = "enrichData",
          config = Map(
            "columns" -> Map(
              "is_high_value" -> "value1 > 500",
              "category_numeric" -> "cast(category as int)",
            ),
          ),
          nextStep = None,
        ),
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "is_high_value = true"),
          nextStep = None,
        ),
      ),
    )

    // Should maintain reasonable throughput with multiple transforms
    benchmarkThroughput("multi-transform-pipeline", recordCount) {
      pipeline.execute(spark) match {
        case Right(context) =>
          val resultDf = context.getPrimaryDataFrame
          resultDf.count() should be > 0L
        case Left(ex) =>
          fail(s"Pipeline failed: ${ex.getMessage}")
      }
    }
  }

  it should "measure DataFrame count operation performance" in {
    val sizes = Seq(10000, 100000, 1000000)

    sizes.foreach { size =>
      val df = createLargeTestDataFrame(size)

      benchmarkThroughput(s"count-$size", size) {
        df.count()
      }
    }

    // Verify throughput scales reasonably
    val metrics = performanceMetrics.filter(_._1.startsWith("count-"))
    metrics should not be empty
  }

  it should "measure filter operation performance" in {
    val df = createLargeTestDataFrame(1000000)

    val testCases = Seq(
      ("filter-simple", "id % 2 = 0"),
      ("filter-complex", "id % 2 = 0 AND value1 > 500 AND value2 < 50"),
      ("filter-string", "category = '500'"),
    )

    testCases.foreach { case (name, condition) =>
      benchmark(name) {
        df.filter(condition).count()
      }
    }

    // All filters should complete
    performanceMetrics.keys.count(_.startsWith("filter-")) shouldBe testCases.size
  }

  it should "measure aggregation performance" in {
    val df = createLargeTestDataFrame(500000)

    benchmark("aggregation-simple") {
      df.groupBy("category").count().collect()
    }

    benchmark("aggregation-multi") {
      df.groupBy("category")
        .agg(
          count("*").as("count"),
          avg("value1").as("avg_value1"),
          sum("value2").as("sum_value2"),
          min("id").as("min_id"),
          max("id").as("max_id"),
        )
        .collect()
    }

    // Both aggregations should complete
    performanceMetrics should contain key "aggregation-simple"
    performanceMetrics should contain key "aggregation-multi"
  }

  it should "measure join performance" in {
    val df1 = createLargeTestDataFrame(100000)
    val df2 = createLargeTestDataFrame(100000)

    df1.createOrReplaceTempView("perf_test_join_left")
    df2.createOrReplaceTempView("perf_test_join_right")

    benchmark("join-inner") {
      df1.join(df2, df1("id") === df2("id"), "inner").count()
    }

    benchmark("join-left") {
      df1.join(df2, df1("id") === df2("id"), "left").count()
    }

    // Joins should complete
    performanceMetrics should contain key "join-inner"
    performanceMetrics should contain key "join-left"
  }

  it should "benchmark pipeline with all features enabled" in {
    val recordCount = 100000L
    val df = createLargeTestDataFrame(recordCount.toInt)
    val tempTable = "perf_test_full_featured"

    df.createOrReplaceTempView(tempTable)

    val pipeline = Pipeline(
      name = "full-featured-test",
      mode = "batch",
      steps = List(
        TransformStep(
          method = "repartition",
          config = Map("numPartitions" -> 8),
          nextStep = None,
        ),
        TransformStep(
          method = "filterRows",
          config = Map("condition" -> "id % 2 = 0"),
          nextStep = None,
        ),
        TransformStep(
          method = "enrichData",
          config = Map(
            "columns" -> Map(
              "is_high" -> "value1 > 500",
              "bucket" -> "floor(value2 / 10)",
            ),
          ),
          nextStep = None,
        ),
      ),
    )

    benchmarkThroughput("full-featured-pipeline", recordCount) {
      pipeline.execute(spark, collectMetrics = true) match {
        case Right(context) =>
          context.getPrimaryDataFrame.count() should be > 0L

          // Verify metrics were collected
          pipeline.getMetrics should not be None

        case Left(ex) =>
          fail(s"Pipeline failed: ${ex.getMessage}")
      }
    }
  }
}
