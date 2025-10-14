#!/bin/bash
# Kafka Topics Initialization Script
# Creates topics for pipeline testing

set -e

echo "Waiting for Kafka to be ready..."
sleep 10

# Create topics for testing
kafka-topics --create --if-not-exists \
  --bootstrap-server localhost:9092 \
  --topic user-events \
  --partitions 3 \
  --replication-factor 1

kafka-topics --create --if-not-exists \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --partitions 3 \
  --replication-factor 1

kafka-topics --create --if-not-exists \
  --bootstrap-server localhost:9092 \
  --topic product-updates \
  --partitions 2 \
  --replication-factor 1

kafka-topics --create --if-not-exists \
  --bootstrap-server localhost:9092 \
  --topic test-output \
  --partitions 2 \
  --replication-factor 1

echo "Kafka topics created successfully:"
kafka-topics --list --bootstrap-server localhost:9092

# Produce some test messages to user-events topic
echo "Publishing sample messages to user-events topic..."
echo '{"user_id": 1, "event_type": "login", "timestamp": 1697000000}' | kafka-console-producer --bootstrap-server localhost:9092 --topic user-events
echo '{"user_id": 2, "event_type": "purchase", "amount": 299.99, "timestamp": 1697000060}' | kafka-console-producer --bootstrap-server localhost:9092 --topic user-events
echo '{"user_id": 1, "event_type": "logout", "timestamp": 1697001800}' | kafka-console-producer --bootstrap-server localhost:9092 --topic user-events

echo "Kafka initialization complete!"
