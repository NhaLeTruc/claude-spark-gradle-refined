# Docker Local Testing Environment

This directory contains Docker Compose configuration and initialization scripts for local development and integration testing of the Data Pipeline Orchestration Application.

## Services Included

The `docker-compose.yml` in the root directory provides the following services:

| Service | Port | Purpose | Health Check |
|---------|------|---------|--------------|
| **PostgreSQL** | 5432 | Relational database for testing JDBC extract/load | pg_isready |
| **MySQL** | 3306 | Alternative JDBC source for multi-source pipelines | mysqladmin ping |
| **Kafka + Zookeeper** | 29092, 9092, 2181 | Message streaming for real-time pipelines | broker API check |
| **HashiCorp Vault** | 8200 | Secrets management for credentials | vault status |
| **MinIO** | 9000, 9001 | S3-compatible object storage | health endpoint |

## Quick Start

### 1. Copy Environment Variables

```bash
cp .env.example .env
# Edit .env if needed (defaults work for local testing)
```

### 2. Start All Services

```bash
docker-compose up -d
```

This will:
- Start all services in detached mode
- Initialize databases with sample data
- Create Kafka topics
- Populate Vault with credentials
- Create MinIO buckets with sample files

### 3. Verify Services

```bash
# Check all services are healthy
docker-compose ps

# View logs for a specific service
docker-compose logs -f postgres
docker-compose logs -f kafka
docker-compose logs -f vault
```

### 4. Access Services

- **PostgreSQL**: `jdbc:postgresql://localhost:5432/pipelinedb`
  - User: `pipelineuser`
  - Password: `pipelinepass`

- **MySQL**: `jdbc:mysql://localhost:3306/pipelinedb`
  - User: `pipelineuser`
  - Password: `pipelinepass`

- **Kafka**: `localhost:29092` (from host) or `kafka:9092` (from containers)

- **Vault UI**: http://localhost:8200
  - Token: `dev-token` (see .env)

- **MinIO Console**: http://localhost:9001
  - User: `minioadmin`
  - Password: `minioadmin`

### 5. Stop Services

```bash
# Stop all services
docker-compose stop

# Stop and remove containers (keeps volumes)
docker-compose down

# Remove everything including volumes (data loss!)
docker-compose down -v
```

## Initialization Scripts

### PostgreSQL (`postgres/init.sql`)
Creates tables:
- `users` - 10 sample users for simple ETL
- `orders` - 12 sample orders for join testing
- `products` - 12 products for multi-source testing
- `events` - 10 events for streaming testing

### MySQL (`mysql/init.sql`)
Creates tables:
- `customers` - 10 customers for multi-source joins
- `transactions` - 15 transactions for aggregation
- `product_inventory` - 12 inventory records for validation
- `sales_metrics` - 7 days of metrics for incremental loads

### Kafka (`kafka/topics.sh`)
Creates topics:
- `user-events` (3 partitions)
- `order-events` (3 partitions)
- `product-updates` (2 partitions)
- `test-output` (2 partitions)

Also publishes 3 sample JSON messages to `user-events`.

### Vault (`vault/init-vault.sh`)
Stores credentials at:
- `secret/database/postgres` - PostgreSQL connection details
- `secret/database/mysql` - MySQL connection details
- `secret/aws/s3` - MinIO/S3 credentials
- `secret/kafka/default` - Kafka bootstrap servers
- `secret/deltalake/default` - DeltaLake configuration

### MinIO (`minio/init-buckets.sh`)
Creates buckets:
- `pipeline-data` - General data storage
- `pipeline-delta` - DeltaLake tables
- `pipeline-avro` - Avro files
- `pipeline-parquet` - Parquet files
- `pipeline-json` - JSON files
- `pipeline-csv` - CSV files

Uploads sample files:
- `pipeline-data/sample/data.csv`
- `pipeline-data/sample/data.json`

## Testing Pipeline Examples

Once services are running, you can test example pipelines:

```bash
# Simple ETL: PostgreSQL → Transform → S3
./bin/run-local.sh config/examples/simple-etl.json

# Multi-source: PostgreSQL + MySQL → Join → DeltaLake
./bin/run-local.sh config/examples/multi-source-join.json

# Streaming: Kafka → Transform → DeltaLake
./bin/run-local.sh config/examples/streaming-kafka.json
```

## Troubleshooting

### Services Won't Start

```bash
# Check Docker daemon is running
docker info

# Check for port conflicts
lsof -i :5432
lsof -i :3306
lsof -i :8200

# View service logs
docker-compose logs <service-name>
```

### Database Connection Issues

```bash
# Test PostgreSQL connection
docker exec -it pipeline-postgres psql -U pipelineuser -d pipelinedb -c "SELECT COUNT(*) FROM users;"

# Test MySQL connection
docker exec -it pipeline-mysql mysql -u pipelineuser -ppipelinepass -e "SELECT COUNT(*) FROM customers;"
```

### Vault Not Initialized

```bash
# Restart vault initialization
docker-compose restart vault-init

# Manually check Vault
docker exec -it pipeline-vault vault status
docker exec -it pipeline-vault vault kv list secret/database/
```

### Kafka Issues

```bash
# List topics
docker exec -it pipeline-kafka kafka-topics --list --bootstrap-server localhost:9092

# Consume test messages
docker exec -it pipeline-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic user-events \
  --from-beginning
```

### MinIO Access Issues

```bash
# Check MinIO health
curl http://localhost:9000/minio/health/live

# Restart MinIO initialization
docker-compose restart minio-init

# List buckets
docker exec -it pipeline-minio mc ls myminio/
```

## Network Configuration

All services are connected via the `pipeline-network` bridge network. Services can communicate using their container names:

- From pipeline app: `postgres:5432`, `mysql:3306`, `kafka:9092`, `vault:8200`, `minio:9000`
- From host machine: `localhost:<port>` (see table above)

## Data Persistence

Volumes are used for data persistence:
- `postgres_data` - PostgreSQL database files
- `mysql_data` - MySQL database files
- `kafka_data` - Kafka logs and topics
- `vault_data` - Vault storage backend
- `minio_data` - MinIO object storage

To completely reset the environment:

```bash
docker-compose down -v
docker-compose up -d
```

## Development Workflow

1. **Make code changes** in your IDE
2. **Rebuild application**: `./gradlew build`
3. **Test against local services**: `./bin/run-local.sh config/examples/simple-etl.json`
4. **View logs**: Check console output and `docker-compose logs`
5. **Iterate**: Repeat as needed

## Integration Tests

To run integration tests against the Docker environment:

```bash
# Ensure services are running
docker-compose up -d

# Run integration tests
./gradlew integrationTest

# Or run all tests
./gradlew test
```

## Production Deployment

This Docker environment is for **local development only**. For production:

1. Use managed services (RDS, MSK, S3, etc.)
2. Store production credentials in HashiCorp Vault or AWS Secrets Manager
3. Use `.env.production` or environment-specific configuration
4. Deploy application using spark-submit to production Spark cluster

See [DEPLOYMENT.md](../DEPLOYMENT.md) for production deployment guidelines.

## Additional Resources

- [Main README](../README.md) - Application overview and quickstart
- [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) - Detailed troubleshooting guide
- [PERFORMANCE_GUIDE.md](../PERFORMANCE_GUIDE.md) - Optimization techniques
- [Example Pipelines](../config/examples/) - Sample JSON configurations

## Support

For issues specific to Docker environment:
1. Check service logs: `docker-compose logs <service>`
2. Verify health checks: `docker-compose ps`
3. Review initialization scripts in `docker/` directory
4. Consult TROUBLESHOOTING.md for common issues

For application issues, see the main project documentation.
