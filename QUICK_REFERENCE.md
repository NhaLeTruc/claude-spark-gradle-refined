# Quick Reference - Data Pipeline Orchestration Application

**One-page reference for common operations**

---

## 🚀 Quick Start (3 Steps)

```bash
# 1. Build
./gradlew build shadowJar

# 2. Setup Vault
export VAULT_ADDR='http://localhost:8200'
export VAULT_TOKEN='hvs.xxxxx'
vault kv put secret/postgres host=localhost port=5432 database=mydb username=user password=pass

# 3. Run
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config/examples/simple-etl.json
```

---

## 📁 Project Structure

```
claude-spark-gradle-two/
├── src/main/scala/com/pipeline/     # Source code
│   ├── core/                        # Pipeline orchestration
│   ├── config/                      # JSON parsing
│   ├── credentials/                 # Vault integration
│   ├── operations/                  # Extract/Load/Transform
│   └── retry/                       # Fault tolerance
├── src/test/scala/com/pipeline/     # Tests (151 passing)
├── config/examples/                 # Example pipelines
├── build.gradle                     # Build configuration
└── README.md                        # Full documentation
```

---

## 🔨 Common Commands

### Build
```bash
./gradlew build              # Build + run tests
./gradlew build -x test      # Build without tests
./gradlew shadowJar          # Build uber-JAR
./gradlew clean build        # Clean + build
```

### Test
```bash
./gradlew test                                  # All tests
./gradlew test --tests "*.unit.*"             # Unit tests only
./gradlew test --tests "*PipelineTest"        # Specific test
./gradlew jacocoTestReport                    # Coverage report
```

### Run
```bash
# Local
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar config.json

# Spark cluster
spark-submit --class com.pipeline.cli.PipelineRunner \
  --master spark://master:7077 \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar config.json
```

---

## 🔐 Vault Quick Reference

### Setup
```bash
# Start Vault (dev mode)
vault server -dev

# Set environment
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='hvs.xxxxx'
```

### Store Credentials
```bash
# PostgreSQL
vault kv put secret/postgres host=localhost port=5432 database=db username=user password=pass

# MySQL
vault kv put secret/mysql host=localhost port=3306 database=db username=root password=pass

# S3
vault kv put secret/s3 accessKeyId=AKIA... secretAccessKey=wJal... region=us-west-2

# Kafka
vault kv put secret/kafka bootstrap.servers=localhost:9092 security.protocol=PLAINTEXT
```

### Read Credentials
```bash
vault kv get secret/postgres
vault kv get -format=json secret/s3 | jq .
```

---

## 📝 Pipeline JSON Template

```json
{
  "name": "my-pipeline",
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
        "condition": "active = true"
      }
    },
    {
      "type": "load",
      "method": "toS3",
      "config": {
        "bucket": "data-lake",
        "path": "/output",
        "format": "parquet",
        "credentialPath": "secret/data/s3"
      }
    }
  ]
}
```

---

## 🎯 Step Types Reference

### Extract Methods
- `fromPostgres` - PostgreSQL tables
- `fromMySQL` - MySQL tables
- `fromKafka` - Kafka topics
- `fromS3` - S3 files (parquet, json, csv, avro, orc)
- `fromDeltaLake` - Delta Lake tables

### Transform Methods
- `filterRows` - Filter by SQL condition
- `joinDataFrames` - Join multiple DataFrames
- `aggregateData` - Group by and aggregate
- `enrichData` - Add computed columns
- `reshapeData` - Pivot/unpivot
- `unionDataFrames` - Union multiple DataFrames

### Validate Methods
- `validateSchema` - Schema validation
- `validateNulls` - Null value checks
- `validateRanges` - Value range checks
- `validateReferentialIntegrity` - Foreign key checks
- `validateBusinessRules` - Custom business rules

### Load Methods
- `toPostgres` - PostgreSQL tables
- `toMySQL` - MySQL tables
- `toKafka` - Kafka topics
- `toS3` - S3 files
- `toDeltaLake` - Delta Lake tables

---

## 🔧 Configuration Options

### Extract Config
```json
{
  "table": "table_name",              // Table to extract
  "query": "SELECT * FROM ...",       // Alternative to table
  "partitionColumn": "id",            // For parallel reads
  "numPartitions": 10,                // Number of partitions
  "credentialPath": "secret/data/postgres",
  "registerAs": "dataframe_name"      // Register for joins
}
```

### Transform Config
```json
{
  "condition": "age > 18",                    // For filterRows
  "inputDataFrames": ["df1", "df2"],         // For joins
  "joinType": "inner",                       // inner, left, right, outer
  "joinColumn": "id",                        // Column to join on
  "groupBy": ["col1", "col2"],               // For aggregations
  "aggregations": {"sum": "sum(amount)"},    // Aggregation functions
  "registerAs": "result"                     // Register output
}
```

### Load Config
```json
{
  "bucket": "bucket-name",               // S3 bucket
  "path": "/output/path",                // Output path
  "format": "parquet",                   // parquet, json, csv, avro, orc
  "mode": "overwrite",                   // overwrite, append, errorIfExists, ignore
  "partitionBy": ["year", "month"],      // Partition columns
  "compression": "snappy",               // Compression codec
  "credentialPath": "secret/data/s3"
}
```

---

## 🔍 Troubleshooting

### Build Fails
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Check Java version
java -version  # Should be 17+

# Increase memory
export GRADLE_OPTS="-Xmx4g"
```

### Vault Connection Failed
```bash
# Check Vault is running
curl $VAULT_ADDR/v1/sys/health

# Verify token
vault token lookup

# Test connectivity
vault kv get secret/postgres
```

### Spark Job Fails
```bash
# Check logs
tail -f logs/pipeline.log

# Increase memory
spark-submit --driver-memory 8G --executor-memory 16G ...

# Check Spark UI
http://localhost:4040
```

---

## 📊 Useful Spark Submit Options

```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master spark://master:7077 \
  --deploy-mode cluster \
  --driver-memory 4G \
  --executor-memory 8G \
  --num-executors 10 \
  --executor-cores 4 \
  --conf spark.sql.adaptive.enabled=true \
  --conf spark.sql.shuffle.partitions=200 \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  config.json
```

**Common Configurations**:
- `--driver-memory` - Driver memory (default: 1G)
- `--executor-memory` - Executor memory (default: 1G)
- `--num-executors` - Number of executors
- `--executor-cores` - Cores per executor
- `--total-executor-cores` - Total cores across all executors
- `--conf spark.sql.shuffle.partitions` - Shuffle partitions (default: 200)

---

## 📈 Performance Tips

1. **Partitioning**: Use `partitionColumn` for large tables
2. **Shuffle**: Adjust `spark.sql.shuffle.partitions` based on data size
3. **Memory**: Increase executor memory for large DataFrames
4. **Compression**: Use `snappy` for good balance of speed/size
5. **Format**: Use `parquet` for best performance
6. **Caching**: Cache intermediate DataFrames for reuse

---

## 📞 Quick Links

- **Full Documentation**: [README.md](README.md)
- **Deployment Guide**: [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
- **Implementation Details**: [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)
- **Examples**: [config/examples/](config/examples/)
- **Specifications**: [specs/001-build-an-application/](specs/001-build-an-application/)

---

## ✅ Checklist for New Pipeline

- [ ] Define pipeline JSON configuration
- [ ] Store credentials in Vault
- [ ] Test Vault connectivity
- [ ] Validate JSON syntax (`jq .`)
- [ ] Test locally first
- [ ] Monitor logs for errors
- [ ] Check Spark UI for progress
- [ ] Verify output data

---

**Quick Reference Version**: 1.0
**Status**: Production Ready ✅
**Tests**: 151 passing (100%)
