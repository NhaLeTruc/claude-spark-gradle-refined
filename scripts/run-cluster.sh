#!/bin/bash
#
# Cluster execution script - submits pipeline to Spark cluster using spark-submit
#
# Usage: ./bin/run-cluster.sh <config-file.json> [spark-options]
#

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <config-file.json> [spark-options]"
  echo "Example: $0 config/examples/simple-etl.json --master yarn --deploy-mode cluster"
  exit 1
fi

CONFIG_FILE="$1"
shift  # Remove first argument, remaining args are spark options

if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: Config file not found: $CONFIG_FILE"
  exit 1
fi

# Set Vault environment variables if not already set
if [ -z "$VAULT_ADDR" ]; then
  echo "Error: VAULT_ADDR must be set for cluster execution"
  exit 1
fi

if [ -z "$VAULT_TOKEN" ]; then
  echo "Error: VAULT_TOKEN must be set"
  exit 1
fi

# Build the shadow JAR if it doesn't exist
SHADOW_JAR_PATH="build/libs/pipeline-app-all.jar"
if [ ! -f "$SHADOW_JAR_PATH" ]; then
  echo "Building shadow JAR..."
  ./gradlew shadowJar
fi

# Default Spark options
MASTER="${SPARK_MASTER:-yarn}"
DEPLOY_MODE="${DEPLOY_MODE:-cluster}"
DRIVER_MEMORY="${DRIVER_MEMORY:-2g}"
EXECUTOR_MEMORY="${EXECUTOR_MEMORY:-4g}"
EXECUTOR_CORES="${EXECUTOR_CORES:-2}"
NUM_EXECUTORS="${NUM_EXECUTORS:-4}"

echo "Submitting pipeline to Spark cluster..."
echo "Config: $CONFIG_FILE"
echo "Master: $MASTER"
echo "Deploy Mode: $DEPLOY_MODE"
echo "Shadow JAR: $SHADOW_JAR_PATH"
echo "Vault: $VAULT_ADDR"
echo ""

spark-submit \
  --master "$MASTER" \
  --deploy-mode "$DEPLOY_MODE" \
  --driver-memory "$DRIVER_MEMORY" \
  --executor-memory "$EXECUTOR_MEMORY" \
  --executor-cores "$EXECUTOR_CORES" \
  --num-executors "$NUM_EXECUTORS" \
  --conf "spark.executor.memoryOverhead=1g" \
  --conf "spark.driver.memoryOverhead=512m" \
  --conf "spark.sql.adaptive.enabled=true" \
  --conf "spark.sql.adaptive.coalescePartitions.enabled=true" \
  --conf "spark.serializer=org.apache.spark.serializer.KryoSerializer" \
  --conf "spark.kryoserializer.buffer.max=512m" \
  --conf "spark.network.timeout=600s" \
  --conf "spark.executor.heartbeatInterval=60s" \
  --conf "spark.dynamicAllocation.enabled=false" \
  --files "$CONFIG_FILE" \
  --conf "spark.executorEnv.VAULT_ADDR=$VAULT_ADDR" \
  --conf "spark.executorEnv.VAULT_TOKEN=$VAULT_TOKEN" \
  --conf "spark.yarn.appMasterEnv.VAULT_ADDR=$VAULT_ADDR" \
  --conf "spark.yarn.appMasterEnv.VAULT_TOKEN=$VAULT_TOKEN" \
  "$@" \
  "$SHADOW_JAR_PATH" \
  "$(basename $CONFIG_FILE)"

echo ""
echo "Pipeline submitted successfully!"
echo "Check YARN UI for application status"
