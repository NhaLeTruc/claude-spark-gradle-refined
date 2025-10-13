# Research & Technical Decisions

**Feature**: Data Pipeline Orchestration Application
**Date**: 2025-10-13
**Branch**: 001-build-an-application

This document captures research findings and technical decisions made during Phase 0 planning to resolve unknowns and establish architectural patterns.

## Build Tool & Dependency Management

### Decision: Gradle 8.5+ with Scala Plugin

**Rationale**:
- User explicitly specified Gradle (vs Maven or SBT)
- Gradle 8.5+ supports Scala 2.12 through scala plugin
- Compatible with Spark 3.5.6 ecosystem
- Minimal library approach aligns with user requirements

**Alternatives Considered**:
- **SBT**: More Scala-native but user specified Gradle
- **Maven**: Verbose XML configuration, slower than Gradle

**Configuration Approach**:
```gradle
plugins {
    id 'scala'
    id 'application'
}

scala {
    zincVersion = '1.9.0'
}

sourceCompatibility = JavaVersion.VERSION_11
targetCompatibility = JavaVersion.VERSION_11
```

**Key Dependencies** (minimal set):
- Spark 3.5.6 (brings Jackson, Netty, Guava transitively - reuse these)
- ScalaTest 3.2.17 (testing only)
- Testcontainers 1.19.3 (integration testing only)
- Vault client library (only new operational dependency)

### Dual Execution Mode: CLI and spark-submit

**Decision**: Support both local CLI execution and cluster spark-submit deployment

**Implementation Strategy**:

1. **Shadow/Uber-JAR Plugin**: Use Gradle Shadow plugin for fat JAR creation
```gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

shadowJar {
    archiveBaseName.set('pipeline-app')
    archiveClassifier.set('all')
    mergeServiceFiles()  // Merge META-INF/services for SPI

    // Relocate conflicting dependencies if needed
    relocate 'com.google.common', 'shaded.guava'
}
```

2. **Dependency Scopes**:
   - **CLI Mode (standard jar)**: Include all dependencies (Spark embedded)
   - **Cluster Mode (uber-jar)**: Mark Spark as 'provided' scope, include only application code + non-Spark dependencies

```gradle
configurations {
    spark {
        // Spark dependencies provided by cluster
        transitive = true
    }
}

dependencies {
    // Spark provided in cluster mode, embedded in CLI mode
    implementation('org.apache.spark:spark-core_2.12:3.5.6')
    implementation('org.apache.spark:spark-sql_2.12:3.5.6')

    // Always included (not provided by Spark cluster)
    implementation('com.bettercloud:vault-java-driver:5.1.0')
    implementation('org.postgresql:postgresql:42.6.0')
    implementation('com.mysql:mysql-connector-j:8.2.0')
}

// Create two JARs: one for CLI, one for spark-submit
task cliJar(type: Jar) {
    from sourceSets.main.output
    archiveClassifier.set('cli')
    // Includes all dependencies
}

shadowJar {
    // Excludes Spark (provided by cluster)
    dependencies {
        exclude(dependency('org.apache.spark:.*'))
    }
}
```

3. **Main Class Detection**:
```scala
object PipelineRunner {
  def main(args: Array[String]): Unit = {
    // Detect execution mode
    val spark = if (isClusterMode) {
      // spark-submit provides SparkSession
      SparkSession.builder().getOrCreate()
    } else {
      // CLI creates local SparkSession
      SparkSession.builder()
        .appName("Pipeline-Local")
        .master("local[*]")
        .config("spark.driver.host", "localhost")
        .getOrCreate()
    }

    // Parse args and execute pipeline
    val configPath = args(0)
    val pipeline = Pipeline.fromConfig(configPath, spark)
    pipeline.main(spark)

    if (!isClusterMode) {
      spark.stop()
    }
  }

  private def isClusterMode: Boolean = {
    // Check if running in cluster (spark-submit sets this)
    sys.env.contains("SPARK_MASTER") ||
    sys.props.contains("spark.master") &&
    !sys.props("spark.master").startsWith("local")
  }
}
```

**Usage Examples**:

```bash
# CLI Mode (local execution)
java -jar build/libs/pipeline-app-cli.jar config/pipeline.json

# Or via Gradle
./gradlew run --args="config/pipeline.json"

# Cluster Mode (spark-submit)
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master spark://cluster:7077 \
  --deploy-mode cluster \
  --executor-memory 4G \
  --total-executor-cores 16 \
  --conf spark.driver.extraJavaOptions="-DVAULT_ADDR=https://vault.prod" \
  --conf spark.executor.extraJavaOptions="-DVAULT_TOKEN=$VAULT_TOKEN" \
  build/libs/pipeline-app-all.jar \
  s3://configs/production-pipeline.json
```

**Alternatives Considered**:
- **Separate CLI and Spark Projects**: Duplication, harder to maintain
- **Always Use spark-submit**: Overkill for local development
- **Assembly Plugin**: Shadow plugin more feature-rich for dependency conflicts

## Credential Configuration Pattern

### Decision: Factory Pattern with Sealed Trait Hierarchy

**Rationale**:
- Factory pattern recommended in spec assumption A-011
- Sealed trait ensures compile-time exhaustiveness checking
- Type-safe credential retrieval from Vault
- Single responsibility: each config type handles one credential category

**Implementation Pattern**:
```scala
sealed trait CredentialConfig {
  def fetchFromVault(client: VaultClient, path: String): Map[String, String]
}

object CredentialConfigFactory {
  def create(configType: String): CredentialConfig = configType match {
    case "jdbc" => new JdbcConfig()
    case "iam" => new IAMConfig()
    case "other" => new OtherConfig()
    case _ => throw new IllegalArgumentException(s"Unknown config type: $configType")
  }
}

class JdbcConfig extends CredentialConfig {
  override def fetchFromVault(client: VaultClient, path: String): Map[String, String] = {
    // Fetch and validate JDBC-specific keys: host, port, database, username, password
  }
}

class IAMConfig extends CredentialConfig {
  override def fetchFromVault(client: VaultClient, path: String): Map[String, String] = {
    // Fetch IAM-specific keys: accessKey, secretKey, region, sessionToken (optional)
  }
}

class OtherConfig extends CredentialConfig {
  override def fetchFromVault(client: VaultClient, path: String): Map[String, String] = {
    // Fetch Kafka/DeltaLake keys: varies by service
  }
}
```

**Alternatives Considered**:
- **Strategy Pattern**: Similar outcome but factory clearer for creation logic
- **Type Classes**: Over-engineered for this use case
- **Simple Inheritance**: Less flexible than sealed trait for pattern matching

## Chain of Responsibility Implementation

### Decision: Trait-Based Chaining with Option[PipelineStep]

**Rationale**:
- Scala's Option type provides safe null handling
- Trait allows mix-ins and interface segregation
- Immutable design supports retry logic (re-execute same chain)
- Type-safe pipeline composition

**Implementation Pattern**:
```scala
trait PipelineStep {
  def execute(data: Either[GenericRecord, DataFrame],
              spark: SparkSession): Either[GenericRecord, DataFrame]

  def nextStep: Option[PipelineStep]

  def executeChain(data: Either[GenericRecord, DataFrame],
                   spark: SparkSession): Either[GenericRecord, DataFrame] = {
    val result = execute(data, spark)
    nextStep match {
      case Some(step) => step.executeChain(result, spark)
      case None => result
    }
  }
}

case class ExtractStep(method: String, config: Map[String, Any],
                       nextStep: Option[PipelineStep]) extends PipelineStep {
  override def execute(data: Either[GenericRecord, DataFrame],
                       spark: SparkSession): Either[GenericRecord, DataFrame] = {
    // Call ExtractMethods static method based on `method` string
    val extractedData = ExtractMethods.invokeMethod(method, config, spark)
    Right(extractedData) // Returns DataFrame
  }
}

case class TransformStep(method: String, params: Map[String, Any],
                        nextStep: Option[PipelineStep]) extends PipelineStep {
  override def execute(data: Either[GenericRecord, DataFrame],
                       spark: SparkSession): Either[GenericRecord, DataFrame] = {
    val df = data.getOrElse(throw new IllegalStateException("Expected DataFrame"))
    val transformed = UserMethods.invokeMethod(method, df, params)
    Right(transformed)
  }
}
```

**Alternatives Considered**:
- **Null References**: Unsafe, violates Scala idioms
- **Monadic Chains**: Over-engineered for linear pipeline
- **Akka Streams**: Heavy dependency, overkill for single-node processing

### Multi-DataFrame Support Enhancement

**Decision**: Enhance Chain of Responsibility with PipelineContext for multi-DataFrame operations

**Rationale**:
- Complex transformations require multiple DataFrames (joins, unions, lookups)
- User requested: "TransformStep can receives and produces multiple spark dataframes"
- Named DataFrame registry enables flexible multi-source operations
- Maintains backward compatibility with single DataFrame flow

**Implementation Pattern**:
```scala
case class PipelineContext(
  primary: Either[GenericRecord, DataFrame],  // Main data flow
  dataFrames: mutable.Map[String, DataFrame] = mutable.Map.empty  // Named registry
) {
  def register(name: String, df: DataFrame): PipelineContext
  def get(name: String): Option[DataFrame]
  def updatePrimary(data: Either[GenericRecord, DataFrame]): PipelineContext
}

// Updated PipelineStep trait
sealed trait PipelineStep {
  def execute(context: PipelineContext, spark: SparkSession): PipelineContext
  def executeChain(context: PipelineContext, spark: SparkSession): PipelineContext
}

// Extract step with registration
case class ExtractStep(...) extends PipelineStep {
  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    val df = ExtractMethods.invokeMethod(method, config, spark)

    // Register if name provided
    val updated = config.get("registerAs") match {
      case Some(name) => context.register(name.toString, df)
      case None => context
    }

    updated.updatePrimary(Right(df))
  }
}

// Transform step with multi-DataFrame input
case class TransformStep(...) extends PipelineStep {
  override def execute(context: PipelineContext, spark: SparkSession): PipelineContext = {
    context.primary match {
      case Right(primaryDf) =>
        // Resolve additional DataFrames from registry
        val enrichedConfig = config.get("inputDataFrames") match {
          case Some(names: List[String]) =>
            val resolvedDFs = names.flatMap(n => context.get(n).map(df => n -> df)).toMap
            config ++ Map("resolvedDataFrames" -> resolvedDFs)
          case _ => config
        }

        val transformed = UserMethods.invokeMethod(method, primaryDf, enrichedConfig, context)
        context.updatePrimary(Right(transformed))
    }
  }
}
```

**Configuration Example**:
```json
{
  "steps": [
    {
      "stepType": "extract",
      "method": "fromPostgres",
      "config": {
        "table": "orders",
        "registerAs": "orders"
      }
    },
    {
      "stepType": "extract",
      "method": "fromPostgres",
      "config": {
        "table": "customers",
        "registerAs": "customers"
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

**Multi-DataFrame UserMethods**:
```scala
object UserMethods {
  def joinDataFrames(primaryDf: DataFrame, params: Map[String, Any],
                     context: PipelineContext): DataFrame = {
    val resolvedDFs = params("resolvedDataFrames").asInstanceOf[Map[String, DataFrame]]
    val rightDf = resolvedDFs.values.head  // Get registered DataFrame
    val joinKey = params("joinKey").toString
    val joinType = params.getOrElse("joinType", "inner").toString

    primaryDf.join(rightDf, Seq(joinKey), joinType)
  }

  def unionDataFrames(primaryDf: DataFrame, params: Map[String, Any],
                      context: PipelineContext): DataFrame = {
    val resolvedDFs = params("resolvedDataFrames").asInstanceOf[Map[String, DataFrame]]
    resolvedDFs.values.foldLeft(primaryDf)((acc, df) => acc.union(df))
  }

  // Signature update for all methods
  def invokeMethod(methodName: String, df: DataFrame, params: Map[String, Any],
                   context: PipelineContext): DataFrame = {
    methodName match {
      case "joinDataFrames" => joinDataFrames(df, params, context)
      case "unionDataFrames" => unionDataFrames(df, params, context)
      case "filterRows" => filterRows(df, params)  // Single-DF methods ignore context
      // ... other methods
    }
  }
}
```

**Benefits**:
- Supports complex ETL patterns (star schema joins, dimension lookups)
- Maintains single primary data flow for simple pipelines
- Named registry provides clear DataFrame provenance
- Backward compatible (registerAs and inputDataFrames are optional)

**Alternatives Considered**:
- **Multiple Primary Flows**: Complicates Chain of Responsibility pattern
- **External DataFrame Cache**: Loses encapsulation, harder to test
- **GraphQL-Style Query Language**: Over-engineered, JSON config sufficient

## Data Format Conversion Strategy

### Decision: Implicit Conversions with Explicit Type Checking

**Rationale**:
- Avro and DataFrame are the only supported formats (spec constraint)
- Either[GenericRecord, DataFrame] makes format explicit
- Implicit conversions reduce boilerplate
- Pattern matching ensures type safety

**Implementation Pattern**:
```scala
object FormatConverters {
  implicit class AvroToDataFrame(record: GenericRecord) {
    def toDataFrame(spark: SparkSession): DataFrame = {
      val schema = AvroConverter.toSparkSchema(record.getSchema)
      val row = AvroConverter.toRow(record)
      spark.createDataFrame(spark.sparkContext.parallelize(Seq(row)), schema)
    }
  }

  implicit class DataFrameToAvro(df: DataFrame) {
    def toAvro(avroSchema: Schema): RDD[GenericRecord] = {
      df.rdd.map(row => AvroConverter.toGenericRecord(row, avroSchema))
    }
  }
}

object AvroConverter {
  def toSparkSchema(avroSchema: Schema): StructType = {
    SchemaConverters.toSqlType(avroSchema).dataType.asInstanceOf[StructType]
  }

  def toRow(record: GenericRecord): Row = {
    // Convert GenericRecord fields to Row
  }

  def toGenericRecord(row: Row, avroSchema: Schema): GenericRecord = {
    // Convert Row fields to GenericRecord
  }
}
```

**Alternatives Considered**:
- **Always Convert to DataFrame**: Loses Avro optimizations for small datasets
- **Abstract Format Interface**: Over-engineered, only 2 formats
- **Manual Conversion Everywhere**: Boilerplate, error-prone

## Vault Integration

### Decision: Bettercloud vault-java-driver with Connection Pooling

**Rationale**:
- Widely used in Spark/Hadoop ecosystem
- Simple API: `vault.logical().read(path)`
- Supports AppRole and Token auth (both useful for different envs)
- Minimal dependencies (no heavy Spring framework)

**Implementation Pattern**:
```scala
class VaultClient(vaultAddr: String, token: String) {
  private val config = new VaultConfig()
    .address(vaultAddr)
    .token(token)
    .build()

  private val vault = new Vault(config)

  def readSecret(path: String): Map[String, String] = {
    try {
      val response = vault.logical().read(path)
      response.getData.asScala.toMap.map { case (k, v) => k -> v.toString }
    } catch {
      case e: VaultException =>
        throw new RuntimeException(s"Failed to read secret from Vault at path $path", e)
    }
  }
}

object VaultClient {
  def fromEnv(): VaultClient = {
    val addr = sys.env.getOrElse("VAULT_ADDR", "http://localhost:8200")
    val token = sys.env.getOrElse("VAULT_TOKEN", throw new RuntimeException("VAULT_TOKEN not set"))
    new VaultClient(addr, token)
  }
}
```

**Local Development Setup**:
- Docker Compose runs Vault in dev mode
- Init script reads `.env` file and writes secrets to Vault KV v2 engine
- `.env.example` documents required credential structure

**Alternatives Considered**:
- **Spring Cloud Vault**: Too heavy, requires Spring framework
- **HashiCorp's Official Go Client via JNI**: Complex integration
- **Direct HTTP API**: Manual error handling, reinventing the wheel

## Docker Compose Testing Environment

### Decision: Testcontainers for Integration Tests + Manual docker-compose.yml

**Rationale**:
- Testcontainers provides programmatic container lifecycle management
- docker-compose.yml enables manual local testing and debugging
- Both use same images, ensuring consistency
- Testcontainers integrates with ScalaTest for test isolation

**Docker Services Required**:
1. **PostgreSQL**: Official postgres:15-alpine (lightweight)
2. **MySQL**: Official mysql:8.0
3. **Kafka**: Confluent kafka:7.5.0 (includes Zookeeper via KRaft mode)
4. **Vault**: hashicorp/vault:1.15 (dev mode for local testing)
5. **MinIO**: minio/minio:latest (S3-compatible storage)
6. **DeltaLake**: No separate service (uses local filesystem with MinIO for cloud storage)

**Testcontainers Integration**:
```scala
class PostgresIntegrationTest extends AnyFlatSpec with BeforeAndAfterAll {
  val postgres: PostgreSQLContainer = new PostgreSQLContainer("postgres:15-alpine")
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test")

  override def beforeAll(): Unit = {
    postgres.start()
    // Populate test data
  }

  override def afterAll(): Unit = {
    postgres.stop()
  }

  "ExtractMethods.fromPostgres" should "extract data from PostgreSQL" in {
    val config = Map(
      "host" -> postgres.getHost,
      "port" -> postgres.getFirstMappedPort,
      "database" -> "testdb",
      "table" -> "test_table",
      "username" -> "test",
      "password" -> "test"
    )

    val spark = SparkSession.builder().master("local[*]").getOrCreate()
    val df = ExtractMethods.fromPostgres(config, spark)

    assert(df.count() == 100) // Expected test data size
  }
}
```

**Alternatives Considered**:
- **Embedded H2/Derby**: Not representative of production databases
- **Mock Objects Only**: Can't catch integration issues (connection pooling, JDBC drivers)
- **Require Local Services**: Brittle, environmental dependencies

## Static Methods Implementation

### Decision: Object Singleton with Reflection-Based Method Dispatch

**Rationale**:
- Spec requires static methods for ExtractMethods, LoadMethods, UserMethods
- Scala objects provide static-like singletons
- Reflection enables JSON-driven method selection without hardcoded dispatch
- Type safety preserved through runtime checks

**Implementation Pattern**:
```scala
object ExtractMethods {
  def fromPostgres(config: Map[String, Any], spark: SparkSession): DataFrame = {
    val jdbcUrl = s"jdbc:postgresql://${config("host")}:${config("port")}/${config("database")}"
    spark.read
      .format("jdbc")
      .option("url", jdbcUrl)
      .option("dbtable", config("table").toString)
      .option("user", config("username").toString)
      .option("password", config("password").toString)
      .load()
  }

  def fromKafka(config: Map[String, Any], spark: SparkSession): DataFrame = {
    spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", config("bootstrapServers").toString)
      .option("subscribe", config("topic").toString)
      .load()
  }

  // ... other extract methods

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

object UserMethods {
  // 5 Transformation methods
  def filterRows(df: DataFrame, params: Map[String, Any]): DataFrame = {
    df.filter(params("condition").toString)
  }

  def aggregateData(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val groupCols = params("groupBy").asInstanceOf[Seq[String]]
    val aggExpr = params("aggregations").asInstanceOf[Map[String, String]]
    df.groupBy(groupCols.head, groupCols.tail: _*)
      .agg(aggExpr.head, aggExpr.tail.toSeq: _*)
  }

  def joinDataFrames(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val rightDf = params("rightDataFrame").asInstanceOf[DataFrame]
    val joinKey = params("joinKey").toString
    df.join(rightDf, joinKey)
  }

  def enrichData(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val newColumns = params("columns").asInstanceOf[Map[String, String]]
    newColumns.foldLeft(df) { case (accDf, (colName, expr)) =>
      accDf.withColumn(colName, org.apache.spark.sql.functions.expr(expr))
    }
  }

  def reshapeData(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val operation = params("operation").toString
    operation match {
      case "pivot" => df.groupBy(params("pivotKey").toString).pivot(params("pivotCol").toString).sum()
      case "unpivot" => // Use stack() function
      case _ => throw new IllegalArgumentException(s"Unknown reshape operation: $operation")
    }
  }

  // 5 Validation methods
  def validateSchema(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val expectedSchema = params("schema").asInstanceOf[StructType]
    require(df.schema == expectedSchema, s"Schema mismatch: expected $expectedSchema, got ${df.schema}")
    df
  }

  def validateNulls(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val requiredCols = params("requiredColumns").asInstanceOf[Seq[String]]
    requiredCols.foreach { col =>
      val nullCount = df.filter(df(col).isNull).count()
      require(nullCount == 0, s"Column $col has $nullCount null values")
    }
    df
  }

  def validateRanges(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val ranges = params("ranges").asInstanceOf[Map[String, (Double, Double)]]
    ranges.foreach { case (col, (min, max)) =>
      val outOfRange = df.filter(df(col) < min || df(col) > max).count()
      require(outOfRange == 0, s"Column $col has $outOfRange values out of range [$min, $max]")
    }
    df
  }

  def validateReferentialIntegrity(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val referenceTable = params("referenceTable").asInstanceOf[DataFrame]
    val foreignKey = params("foreignKey").toString
    val invalidRefs = df.join(referenceTable, Seq(foreignKey), "left_anti").count()
    require(invalidRefs == 0, s"Found $invalidRefs rows with invalid foreign key references")
    df
  }

  def validateBusinessRules(df: DataFrame, params: Map[String, Any]): DataFrame = {
    val rules = params("rules").asInstanceOf[Seq[String]]
    rules.foreach { rule =>
      val violations = df.filter(s"NOT ($rule)").count()
      require(violations == 0, s"Business rule '$rule' violated by $violations rows")
    }
    df
  }

  def invokeMethod(methodName: String, df: DataFrame,
                   params: Map[String, Any]): DataFrame = {
    methodName match {
      // Transformations
      case "filterRows" => filterRows(df, params)
      case "aggregateData" => aggregateData(df, params)
      case "joinDataFrames" => joinDataFrames(df, params)
      case "enrichData" => enrichData(df, params)
      case "reshapeData" => reshapeData(df, params)
      // Validations
      case "validateSchema" => validateSchema(df, params)
      case "validateNulls" => validateNulls(df, params)
      case "validateRanges" => validateRanges(df, params)
      case "validateReferentialIntegrity" => validateReferentialIntegrity(df, params)
      case "validateBusinessRules" => validateBusinessRules(df, params)
      case _ => throw new IllegalArgumentException(s"Unknown user method: $methodName")
    }
  }
}
```

**Alternatives Considered**:
- **True Reflection (java.lang.reflect)**: More flexible but breaks at runtime, harder to test
- **Type Classes**: Over-engineered, Scala idiom but adds complexity
- **Hardcoded Dispatch**: Simple but violates open/closed principle

## Performance Optimization Strategies

### Decision: Spark Partitioning + Broadcast Joins + Caching

**Rationale**:
- Spec requires 100K records/sec (simple) and 10K records/sec (complex)
- Spark's built-in optimizations (Catalyst, Tungsten) handle most cases
- Explicit partitioning for skewed data
- Broadcast joins for small reference tables
- Caching for iterative pipelines

**Implementation Guidelines**:
```scala
// In ExtractMethods - partition on read for large tables
def fromPostgres(config: Map[String, Any], spark: SparkSession): DataFrame = {
  val partitionColumn = config.get("partitionColumn").map(_.toString)
  val numPartitions = config.getOrElse("numPartitions", 8).asInstanceOf[Int]

  val reader = spark.read.format("jdbc").options(jdbcOptions)

  partitionColumn match {
    case Some(col) =>
      reader
        .option("partitionColumn", col)
        .option("numPartitions", numPartitions)
        .option("lowerBound", config("lowerBound"))
        .option("upperBound", config("upperBound"))
        .load()
    case None =>
      reader.load()
  }
}

// In UserMethods - broadcast small tables for joins
def joinDataFrames(df: DataFrame, params: Map[String, Any]): DataFrame = {
  val rightDf = params("rightDataFrame").asInstanceOf[DataFrame]
  val joinKey = params("joinKey").toString
  val broadcastJoin = params.getOrElse("broadcast", false).asInstanceOf[Boolean]

  if (broadcastJoin) {
    df.join(org.apache.spark.sql.functions.broadcast(rightDf), joinKey)
  } else {
    df.join(rightDf, joinKey)
  }
}

// In Pipeline - cache intermediate results if reused
class Pipeline(steps: List[PipelineStep]) {
  def execute(spark: SparkSession): Unit = {
    var currentData: Either[GenericRecord, DataFrame] = Left(null) // Placeholder

    steps.foreach { step =>
      currentData = step.execute(currentData, spark)

      // Cache if step is reused (detected by step config)
      currentData match {
        case Right(df) if step.shouldCache => df.cache()
        case _ => // No caching
      }
    }
  }
}
```

**Performance Testing Approach**:
- Generate 1M row test datasets
- Measure throughput for simple pipelines (filter + map)
- Measure throughput for complex pipelines (join + aggregate + multiple transforms)
- Use Spark UI to identify bottlenecks
- Profile with YourKit or VisualVM for JVM-level issues

**Alternatives Considered**:
- **Custom Parallel Processing**: Reinventing Spark's optimizations
- **Always Broadcast**: Breaks with large tables, OOM errors
- **Always Cache**: Wastes memory, slower for single-pass pipelines

## Retry Logic Implementation

### Decision: Tail-Recursive Retry with Exponential Backoff (Configurable)

**Rationale**:
- Spec requires max 3 retries with fixed 5-second delays (initial requirement)
- Designed for configurability to support exponential backoff in future
- Tail recursion prevents stack overflow
- Functional approach (Try/Either) avoids mutable state

**Implementation Pattern**:
```scala
object RetryStrategy {
  case class RetryConfig(maxRetries: Int = 3, delaySeconds: Int = 5, exponential: Boolean = false)

  @tailrec
  def retry[T](attempt: Int, config: RetryConfig)(fn: => T): Try[T] = {
    Try(fn) match {
      case Success(result) => Success(result)
      case Failure(ex) if attempt < config.maxRetries =>
        val delay = if (config.exponential) config.delaySeconds * math.pow(2, attempt).toInt
                    else config.delaySeconds
        println(s"Attempt ${attempt + 1} failed: ${ex.getMessage}. Retrying in $delay seconds...")
        Thread.sleep(delay * 1000)
        retry(attempt + 1, config)(fn)
      case Failure(ex) =>
        Failure(new RuntimeException(s"Failed after ${config.maxRetries} retries", ex))
    }
  }
}

// In Pipeline
class Pipeline(config: PipelineConfig) {
  def main(spark: SparkSession): Try[Unit] = {
    val retryConfig = RetryStrategy.RetryConfig(
      maxRetries = 3,
      delaySeconds = 5,
      exponential = false // Fixed delay per spec
    )

    RetryStrategy.retry(0, retryConfig) {
      executeSteps(spark)
    }
  }

  private def executeSteps(spark: SparkSession): Unit = {
    // Execute all steps in chain
    steps.head.executeChain(initialData, spark)
  }
}
```

**Alternatives Considered**:
- **No Retry**: Violates spec requirement FR-016
- **Blocking Sleep**: Chosen for simplicity (async retry over-engineered for single-node)
- **Akka Retry**: Heavy dependency for simple use case

## JSON Configuration Parsing

### Decision: Jackson ObjectMapper with Case Class Deserialization

**Rationale**:
- Jackson included with Spark (jackson-module-scala 2.15.3)
- No additional dependencies
- Type-safe deserialization to case classes
- Supports JSON references (read external files)

**Implementation Pattern**:
```scala
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object PipelineConfigParser {
  private val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  def parse(jsonPath: String): PipelineConfig = {
    val file = new File(jsonPath)
    val config = mapper.readValue(file, classOf[PipelineConfig])

    // Resolve external references
    resolveReferences(config)
  }

  private def resolveReferences(config: PipelineConfig): PipelineConfig = {
    config.copy(
      steps = config.steps.map { step =>
        step.configRef match {
          case Some(refPath) =>
            val externalConfig = parseStepConfig(refPath)
            step.copy(config = step.config ++ externalConfig)
          case None => step
        }
      }
    )
  }
}

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
```

**JSON Schema Validation**:
- JSON schemas in `contracts/` directory
- Validated at parse time using `everit-json-schema` library (if needed, but prefer runtime checks for simplicity)

**Alternatives Considered**:
- **circe**: Popular Scala JSON library but additional dependency
- **Play JSON**: Requires Play framework
- **Spray JSON**: Less feature-rich than Jackson

## Logging Strategy

### Decision: SLF4J + Logback with Structured JSON Logging

**Rationale**:
- SLF4J is Spark's logging facade (already present)
- Logback for production (JSON formatter for log aggregation)
- Structured logs support observability requirements (constitution VI)
- MDC (Mapped Diagnostic Context) for pipeline/step correlation IDs

**Implementation Pattern**:
```scala
import org.slf4j.{Logger, LoggerFactory, MDC}

class Pipeline(config: PipelineConfig) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def execute(spark: SparkSession): Unit = {
    val executionId = java.util.UUID.randomUUID().toString
    MDC.put("executionId", executionId)
    MDC.put("pipelineName", config.name)

    logger.info(s"Starting pipeline execution: ${config.name}")

    try {
      steps.zipWithIndex.foreach { case (step, idx) =>
        MDC.put("stepIndex", idx.toString)
        MDC.put("stepType", step.stepType)

        val startTime = System.currentTimeMillis()
        val result = step.execute(...)
        val duration = System.currentTimeMillis() - startTime

        result match {
          case Right(df) =>
            val recordCount = df.count()
            logger.info(s"Step $idx completed", Map(
              "recordCount" -> recordCount,
              "durationMs" -> duration
            ))
          case Left(_) =>
            logger.info(s"Step $idx completed (Avro)", Map("durationMs" -> duration))
        }
      }

      logger.info(s"Pipeline execution completed successfully")
    } catch {
      case ex: Exception =>
        logger.error(s"Pipeline execution failed", ex)
        throw ex
    } finally {
      MDC.clear()
    }
  }
}
```

**Logback Configuration** (logback.xml):
```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <includeMdcKeyName>executionId</includeMdcKeyName>
      <includeMdcKeyName>pipelineName</includeMdcKeyName>
      <includeMdcKeyName>stepIndex</includeMdcKeyName>
      <includeMdcKeyName>stepType</includeMdcKeyName>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
```

**Alternatives Considered**:
- **Log4j2**: Security vulnerabilities (Log4Shell), avoid
- **Spark's Internal Logging**: Less control, harder to structure
- **Custom Logging**: Reinventing the wheel

## Summary of Technology Stack

| Component | Technology | Version | Rationale |
|-----------|-----------|---------|-----------|
| Language | Scala | 2.12.x | Spark 3.5.6 compatibility |
| Build Tool | Gradle | 8.5+ | User-specified, minimal deps |
| Processing Engine | Apache Spark | 3.5.6 | Spec requirement |
| Data Format | Avro | 1.11.3 | Spark-compatible |
| Delta Lake | Delta Lake | 3.0.0 | Spark 3.5 compatible |
| Kafka Client | Apache Kafka | 3.6.0 | Recent stable release |
| AWS SDK | AWS SDK v2 | 2.20.x | S3 access |
| JDBC Drivers | PostgreSQL JDBC | 42.6.0 | Recent stable |
| | MySQL Connector/J | 8.2.0 | Recent stable |
| Vault Client | vault-java-driver | 5.1.0 | Minimal deps |
| JSON Parsing | Jackson Scala | 2.15.3 | Included with Spark |
| Testing | ScalaTest | 3.2.17 | Scala standard |
| | Testcontainers | 1.19.3 | Docker integration tests |
| Logging | SLF4J + Logback | 1.7.36 / 1.2.11 | Spark default |
| Container Orchestration | Docker Compose | 2.x | Local testing environment |

**Total External Dependencies**: 12 runtime libraries (excluding transitive Spark dependencies)
**Minimal Library Constraint**: ✅ Achieved by reusing Spark's transitive dependencies and avoiding abstractions

## Open Questions & Future Research

1. **Schema Evolution**: How to handle schema changes in sources?
   - *Deferred to future iteration* - initial implementation assumes static schemas

2. **Performance Tuning**: Optimal Spark configurations for different pipeline patterns?
   - *Addressed through performance testing* - document recommended configs in quickstart

3. **Security**: Vault token rotation and renewal strategy?
   - *Initial implementation*: Static tokens from .env (dev only)
   - *Production recommendation*: AppRole with TTL/renewal (document in quickstart)

4. **Monitoring**: Integration with external metrics systems (Prometheus, Datadog)?
   - *Out of scope* per spec - structured logs only

5. **Multi-tenancy**: Isolating pipelines for different teams?
   - *Out of scope* per spec - single-node execution assumed

## References

- [Apache Spark 3.5.6 Documentation](https://spark.apache.org/docs/3.5.6/)
- [Delta Lake 3.0 Documentation](https://docs.delta.io/latest/index.html)
- [Bettercloud Vault Java Driver](https://github.com/BetterCloud/vault-java-driver)
- [Testcontainers Scala](https://github.com/testcontainers/testcontainers-scala)
- [Gradle Scala Plugin](https://docs.gradle.org/current/userguide/scala_plugin.html)
- [Jackson Scala Module](https://github.com/FasterXML/jackson-module-scala)

---

**Research Phase Complete**: All technical unknowns resolved. Ready for Phase 1 (Data Model & Contracts).
