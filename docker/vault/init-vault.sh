#!/bin/sh
# Vault Initialization Script
# Populates Vault with credentials from environment variables

set -e

echo "Waiting for Vault to be ready..."
sleep 5

# Enable KV secrets engine v2
vault secrets enable -path=secret kv-v2 || echo "Secrets engine already enabled"

# Store PostgreSQL credentials
echo "Storing PostgreSQL credentials..."
vault kv put secret/database/postgres \
  host="postgres" \
  port="5432" \
  database="pipelinedb" \
  username="pipelineuser" \
  password="${POSTGRES_PASSWORD:-pipelinepass}"

# Store MySQL credentials
echo "Storing MySQL credentials..."
vault kv put secret/database/mysql \
  host="mysql" \
  port="3306" \
  database="pipelinedb" \
  username="pipelineuser" \
  password="${MYSQL_PASSWORD:-pipelinepass}"

# Store S3/MinIO credentials
echo "Storing S3/MinIO credentials..."
vault kv put secret/aws/s3 \
  accessKeyId="${MINIO_ACCESS_KEY:-minioadmin}" \
  secretAccessKey="${MINIO_SECRET_KEY:-minioadmin}" \
  region="us-east-1" \
  endpoint="http://minio:9000"

# Store Kafka credentials
echo "Storing Kafka credentials..."
vault kv put secret/kafka/default \
  bootstrap.servers="kafka:9092" \
  security.protocol="PLAINTEXT"

# Store DeltaLake configuration
echo "Storing DeltaLake configuration..."
vault kv put secret/deltalake/default \
  path="s3a://pipeline-delta" \
  accessKeyId="${MINIO_ACCESS_KEY:-minioadmin}" \
  secretAccessKey="${MINIO_SECRET_KEY:-minioadmin}" \
  endpoint="http://minio:9000"

echo "Vault initialization complete!"
echo "Stored credentials:"
vault kv list secret/database/
vault kv list secret/aws/
vault kv list secret/kafka/
vault kv list secret/deltalake/
