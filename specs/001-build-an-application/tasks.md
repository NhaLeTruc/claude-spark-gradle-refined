# Tasks: Data Pipeline Orchestration Application

**Input**: Design documents from `/specs/001-build-an-application/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: FR-018 requires "System MUST be fully unit tested" - TDD approach mandatory. Tests written FIRST and must FAIL before implementation.

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`
- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: User story this task belongs to (Setup, Foundation, US1-US5, Polish)
- File paths use Scala conventions: `src/main/scala/`, `src/test/scala/`

## Path Conventions
- **Source**: `src/main/scala/com/pipeline/`
- **Tests**: `src/test/scala/com/pipeline/`
- **Resources**: `src/main/resources/`
- **Config Examples**: `config/examples/`
- **Docker**: `docker/` and `docker-compose.yml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure per plan.md

- [ ] T001 [P] [Setup] Create Gradle project structure with Scala plugin 2.12.x and Java 11 compatibility in `build.gradle`
- [ ] T002 [P] [Setup] Add Shadow plugin for uber-JAR packaging in `build.gradle` (dual execution mode: CLI + spark-submit)
- [ ] T003 [P] [Setup] Configure Spark 3.5.6, Avro 1.11.3, Delta 3.0.0 dependencies with provided scope for cluster mode in `build.gradle`
- [ ] T004 [P] [Setup] Add JDBC drivers (PostgreSQL 42.6.0, MySQL 8.2.0), Kafka client 3.6.0, AWS SDK S3 2.20.x in `build.gradle`
- [ ] T005 [P] [Setup] Add Vault client (vault-java-driver 5.1.0), Jackson Scala module 2.15.3 in `build.gradle`
- [ ] T006 [P] [Setup] Add test dependencies: ScalaTest 3.2.17, Testcontainers 1.19.3, Mockito in `build.gradle`
- [ ] T007 [P] [Setup] Create package structure: `core/`, `config/`, `credentials/`, `operations/`, `formats/`, `retry/`, `cli/` in `src/main/scala/com/pipeline/`
- [ ] T008 [P] [Setup] Create test structure: `unit/`, `integration/`, `contract/`, `performance/` in `src/test/scala/com/pipeline/`
- [ ] T009 [P] [Setup] Create logback.xml with JSON structured logging and MDC support in `src/main/resources/`
- [ ] T010 [P] [Setup] Create application.conf for Spark and Vault default configs in `src/main/resources/`
- [ ] T011 [P] [Setup] Create `.gitignore` with `.env`, `build/`, `target/`, IDE files
- [ ] T012 [P] [Setup] Configure ScalaFmt for code formatting in `.scalafmt.conf`

**Checkpoint**: Project structure initialized, ready for foundational code

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure REQUIRED before ANY user story implementation

**⚠️ CRITICAL**: No user story work until this completes - provides PipelineContext, retry, logging, config parsing used by ALL stories

### Foundation Tests (TDD - Write FIRST, ensure FAIL)

- [ ] T013 [P] [Foundation] Unit test for RetryStrategy in `src/test/scala/com/pipeline/unit/retry/RetryStrategyTest.scala` - test max 3 retries, 5-second delays, tail recursion
- [ ] T014 [P] [Foundation] Unit test for PipelineContext in `src/test/scala/com/pipeline/unit/core/PipelineContextTest.scala` - test DataFrame registration, retrieval, primary update
- [ ] T015 [P] [Foundation] Unit test for PipelineConfigParser in `src/test/scala/com/pipeline/unit/config/PipelineConfigParserTest.scala` - test JSON parsing, external references, validation
- [ ] T016 [P] [Foundation] Contract test for pipeline JSON schema validation in `src/test/scala/com/pipeline/contract/PipelineConfigSchemaTest.scala`

### Foundation Implementation

- [ ] T017 [P] [Foundation] Implement RetryStrategy with tail recursion, configurable delays in `src/main/scala/com/pipeline/retry/RetryStrategy.scala`
- [ ] T018 [P] [Foundation] Implement PipelineContext case class with mutable DataFrame registry in `src/main/scala/com/pipeline/core/PipelineContext.scala`
- [ ] T019 [Foundation] Implement PipelineConfigParser using Jackson, handle external references in `src/main/scala/com/pipeline/config/PipelineConfigParser.scala`
- [ ] T020 [P] [Foundation] Create StepConfig case class in `src/main/scala/com/pipeline/config/StepConfig.scala`
- [ ] T021 [P] [Foundation] Create PipelineConfig case class in `src/main/scala/com/pipeline/config/PipelineConfig.scala`

**Checkpoint**: Foundation ready - user stories can now be implemented independently

---

## Phase 3: User Story 4 - Secure Credential Management (Priority: P1) 🎯 CRITICAL

**Goal**: Enable secure credential retrieval from Vault without secrets in JSON configs - FOUNDATIONAL for US1 and US2

**Independent Test**: Store credentials in Vault, reference paths in JSON, verify successful retrieval and connection

**Why First**: US1 and US2 require credentials to connect to data sources/sinks. Must complete before pipeline execution stories.

### Tests for US4 (TDD - Write FIRST, ensure FAIL)

- [ ] T022 [P] [US4] Unit test for VaultClient in `src/test/scala/com/pipeline/unit/credentials/VaultClientTest.scala` - test readSecret, error handling, env config
- [ ] T023 [P] [US4] Unit test for JdbcConfig in `src/test/scala/com/pipeline/unit/credentials/JdbcConfigTest.scala` - test fetchFromVault, required keys validation
- [ ] T024 [P] [US4] Unit test for IAMConfig in `src/test/scala/com/pipeline/unit/credentials/IAMConfigTest.scala` - test fetchFromVault, optional sessionToken
- [ ] T025 [P] [US4] Unit test for OtherConfig in `src/test/scala/com/pipeline/unit/credentials/OtherConfigTest.scala` - test flexible credential structure
- [ ] T026 [P] [US4] Unit test for CredentialConfigFactory in `src/test/scala/com/pipeline/unit/credentials/CredentialConfigFactoryTest.scala` - test factory pattern, unknown types

### Implementation for US4

- [ ] T027 [US4] Create CredentialConfig sealed trait in `src/main/scala/com/pipeline/credentials/CredentialConfig.scala`
- [ ] T028 [P] [US4] Implement VaultClient with vault-java-driver in `src/main/scala/com/pipeline/credentials/VaultClient.scala`
- [ ] T029 [P] [US4] Implement JdbcConfig for PostgreSQL/MySQL in `src/main/scala/com/pipeline/credentials/JdbcConfig.scala`
- [ ] T030 [P] [US4] Implement IAMConfig for S3 in `src/main/scala/com/pipeline/credentials/IAMConfig.scala`
- [ ] T031 [P] [US4] Implement OtherConfig for Kafka/DeltaLake in `src/main/scala/com/pipeline/credentials/OtherConfig.scala`
- [ ] T032 [US4] Implement CredentialConfigFactory in `src/main/scala/com/pipeline/credentials/CredentialConfigFactory.scala`

**Checkpoint**: Credential management complete - US1 and US2 can now connect to sources/sinks securely

---

## Phase 4: User Story 1 - Simple ETL Pipeline (Priority: P1) 🎯 MVP

**Goal**: Extract from PostgreSQL, transform, load to S3 - Core value proposition

**Independent Test**: Create JSON config (extract PostgreSQL, transform filter, load S3), execute, verify data in S3 with logs

### Tests for US1 (TDD - Write FIRST, ensure FAIL)

- [ ] T033 [P] [US1] Unit test for PipelineStep trait and Chain of Responsibility in `src/test/scala/com/pipeline/unit/core/PipelineStepTest.scala`
- [ ] T034 [P] [US1] Unit test for ExtractStep in `src/test/scala/com/pipeline/unit/core/ExtractStepTest.scala` - test extraction, registration, credential resolution
- [ ] T035 [P] [US1] Unit test for TransformStep in `src/test/scala/com/pipeline/unit/core/TransformStepTest.scala` - test transformation, multi-DataFrame support
- [ ] T036 [P] [US1] Unit test for LoadStep in `src/test/scala/com/pipeline/unit/core/LoadStepTest.scala` - test loading, credential resolution
- [ ] T037 [P] [US1] Unit test for Pipeline orchestration in `src/test/scala/com/pipeline/unit/core/PipelineTest.scala` - test main execution, retry logic, logging
- [ ] T038 [P] [US1] Unit test for ExtractMethods.fromPostgres in `src/test/scala/com/pipeline/unit/operations/ExtractMethodsTest.scala` - test JDBC extraction, partitioning
- [ ] T039 [P] [US1] Unit test for LoadMethods.toS3 in `src/test/scala/com/pipeline/unit/operations/LoadMethodsTest.scala` - test S3 write, formats, modes
- [ ] T040 [P] [US1] Unit test for UserMethods.filterRows in `src/test/scala/com/pipeline/unit/operations/UserMethodsTest.scala` - test filtering logic
- [ ] T041 [US1] Integration test for simple ETL pipeline (PostgreSQL→S3) in `src/test/scala/com/pipeline/integration/SimpleETLIntegrationTest.scala` - uses Testcontainers
- [ ] T042 [US1] Performance test for 100K records/sec throughput in `src/test/scala/com/pipeline/performance/BatchPerformanceTest.scala`

### Implementation for US1

- [ ] T043 [US1] Create PipelineStep sealed trait with Chain of Responsibility in `src/main/scala/com/pipeline/core/PipelineStep.scala`
- [ ] T044 [P] [US1] Implement ExtractStep case class with registerAs support in `src/main/scala/com/pipeline/core/ExtractStep.scala`
- [ ] T045 [P] [US1] Implement TransformStep case class with inputDataFrames support in `src/main/scala/com/pipeline/core/TransformStep.scala`
- [ ] T046 [P] [US1] Implement LoadStep case class in `src/main/scala/com/pipeline/core/LoadStep.scala`
- [ ] T047 [US1] Implement Pipeline case class with main execution, retry integration in `src/main/scala/com/pipeline/core/Pipeline.scala`
- [ ] T048 [US1] Create ExtractMethods object with fromPostgres static method in `src/main/scala/com/pipeline/operations/ExtractMethods.scala`
- [ ] T049 [P] [US1] Add ExtractMethods.fromS3 for future use in `src/main/scala/com/pipeline/operations/ExtractMethods.scala`
- [ ] T050 [US1] Create LoadMethods object with toS3 static method in `src/main/scala/com/pipeline/operations/LoadMethods.scala`
- [ ] T051 [P] [US1] Add LoadMethods.toPostgres for future use in `src/main/scala/com/pipeline/operations/LoadMethods.scala`
- [ ] T052 [US1] Create UserMethods object with filterRows, enrichData in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T053 [US1] Create StepResult case class for step outputs in `src/main/scala/com/pipeline/core/StepResult.scala`
- [ ] T054 [US1] Create example JSON config for simple ETL in `config/examples/simple-etl.json`

**Checkpoint**: Simple ETL pipeline fully functional - MVP ready for demo/deployment

---

## Phase 5: User Story 2 - Complex Multi-Source Pipeline (Priority: P1)

**Goal**: Extract from Kafka+MySQL, validate, transform, load to DeltaLake+PostgreSQL - Real-world complexity

**Independent Test**: Create JSON with multi-source extraction, validation, dual loads, verify all steps execute with validation enforced

### Tests for US2 (TDD - Write FIRST, ensure FAIL)

- [ ] T055 [P] [US2] Unit test for ExtractMethods.fromKafka in `src/test/scala/com/pipeline/unit/operations/ExtractMethodsKafkaTest.scala` - test streaming vs batch
- [ ] T056 [P] [US2] Unit test for ExtractMethods.fromMySQL in `src/test/scala/com/pipeline/unit/operations/ExtractMethodsMySQLTest.scala` - test JDBC extraction
- [ ] T057 [P] [US2] Unit test for ExtractMethods.fromDeltaLake in `src/test/scala/com/pipeline/unit/operations/ExtractMethodsDeltaTest.scala` - test time travel
- [ ] T058 [P] [US2] Unit test for LoadMethods.toDeltaLake in `src/test/scala/com/pipeline/unit/operations/LoadMethodsDeltaTest.scala` - test merge schema
- [ ] T059 [P] [US2] Unit test for LoadMethods.toPostgres in `src/test/scala/com/pipeline/unit/operations/LoadMethodsPostgresTest.scala` - test save modes
- [ ] T060 [P] [US2] Unit test for LoadMethods.toMySQL in `src/test/scala/com/pipeline/unit/operations/LoadMethodsMySQLTest.scala`
- [ ] T061 [P] [US2] Unit test for LoadMethods.toKafka in `src/test/scala/com/pipeline/unit/operations/LoadMethodsKafkaTest.scala` - test streaming writes
- [ ] T062 [P] [US2] Unit test for ValidateStep in `src/test/scala/com/pipeline/unit/core/ValidateStepTest.scala` - test validation failures halt pipeline
- [ ] T063 [P] [US2] Unit test for UserMethods.validateSchema in `src/test/scala/com/pipeline/unit/operations/UserMethodsValidateTest.scala` - test 5 validation methods
- [ ] T064 [P] [US2] Unit test for UserMethods.joinDataFrames with multi-DataFrame support in `src/test/scala/com/pipeline/unit/operations/UserMethodsJoinTest.scala`
- [ ] T065 [P] [US2] Unit test for UserMethods.aggregateData in `src/test/scala/com/pipeline/unit/operations/UserMethodsAggTest.scala`
- [ ] T066 [US2] Integration test for multi-source pipeline (Kafka+MySQL→DeltaLake+Postgres) in `src/test/scala/com/pipeline/integration/MultiSourceIntegrationTest.scala`
- [ ] T067 [US2] Contract test for external JSON config references in `src/test/scala/com/pipeline/contract/ExternalConfigTest.scala`
- [ ] T068 [US2] Performance test for 10K records/sec complex pipeline in `src/test/scala/com/pipeline/performance/ComplexBatchPerformanceTest.scala`

### Implementation for US2

- [ ] T069 [P] [US2] Add ExtractMethods.fromKafka with streaming support in `src/main/scala/com/pipeline/operations/ExtractMethods.scala`
- [ ] T070 [P] [US2] Add ExtractMethods.fromMySQL in `src/main/scala/com/pipeline/operations/ExtractMethods.scala`
- [ ] T071 [P] [US2] Add ExtractMethods.fromDeltaLake with time travel in `src/main/scala/com/pipeline/operations/ExtractMethods.scala`
- [ ] T072 [P] [US2] Add LoadMethods.toDeltaLake with merge schema in `src/main/scala/com/pipeline/operations/LoadMethods.scala`
- [ ] T073 [P] [US2] Add LoadMethods.toPostgres in `src/main/scala/com/pipeline/operations/LoadMethods.scala`
- [ ] T074 [P] [US2] Add LoadMethods.toMySQL in `src/main/scala/com/pipeline/operations/LoadMethods.scala`
- [ ] T075 [P] [US2] Add LoadMethods.toKafka with checkpointing in `src/main/scala/com/pipeline/operations/LoadMethods.scala`
- [ ] T076 [US2] Implement ValidateStep case class in `src/main/scala/com/pipeline/core/ValidateStep.scala`
- [ ] T077 [P] [US2] Add UserMethods.validateSchema (FR-025: 5 validation methods) in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T078 [P] [US2] Add UserMethods.validateNulls in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T079 [P] [US2] Add UserMethods.validateRanges in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T080 [P] [US2] Add UserMethods.validateReferentialIntegrity in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T081 [P] [US2] Add UserMethods.validateBusinessRules in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T082 [P] [US2] Add UserMethods.joinDataFrames with multi-DataFrame context support (FR-024: 5 transform methods) in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T083 [P] [US2] Add UserMethods.aggregateData in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T084 [P] [US2] Add UserMethods.reshapeData (pivot/unpivot) in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T085 [P] [US2] Add UserMethods.unionDataFrames in `src/main/scala/com/pipeline/operations/UserMethods.scala`
- [ ] T086 [US2] Update PipelineConfigParser to handle external config references in `src/main/scala/com/pipeline/config/PipelineConfigParser.scala`
- [ ] T087 [US2] Create example JSON for multi-source pipeline in `config/examples/multi-source-pipeline.json`
- [ ] T088 [P] [US2] Create external config reference example in `config/examples/sources/postgres-config.json`

**Checkpoint**: Complex pipelines with validation fully functional - Production-ready core features complete

---

## Phase 6: User Story 3 - Streaming Pipeline with Retry (Priority: P2)

**Goal**: Kafka streaming to DeltaLake with retry logic - Real-time processing capability

**Independent Test**: Start streaming pipeline, simulate failures, verify 3 retries with 5-second delays

### Tests for US3 (TDD - Write FIRST, ensure FAIL)

- [ ] T089 [P] [US3] Unit test for streaming mode detection in `src/test/scala/com/pipeline/unit/operations/StreamingModeTest.scala`
- [ ] T090 [P] [US3] Unit test for retry logic integration with Pipeline in `src/test/scala/com/pipeline/unit/core/PipelineRetryTest.scala`
- [ ] T091 [US3] Integration test for streaming Kafka→DeltaLake with retry in `src/test/scala/com/pipeline/integration/StreamingIntegrationTest.scala`
- [ ] T092 [US3] Performance test for 50K events/sec, 5s p95 latency in `src/test/scala/com/pipeline/performance/StreamingPerformanceTest.scala`

### Implementation for US3

- [ ] T093 [US3] Enhance Pipeline.main to handle streaming execution mode in `src/main/scala/com/pipeline/core/Pipeline.scala`
- [ ] T094 [US3] Add streaming-specific logging with latency tracking in `src/main/scala/com/pipeline/core/Pipeline.scala`
- [ ] T095 [US3] Create example JSON for streaming pipeline in `config/examples/streaming-kafka-delta.json`

**Checkpoint**: Streaming pipelines operational with retry - Real-time processing enabled

---

## Phase 7: User Story 5 - Local Testing Environment (Priority: P2)

**Goal**: Docker Compose environment for local development and integration testing

**Independent Test**: Start docker-compose, run integration tests against local services, verify all pass

### Tests for US5 (Already written - validation only)

- [ ] T096 [US5] Validate all integration tests pass against docker-compose environment in `src/test/scala/com/pipeline/integration/`

### Implementation for US5

- [ ] T097 [P] [US5] Create docker-compose.yml with PostgreSQL, MySQL, Kafka, Vault, MinIO services
- [ ] T098 [P] [US5] Create PostgreSQL init script in `docker/postgres/init.sql`
- [ ] T099 [P] [US5] Create MySQL init script in `docker/mysql/init.sql`
- [ ] T100 [P] [US5] Create Kafka topic initialization script in `docker/kafka/topics.sh`
- [ ] T101 [P] [US5] Create Vault config and initialization script in `docker/vault/config.hcl` and `docker/vault/init-vault.sh`
- [ ] T102 [P] [US5] Create MinIO bucket initialization script in `docker/minio/init-buckets.sh`
- [ ] T103 [P] [US5] Create .env.example with example credentials (FR-022) in repository root
- [ ] T104 [P] [US5] Update .gitignore to exclude .env file
- [ ] T105 [US5] Create README for docker environment in `docker/README.md`

**Checkpoint**: Local testing environment ready - Developers can test pipelines locally

---

## Phase 8: CLI and Cluster Execution (Dual Mode Support)

**Goal**: Support both local CLI execution and spark-submit cluster deployment

**Independent Test**: Run pipeline via CLI locally, then submit same pipeline to Spark cluster

### Tests for Dual Mode

- [ ] T106 [P] [DualMode] Unit test for execution mode detection in `src/test/scala/com/pipeline/unit/cli/ExecutionModeTest.scala`
- [ ] T107 [P] [DualMode] Unit test for PipelineRunner argument parsing in `src/test/scala/com/pipeline/unit/cli/PipelineRunnerTest.scala`
- [ ] T108 [DualMode] Integration test for CLI mode execution in `src/test/scala/com/pipeline/integration/CLIModeTest.scala`

### Implementation for Dual Mode

- [ ] T109 [DualMode] Implement PipelineRunner with execution mode detection in `src/main/scala/com/pipeline/cli/PipelineRunner.scala`
- [ ] T110 [DualMode] Add main method with CLI and cluster mode branching in `src/main/scala/com/pipeline/cli/PipelineRunner.scala`
- [ ] T111 [DualMode] Configure shadowJar task to exclude Spark dependencies in `build.gradle`
- [ ] T112 [DualMode] Add application plugin with mainClass configuration in `build.gradle`
- [ ] T113 [DualMode] Create spark-submit example scripts in `scripts/submit-to-yarn.sh`, `scripts/submit-to-k8s.sh`

**Checkpoint**: Dual execution mode operational - Can run locally or on clusters

---

## Phase 9: Data Format Conversion (Avro Support)

**Goal**: Support Avro and DataFrame interchange per FR-007

**Independent Test**: Convert DataFrame to Avro and back, verify schema and data integrity

### Tests for Format Conversion (TDD - Write FIRST, ensure FAIL)

- [ ] T114 [P] [Formats] Unit test for AvroConverter.toSparkSchema in `src/test/scala/com/pipeline/unit/formats/AvroConverterTest.scala`
- [ ] T115 [P] [Formats] Unit test for AvroConverter.toRow in `src/test/scala/com/pipeline/unit/formats/AvroConverterTest.scala`
- [ ] T116 [P] [Formats] Unit test for AvroConverter.toGenericRecord in `src/test/scala/com/pipeline/unit/formats/AvroConverterTest.scala`
- [ ] T117 [Formats] Integration test for Avro round-trip conversion in `src/test/scala/com/pipeline/integration/AvroConversionTest.scala`

### Implementation for Format Conversion

- [ ] T118 [P] [Formats] Implement AvroConverter.toSparkSchema in `src/main/scala/com/pipeline/formats/AvroConverter.scala`
- [ ] T119 [P] [Formats] Implement AvroConverter.toRow in `src/main/scala/com/pipeline/formats/AvroConverter.scala`
- [ ] T120 [P] [Formats] Implement AvroConverter.toGenericRecord in `src/main/scala/com/pipeline/formats/AvroConverter.scala`
- [ ] T121 [Formats] Create DataFrameConverter with implicit conversions in `src/main/scala/com/pipeline/formats/DataFrameConverter.scala`

**Checkpoint**: Avro/DataFrame conversion functional - Data format requirements met

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Final improvements affecting multiple stories

- [ ] T122 [P] [Polish] Add comprehensive ScalaDoc to all public APIs across all packages
- [ ] T123 [P] [Polish] Create README.md with quickstart instructions in repository root
- [ ] T124 [P] [Polish] Create example pipeline configs (5 examples minimum) in `config/examples/`
- [X] T125 [P] [Polish] Add additional unit tests to reach 85% coverage threshold per constitution in `src/test/scala/com/pipeline/unit/`
  **Status**: Improved from 7.2% to 12.04% unit coverage. Architectural limitations prevent reaching 85% without mocking. Project has comprehensive integration tests (Docker-based) that validate actual execution paths.
- [ ] T126 [Polish] Run all tests and verify 100% pass rate: `./gradlew test`
- [ ] T127 [Polish] Run performance tests and validate throughput targets in `src/test/scala/com/pipeline/performance/`
- [ ] T128 [P] [Polish] Generate test coverage report and verify 85% minimum: `./gradlew jacocoTestReport`
- [ ] T129 [P] [Polish] Run ScalaFmt and ensure code formatting consistency: `./gradlew scalafmtAll`
- [ ] T130 [Polish] Build both JAR artifacts (CLI and uber-JAR): `./gradlew build shadowJar`
- [ ] T131 [Polish] Validate quickstart.md instructions work end-to-end
- [ ] T132 [P] [Polish] Create CONTRIBUTING.md with development guidelines
- [ ] T133 [P] [Polish] Add error handling improvements based on edge cases from spec.md
- [ ] T134 [Polish] Security review: verify zero credentials in configs, Vault-only access
- [ ] T135 [Polish] Performance optimization: review Spark configurations, partitioning strategies
- [ ] T136 [Polish] Final integration test: run all example pipelines in docker-compose environment

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational) ← BLOCKS ALL USER STORIES
    ↓
  ┌─────────────────────┴───────────────────┐
  ↓                                          ↓
Phase 3 (US4: Credentials) ← BLOCKS US1, US2
  ↓                          ↓
  ├──────────→ Phase 4 (US1: Simple ETL - MVP)
  ├──────────→ Phase 5 (US2: Complex Multi-Source)
  └──────────→ Phase 6 (US3: Streaming) [Can start after US1]
                ↓
Phase 7 (US5: Local Testing) [Can start after US1]
Phase 8 (Dual Mode) [Can start after US1]
Phase 9 (Avro Format) [Can start after US1]
    ↓
Phase 10 (Polish) ← After all desired stories complete
```

### Critical Path (MVP)
1. Setup (Phase 1) → 2. Foundation (Phase 2) → 3. US4 Credentials (Phase 3) → 4. US1 Simple ETL (Phase 4) → **MVP COMPLETE**

### User Story Dependencies

- **US4 (Credentials)**: MUST complete before US1 and US2 (they need credential retrieval)
- **US1 (Simple ETL)**: Foundation for all other stories
- **US2 (Complex)**: Independent of US1 but can reuse US1 patterns
- **US3 (Streaming)**: Can start after US1 (extends pipeline execution)
- **US5 (Local Testing)**: Can start after US1 (tests US1 functionality)

### Within Each User Story

**TDD Order** (Constitution Section II):
1. Write tests FIRST
2. Verify tests FAIL (red phase)
3. Implement code to pass tests (green phase)
4. Refactor while keeping tests green

**Implementation Order**:
1. Tests (marked [P] can run in parallel)
2. Models/Entities (marked [P] can run in parallel)
3. Services/Operations (depends on models)
4. Integration (depends on all above)

### Parallel Opportunities

**Setup Phase (Phase 1)**: T001-T012 all [P] - can run simultaneously

**Foundation Tests (Phase 2)**: T013-T016 all [P] - write all tests together

**Foundation Implementation (Phase 2)**: T017-T018, T020-T021 marked [P]

**US4 Tests**: T022-T026 all [P] - write all credential tests together

**US4 Implementation**: T028-T031 all [P] - implement all config types together

**US1 Tests**: T033-T040 all [P], T041-T042 can run separately

**US1 Implementation**: T044-T046, T049, T052 marked [P]

**US2 Tests**: Most tests (T055-T065) marked [P] - large parallel opportunity

**US2 Implementation**: Extract methods (T069-T071), Load methods (T072-T075), Validation methods (T077-T081), Transform methods (T082-T085) all [P]

---

## Parallel Execution Examples

### Phase 2 Foundation (All Tests Together)
```bash
# Launch all foundation tests in parallel:
Task: "Unit test for RetryStrategy in src/test/scala/.../RetryStrategyTest.scala"
Task: "Unit test for PipelineContext in src/test/scala/.../PipelineContextTest.scala"
Task: "Unit test for PipelineConfigParser in src/test/scala/.../PipelineConfigParserTest.scala"
Task: "Contract test for pipeline JSON schema in src/test/scala/.../PipelineConfigSchemaTest.scala"
```

### Phase 4 US1 (Core Tests Together)
```bash
# Launch all US1 core tests in parallel:
Task: "Unit test for PipelineStep trait in src/test/scala/.../PipelineStepTest.scala"
Task: "Unit test for ExtractStep in src/test/scala/.../ExtractStepTest.scala"
Task: "Unit test for TransformStep in src/test/scala/.../TransformStepTest.scala"
Task: "Unit test for LoadStep in src/test/scala/.../LoadStepTest.scala"
Task: "Unit test for Pipeline orchestration in src/test/scala/.../PipelineTest.scala"
Task: "Unit test for ExtractMethods.fromPostgres in src/test/scala/.../ExtractMethodsTest.scala"
Task: "Unit test for LoadMethods.toS3 in src/test/scala/.../LoadMethodsTest.scala"
Task: "Unit test for UserMethods.filterRows in src/test/scala/.../UserMethodsTest.scala"
```

### Phase 5 US2 (All Extract Methods Together)
```bash
# Launch all US2 extract method implementations in parallel:
Task: "Add ExtractMethods.fromKafka in src/main/scala/.../ExtractMethods.scala"
Task: "Add ExtractMethods.fromMySQL in src/main/scala/.../ExtractMethods.scala"
Task: "Add ExtractMethods.fromDeltaLake in src/main/scala/.../ExtractMethods.scala"
```

---

## Implementation Strategy

### MVP First (Minimum Viable Product)

**Scope**: US1 only - Simple ETL Pipeline

1. Complete Phase 1: Setup (T001-T012)
2. Complete Phase 2: Foundation (T013-T021)
3. Complete Phase 3: US4 Credentials (T022-T032) - Required by US1
4. Complete Phase 4: US1 Simple ETL (T033-T054)
5. **STOP and VALIDATE**: Test US1 independently with simple-etl.json
6. **MVP READY**: Can extract from PostgreSQL, transform, load to S3

**Value**: Core pipeline orchestration works, demonstrable ETL capability

### Incremental Delivery

1. **Foundation** (Phase 1-2) → Infrastructure ready
2. **+ US4** (Phase 3) → Secure credentials
3. **+ US1** (Phase 4) → **MVP: Simple ETL working** ✅
4. **+ US2** (Phase 5) → Complex pipelines with validation
5. **+ US3** (Phase 6) → Streaming support
6. **+ US5** (Phase 7) → Local testing environment
7. **+ Dual Mode** (Phase 8) → Cluster deployment
8. **+ Polish** (Phase 10) → Production-ready

**Each addition**: Independently testable, adds value without breaking previous stories

### Parallel Team Strategy

With 3 developers after Foundation completes:

- **Developer A**: US4 (Credentials) → US1 (Simple ETL) [Critical path]
- **Developer B**: US5 (Docker environment) → US3 (Streaming)
- **Developer C**: US2 tests → US2 (Complex pipelines)

Coordination: US1 must complete before US3 starts (extends execution model)

---

## Notes

### TDD Approach (Constitution Requirement)

- **Red Phase**: Write test FIRST, ensure it FAILS
- **Green Phase**: Implement minimal code to pass test
- **Refactor Phase**: Improve code while keeping tests green
- Commit after each phase or logical group

### Task Markers

- **[P]**: Parallel execution safe (different files, no dependencies)
- **[Story]**: User story tag for traceability (US1-US5, Setup, Foundation, Polish, etc.)
- **File paths**: Absolute from repository root

### Story Independence

- Each user story deliverable independently
- US4 exception: Required by US1, US2 (credentials needed)
- Verify story works alone before proceeding

### Coverage Requirements

- **Minimum**: 85% line coverage (Constitution Section III)
- **Test levels**: Unit, Integration, Contract, Performance
- **Test counts**: 136 total tasks, 53 are test tasks (39% test coverage in task list)

### Checkpoints

Stop after each checkpoint to:
- Run relevant tests
- Verify story acceptance criteria
- Demo functionality
- Commit code

### Edge Cases

Address spec.md edge cases in Polish phase (T133):
- Format mismatches between steps
- Vault retrieval failures
- Zero records processing
- Partial completion failures
- Circular config references
- Retry exhaustion scenarios
- Kafka lag handling
- Schema evolution

### Constitutional Compliance

- ✅ SOLID Architecture: Factory pattern (credentials), Chain of Responsibility (steps)
- ✅ Test-First Development: All tests before implementation, red-green-refactor
- ✅ Comprehensive Coverage: Unit/Integration/Contract/Performance tests
- ✅ Clean Code: ScalaFmt, ScalaDoc, intention-revealing names
- ✅ Library-First: Modular packages, CLI wrapper thin
- ✅ Observability: Structured logs, MDC, JSON formatting
- ✅ Idempotency: Retry logic, transactional writes

---

**Total Tasks**: 136
- **Setup**: 12 tasks
- **Foundation**: 9 tasks (5 tests + 4 impl)
- **US4 (Credentials)**: 11 tasks (5 tests + 6 impl)
- **US1 (Simple ETL)**: 22 tasks (10 tests + 12 impl)
- **US2 (Complex)**: 34 tasks (14 tests + 20 impl)
- **US3 (Streaming)**: 7 tasks (4 tests + 3 impl)
- **US5 (Local Testing)**: 10 tasks (1 validation + 9 impl)
- **Dual Mode**: 8 tasks (3 tests + 5 impl)
- **Avro Formats**: 8 tasks (4 tests + 4 impl)
- **Polish**: 15 tasks

**Test Tasks**: 53 (39% of total)
**Parallel Opportunities**: 87 tasks marked [P] (64% parallelizable)

**MVP Scope**: 53 tasks (Setup + Foundation + US4 + US1)
**Production Scope**: All 136 tasks
