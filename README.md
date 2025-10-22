# Data Pipeline Orchestration Application

A production-ready Apache Spark-based data pipeline orchestration framework that enables no-code pipeline creation through JSON configuration files.

## 🚀 Features

### Core Capabilities
- **No-Code Pipeline Creation**: Define complex ETL/ELT pipelines using JSON configuration files
- **Secure Credential Management**: Integration with HashiCorp Vault for zero-credentials-in-config security
- **Dual Execution Mode**: Run locally via CLI or deploy to Spark clusters via spark-submit
- **Automatic Retry Logic**: Built-in fault tolerance with configurable attempts and delays (default: 3 attempts, 5s delay)
- **Multi-DataFrame Operations**: Support for complex joins across multiple registered data sources
- **Observability**: Structured JSON logging with MDC correlation IDs for request tracing

### Data Sources (Extract) - 6 Methods
- **PostgreSQL**: JDBC with partitioning, query/table support, connection pooling
- **MySQL**: JDBC with partitioning, query/table support, connection pooling
- **Kafka**: Batch and streaming modes with configurable offsets
- **S3**: Multi-format support (Parquet, JSON, CSV, Avro, ORC) with IAM authentication
- **DeltaLake**: Time travel support (version and timestamp-based reads)
- **Avro**: Native Avro file support with schema inference

### Data Sinks (Load) - 6 Methods
- **PostgreSQL**: Batch writes with configurable batch sizes, multiple save modes
- **MySQL**: Batch writes with configurable batch sizes, multiple save modes
- **Kafka**: JSON serialization with automatic key/value column handling
- **S3**: Multi-format writes with partitioning, compression (Snappy, GZIP, LZ4, ZSTD)
- **DeltaLake**: Schema merge/overwrite support, partitioning, ACID transactions
- **Avro**: Compression and partitioning support

### Transformations - 8 Methods
- **filterRows**: SQL WHERE conditions via DataFrame.filter()
- **enrichData**: Add computed columns using SQL expressions
- **joinDataFrames**: Multi-DataFrame joins with multiple join types
- **aggregateData**: GroupBy with sum, avg, count, min, max aggregations
- **reshapeData**: Pivot operations with custom aggregations
- **unionDataFrames**: DataFrame unions with optional deduplication
- **toAvroSchema**: Generate Avro JSON schema from DataFrame structure
- **evolveAvroSchema**: Schema evolution with automatic column addition/removal

### Validations - 5 Methods
- **validateSchema**: Column name and type validation with detailed error reporting
- **validateNulls**: NOT NULL constraint checking with violation counts
- **validateRanges**: Min/max value validation for numeric columns
- **validateReferentialIntegrity**: Foreign key validation across DataFrames
- **validateBusinessRules**: Custom SQL rule validation with violation tracking

### Avro Support (NEW)
- **Schema Evolution**: Forward/backward compatibility checking
- **Type-Safe Conversion**: Automatic Avro ↔ Spark DataType mapping
- **Compression**: Snappy, Deflate, Bzip2, XZ codec support
- **Parquet Integration**: Efficient storage with embedded Avro metadata

## 📋 Requirements

- **Java**: 17+ (tested with Java 17)
- **Scala**: 2.12.x (compatible with Spark 3.5.6)
- **Apache Spark**: 3.5.6
- **Gradle**: 8.5+ (Gradle wrapper included)
- **HashiCorp Vault**: Optional for credential management (development/testing can use .env files)

## 🏗️ Architecture

### Design Patterns

- **Chain of Responsibility**: Pipeline steps execute in sequence
- **Factory Pattern**: Credential configuration creation
- **Immutable Data Structures**: All configurations are immutable
- **Test-First Development**: Full TDD approach with 151+ tests

### Core Components

```
src/main/scala/com/pipeline/
├── core/              # Pipeline orchestration
│   ├── PipelineStep.scala
│   ├── PipelineContext.scala
│   └── Pipeline.scala
├── config/            # Configuration parsing
│   ├── PipelineConfig.scala
│   └── PipelineConfigParser.scala
├── credentials/       # Vault integration
│   ├── VaultClient.scala
│   ├── CredentialConfig.scala
│   └── CredentialConfigFactory.scala
├── operations/        # Data operations
│   ├── ExtractMethods.scala
│   ├── LoadMethods.scala
│   └── UserMethods.scala
└── retry/             # Fault tolerance
    └── RetryStrategy.scala
```

## 🚀 Quick Start

### 1. Build the Project

```bash
# Optional run gradle wrapper if your gradle version is higher
gradle wrapper

# Build standard JAR (includes Spark for local execution)
./gradlew clean build

# Build uber-JAR (excludes Spark for cluster execution)
./gradlew shadowJar
```

**Build Artifacts**:
- `build/libs/pipeline-app-1.0-SNAPSHOT.jar` - Standard JAR for local CLI
- `build/libs/pipeline-app-1.0-SNAPSHOT-all.jar` - Uber-JAR for spark-submit

### 2. Configure Vault (Optional)

```bash
# Set Vault environment variables
export VAULT_ADDR="http://localhost:8200"
export VAULT_TOKEN="your-vault-token"

# Store credentials in Vault
vault kv put secret/postgres host=localhost port=5432 database=mydb username=user password=pass
vault kv put secret/s3 accessKeyId=AKIA... secretAccessKey=wJalr... region=us-west-2
```

### 3. Create a Pipeline Configuration

See `config/examples/simple-etl.json`:

```json
{
  "name": "simple-etl-pipeline",
  "mode": "batch",
  "steps": [
    {
      "type": "extract",
      "method": "fromPostgres",
      "config": {
        "table": "users",
        "credentialPath": "secret/data/postgres",
        "registerAs": "users"
      }
    },
    {
      "type": "transform",
      "method": "filterRows",
      "config": {
        "condition": "active = true AND age >= 18"
      }
    },
    {
      "type": "load",
      "method": "toS3",
      "config": {
        "bucket": "data-lake",
        "path": "/processed/users",
        "format": "parquet",
        "mode": "overwrite",
        "credentialPath": "secret/data/s3"
      }
    }
  ]
}
```

### 4. Run the Pipeline

**Local CLI Mode**:
```bash
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config/examples/simple-etl.json
```

**Spark Cluster Mode** (Standalone):
```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master spark://spark-master:7077 \
  --executor-memory 4G \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  config/examples/simple-etl.json
```

**YARN**:
```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master yarn \
  --deploy-mode cluster \
  --executor-memory 4G \
  --num-executors 10 \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  config/examples/simple-etl.json
```

**Kubernetes**:
```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master k8s://https://kubernetes.default.svc:443 \
  --deploy-mode cluster \
  --executor-memory 4G \
  --conf spark.kubernetes.container.image=spark:3.5.6 \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  config/examples/simple-etl.json
```

## 📚 Pipeline Configuration Reference

### Pipeline Structure

```json
{
  "name": "pipeline-name",           // Unique pipeline identifier
  "mode": "batch|streaming",         // Execution mode
  "steps": [...]                     // Ordered list of steps
}
```

### Step Types

#### Extract Step
```json
{
  "type": "extract",
  "method": "fromPostgres|fromMySQL|fromKafka|fromS3|fromDeltaLake",
  "config": {
    "table": "table_name",           // For JDBC sources
    "query": "SELECT ...",           // Alternative to table
    "credentialPath": "secret/data/postgres",
    "registerAs": "dataframe_name"   // Register for multi-DF operations
  }
}
```

#### Transform Step
```json
{
  "type": "transform",
  "method": "filterRows|joinDataFrames|aggregateData|enrichData|reshapeData|unionDataFrames",
  "config": {
    "condition": "age > 18",                        // For filterRows
    "inputDataFrames": ["df1", "df2"],             // For joins/unions
    "joinType": "inner|left|right|outer",          // For joins
    "groupBy": ["col1", "col2"],                   // For aggregations
    "aggregations": {"sum_col": "sum(amount)"},    // For aggregations
    "registerAs": "output_name"
  }
}
```

#### Validate Step
```json
{
  "type": "validate",
  "method": "validateSchema|validateNulls|validateRanges|validateReferentialIntegrity|validateBusinessRules",
  "config": {
    "rules": {...}
  }
}
```

#### Load Step
```json
{
  "type": "load",
  "method": "toPostgres|toMySQL|toKafka|toS3|toDeltaLake",
  "config": {
    "bucket": "bucket-name",         // For S3
    "path": "/output/path",
    "format": "parquet|json|csv|avro|orc",
    "mode": "overwrite|append|errorIfExists|ignore",
    "partitionBy": ["year", "month"],
    "compression": "snappy",
    "credentialPath": "secret/data/s3"
  }
}
```

## 🔒 Security

**Zero Credentials in Configuration Files**: All credentials are stored in HashiCorp Vault and referenced by path.

**Credential Types**:
- **JDBC** (PostgreSQL, MySQL): host, port, database, username, password
- **IAM** (S3): accessKeyId, secretAccessKey, sessionToken (optional), region
- **Other** (Kafka, DeltaLake): Flexible key-value pairs

## 🧪 Testing

### Run Tests

```bash
# Run all tests
./gradlew test

# Run specific test categories
./gradlew test --tests "com.pipeline.unit.*"
./gradlew test --tests "com.pipeline.contract.*"
./gradlew test --tests "com.pipeline.integration.*"
./gradlew test --tests "com.pipeline.performance.*"

# Generate coverage report
./gradlew jacocoTestReport
```

### Test Coverage

**Current Status**: 150+ tests with comprehensive coverage
- **Unit tests**: 130+ tests covering core logic, operations, and configurations
- **Integration tests**: Docker-based tests validating end-to-end pipeline execution
- **Contract tests**: 10+ tests for JSON schema validation and API contracts
- **Performance tests**: Spec-aligned tests validating throughput and latency requirements

### Performance Test Requirements (from spec.md)

All performance tests validate against specification requirements:
- **SC-002**: Batch simple operations ≥100K records/sec ✅
- **SC-003**: Batch complex operations ≥10K records/sec ✅
- **SC-004**: Streaming p95 latency <5 seconds ✅

**Recent Improvements** (2025-10-22):
- Added specification-aligned performance tests with warmup iterations
- Fixed percentile calculation bug (now uses nearest-rank method)
- Added resource monitoring capabilities (CPU, memory tracking)
- Improved test documentation with clear pass/fail criteria

### Performance Test Highlights

```scala
// Example: SC-002 Test (100K records/sec for simple batch operations)
it should "meet SC-002: 100K records/sec for simple batch operations" in {
  val recordCount = 1000000L
  val minThroughput = 100000.0 // 100K records/sec per spec

  // Warmup run to eliminate JVM/Spark cold start
  pipeline.execute(spark)

  // Actual measurement run
  assertThroughput("SC-002-simple-batch", recordCount, minThroughput) {
    pipeline.execute(spark)
  }

  logger.info("✓ SC-002 PASSED")
}
```

See [src/test/scala/com/pipeline/performance/](src/test/scala/com/pipeline/performance/) for complete performance test suite.

## 📊 Example Pipelines

### Simple ETL (PostgreSQL → S3)
See: `config/examples/simple-etl.json`

### Multi-Source Join (PostgreSQL + MySQL → DeltaLake)
See: `config/examples/multi-source-join.json`

### Streaming (Kafka → DeltaLake)
See: `config/examples/streaming-kafka.json`

## 🔄 Retry Logic

All pipelines automatically retry on failure:
- **Max Attempts**: 3 (configurable)
- **Delay**: 5 seconds between attempts (configurable)
- **Strategy**: Tail-recursive for stack safety

## 📝 Logging

Structured JSON logging with correlation IDs:

```json
{
  "timestamp": "2025-10-14T20:55:29.422Z",
  "level": "INFO",
  "message": "Starting pipeline execution",
  "pipelineName": "simple-etl-pipeline",
  "pipelineMode": "batch",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## 🏗️ Development

### Code Style
```bash
# Format code
./gradlew scalafmtAll

# Check formatting
./gradlew checkScalafmtAll
```

### Project Structure
- TDD approach (tests written first)
- SOLID principles
- Immutable data structures
- Functional programming style

## 📄 License

Copyright © 2025. All rights reserved.

## 📚 Documentation

**Complete documentation is available in the [`docs/`](docs/) directory**:

### Quick Links
- **[📖 Getting Started](docs/guides/GETTING_STARTED.md)** - 5-minute quick start
- **[📘 Documentation Index](docs/README.md)** - Complete documentation hub
- **[🔧 API Reference](docs/api/)** - ScalaDoc API documentation
- **[🏛️ Architecture Decisions (ADRs)](docs/adr/)** - Design decision records

### Feature Documentation
- **[Streaming Infrastructure](docs/features/STREAMING_INFRASTRUCTURE_COMPLETE.md)** - Dual-mode execution
- **[Error Handling](docs/features/ERROR_HANDLING_COMPLETE.md)** - Custom exception hierarchy
- **[Performance Features](docs/features/PERFORMANCE_FEATURES_COMPLETE.md)** - Caching & repartitioning
- **[Metrics Collection](docs/features/METRICS_COLLECTION_COMPLETE.md)** - Monitoring & observability
- **[Security Enhancements](docs/features/SECURITY_ENHANCEMENTS_COMPLETE.md)** - Vault-only mode & auditing
- **[Integration Testing](docs/features/INTEGRATION_TESTING_COMPLETE.md)** - Testcontainers & E2E tests
- **[Technical Debt Report](docs/features/TECHNICAL_DEBT_REPORT.md)** - Current status & improvements

### Generate Documentation
```bash
# Generate ScalaDoc API documentation
./gradlew scaladoc

# Generate all documentation
./gradlew documentation
```

### Example Pipelines

All examples located in `config/examples/`:

- **[simple-etl.json](config/examples/simple-etl.json)**: Basic PostgreSQL → Transform → S3 pipeline
- **[multi-source-join.json](config/examples/multi-source-join.json)**: Multi-source join (PostgreSQL + MySQL)
- **[streaming-kafka.json](config/examples/streaming-kafka.json)**: Real-time Kafka streaming pipeline
- **[avro-etl.json](config/examples/avro-etl.json)**: Avro format pipeline with compression
- **[avro-schema-evolution.json](config/examples/avro-schema-evolution.json)**: Schema evolution example
- **[data-quality-pipeline.json](config/examples/data-quality-pipeline.json)**: Comprehensive data validation
- **[incremental-load-pipeline.json](config/examples/incremental-load-pipeline.json)**: Incremental processing pattern
- **[aggregation-pipeline.json](config/examples/aggregation-pipeline.json)**: Complex aggregations with pivot

## 🤝 Contributing

This is a demonstration project. See `specs/` directory for full specifications and design documents.

### Specification Documents
- **[spec.md](specs/001-build-an-application/spec.md)**: Feature specification with user stories
- **[plan.md](specs/001-build-an-application/plan.md)**: Implementation plan and architecture
- **[tasks.md](specs/001-build-an-application/tasks.md)**: Detailed task breakdown with dependencies
- **[data-model.md](specs/001-build-an-application/data-model.md)**: Entity definitions and relationships

## 📞 Support

For issues and questions, refer to:
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common issues and solutions
- **Project specifications** in `specs/001-build-an-application/`
- **Performance test documentation** in [src/test/scala/com/pipeline/performance/](src/test/scala/com/pipeline/performance/)

## 🎯 Project Status

**Current Status**: Active Development 🚧

**Completed Phases**:
- ✅ Phase 1: Setup (Project structure and dependencies)
- ✅ Phase 2: Foundation (Core infrastructure)
- ✅ Phase 3: Credential Management (Vault integration)
- ✅ Phase 4: Simple ETL Pipeline (MVP)
- ✅ Phase 5: Complex Multi-Source Pipelines
- ✅ Phase 8: Dual Mode Execution (CLI + spark-submit)
- ✅ Phase 9: Avro Format Support
- 🚧 Phase 10: Polish & Performance Optimization (In Progress)

**Recent Updates** (2025-10-22):
- ✅ Enhanced performance tests with spec-aligned validation (Task T127)
- ✅ Fixed percentile calculation bug in latency measurements
- ✅ Added resource monitoring capabilities (CPU, memory tracking)
- ✅ Improved test documentation and logging

**Test Statistics**:
- **Total Tests**: 150+
- **Pass Rate**: 100%
- **Coverage**: Comprehensive unit, integration, contract, and performance tests
- **Performance**: All spec requirements validated (SC-002, SC-003, SC-004)

**Build Artifacts**:
- Standard JAR: `build/libs/pipeline-app-1.0-SNAPSHOT.jar` (~450MB with Spark)
- Uber JAR: `build/libs/pipeline-app-1.0-SNAPSHOT-all.jar` (~450MB for cluster deployment)

---

**Built with** ❤️ **using Test-Driven Development and SOLID principles**
