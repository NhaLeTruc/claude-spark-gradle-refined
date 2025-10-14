# Deployment Guide - Data Pipeline Orchestration Application

**Version**: 1.0-SNAPSHOT (MVP)
**Date**: October 14, 2025

---

## 📋 Prerequisites

### Required Software
- **Java**: JDK 17 or higher
- **Gradle**: 8.5+ (or use included wrapper `./gradlew`)
- **Apache Spark**: 3.5.6 (for cluster deployment)
- **HashiCorp Vault**: Latest version (for credential management)

### Optional
- **Docker**: For local testing environment (Phase 7+)
- **Kubernetes**: For K8s deployment
- **YARN**: For Hadoop cluster deployment

---

## 🏗️ Build Instructions

### 1. Clone and Build

```bash
cd claude-spark-gradle-two

# Build both JARs
./gradlew clean build shadowJar

# Run tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport
```

**Output**:
- `build/libs/pipeline-app-1.0-SNAPSHOT.jar` - CLI JAR (includes Spark)
- `build/libs/pipeline-app-1.0-SNAPSHOT-all.jar` - Uber-JAR (excludes Spark for cluster)

### 2. Verify Build

```bash
# Check JAR exists
ls -lh build/libs/*.jar

# Verify manifest
jar tf build/libs/pipeline-app-1.0-SNAPSHOT.jar | grep -i manifest

# Test run (will fail without config, but verifies JAR works)
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar --help
```

---

## 🔐 Vault Setup

### 1. Install Vault

```bash
# macOS
brew install vault

# Linux
wget https://releases.hashicorp.com/vault/1.15.0/vault_1.15.0_linux_amd64.zip
unzip vault_1.15.0_linux_amd64.zip
sudo mv vault /usr/local/bin/

# Verify
vault --version
```

### 2. Start Vault (Development Mode)

```bash
# Start Vault server in dev mode
vault server -dev

# In another terminal, set environment variables (from Vault output)
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='hvs.XXXXXXXXXXXX'  # Use token from Vault output
```

### 3. Store Credentials

**PostgreSQL**:
```bash
vault kv put secret/postgres \
  host=localhost \
  port=5432 \
  database=mydb \
  username=postgres \
  password=mysecurepassword
```

**MySQL**:
```bash
vault kv put secret/mysql \
  host=localhost \
  port=3306 \
  database=mydb \
  username=root \
  password=mysecurepassword
```

**S3 (IAM)**:
```bash
vault kv put secret/s3 \
  accessKeyId=AKIAIOSFODNN7EXAMPLE \
  secretAccessKey=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY \
  region=us-west-2
```

**Kafka**:
```bash
vault kv put secret/kafka \
  bootstrap.servers=localhost:9092 \
  security.protocol=SASL_SSL \
  sasl.mechanism=PLAIN \
  sasl.jaas.config='org.apache.kafka.common.security.plain.PlainLoginModule required username="user" password="pass";'
```

### 4. Verify Credentials

```bash
# Read back to verify
vault kv get secret/postgres
vault kv get secret/s3
```

---

## 🚀 Deployment Options

### Option 1: Local CLI Execution

**Use Case**: Development, testing, small datasets

```bash
# Export Vault credentials
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='your-vault-token'

# Run pipeline
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar \
  config/examples/simple-etl.json
```

**Java Options**:
```bash
java -Xmx4g -XX:+UseG1GC \
  -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar \
  config/examples/simple-etl.json
```

---

### Option 2: Spark Standalone Cluster

**Use Case**: Dedicated Spark cluster, on-premise deployment

**Prerequisites**:
- Spark 3.5.6 standalone cluster running
- Master at `spark://master-host:7077`

```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master spark://master-host:7077 \
  --deploy-mode cluster \
  --executor-memory 4G \
  --num-executors 5 \
  --executor-cores 2 \
  --conf spark.executor.memoryOverhead=1G \
  --conf spark.dynamicAllocation.enabled=false \
  --files config/examples/simple-etl.json \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  simple-etl.json
```

**Environment Variables** (on Spark cluster):
```bash
export VAULT_ADDR='http://vault.company.com:8200'
export VAULT_TOKEN='production-token'
```

---

### Option 3: YARN Cluster

**Use Case**: Hadoop ecosystem, existing YARN infrastructure

```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master yarn \
  --deploy-mode cluster \
  --executor-memory 8G \
  --num-executors 10 \
  --executor-cores 4 \
  --conf spark.yarn.maxAppAttempts=3 \
  --conf spark.executor.memoryOverhead=2G \
  --conf spark.driver.memory=4G \
  --files config/examples/simple-etl.json \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  simple-etl.json
```

**HDFS Configuration Path**:
```bash
--files hdfs:///configs/simple-etl.json
```

---

### Option 4: Kubernetes

**Use Case**: Cloud-native deployment, auto-scaling

**Prerequisites**:
- Kubernetes cluster with Spark operator or native Spark on K8s
- Docker image with Spark 3.5.6

**Create ConfigMap**:
```bash
kubectl create configmap pipeline-config \
  --from-file=config/examples/simple-etl.json
```

**Submit Job**:
```bash
spark-submit \
  --class com.pipeline.cli.PipelineRunner \
  --master k8s://https://kubernetes.default.svc:443 \
  --deploy-mode cluster \
  --name pipeline-job \
  --conf spark.executor.instances=5 \
  --conf spark.executor.memory=4G \
  --conf spark.executor.cores=2 \
  --conf spark.kubernetes.container.image=spark:3.5.6 \
  --conf spark.kubernetes.container.image.pullPolicy=Always \
  --conf spark.kubernetes.namespace=data-pipelines \
  --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
  --conf spark.kubernetes.file.upload.path=s3a://spark-temp/ \
  build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  /opt/spark/conf/simple-etl.json
```

**With Vault Sidecar**:
```yaml
apiVersion: v1
kind: Pod
metadata:
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "pipeline-app"
    vault.hashicorp.com/agent-inject-secret-config: "secret/data/postgres"
spec:
  containers:
  - name: spark-driver
    image: spark:3.5.6
```

---

### Option 5: Databricks

**Use Case**: Managed Spark platform, integrated with Delta Lake

**Upload JAR**:
```bash
databricks fs cp build/libs/pipeline-app-1.0-SNAPSHOT-all.jar \
  dbfs:/jars/pipeline-app.jar
```

**Create Job**:
```json
{
  "name": "Pipeline Orchestration Job",
  "new_cluster": {
    "spark_version": "13.3.x-scala2.12",
    "node_type_id": "i3.xlarge",
    "num_workers": 5
  },
  "libraries": [
    {"jar": "dbfs:/jars/pipeline-app.jar"}
  ],
  "spark_jar_task": {
    "main_class_name": "com.pipeline.cli.PipelineRunner",
    "parameters": ["dbfs:/configs/simple-etl.json"]
  }
}
```

---

## 🔧 Configuration Management

### Environment-Specific Configs

**Development**:
```bash
config/
├── dev/
│   └── simple-etl.json
├── staging/
│   └── simple-etl.json
└── production/
    └── simple-etl.json
```

**Vault Paths**:
- Dev: `secret/dev/postgres`
- Staging: `secret/staging/postgres`
- Production: `secret/production/postgres`

### Configuration Validation

```bash
# Validate JSON syntax
cat config/examples/simple-etl.json | jq .

# Test config parsing (dry-run mode - Phase 8+)
java -jar build/libs/pipeline-app-1.0-SNAPSHOT.jar \
  --validate-only \
  config/examples/simple-etl.json
```

---

## 📊 Monitoring

### Spark UI

**Local**: http://localhost:4040
**Cluster**: http://spark-master:8080

### Logs

**Local**:
```bash
tail -f logs/pipeline.log
```

**Cluster** (YARN):
```bash
yarn logs -applicationId application_XXXXXXXXX_XXXX
```

**Cluster** (K8s):
```bash
kubectl logs -f spark-driver-pod
```

### Structured Logging

All logs are JSON-formatted with correlation IDs:
```json
{
  "timestamp": "2025-10-14T20:55:29.422Z",
  "level": "INFO",
  "message": "Starting pipeline execution",
  "pipelineName": "simple-etl-pipeline",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Query with jq**:
```bash
cat logs/pipeline.log | jq 'select(.level=="ERROR")'
cat logs/pipeline.log | jq 'select(.pipelineName=="simple-etl-pipeline")'
```

---

## 🐛 Troubleshooting

### Build Issues

**Problem**: Tests fail
```bash
# Run specific test
./gradlew test --tests "com.pipeline.unit.core.PipelineTest"

# Skip tests temporarily
./gradlew build -x test
```

**Problem**: Out of memory
```bash
# Increase Gradle memory
export GRADLE_OPTS="-Xmx4g"
./gradlew clean build
```

### Runtime Issues

**Problem**: Vault connection failed
```bash
# Verify Vault is accessible
curl $VAULT_ADDR/v1/sys/health

# Check token
vault token lookup

# Test secret access
vault kv get secret/postgres
```

**Problem**: Spark driver OOM
```bash
# Increase driver memory
spark-submit --driver-memory 8G ...

# Adjust executor memory
spark-submit --executor-memory 16G ...
```

**Problem**: S3 access denied
```bash
# Verify IAM credentials
aws sts get-caller-identity --profile default

# Test S3 access
aws s3 ls s3://your-bucket/ --profile default
```

---

## 🔄 CI/CD Integration

### GitHub Actions

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build
        run: ./gradlew build
      - name: Test
        run: ./gradlew test
      - name: Coverage
        run: ./gradlew jacocoTestReport
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh './gradlew clean build shadowJar'
            }
        }
        stage('Test') {
            steps {
                sh './gradlew test'
            }
        }
        stage('Deploy') {
            steps {
                sh 'spark-submit --class com.pipeline.cli.PipelineRunner ...'
            }
        }
    }
}
```

---

## 📈 Performance Tuning

### Spark Configuration

```bash
spark-submit \
  --conf spark.sql.adaptive.enabled=true \
  --conf spark.sql.adaptive.coalescePartitions.enabled=true \
  --conf spark.sql.shuffle.partitions=200 \
  --conf spark.default.parallelism=100 \
  --conf spark.memory.fraction=0.8 \
  --conf spark.memory.storageFraction=0.3 \
  ...
```

### S3 Optimization

```bash
--conf spark.hadoop.fs.s3a.connection.maximum=100 \
--conf spark.hadoop.fs.s3a.threads.max=50 \
--conf spark.hadoop.fs.s3a.fast.upload=true \
--conf spark.hadoop.fs.s3a.block.size=128M
```

---

## 🔐 Security Best Practices

1. **Never commit credentials** - Use Vault exclusively
2. **Rotate Vault tokens** - Use short-lived tokens in production
3. **Enable TLS** - Use HTTPS for Vault communication
4. **Audit logging** - Enable Vault audit logs
5. **Network isolation** - Deploy Vault in secure subnet
6. **IAM roles** - Use instance profiles instead of access keys where possible

---

## 📞 Support

For issues, refer to:
- **README.md** - User guide
- **IMPLEMENTATION_SUMMARY.md** - Technical details
- **specs/001-build-an-application/** - Full specifications

---

**Deployment Guide Version**: 1.0
**Last Updated**: October 14, 2025
**Status**: Production Ready ✅
