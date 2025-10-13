# Implementation Plan: Data Pipeline Orchestration Application

**Branch**: `001-build-an-application` | **Date**: 2025-10-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-build-an-application/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a configuration-driven data pipeline orchestration platform that enables data engineers to create ETL/streaming pipelines using JSON configurations. The application uses Apache Spark 3.5.6 with Scala 2.12 and Gradle for build management. Pipelines support extract-transform-validate-load operations across multiple data sources (PostgreSQL, MySQL, Kafka, DeltaLake, S3) with secure credential management via HashiCorp Vault. Implementation follows Chain of Responsibility pattern for pipeline steps, supports both batch and streaming execution models, and includes comprehensive testing with docker-compose local environment.

## Technical Context

**Language/Version**: Scala 2.12.x (compatible with Spark 3.5.6)
**Build Tool**: Gradle 8.5+ with Scala plugin
**Primary Dependencies**:
- Apache Spark 3.5.6 (spark-core, spark-sql, spark-streaming)
- Apache Avro 1.11.3 (compatible with Spark 3.5.6)
- Delta Lake 3.0.0 (compatible with Spark 3.5.6)
- Kafka client 3.6.0
- AWS SDK for S3 2.20.x
- PostgreSQL JDBC 42.6.0
- MySQL Connector/J 8.2.0
- HashiCorp Vault client (Bettercloud vault-java-driver 5.1.0)
- JSON parsing (jackson-module-scala 2.15.3 - included with Spark)

**Storage**: External data sources/sinks only (PostgreSQL, MySQL, Kafka, DeltaLake, S3)
**Testing**: ScalaTest 3.2.17, Testcontainers 1.19.3, Docker Compose for integration environment
**Target Platform**: JVM 11+ (compatible with Spark 3.5.6), Linux/macOS development environment
**Project Type**: Single JVM application with library-first architecture (core libraries + dual execution modes)
**Execution Modes**:
- CLI mode: Local execution via Gradle run or jar file (development and testing)
- spark-submit mode: Cluster execution via spark-submit with uber-jar (production deployment)
**Performance Goals**:
- Batch simple: 100K records/sec minimum
- Batch complex: 10K records/sec minimum
- Streaming: 5s p95 latency, 50K events/sec throughput

**Constraints**:
- Maximum 3 retries per pipeline with fixed 5-second delays
- JSON-only configuration (no code required from users)
- All credentials via Vault (zero secrets in configs)
- Avro and DataFrame only for inter-step data formats
- Static methods for all extract/load/transform operations

**Scale/Scope**:
- 8 core entities (Pipeline, PipelineStep, 3 method collections, 3 config types)
- 5 data source/sink integrations (7 total including duplicates)
- 10+ transformation/validation methods
- Multi-DataFrame support for complex transformations (joins, unions, etc.)
- Deployment modes: local (CLI) and distributed (spark-submit to cluster)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Design Evaluation

#### ✅ I. SOLID Architecture
**Status**: COMPLIANT
- Single Responsibility: Each entity has clear responsibility (Pipeline=orchestration, PipelineStep=single operation, ExtractMethods=data source access)
- Open/Closed: Chain of Responsibility pattern allows adding new step types without modifying existing steps
- Liskov Substitution: All PipelineStep implementations must be substitutable
- Interface Segregation: Config interfaces (JdbcConfig, IAMConfig, OtherConfig) segregated by credential type
- Dependency Inversion: Steps depend on abstract config interfaces, not concrete Vault client

**Design decisions aligned**: Factory pattern for config objects, Chain of Responsibility for steps, interface-based credential management

#### ✅ II. Test-First Development
**Status**: COMPLIANT
- Requirement FR-018: "System MUST be fully unit tested with comprehensive test coverage"
- TDD workflow will be enforced through tasks.md with failing tests before implementation
- All entities designed to be independently testable

**Approach**: Tests written before implementation, ScalaTest with mocked dependencies for unit tests

#### ✅ III. Comprehensive Test Coverage
**Status**: COMPLIANT
- **Unit Tests**: Required for all entities (Pipeline, PipelineStep, ExtractMethods, LoadMethods, UserMethods, configs)
- **Integration Tests**: FR-019 requires docker-compose environment, FR-020 requires mocked test data
- **Contract Tests**: JSON schema validation for pipeline configs, Avro schema validation for data interchange
- **Performance Tests**: FR-002/FR-003 specify throughput targets (100K/10K records/sec, 5s latency, 50K events/sec)

**Test structure**: `src/test/scala/` with subdirectories for unit/integration/contract/performance tests

#### ✅ IV. Clean Code Standards
**Status**: COMPLIANT
- Scala idioms: case classes for immutable data, pattern matching for type safety
- Function composition for transformations
- Explicit error handling with Either/Try types (no silent failures)
- ScalaFmt for formatting consistency

**Standards**: Intention-revealing names, single-responsibility functions, self-documenting code

#### ✅ V. Library-First Architecture
**Status**: COMPLIANT
- Core libraries: pipeline orchestration, config parsing, credential management, extract/load/transform operations
- CLI wrapper: thin layer to execute pipelines from command line
- Each library independently testable without system dependencies

**Module structure**:
- `pipeline-core`: Pipeline and PipelineStep abstractions
- `pipeline-config`: JSON parsing and validation
- `pipeline-credentials`: Vault integration and config objects
- `pipeline-operations`: ExtractMethods, LoadMethods, UserMethods
- `pipeline-cli`: Command-line runner with dual modes:
  - Local mode: Gradle run or java -jar (creates local SparkSession)
  - Cluster mode: Main class for spark-submit (receives SparkSession from cluster)

#### ✅ VI. Observability as Code
**Status**: COMPLIANT
- FR-017: "System MUST emit execution logs for monitoring and debugging"
- SC-006: "Pipeline execution logs provide sufficient detail to diagnose failures"
- Structured logging with SLF4J + Logback, JSON formatter for production

**Instrumentation**:
- Record counts at each step
- Processing time per batch/micro-batch
- Retry attempts and outcomes
- Validation failures with affected record counts
- Resource usage (Spark metrics integration)

#### ✅ VII. Idempotency and Fault Tolerance
**Status**: COMPLIANT
- FR-016: "System MUST retry failed pipelines up to 3 times with 5-second delays"
- Idempotent design: pipeline re-runs produce same results
- No partial state persistence (all-or-nothing commits)

**Implementation**:
- Retry logic in Pipeline.main execution method
- Transactional writes for JDBC sinks
- Kafka producer idempotence enabled
- S3 overwrites for deterministic results

### Data Quality Standards Evaluation

#### ✅ Validation Gates
**Status**: COMPLIANT
- FR-005: "System MUST provide validation methods for data quality checks"
- UserMethods includes 5+ validation methods (schema, nulls, ranges, referential integrity, business rules)
- Validation failures halt pipeline execution per constitution requirement

**Implementation**: Validation as PipelineStep in chain, throws exception on failure to halt execution

#### ⚠️ Data Lineage Tracking
**Status**: PARTIAL COMPLIANCE - OUT OF SCOPE
- Constitution requires lineage tracking (source, transformation version, execution ID)
- Feature spec "Out of Scope" explicitly excludes "Data lineage tracking across pipeline executions"

**Justification**: Initial implementation focuses on core pipeline functionality. Lineage tracking deferred to future iteration to limit scope. This is an accepted constitutional deviation documented here.

**Mitigation**: Logs include pipeline config path, execution timestamp, and step sequence - provides basic traceability.

#### ✅ Data Quality Metrics
**Status**: COMPLIANT via Logging
- Validation failure rates logged per rule
- Null rates detected during validation steps
- Schema evolution detected through validation exceptions

**Implementation**: Metrics exposed through structured logs (SC-006 requirement), not separate metrics system

### Performance Standards Evaluation

#### ✅ Throughput Requirements
**Status**: COMPLIANT
- FR-008/FR-009: Batch (100K/10K records/sec) and Streaming (5s p95, 50K events/sec) targets specified
- Spark 3.5.6 performance characteristics well-documented and tested for these targets
- Performance tests required (FR-018) to validate against standards

**Validation**: Performance test suite validates throughput/latency targets with representative data volumes

#### ✅ Scalability Requirements
**Status**: COMPLIANT (within single-node scope)
- Spark provides horizontal scalability through partitioning
- Stateless pipeline steps (Chain of Responsibility pattern)
- Configuration-driven resource allocation (Spark configs)

**Note**: "Out of Scope" explicitly excludes "Distributed deployment across multiple nodes" - single-node execution assumed, but Spark's internal parallelism provides scalability

#### ✅ Performance Testing
**Status**: COMPLIANT
- FR-018 requires comprehensive testing including performance validation
- Benchmarks with representative data volumes (100K-1M records for batch, sustained load for streaming)
- Memory/CPU profiling via Spark UI and JVM metrics

### Pre-Design Gate Decision

**✅ GATE PASSED WITH ONE DOCUMENTED DEVIATION**

**Deviation**: Data lineage tracking (Constitution Section II) deferred to future scope per explicit feature requirements.

**Rationale**: Feature spec explicitly excludes lineage tracking to focus on core pipeline orchestration capabilities in initial implementation. Constitution allows deviations with justification and tech debt tracking.

**Remediation**: Tech debt ticket to be created for lineage tracking in future iteration. Interim logging provides basic traceability.

**Proceed to Phase 0 Research**: ✅ Approved

## Project Structure

### Documentation (this feature)

```
specs/001-build-an-application/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output: technology decisions and patterns
├── data-model.md        # Phase 1 output: entity definitions and relationships
├── quickstart.md        # Phase 1 output: setup and usage guide
├── contracts/           # Phase 1 output: JSON schemas for pipeline configs
│   ├── pipeline-schema.json
│   ├── step-schema.json
│   └── credential-schema.json
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```
claude-spark-gradle-two/
├── build.gradle                    # Root Gradle build configuration (includes shadowJar for uber-jar)
├── settings.gradle                 # Multi-module project settings
├── gradle/                         # Gradle wrapper and dependencies
├── docker-compose.yml              # Local testing environment (FR-019)
├── .env.example                    # Example credentials file (FR-022)
├── .gitignore                      # Includes .env exclusion
│
├── build/
│   └── libs/
│       ├── pipeline-app.jar                    # Standard jar (CLI mode)
│       └── pipeline-app-all.jar                # Uber-jar with dependencies (spark-submit mode)
│
├── src/
│   ├── main/
│   │   ├── scala/
│   │   │   └── com/pipeline/
│   │   │       ├── core/           # Pipeline orchestration (Pipeline, PipelineStep)
│   │   │       │   ├── Pipeline.scala
│   │   │       │   ├── PipelineStep.scala
│   │   │       │   └── StepResult.scala
│   │   │       │
│   │   │       ├── config/         # JSON parsing and pipeline configuration
│   │   │       │   ├── PipelineConfigParser.scala
│   │   │       │   ├── StepConfig.scala
│   │   │       │   └── ConfigReference.scala
│   │   │       │
│   │   │       ├── credentials/    # Vault integration and credential configs
│   │   │       │   ├── VaultClient.scala
│   │   │       │   ├── CredentialConfig.scala    # Base trait
│   │   │       │   ├── JdbcConfig.scala
│   │   │       │   ├── IAMConfig.scala
│   │   │       │   └── OtherConfig.scala
│   │   │       │
│   │   │       ├── operations/     # Extract/Load/Transform methods
│   │   │       │   ├── ExtractMethods.scala      # Static methods for data sources
│   │   │       │   ├── LoadMethods.scala         # Static methods for data sinks
│   │   │       │   └── UserMethods.scala         # Transform and validation methods
│   │   │       │
│   │   │       ├── formats/        # Data format conversion (Avro <-> DataFrame)
│   │   │       │   ├── AvroConverter.scala
│   │   │       │   └── DataFrameConverter.scala
│   │   │       │
│   │   │       ├── retry/          # Retry logic implementation
│   │   │       │   └── RetryStrategy.scala
│   │   │       │
│   │   │       └── cli/            # Command-line interface
│   │   │           └── PipelineRunner.scala
│   │   │
│   │   └── resources/
│   │       ├── logback.xml         # Structured logging configuration
│   │       └── application.conf    # Default Spark and Vault settings
│   │
│   └── test/
│       └── scala/
│           └── com/pipeline/
│               ├── unit/           # Unit tests (mocked dependencies)
│               │   ├── core/
│               │   ├── config/
│               │   ├── credentials/
│               │   └── operations/
│               │
│               ├── integration/    # Integration tests (docker services)
│               │   ├── PostgresIntegrationTest.scala
│               │   ├── KafkaIntegrationTest.scala
│               │   └── EndToEndPipelineTest.scala
│               │
│               ├── contract/       # Schema and API contract tests
│               │   ├── PipelineConfigSchemaTest.scala
│               │   └── AvroSchemaTest.scala
│               │
│               └── performance/    # Performance and throughput tests
│                   ├── BatchPerformanceTest.scala
│                   └── StreamingPerformanceTest.scala
│
├── config/                         # Example pipeline configurations
│   ├── examples/
│   │   ├── simple-etl.json
│   │   ├── multi-source-pipeline.json
│   │   └── streaming-kafka-delta.json
│   └── schemas/                    # JSON schemas for validation
│       └── (symlink to specs/001-build-an-application/contracts/)
│
└── docker/                         # Docker Compose services and init scripts
    ├── postgres/
    │   └── init.sql
    ├── mysql/
    │   └── init.sql
    ├── kafka/
    │   └── topics.sh
    ├── vault/
    │   ├── config.hcl
    │   └── init-vault.sh           # Populates Vault from .env
    └── minio/                      # S3-compatible storage
        └── init-buckets.sh
```

**Structure Decision**: Single JVM application with modular package structure following library-first architecture principle. All functionality organized into cohesive packages (core, config, credentials, operations) that can be independently tested and potentially extracted into separate Gradle modules in future if needed. CLI supports dual execution modes: (1) Local mode via standard jar with embedded Spark dependencies, (2) Cluster mode via uber-jar for spark-submit with provided Spark dependencies. Docker environment provides isolated testing infrastructure per FR-019. Example configs demonstrate usage patterns per FR-001.

## Complexity Tracking

*Fill ONLY if Constitution Check has violations that must be justified*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Data Lineage Tracking (Constitution Section II) deferred | Feature spec explicitly excludes lineage tracking to limit initial scope. Basic traceability provided via structured logs (pipeline config path, timestamp, step sequence). | Full lineage implementation would require: (1) Record-level tracking database, (2) Lineage API for downstream consumers, (3) Schema versioning system. These are explicitly out-of-scope per feature requirements. Structured logs provide sufficient debugging capability for MVP. Tech debt ticket to be created for future iteration. |

---

## Phase 0: Research & Technical Decisions

This section documents research findings that resolve all unknowns from Technical Context and establish architectural patterns for implementation.

See [research.md](research.md) for detailed research findings.

### Summary of Key Decisions

1. **Build Tool**: Gradle 8.5+ with Scala plugin (user-specified)
2. **Dependency Management**: Minimize libraries - use Spark's included Jackson for JSON, avoid unnecessary abstractions
3. **Credential Config Pattern**: Factory pattern for CredentialConfig implementations (JdbcConfig, IAMConfig, OtherConfig)
4. **Chain of Responsibility**: PipelineStep trait with `execute()` method and `nextStep: Option[PipelineStep]` field
5. **Data Format Handling**: Implicit conversions between Avro GenericRecord and Spark Row
6. **Vault Integration**: Bettercloud vault-java-driver for simplicity (used by many Spark shops)
7. **Docker Compose**: Testcontainers for integration tests, docker-compose.yml for manual local testing
8. **Performance**: Spark partitioning strategies based on data volume (repartition for load balancing)

## Phase 1: Design & Contracts

### Data Model

See [data-model.md](data-model.md) for complete entity definitions and relationships.

### API Contracts

See [contracts/](contracts/) for JSON schemas defining pipeline configuration structure.

### Getting Started

See [quickstart.md](quickstart.md) for setup instructions and example usage.

## Post-Design Constitution Re-evaluation

*Re-check after Phase 1 design artifacts complete*

### Post-Design Gate Decision

**Status**: ✅ GATE PASSED

All Phase 1 design artifacts (data-model.md, contracts/, quickstart.md) have been created and reviewed against constitutional principles:

#### SOLID Architecture
- ✅ Data model defines clear single responsibilities for each entity
- ✅ Chain of Responsibility allows extending pipeline without modifying core
- ✅ CredentialConfig trait enables Liskov substitution
- ✅ Segregated interfaces for different credential types
- ✅ Dependencies on abstractions (traits) not concrete implementations

#### Test Coverage
- ✅ Data model designed for testability (immutable case classes, pure functions)
- ✅ Integration test scenarios defined in quickstart
- ✅ Contract tests enabled via JSON schemas
- ✅ Performance test criteria documented

#### Library-First
- ✅ Package structure supports independent library modules
- ✅ Core logic separated from CLI runner
- ✅ Each package has clear API boundaries

#### Observability
- ✅ Logging points identified in data model (Pipeline.execute, PipelineStep.execute)
- ✅ Metrics collection points defined (record counts, timing, retry attempts)

#### Idempotency
- ✅ Data model supports deterministic execution (immutable configs)
- ✅ Retry strategy accommodates idempotent operations

**No new deviations introduced during design phase.**

**Proceed to Phase 2 (/speckit.tasks)**: ✅ Approved

---

## Next Steps

1. Review this implementation plan with stakeholders
2. Run `/speckit.tasks` to generate dependency-ordered tasks from this plan and feature spec
3. Execute tasks using `/speckit.implement` to build the application
4. Validate against success criteria (SC-001 through SC-012) during implementation

**Plan Status**: ✅ Complete and ready for task generation
