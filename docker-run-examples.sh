#!/bin/bash
# Docker Run Examples with Different AWS Credentials Methods

# =============================================================================
# Example 1: Static AWS Access/Secret Keys
# =============================================================================
# Use this when you have long-lived IAM user credentials
# NOT RECOMMENDED for production (credentials don't expire)

docker run -d \
  --name spark-local \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e SPARK_DRIVER_MEMORY=2g \
  -e SPARK_DRIVER_CORES=2 \
  -e AWS_REGION=eu-central-1 \
  -e AWS_ACCESS_KEY_ID="AKIAIOSFODNN7EXAMPLE" \
  -e AWS_SECRET_ACCESS_KEY="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY" \
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
  spark-stream-table:latest


# =============================================================================
# Example 2: IAM Role for EKS Pod (IRSA - IAM Roles for Service Accounts)
# =============================================================================
# Use this when running in EKS with pod-level IAM roles
# The pod automatically gets credentials from the IAM role via OIDC
# No need to pass credentials - they're injected by EKS

# Prerequisites:
# 1. Create IAM role with trust policy for your EKS cluster OIDC provider
# 2. Annotate Kubernetes service account with IAM role ARN
# 3. EKS automatically injects AWS_WEB_IDENTITY_TOKEN_FILE and AWS_ROLE_ARN

# Kubernetes Deployment Example:
cat <<'EOF'
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spark-app-sa
  namespace: default
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::667654397356:role/SparkAppRole
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spark-stream-table
  namespace: default
spec:
  replicas: 1
  selector:
    matchLabels:
      app: spark-stream-table
  template:
    metadata:
      labels:
        app: spark-stream-table
    spec:
      serviceAccountName: spark-app-sa  # This enables IRSA
      containers:
      - name: spark-app
        image: spark-stream-table:latest
        env:
        - name: SPARK_MODE
          value: "local"
        - name: PARALLELISM
          value: "4"
        - name: SPARK_DRIVER_MEMORY
          value: "2g"
        - name: SPARK_DRIVER_CORES
          value: "2"
        - name: AWS_REGION
          value: "eu-central-1"
        # No need to set AWS credentials - EKS injects them via IRSA:
        # AWS_WEB_IDENTITY_TOKEN_FILE, AWS_ROLE_ARN, AWS_ROLE_SESSION_NAME
        - name: CATALOG_TYPE
          value: "s3tables"
        - name: S3_TABLE_BUCKET_ARN
          value: "arn:aws:s3tables:eu-central-1:667654397356:bucket/iceberg-warehouse1"
        - name: TABLE_NAMESPACE
          value: "raw_data"
        - name: TABLE_NAME
          value: "test_table"
        - name: CHECKPOINT_DIR
          value: "s3a://conf-parq/spark-checkpoints"
        - name: TRIGGER_INTERVAL
          value: "10 seconds"
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "bastion.ocp.tangram-soft.com:31700"
        - name: KAFKA_OUTPUT_TOPIC
          value: "iceberg-output-topic"
        - name: KAFKA_SECURITY_PROTOCOL
          value: "PLAINTEXT"
        ports:
        - containerPort: 4040
          name: spark-ui
EOF

# For testing IRSA locally with docker (simulating EKS behavior):
# You would need to set up web identity token file and role ARN
docker run -d \
  --name spark-local \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e SPARK_DRIVER_MEMORY=2g \
  -e SPARK_DRIVER_CORES=2 \
  -e AWS_REGION=eu-central-1 \
  -e AWS_ROLE_ARN="arn:aws:iam::667654397356:role/SparkAppRole" \
  -e AWS_WEB_IDENTITY_TOKEN_FILE="/var/run/secrets/eks.amazonaws.com/serviceaccount/token" \
  -v "/path/to/token:/var/run/secrets/eks.amazonaws.com/serviceaccount/token:ro" \
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
  spark-stream-table:latest


# =============================================================================
# Example 3: STS Temporary Credentials (AWS_SESSION_TOKEN)
# =============================================================================
# Use this for temporary credentials obtained via STS AssumeRole
# RECOMMENDED for production (credentials expire, more secure)
# This is what the run-docker-with-aws.sh script does automatically

# Manual method:
# Step 1: Get temporary credentials
CREDS=$(aws sts assume-role \
  --role-arn arn:aws:iam::667654397356:role/AlexeyLocalAppRole \
  --role-session-name docker-session \
  --duration-seconds 3600 \
  --output json)

AWS_ACCESS_KEY_ID=$(echo $CREDS | jq -r '.Credentials.AccessKeyId')
AWS_SECRET_ACCESS_KEY=$(echo $CREDS | jq -r '.Credentials.SecretAccessKey')
AWS_SESSION_TOKEN=$(echo $CREDS | jq -r '.Credentials.SessionToken')

# Step 2: Run container with temporary credentials
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
  spark-stream-table:latest

# Or use the automated script:
# ./run-docker-with-aws.sh


# =============================================================================
# Example 4: EC2 Instance Profile (IMDSv2)
# =============================================================================
# Use this when running on EC2 with an instance profile attached
# The container automatically fetches credentials from EC2 metadata service
# No credentials need to be passed - AWS SDK uses default credential chain

docker run -d \
  --name spark-local \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e SPARK_DRIVER_MEMORY=2g \
  -e SPARK_DRIVER_CORES=2 \
  -e AWS_REGION=eu-central-1 \
  # No AWS credentials env vars needed - fetched from EC2 metadata
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
  spark-stream-table:latest


# =============================================================================
# Example 5: AWS Profile from mounted credentials file
# =============================================================================
# Use this when you have AWS CLI profiles configured
# Works with both static credentials and SSO-based profiles (if cached)

docker run -d \
  --name spark-local \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e SPARK_DRIVER_MEMORY=2g \
  -e SPARK_DRIVER_CORES=2 \
  -e AWS_REGION=eu-central-1 \
  -e AWS_PROFILE=AlexeyLocalAppRole \
  -v "$HOME/.aws:/root/.aws:ro" \
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
  spark-stream-table:latest


# =============================================================================
# Credential Priority (AWS SDK Default Credential Provider Chain)
# =============================================================================
# The AWS SDK looks for credentials in this order:
# 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)
# 2. Web Identity Token (AWS_WEB_IDENTITY_TOKEN_FILE + AWS_ROLE_ARN) - Used by EKS IRSA
# 3. AWS Profile from credentials/config files (AWS_PROFILE or default)
# 4. ECS container credentials (AWS_CONTAINER_CREDENTIALS_RELATIVE_URI)
# 5. EC2 instance profile credentials (IMDSv2)
#
# Choose the method that best fits your environment and security requirements.

# =============================================================================
# Security Best Practices
# =============================================================================
# ✓ Use temporary credentials (STS, IRSA) instead of static keys
# ✓ Use IAM roles whenever possible (EC2, EKS, ECS)
# ✓ Never hardcode credentials in code or Dockerfiles
# ✓ Use secrets management (AWS Secrets Manager, Kubernetes Secrets) for sensitive data
# ✓ Set appropriate IAM policies with least privilege
# ✓ Rotate credentials regularly
# ✓ Enable CloudTrail to audit AWS API calls
