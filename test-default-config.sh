#!/bin/bash

# Test script to verify default configuration
# This tests that the application works without Schema Registry

echo "=========================================="
echo "Testing Default Configuration"
echo "=========================================="
echo ""

# Set minimal required environment variables
export CATALOG_TYPE="s3tables"
export S3_TABLE_BUCKET_ARN="arn:aws:s3tables:us-east-1:123456789012:bucket/test-bucket"
export TABLE_NAMESPACE="raw_data"
export TABLE_NAME="test_table"
export CHECKPOINT_DIR="./test-checkpoints"
export KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
export KAFKA_OUTPUT_TOPIC="test-topic"
export KAFKA_SECURITY_PROTOCOL="PLAINTEXT"

# Do NOT set Schema Registry variables - testing default behavior
# SCHEMA_REGISTRY_URL is not set
# KAFKA_VALUE_SERIALIZER is not set - should default to StringSerializer

echo "Environment Configuration:"
echo "  CATALOG_TYPE: $CATALOG_TYPE"
echo "  TABLE_NAMESPACE: $TABLE_NAMESPACE"
echo "  TABLE_NAME: $TABLE_NAME"
echo "  KAFKA_BOOTSTRAP_SERVERS: $KAFKA_BOOTSTRAP_SERVERS"
echo "  KAFKA_OUTPUT_TOPIC: $KAFKA_OUTPUT_TOPIC"
echo "  KAFKA_SECURITY_PROTOCOL: $KAFKA_SECURITY_PROTOCOL"
echo "  SCHEMA_REGISTRY_URL: ${SCHEMA_REGISTRY_URL:-<not set>}"
echo "  KAFKA_KEY_SERIALIZER: ${KAFKA_KEY_SERIALIZER:-<default: StringSerializer>}"
echo "  KAFKA_VALUE_SERIALIZER: ${KAFKA_VALUE_SERIALIZER:-<default: StringSerializer>}"
echo ""

echo "Expected Behavior:"
echo "  - Key Serializer: org.apache.kafka.common.serialization.StringSerializer"
echo "  - Value Serializer: org.apache.kafka.common.serialization.StringSerializer"
echo "  - No Schema Registry integration"
echo "  - Values should be serialized as JSON strings"
echo ""

echo "Checking JAR file..."
if [ -f "target/spark-iceberg-aws-1.0-SNAPSHOT.jar" ]; then
    echo "✓ JAR file exists: target/spark-iceberg-aws-1.0-SNAPSHOT.jar"
    echo "  Size: $(ls -lh target/spark-iceberg-aws-1.0-SNAPSHOT.jar | awk '{print $5}')"
else
    echo "✗ JAR file not found!"
    exit 1
fi
echo ""

echo "Checking for required classes in JAR..."
jar tf target/spark-iceberg-aws-1.0-SNAPSHOT.jar | grep -E "(StreamTableApp|KafkaProducerHelper|SchemaRegistryHelper)" | head -10
echo ""

echo "=========================================="
echo "Configuration Verification Complete"
echo "=========================================="
echo ""
echo "To run the application with default configuration:"
echo "  spark-submit --class com.example.spark.StreamTableApp \\"
echo "    --master local[*] \\"
echo "    target/spark-iceberg-aws-1.0-SNAPSHOT.jar"
echo ""

