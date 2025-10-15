# Getting Started

Welcome to the Data Pipeline Orchestration Application! This guide will help you create your first data pipeline.

---

## Prerequisites

- **Java 17** or higher
- **Apache Spark 3.5.6** (included in distribution)
- **Gradle 8.x** (for building from source)
- **Docker** (optional, for integration tests)

---

## Quick Start (5 Minutes)

### 1. Install

```bash
# Download latest release
wget https://github.com/your-org/pipeline/releases/download/v1.0.0/pipeline-app-all.jar

# Or build from source
git clone https://github.com/your-org/pipeline.git
cd pipeline
./gradlew build
```

### 2. Create Your First Pipeline

Create `my-first-pipeline.json`:

```json
{
  "name": "my-first-pipeline",
  "mode": "batch",
  "steps": [
    {
      "type": "extract",
      "method": "fromPostgres",
      "config": {
        "host": "localhost",
        "port": "5432",
        "database": "mydb",
        "username": "user",
        "password": "pass",
        "table": "users"
      }
    },
    {
      "type": "transform",
      "method": "filterRows",
      "config": {
        "condition": "age >= 18"
      }
    },
    {
      "type": "load",
      "method": "toS3",
      "config": {
        "bucket": "my-bucket",
        "path": "output/users",
        "format": "parquet"
      }
    }
  ]
}
```

### 3. Run

```bash
# Using built JAR
java -jar build/libs/pipeline-app-all.jar my-first-pipeline.json

# Or using Gradle
./gradlew run --args="my-first-pipeline.json"
```

That's it! Your first pipeline is running.

---

## Next Steps

### Basic Tutorials

1. **[Batch Processing](how-to/BATCH_PIPELINES.md)** - Learn batch ETL patterns
2. **[Streaming Processing](how-to/STREAMING_PIPELINES.md)** - Real-time data pipelines
3. **[Data Validation](how-to/DATA_VALIDATION.md)** - Ensure data quality
4. **[Multi-DataFrame Operations](how-to/MULTI_DATAFRAME.md)** - Join multiple sources

### Configuration

- **[Configuration Guide](CONFIGURATION.md)** - Complete config reference
- **[Extract Methods](reference/EXTRACT_METHODS.md)** - All data sources
- **[Transform Methods](reference/TRANSFORM_METHODS.md)** - All transformations
- **[Load Methods](reference/LOAD_METHODS.md)** - All data sinks

### Advanced Topics

- **[Performance Tuning](how-to/PERFORMANCE_TUNING.md)** - Optimization techniques
- **[Security Best Practices](how-to/SECURITY_BEST_PRACTICES.md)** - Secure credentials
- **[Monitoring](how-to/MONITORING.md)** - Metrics and observability

---

## Example Pipelines

The `config/examples/` directory contains ready-to-run examples:

- `batch-postgres-to-s3.json` - Basic batch ETL
- `streaming-kafka.json` - Kafka streaming pipeline
- `multi-source-join.json` - Multi-DataFrame joins
- `data-quality-validation.json` - Data validation
- `batch-with-metrics.json` - Pipeline with metrics

---

## Common Tasks

### Extract from PostgreSQL

```json
{
  "type": "extract",
  "method": "fromPostgres",
  "config": {
    "credentialPath": "secret/data/postgres",
    "table": "users"
  }
}
```

### Transform Data

```json
{
  "type": "transform",
  "method": "filterRows",
  "config": {
    "condition": "age >= 18 AND status = 'active'"
  }
}
```

### Load to S3

```json
{
  "type": "load",
  "method": "toS3",
  "config": {
    "credentialPath": "secret/data/s3",
    "bucket": "my-bucket",
    "path": "output/data",
    "format": "parquet",
    "mode": "overwrite"
  }
}
```

---

## Getting Help

- **Documentation**: See [docs/README.md](../README.md)
- **Examples**: Browse `config/examples/`
- **Issues**: [GitHub Issues](https://github.com/your-org/pipeline/issues)
- **API Reference**: [ScalaDoc](../api/index.html)

---

## What's Next?

Now that you have a working pipeline:

1. ✅ **Add more steps** - Chain multiple transformations
2. ✅ **Add validation** - Ensure data quality
3. ✅ **Enable metrics** - Monitor performance
4. ✅ **Secure credentials** - Use Vault integration
5. ✅ **Deploy to production** - See deployment guide

Happy pipelining! 🚀
