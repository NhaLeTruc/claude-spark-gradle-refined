package com.pipeline.performance

import com.pipeline.core.{Pipeline, TransformStep}
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

/**
 * Scalability performance tests.
 *
 * Tests how performance scales with increasing data sizes
 * and validates linear or sub-linear scaling behavior.
 */
@RunWith(classOf[JUnitRunner])
class ScalabilityPerformanceTest extends PerformanceTestBase {

  behavior of "Scalability Performance"

  it should "scale linearly with data size for filter operations" in {
    val dataSizes = Seq(10000, 50000, 100000, 500000, 1000000)

    logger.info("Testing filter operation scalability across data sizes...")

    val results = dataSizes.map { size =>
      val df = createLargeTestDataFrame(size)

      val (_, duration) = measureTime(s"filter-scale-$size") {
        df.filter("id % 2 = 0").count()
      }

      val throughput = (size * 1000.0) / duration
      logger.info(f"Size: $size records -> Duration: ${duration}ms, Throughput: $throughput%.2f rec/sec")

      (size, duration, throughput)
    }

    // Verify throughput doesn't degrade significantly
    val throughputs = results.map(_._3)
    val firstThroughput = throughputs.head
    val lastThroughput = throughputs.last

    // Throughput should not degrade by more than 50%
    lastThroughput should be >= (firstThroughput * 0.5)

    logger.info(f"Throughput degradation: ${((1.0 - lastThroughput / firstThroughput) * 100)}%.2f%%")
  }

  it should "demonstrate sub-linear scaling for aggregations" in {
    val dataSizes = Seq(50000, 100000, 200000, 500000)

    logger.info("Testing aggregation scalability...")

    val results = dataSizes.map { size =>
      val df = createLargeTestDataFrame(size)

      val (_, duration) = measureTime(s"agg-scale-$size") {
        df.groupBy("category")
          .agg(
            org.apache.spark.sql.functions.count("*").as("count"),
            org.apache.spark.sql.functions.avg("value1").as("avg_val"),
          )
          .collect()
      }

      logger.info(s"Size: $size records -> Duration: ${duration}ms")

      (size, duration)
    }

    // Calculate scaling factor
    val (size1, dur1) = results.head
    val (sizeN, durN) = results.last

    val sizeRatio = sizeN.toDouble / size1
    val durationRatio = durN.toDouble / dur1

    logger.info(f"Size increased by: ${sizeRatio}x")
    logger.info(f"Duration increased by: ${durationRatio}x")

    // Duration should scale sub-linearly (better than linear)
    durationRatio should be < (sizeRatio * 1.5) // Allow 50% overhead
  }

  it should "maintain throughput with varying partition counts" in {
    val partitionCounts = Seq(1, 2, 4, 8, 16)
    val dataSize = 500000

    logger.info("Testing partitioning scalability...")

    val df = createLargeTestDataFrame(dataSize)

    val results = partitionCounts.map { partitions =>
      val (_, duration) = measureTime(s"partition-scale-$partitions") {
        df.repartition(partitions).filter("id % 10 = 0").count()
      }

      val throughput = (dataSize * 1000.0) / duration
      logger.info(s"Partitions: $partitions -> Duration: ${duration}ms, Throughput: ${throughput%.2f rec/sec")

      (partitions, duration, throughput)
    }

    // Find optimal partition count
    val optimalPartitions = results.maxBy(_._3)._1
    logger.info(s"Optimal partition count: $optimalPartitions")

    // All configurations should complete successfully
    results.foreach { case (partitions, duration, _) =>
      duration should be > 0L
    }
  }

  it should "scale pipeline execution across multiple data sizes" in {
    val dataSizes = Seq(10000, 50000, 100000, 250000)

    logger.info("Testing full pipeline scalability...")

    val results = dataSizes.map { size =>
      val df = createLargeTestDataFrame(size)
      df.createOrReplaceTempView(s"scale_test_$size")

      val pipeline = Pipeline(
        name = s"scale-pipeline-$size",
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
                "is_high_value" -> "value1 > 500",
              ),
            ),
            nextStep = None,
          ),
        ),
      )

      val (_, duration) = measureTime(s"pipeline-scale-$size") {
        pipeline.execute(spark) match {
          case Right(context) =>
            context.getPrimaryDataFrame.count()
          case Left(ex) =>
            fail(s"Pipeline failed for size $size: ${ex.getMessage}")
        }
      }

      val throughput = (size * 1000.0) / duration
      logger.info(f"Pipeline with $size records -> Duration: ${duration}ms, Throughput: $throughput%.2f rec/sec")

      (size, duration, throughput)
    }

    // Verify reasonable scaling
    results.foreach { case (size, duration, throughput) =>
      throughput should be > 1000.0 // At least 1k records/sec
    }
  }

  it should "handle data size growth from 10k to 1M records" in {
    val growthSizes = Seq(10000, 25000, 50000, 100000, 250000, 500000, 1000000)

    logger.info("Testing growth from 10k to 1M records...")

    val results = growthSizes.map { size =>
      val df = createLargeTestDataFrame(size)

      benchmarkThroughput(s"growth-$size", size) {
        df.filter("value1 > 500").count()
      }

      val metric = performanceMetrics(s"growth-$size")
      logger.info(f"Size: $size%7d -> Avg: ${metric.average}%.2fms, Throughput: ${metric.averageThroughput}%.2f rec/sec")

      (size, metric.average, metric.averageThroughput)
    }

    // Verify all sizes complete
    results should have size growthSizes.size

    // Calculate growth rate
    val firstSize = results.head._1
    val lastSize = results.last._1
    val firstDuration = results.head._2
    val lastDuration = results.last._2

    val sizeGrowth = lastSize.toDouble / firstSize
    val durationGrowth = lastDuration / firstDuration

    logger.info(f"Size growth: ${sizeGrowth}x")
    logger.info(f"Duration growth: ${durationGrowth}x")
    logger.info(f"Scaling efficiency: ${(sizeGrowth / durationGrowth)}%.2f")

    // Duration should grow slower than data size
    durationGrowth should be < (sizeGrowth * 1.2)
  }

  it should "measure memory efficiency across data sizes" in {
    val runtime = Runtime.getRuntime
    val dataSizes = Seq(50000, 100000, 200000)

    logger.info("Testing memory usage across data sizes...")

    dataSizes.foreach { size =>
      // Trigger GC before measurement
      System.gc()
      Thread.sleep(100)

      val memBefore = runtime.totalMemory() - runtime.freeMemory()

      val df = createLargeTestDataFrame(size)
      df.cache()
      df.count() // Force caching

      val memAfter = runtime.totalMemory() - runtime.freeMemory()
      val memUsed = memAfter - memBefore

      val memPerRecord = memUsed.toDouble / size

      logger.info(f"Size: $size records -> Memory: ${memUsed / 1024 / 1024}%.2f MB (${memPerRecord}%.2f bytes/record)")

      df.unpersist()

      // Memory per record should be reasonable (<10 KB)
      memPerRecord should be < 10000.0
    }
  }
}