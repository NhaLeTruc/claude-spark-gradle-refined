#!/bin/bash
# MinIO Bucket Initialization Script
# Creates S3 buckets for pipeline testing

set -e

echo "Waiting for MinIO to be ready..."
sleep 10

# Configure MinIO client
mc alias set myminio http://minio:9000 ${MINIO_ACCESS_KEY:-minioadmin} ${MINIO_SECRET_KEY:-minioadmin}

# Create buckets
echo "Creating S3 buckets..."
mc mb --ignore-existing myminio/pipeline-data
mc mb --ignore-existing myminio/pipeline-delta
mc mb --ignore-existing myminio/pipeline-avro
mc mb --ignore-existing myminio/pipeline-parquet
mc mb --ignore-existing myminio/pipeline-json
mc mb --ignore-existing myminio/pipeline-csv

# Set bucket policies for testing
echo "Setting bucket policies..."
mc anonymous set download myminio/pipeline-data
mc anonymous set download myminio/pipeline-json
mc anonymous set download myminio/pipeline-csv

# Upload sample data files
echo "Uploading sample data files..."
# Create a sample CSV file
cat > /tmp/sample-data.csv <<EOF
id,name,value
1,item1,100
2,item2,200
3,item3,300
EOF

cat > /tmp/sample-data.json <<EOF
{"id": 1, "name": "item1", "value": 100}
{"id": 2, "name": "item2", "value": 200}
{"id": 3, "name": "item3", "value": 300}
EOF

mc cp /tmp/sample-data.csv myminio/pipeline-data/sample/data.csv
mc cp /tmp/sample-data.json myminio/pipeline-data/sample/data.json

echo "MinIO initialization complete!"
echo "Created buckets:"
mc ls myminio/
