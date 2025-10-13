package com.pipeline.core

import org.apache.avro.generic.GenericRecord
import org.apache.spark.sql.DataFrame

import scala.collection.mutable

/**
 * Pipeline execution context that tracks the primary data flow and registered DataFrames.
 *
 * Supports multi-DataFrame operations for complex transformations (FR-007, FR-023).
 * Implements Constitution Section V: Library-First Architecture.
 *
 * @param primary     Either Avro GenericRecord or Spark DataFrame representing the primary data flow
 * @param dataFrames  Named registry of DataFrames for multi-DataFrame operations
 */
case class PipelineContext(
    primary: Either[GenericRecord, DataFrame],
    dataFrames: mutable.Map[String, DataFrame] = mutable.Map.empty,
) {

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
