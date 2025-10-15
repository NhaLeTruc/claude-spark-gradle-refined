#!/bin/bash
#
# Cluster execution script with local Spark standalone cluster
# Useful for testing cluster mode locally
#
# Usage: ./bin/run-cluster-local-master.sh <config-file.json>
#

set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <config-file.json>"
  echo "Example: $0 config/examples/simple-etl.json"
  exit 1
fi

CONFIG_FILE="$1"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: Config file not found: $CONFIG_FILE"
  exit 1
fi

# Set Vault environment variables if not already set
if [ -z "$VAULT_ADDR" ]; then
  echo "Warning: VAULT_ADDR not set. Using default: http://localhost:8200"
  export VAULT_ADDR="http://localhost:8200"
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

echo "Submitting pipeline to local Spark standalone cluster..."
echo "Config: $CONFIG_FILE"
echo "Master: local[4]"
echo "Shadow JAR: $SHADOW_JAR_PATH"
echo "Vault: $VAULT_ADDR"
echo ""

spark-submit \
  --master "local[4]" \
  --driver-memory 2g \
  --conf "spark.executor.memory=2g" \
  --conf "spark.sql.adaptive.enabled=true" \
  --conf "spark.sql.adaptive.coalescePartitions.enabled=true" \
  --conf "spark.serializer=org.apache.spark.serializer.KryoSerializer" \
  "$SHADOW_JAR_PATH" \
  "$CONFIG_FILE"

echo ""
echo "Pipeline completed successfully!"
