#!/bin/bash

# ==============================================================================
# Spark Application Runner Script
# ==============================================================================
# This script runs the ReadTableApp with configured environment variables
#
# Usage:
#   ./run-read-table.sh [additional-spark-submit-options]
#
# Example:
#   ./run-read-table.sh --verbose
#   ./run-read-table.sh --driver-memory 2g
# ==============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# ==============================================================================
# Configuration
# ==============================================================================

# AWS Configuration
export AWS_REGION="${AWS_REGION:-eu-central-1}"
export AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-667654397356}"
export AWS_ROLE_NAME="${AWS_ROLE_NAME:-AlexeyLocalAppRole}"
export AWS_ROLE_ARN="arn:aws:iam::${AWS_ACCOUNT_ID}:role/${AWS_ROLE_NAME}"

# Session name for assume role
AWS_SESSION_NAME="spark-iceberg-session-$(date +%s)"

# Catalog Configuration
export CATALOG_TYPE="s3tables"
export S3_TABLE_BUCKET_ARN="${S3_TABLE_BUCKET_ARN:-arn:aws:s3tables:eu-central-1:667654397356:bucket/iceberg-warehouse1}"

# Table Configuration
export TABLE_NAMESPACE="${TABLE_NAMESPACE:-raw_data}"
export TABLE_NAME="${TABLE_NAME:-test_table}"

# Spark Configuration
export SPARK_MASTER="${SPARK_MASTER:-local[1]}"

# Spark Submit Options (can be overridden via environment variable)
SPARK_SUBMIT_OPTIONS="${SPARK_SUBMIT_OPTIONS:-}"

# Main class
MAIN_CLASS="com.example.spark.ReadTableApp"

# Project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="$PROJECT_DIR/target/spark-iceberg-aws-1.0-SNAPSHOT.jar"

# Spark installation directory
SPARK_HOME="${SPARK_HOME:-$PROJECT_DIR/spark-3.5.0-bin-hadoop3}"

# Add Spark to PATH if not already present
if [ -d "$SPARK_HOME" ]; then
    export PATH="$SPARK_HOME/bin:$PATH"
    print_info "Using Spark from: $SPARK_HOME"
else
    print_warning "SPARK_HOME not set or directory not found: $SPARK_HOME"
    print_info "Trying to use spark-submit from PATH"
fi

# ==============================================================================
# Pre-flight checks
# ==============================================================================

print_info "Starting Spark application..."
echo ""
print_info "Configuration:"
echo "  Spark Master: $SPARK_MASTER"
echo "  S3 Table Bucket ARN: $S3_TABLE_BUCKET_ARN"
echo "  Table Namespace: $TABLE_NAMESPACE"
echo "  Table Name: $TABLE_NAME"
echo "  AWS Region: $AWS_REGION"
echo "  AWS Role ARN: $AWS_ROLE_ARN"
echo ""

# ==============================================================================
# Assume AWS Role
# ==============================================================================

print_info "Assuming AWS role: $AWS_ROLE_NAME"

# Assume the role and capture the credentials
ASSUME_ROLE_OUTPUT=$(aws sts assume-role \
    --role-arn "$AWS_ROLE_ARN" \
    --role-session-name "$AWS_SESSION_NAME" \
    --region "$AWS_REGION" \
    --output json 2>&1)

if [ $? -ne 0 ]; then
    print_error "Failed to assume role: $AWS_ROLE_ARN"
    echo "$ASSUME_ROLE_OUTPUT"
    print_info "Make sure:"
    print_info "  1. You are logged in to AWS (aws sso login or aws configure)"
    print_info "  2. The role exists and trusts your user/role"
    print_info "  3. You have sts:AssumeRole permission"
    exit 1
fi

# Extract credentials from the assume-role output
export AWS_ACCESS_KEY_ID=$(echo "$ASSUME_ROLE_OUTPUT" | grep -o '"AccessKeyId": "[^"]*' | cut -d'"' -f4)
export AWS_SECRET_ACCESS_KEY=$(echo "$ASSUME_ROLE_OUTPUT" | grep -o '"SecretAccessKey": "[^"]*' | cut -d'"' -f4)
export AWS_SESSION_TOKEN=$(echo "$ASSUME_ROLE_OUTPUT" | grep -o '"SessionToken": "[^"]*' | cut -d'"' -f4)

if [ -z "$AWS_ACCESS_KEY_ID" ] || [ -z "$AWS_SECRET_ACCESS_KEY" ] || [ -z "$AWS_SESSION_TOKEN" ]; then
    print_error "Failed to extract credentials from assume-role response"
    exit 1
fi

print_success "Successfully assumed role: $AWS_ROLE_NAME"
echo "  Access Key: ${AWS_ACCESS_KEY_ID:0:10}..."
echo ""

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    print_error "JAR file not found: $JAR_FILE"
    print_info "Building project..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        print_error "Build failed!"
        exit 1
    fi
    print_success "Build completed"
fi

# Check if spark-submit is available
if ! command -v spark-submit &> /dev/null; then
    print_error "spark-submit not found in PATH"
    print_info "Please install Apache Spark or add it to your PATH"
    exit 1
fi

# ==============================================================================
# Run Spark Application
# ==============================================================================

print_info "Running Spark application..."
echo ""

# Combine additional arguments from command line
ADDITIONAL_ARGS="$@"

# Run spark-submit
# Note: Using temporary credentials from assumed role
spark-submit \
  --class "$MAIN_CLASS" \
  --master "$SPARK_MASTER" \
  --conf "spark.executorEnv.AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID" \
  --conf "spark.executorEnv.AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY" \
  --conf "spark.executorEnv.AWS_SESSION_TOKEN=$AWS_SESSION_TOKEN" \
  --conf "spark.executorEnv.S3_TABLE_BUCKET_ARN=$S3_TABLE_BUCKET_ARN" \
  --conf "spark.executorEnv.TABLE_NAMESPACE=$TABLE_NAMESPACE" \
  --conf "spark.executorEnv.TABLE_NAME=$TABLE_NAME" \
  --conf "spark.executorEnv.CATALOG_TYPE=$CATALOG_TYPE" \
  --conf "spark.executorEnv.AWS_REGION=$AWS_REGION" \
  $SPARK_SUBMIT_OPTIONS \
  $ADDITIONAL_ARGS \
  "$JAR_FILE"

EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    print_success "Application completed successfully!"
else
    print_error "Application failed with exit code: $EXIT_CODE"
fi

exit $EXIT_CODE
