#!/bin/bash
# Script to run Spark Docker container with AWS credentials

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Running Spark Container with AWS Credentials ===${NC}"

# Get AWS credentials using STS
echo -e "${YELLOW}Fetching AWS credentials...${NC}"
CREDS=$(aws sts assume-role \
  --role-arn arn:aws:iam::667654397356:role/AlexeyLocalAppRole \
  --role-session-name docker-session \
  --duration-seconds 3600 \
  --output json)

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to fetch AWS credentials${NC}"
    exit 1
fi

# Extract credentials
AWS_ACCESS_KEY_ID=$(echo $CREDS | jq -r '.Credentials.AccessKeyId')
AWS_SECRET_ACCESS_KEY=$(echo $CREDS | jq -r '.Credentials.SecretAccessKey')
AWS_SESSION_TOKEN=$(echo $CREDS | jq -r '.Credentials.SessionToken')
EXPIRATION=$(echo $CREDS | jq -r '.Credentials.Expiration')

echo -e "${GREEN}✓ AWS Credentials fetched successfully${NC}"
echo -e "${YELLOW}Credentials expire at: ${EXPIRATION}${NC}"

# Remove old container if exists
echo -e "${YELLOW}Removing old container if exists...${NC}"
docker rm -f spark-local 2>/dev/null || true

# Run the container
echo -e "${YELLOW}Starting Docker container...${NC}"
docker run -d \
  --name spark-local \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e SPARK_DRIVER_MEMORY=2g \
  -e SPARK_DRIVER_CORES=2 \
  -e AWS_REGION=eu-central-1 \
  -e AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}" \
  -e AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}" \
  -e AWS_SESSION_TOKEN="${AWS_SESSION_TOKEN}" \
  -e CATALOG_TYPE=s3tables \
  -e S3_TABLE_BUCKET_ARN="arn:aws:s3tables:eu-central-1:667654397356:bucket/iceberg-warehouse1" \
  -e TABLE_NAMESPACE=raw_data \
  -e TABLE_NAME=test_table \
  -e CHECKPOINT_DIR="s3a://conf-parq/spark-checkpoints" \
  -e TRIGGER_INTERVAL="10 seconds" \
  -e KAFKA_BOOTSTRAP_SERVERS=bastion.ocp.tangram-soft.com:31700 \
  -e KAFKA_OUTPUT_TOPIC=iceberg-output-topic \
  -e KAFKA_SECURITY_PROTOCOL=PLAINTEXT \
  -p 4040:4040 \
  -v "$PWD/checkpoints:/app/checkpoints:Z" \
  spark-stream-table:latest

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Container started successfully${NC}"
    echo ""
    echo -e "${GREEN}Container name:${NC} spark-local"
    echo -e "${GREEN}Spark UI:${NC} http://localhost:4040"
    echo ""
    echo -e "${YELLOW}View logs:${NC} docker logs -f spark-local"
    echo -e "${YELLOW}Stop container:${NC} docker stop spark-local"
    echo -e "${YELLOW}Container status:${NC} docker ps | grep spark-local"
else
    echo -e "${RED}Failed to start container${NC}"
    exit 1
fi
