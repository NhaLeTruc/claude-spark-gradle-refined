package com.pipeline.core

import org.apache.avro.generic.GenericRecord
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.streaming.StreamingQuery
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

/**
 * Pipeline execution context that tracks the primary data flow and registered DataFrames.
 *
 * Supports multi-DataFrame operations for complex transformations (FR-007, FR-023).
 * Supports streaming query management for continuous processing (FR-009).
 * Implements Constitution Section V: Library-First Architecture.
 *
 * @param primary          Either Avro GenericRecord or Spark DataFrame representing the primary data flow
 * @param dataFrames       Named registry of DataFrames for multi-DataFrame operations
 * @param streamingQueries Named registry of StreamingQuery objects for lifecycle management
 * @param isStreamingMode  Flag indicating if pipeline is executing in streaming mode
 */
case class PipelineContext(
    primary: Either[GenericRecord, DataFrame],
    dataFrames: mutable.Map[String, DataFrame] = mutable.Map.empty,
    streamingQueries: mutable.Map[String, StreamingQuery] = mutable.Map.empty,
    isStreamingMode: Boolean = false,
) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Registers a DataFrame with a name for later retrieval.
   *
   * Used by ExtractStep with "registerAs" configuration.
   * Enables multi-DataFrame transformations like joins.
   *
   * @param name Name to register the DataFrame under
   * @param df   DataFrame to register
   * @return Updated context with registered DataFrame
   */
  def register(name: String, df: DataFrame): PipelineContext = {
    dataFrames.put(name, df)
    this
  }

  /**
   * Retrieves a registered DataFrame by name.
   *
   * Used by TransformStep with "inputDataFrames" configuration.
   *
   * @param name Name of the DataFrame to retrieve
   * @return Option containing the DataFrame if found
   */
  def get(name: String): Option[DataFrame] = {
    dataFrames.get(name)
  }

  /**
   * Updates the primary data flow.
   *
   * Preserves registered DataFrames in the registry.
   *
   * @param data New primary data (Avro or DataFrame)
   * @return Updated context with new primary data
   */
  def updatePrimary(data: Either[GenericRecord, DataFrame]): PipelineContext = {
    this.copy(primary = data)
  }

  /**
   * Gets the primary DataFrame.
   *
   * Throws exception if primary is not a DataFrame (Avro GenericRecord).
   *
   * @return Primary DataFrame
   */
  def getPrimaryDataFrame: DataFrame = {
    primary match {
      case Right(df) => df
      case Left(_)   => throw new IllegalStateException("Primary data is Avro GenericRecord, not DataFrame")
    }
  }

  /**
   * Checks if primary data is a DataFrame.
   *
   * @return True if primary is DataFrame, false if Avro
   */
  def isPrimaryDataFrame: Boolean = primary.isRight

  /**
   * Checks if primary data is Avro GenericRecord.
   *
   * @return True if primary is Avro, false if DataFrame
   */
  def isPrimaryAvro: Boolean = primary.isLeft

  /**
   * Gets all registered DataFrame names.
   *
   * @return Set of registered DataFrame names
   */
  def registeredNames: Set[String] = dataFrames.keySet.toSet

  /**
   * Clears all registered DataFrames.
   *
   * Primary data is preserved.
   *
   * @return Updated context with empty DataFrame registry
   */
  def clearRegistry(): PipelineContext = {
    dataFrames.clear()
    this
  }

  /**
   * Registers a streaming query for lifecycle management.
   *
   * Used by LoadStep when writing streams to sinks.
   *
   * @param name  Name to register the query under
   * @param query StreamingQuery instance
   * @return Updated context
   */
  def registerStreamingQuery(name: String, query: StreamingQuery): PipelineContext = {
    streamingQueries.put(name, query)
    logger.info(s"Registered streaming query: $name (id=${query.id})")
    this
  }

  /**
   * Retrieves a registered streaming query by name.
   *
   * @param name Name of the query to retrieve
   * @return Option containing the StreamingQuery if found
   */
  def getStreamingQuery(name: String): Option[StreamingQuery] = {
    streamingQueries.get(name)
  }

  /**
   * Gets all registered streaming query names.
   *
   * @return Set of registered query names
   */
  def streamingQueryNames: Set[String] = streamingQueries.keySet.toSet

  /**
   * Stops all running streaming queries.
   *
   * Called during pipeline shutdown or cancellation.
   */
  def stopAllStreams(): Unit = {
    if (streamingQueries.nonEmpty) {
      logger.info(s"Stopping ${streamingQueries.size} streaming queries")
      streamingQueries.values.foreach { query =>
        if (query.isActive) {
          logger.info(s"Stopping query: ${query.name} (id=${query.id})")
          query.stop()
        }
      }
      streamingQueries.clear()
    }
  }

  /**
   * Awaits termination of all streaming queries.
   *
   * @param timeout Optional timeout in milliseconds
   */
  def awaitTermination(timeout: Option[Long] = None): Unit = {
    if (streamingQueries.isEmpty) {
      logger.warn("No streaming queries to await")
      return
    }

    timeout match {
      case Some(ms) =>
        logger.info(s"Awaiting termination for ${ms}ms")
        streamingQueries.values.foreach(_.awaitTermination(ms))
      case None =>
        logger.info("Awaiting termination (indefinite)")
        streamingQueries.values.foreach(_.awaitTermination())
    }
  }

  /**
   * Checks if any streaming queries are currently active.
   *
   * @return True if at least one query is active
   */
  def hasActiveStreams: Boolean = {
    streamingQueries.values.exists(_.isActive)
  }
}

/**
 * Factory methods for PipelineContext creation.
 */
object PipelineContext {

  /**
   * Creates a context from a DataFrame.
   *
   * @param df Primary DataFrame
   * @return New PipelineContext
   */
  def fromDataFrame(df: DataFrame): PipelineContext = {
    PipelineContext(Right(df))
  }

  /**
   * Creates a context from an Avro GenericRecord.
   *
   * @param record Primary Avro record
   * @return New PipelineContext
   */
  def fromAvro(record: GenericRecord): PipelineContext = {
    PipelineContext(Left(record))
  }
}
