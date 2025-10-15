#!/bin/bash
#
# Local execution script - runs pipeline in local mode using standard JAR
#
# Usage: ./bin/run-local.sh <config-file.json>
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

# Build the project if JAR doesn't exist
JAR_PATH="build/libs/pipeline-app.jar"
if [ ! -f "$JAR_PATH" ]; then
  echo "Building project..."
  ./gradlew build
fi

# Run the pipeline in local mode
echo "Running pipeline in local mode..."
echo "Config: $CONFIG_FILE"
echo "Vault: $VAULT_ADDR"
echo ""

java -cp "$JAR_PATH" \
  -Xmx2g \
  -XX:+UseG1GC \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
  --add-opens=java.base/sun.security.action=ALL-UNNAMED \
  --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
  com.pipeline.cli.PipelineRunner "$CONFIG_FILE"

echo ""
echo "Pipeline completed successfully!"
