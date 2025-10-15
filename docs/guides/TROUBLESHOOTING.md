# Troubleshooting Guide

**Data Pipeline Orchestration Application**
**Last Updated**: October 14, 2025

---

## Table of Contents

1. [Build Issues](#build-issues)
2. [Runtime Errors](#runtime-errors)
3. [Vault Connection Issues](#vault-connection-issues)
4. [JDBC Connection Issues](#jdbc-connection-issues)
5. [S3 Access Issues](#s3-access-issues)
6. [Kafka Connection Issues](#kafka-connection-issues)
7. [Performance Issues](#performance-issues)
8. [Memory Issues](#memory-issues)
9. [Common Configuration Mistakes](#common-configuration-mistakes)

---

## Build Issues

### Issue: "Zip64RequiredException" when building shadow JAR

**Symptom**:
```
java.util.zip.ZipException: invalid entry compressed size
```

**Solution**:
Ensure `zip64 = true` is set in `build.gradle`:
```gradle
shadowJar {
    zip64 = true
}
```

---

### Issue: Java 17 module access errors during tests

**Symptom**:
```
java.lang.IllegalAccessError: class org.apache.spark.storage.StorageUtils$
cannot access class sun.nio.ch.DirectBuffer
```

**Solution**:
Add JVM arguments in `build.gradle`:
```gradle
test {
    jvmArgs '--add-opens=java.base/java.lang=ALL-UNNAMED',
            '--add-opens=java.base/sun.nio.ch=ALL-UNNAMED'
            // ... more module opens
}
```

---

### Issue: ScalaTest tests not running

**Symptom**:
```
No tests found
```

**Solution**:
1. Check `@RunWith(classOf[JUnitRunner])` annotation is present
2. Ensure `useJUnit()` in `build.gradle`, not `useJUnitPlatform()`
3. Verify ScalaTest dependency version compatibility

---

## Runtime Errors

### Issue: "Unknown extract method: fromXXX"

**Symptom**:
```
java.lang.IllegalArgumentException: Unknown extract method: fromPostgres
```

**Root Cause**: Method name typo or not implemented

**Solution**:
1. Check method name spelling in JSON config
2. Verify method is listed in `PipelineStep.scala` method dispatch
3. Supported methods:
   - Extract: `fromPostgres`, `fromMySQL`, `fromKafka`, `fromS3`, `fromDeltaLake`, `fromAvro`
   - Transform: `filterRows`, `enrichData`, `joinDataFrames`, `aggregateData`, `reshapeData`, `unionDataFrames`, `toAvroSchema`, `evolveAvroSchema`
   - Load: `toPostgres`, `toMySQL`, `toKafka`, `toS3`, `toDeltaLake`, `toAvro`
   - Validate: `validateSchema`, `validateNulls`, `validateRanges`, `validateReferentialIntegrity`, `validateBusinessRules`

---

### Issue: "Missing required field: table"

**Symptom**:
```
java.lang.IllegalArgumentException: requirement failed: 'table' is required
```

**Root Cause**: Required configuration parameter missing

**Solution**:
Check JSON config for required fields:

**PostgreSQL/MySQL**:
```json
{
  "type": "extract",
  "method": "fromPostgres",
  "config": {
    "table": "users",  // REQUIRED
    "credentialPath": "secret/data/postgres"
  }
}
```

**S3**:
```json
{
  "type": "load",
  "method": "toS3",
  "config": {
    "bucket": "my-bucket",  // REQUIRED
    "path": "/data/",       // REQUIRED
    "format": "parquet"
  }
}
```

---

### Issue: Configuration parsing errors

**Symptom**:
```
com.fasterxml.jackson.core.JsonParseException: Unexpected character
```

**Root Cause**: Invalid JSON syntax

**Solution**:
1. Validate JSON with online validator (jsonlint.com)
2. Common issues:
   - Missing commas between fields
   - Trailing commas (not allowed in strict JSON)
   - Unquoted keys
   - Single quotes instead of double quotes

---

## Vault Connection Issues

### Issue: "VAULT_ADDR not set"

**Symptom**:
```
java.lang.IllegalStateException: VAULT_ADDR not set
```

**Solution**:
```bash
export VAULT_ADDR="http://localhost:8200"
export VAULT_TOKEN="your-token-here"
```

For production:
```bash
export VAULT_ADDR="https://vault.company.com:8200"
export VAULT_TOKEN="$(cat /etc/vault-token)"
export VAULT_NAMESPACE="my-namespace"  # Optional
```

---

### Issue: "Failed to read credentials from Vault"

**Symptom**:
```
RuntimeException: Failed to read credentials from Vault: 403 permission denied
```

**Root Causes**:
1. Invalid token
2. Token expired
3. Insufficient permissions
4. Wrong secret path

**Solution**:

**Check token validity**:
```bash
curl -H "X-Vault-Token: $VAULT_TOKEN" \
     $VAULT_ADDR/v1/sys/health
```

**Check secret exists**:
```bash
vault kv get secret/data/postgres/production
```

**Verify permissions**:
```bash
vault token capabilities secret/data/postgres/production
# Should show: ["read"]
```

**Correct secret path format**:
- KV v2 (default): `secret/data/postgres/production`
- KV v1: `secret/postgres/production`

---

### Issue: Vault timeout in cluster mode

**Symptom**:
```
java.net.SocketTimeoutException: Read timed out
```

**Solution**:
Ensure Vault is accessible from executor nodes:

**Test from executor node**:
```bash
curl -H "X-Vault-Token: $VAULT_TOKEN" \
     $VAULT_ADDR/v1/sys/health
```

**Fix firewall rules** if needed to allow:
- Executor nodes → Vault server on port 8200

---

## JDBC Connection Issues

### Issue: "Connection refused" for PostgreSQL/MySQL

**Symptom**:
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused
```

**Root Causes**:
1. Database not running
2. Wrong host/port
3. Firewall blocking connection
4. Database not accepting remote connections

**Solution**:

**Check database is running**:
```bash
# PostgreSQL
pg_isready -h localhost -p 5432

# MySQL
mysqladmin ping -h localhost -P 3306
```

**Test connection manually**:
```bash
# PostgreSQL
psql -h localhost -p 5432 -U username -d database

# MySQL
mysql -h localhost -P 3306 -u username -p database
```

**Check PostgreSQL accepts remote connections**:
```bash
# Edit postgresql.conf
listen_addresses = '*'

# Edit pg_hba.conf
host    all    all    0.0.0.0/0    md5
```

**Check MySQL accepts remote connections**:
```bash
# Edit my.cnf
bind-address = 0.0.0.0
```

---

### Issue: "Authentication failed"

**Symptom**:
```
FATAL: password authentication failed for user "username"
```

**Root Cause**: Wrong credentials in Vault or config

**Solution**:

**Verify Vault secret format**:
```bash
vault kv get secret/data/postgres/production
```

Expected format:
```json
{
  "host": "localhost",
  "port": 5432,
  "database": "mydb",
  "username": "myuser",
  "password": "correct_password",
  "credentialType": "postgres"
}
```

**Test credentials manually**:
```bash
psql -h localhost -p 5432 -U myuser -d mydb
```

---

### Issue: Partition column errors

**Symptom**:
```
java.sql.SQLException: Column "id" not found
```

**Root Cause**: Partition column doesn't exist or has wrong type

**Solution**:
1. Verify column exists: `\d table_name` (PostgreSQL)
2. Use numeric column (INT, BIGINT, LONG)
3. Column should have good distribution
4. Remove partitioning if column is not suitable:

```json
{
  "table": "users"
  // Remove partitionColumn, numPartitions
}
```

---

## S3 Access Issues

### Issue: "Access Denied" when reading/writing S3

**Symptom**:
```
com.amazonaws.services.s3.model.AmazonS3Exception: Access Denied (403)
```

**Root Causes**:
1. Invalid AWS credentials
2. Insufficient IAM permissions
3. Bucket policy denies access
4. Wrong region

**Solution**:

**Check credentials in Vault**:
```bash
vault kv get secret/data/s3/production
```

Expected format:
```json
{
  "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
  "secretAccessKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
  "region": "us-east-1",
  "credentialType": "iam"
}
```

**Test S3 access manually**:
```bash
aws s3 ls s3://my-bucket/ --region us-east-1
```

**Required IAM permissions**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::my-bucket",
        "arn:aws:s3:::my-bucket/*"
      ]
    }
  ]
}
```

---

### Issue: S3 path format errors

**Symptom**:
```
java.lang.IllegalArgumentException: Invalid S3 URI
```

**Root Cause**: Wrong path format

**Solution**:
Use `s3a://` protocol (not `s3://` or `s3n://`):

✅ **Correct**:
```json
{
  "bucket": "my-bucket",
  "path": "/data/events/"
}
// Results in: s3a://my-bucket/data/events/
```

❌ **Incorrect**:
```json
{
  "path": "s3://my-bucket/data/"  // Wrong protocol
}
```

---

### Issue: "Slow S3 writes"

**Symptom**: Writing to S3 takes hours

**Root Cause**: Too many small files

**Solution**:

**Add coalesce before write**:
```json
{
  "type": "transform",
  "method": "coalesce",
  "config": {
    "numPartitions": 10
  }
},
{
  "type": "load",
  "method": "toS3",
  "config": {
    "bucket": "data-lake",
    "path": "/events/",
    "partitionBy": ["date"]
  }
}
```

**Target**: 100MB-1GB per file

---

## Kafka Connection Issues

### Issue: "Connection to node -1 could not be established"

**Symptom**:
```
org.apache.kafka.common.errors.TimeoutException: Failed to get offsets by times
```

**Root Cause**: Cannot connect to Kafka brokers

**Solution**:

**Check Kafka is running**:
```bash
kafka-broker-api-versions --bootstrap-server localhost:9092
```

**Verify bootstrap servers in config**:
```json
{
  "type": "extract",
  "method": "fromKafka",
  "config": {
    "topic": "events",
    "kafka.bootstrap.servers": "broker1:9092,broker2:9092,broker3:9092"
  }
}
```

**Check firewall**: Ensure executors can reach Kafka brokers

---

### Issue: "Topic does not exist"

**Symptom**:
```
org.apache.kafka.common.errors.UnknownTopicOrPartitionException
```

**Solution**:

**List available topics**:
```bash
kafka-topics --bootstrap-server localhost:9092 --list
```

**Create topic if needed**:
```bash
kafka-topics --bootstrap-server localhost:9092 \
  --create --topic events \
  --partitions 10 --replication-factor 3
```

---

### Issue: Kafka offset errors

**Symptom**:
```
org.apache.kafka.common.errors.OffsetOutOfRangeException
```

**Solution**:
Use valid starting offset strategy:

```json
{
  "startingOffsets": "earliest"  // or "latest"
}
```

For specific offsets:
```json
{
  "startingOffsets": "{\"events\":{\"0\":100,\"1\":200}}"
}
```

---

## Performance Issues

### Issue: Pipeline running very slowly

**Diagnostic Steps**:

1. **Check Spark UI** (`http://driver:4040`):
   - Identify slow stages
   - Look for data skew (long-running tasks)
   - Check shuffle read/write volumes

2. **Check logs for warnings**:
   ```bash
   grep WARN logs/pipeline.log
   ```

3. **Monitor resource usage**:
   - CPU utilization
   - Memory usage
   - Network I/O
   - Disk I/O

**Common Solutions**:
- Enable AQE: `spark.sql.adaptive.enabled=true`
- Partition JDBC reads
- Use Snappy compression
- Repartition data before writes
- See [PERFORMANCE_GUIDE.md](PERFORMANCE_GUIDE.md) for details

---

### Issue: Data skew in joins

**Symptom**: One task takes 10x longer than others

**Solution**:

**Enable skew join optimization**:
```json
{
  "spark.sql.adaptive.skewJoin.enabled": "true",
  "spark.sql.adaptive.skewJoin.skewedPartitionThresholdInBytes": "256MB"
}
```

**Manual salting** (if AQE doesn't help):
Add salt column before join, join on (key, salt), remove salt after

---

## Memory Issues

### Issue: "OutOfMemoryError: Java heap space"

**Symptom**:
```
java.lang.OutOfMemoryError: Java heap space
```

**Root Causes**:
1. collect() on large DataFrame
2. Insufficient executor memory
3. Too many executor cores per executor
4. Memory leak

**Solution**:

**Increase executor memory**:
```bash
spark-submit \
  --executor-memory 12g \
  --conf spark.executor.memoryOverhead=3g
```

**Reduce executor cores**:
```bash
spark-submit \
  --executor-cores 4  # Instead of 8
```

**Avoid collect()**:
- Use `take(n)` for sampling
- Use `show()` for debugging
- Write to disk instead of collecting

---

### Issue: "GC overhead limit exceeded"

**Symptom**:
```
java.lang.OutOfMemoryError: GC overhead limit exceeded
```

**Root Cause**: Too much time spent in garbage collection

**Solution**:

**Tune GC settings**:
```bash
spark-submit \
  --conf spark.executor.extraJavaOptions="-XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=35" \
  --conf spark.driver.extraJavaOptions="-XX:+UseG1GC"
```

**Reduce memory pressure**:
- Increase `spark.memory.fraction` to 0.7
- Decrease `spark.memory.storageFraction` to 0.3
- Add more executors with less memory each

---

## Common Configuration Mistakes

### Mistake: Credentials in JSON config

❌ **Wrong**:
```json
{
  "config": {
    "host": "localhost",
    "username": "myuser",
    "password": "mypassword"  // NEVER DO THIS
  }
}
```

✅ **Correct**:
```json
{
  "config": {
    "credentialPath": "secret/data/postgres/production"
  }
}
```

---

### Mistake: Missing registerAs for multi-DataFrame operations

❌ **Wrong**:
```json
{
  "type": "extract",
  "method": "fromPostgres",
  "config": {
    "table": "users"
    // Missing registerAs
  }
},
{
  "type": "transform",
  "method": "joinDataFrames",
  "config": {
    "inputDataFrames": ["users", "orders"]  // "users" not registered!
  }
}
```

✅ **Correct**:
```json
{
  "type": "extract",
  "method": "fromPostgres",
  "config": {
    "table": "users",
    "registerAs": "users"  // Register for later use
  }
}
```

---

### Mistake: Wrong save mode causing data loss

❌ **Dangerous**:
```json
{
  "type": "load",
  "method": "toS3",
  "config": {
    "path": "s3a://bucket/production-data/",
    "mode": "overwrite"  // Will delete all existing data!
  }
}
```

✅ **Safe**:
```json
{
  "type": "load",
  "method": "toS3",
  "config": {
    "path": "s3a://bucket/production-data/",
    "mode": "append",  // Safer default
    "partitionBy": ["date"]  // With partitioning
  }
}
```

---

## Getting Help

### Enable DEBUG Logging

Edit `src/main/resources/logback.xml`:
```xml
<logger name="com.pipeline" level="DEBUG"/>
```

### Check Application Logs

```bash
# Local mode
tail -f logs/application.log

# Cluster mode
yarn logs -applicationId application_xxx_yyy
```

### Spark UI

Local: http://localhost:4040
Cluster: Check YARN ResourceManager UI

### Report Issues

GitHub: https://github.com/your-org/pipeline-app/issues

Include:
1. Full error message
2. Pipeline JSON configuration
3. Spark version and cluster config
4. Relevant logs

---

**Last Updated**: October 14, 2025
**Version**: 1.0
