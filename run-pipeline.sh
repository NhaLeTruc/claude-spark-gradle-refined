#!/bin/bash

# Helper script to run the pipeline locally with required Java 17 module arguments
# Usage: ./run-pipeline.sh <path-to-config.json>

if [ -z "$1" ]; then
    echo "Usage: $0 <path-to-config.json>"
    echo "Example: $0 config/examples/simple-etl.json"
    exit 1
fi

CONFIG_FILE="$1"

if [ ! -f "$CONFIG_FILE" ]; then
    echo "Error: Config file not found: $CONFIG_FILE"
    exit 1
fi

JAR_FILE="build/libs/pipeline-app-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found: $JAR_FILE"
    echo "Please run './gradlew build' first"
    exit 1
fi

echo "Running pipeline with config: $CONFIG_FILE"
echo ""

java \
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
    --add-opens=java.security.jgss/sun.security.krb5=ALL-UNNAMED \
    -jar "$JAR_FILE" "$CONFIG_FILE"
