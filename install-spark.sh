#!/bin/bash

# ==============================================================================
# Spark Installation Script
# ==============================================================================
# This script downloads and installs Apache Spark 3.5.0 locally for the project
# ==============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPARK_VERSION="3.5.0"
SPARK_HADOOP_VERSION="hadoop3"
SPARK_ARCHIVE="spark-${SPARK_VERSION}-bin-${SPARK_HADOOP_VERSION}.tgz"
SPARK_DIR="spark-${SPARK_VERSION}-bin-${SPARK_HADOOP_VERSION}"
SPARK_URL="https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/${SPARK_ARCHIVE}"

cd "$PROJECT_DIR"

# Check if Spark is already installed
if [ -d "$SPARK_DIR" ]; then
    print_warning "Spark is already installed at: $SPARK_DIR"
    print_info "To reinstall, remove the directory first: rm -rf $SPARK_DIR"
    exit 0
fi

print_info "Installing Apache Spark ${SPARK_VERSION} with ${SPARK_HADOOP_VERSION}..."
echo ""

# Download Spark if not already present
if [ ! -f "$SPARK_ARCHIVE" ]; then
    print_info "Downloading Spark from: $SPARK_URL"
    wget "$SPARK_URL"
    if [ $? -ne 0 ]; then
        print_error "Failed to download Spark"
        exit 1
    fi
    print_success "Download completed"
else
    print_info "Using existing archive: $SPARK_ARCHIVE"
fi

# Extract Spark
print_info "Extracting Spark archive..."
tar -xzf "$SPARK_ARCHIVE"
if [ $? -ne 0 ]; then
    print_error "Failed to extract Spark archive"
    exit 1
fi
print_success "Extraction completed"

# Clean up archive
print_info "Cleaning up archive file..."
rm "$SPARK_ARCHIVE"

echo ""
print_success "Apache Spark ${SPARK_VERSION} installed successfully!"
echo ""
print_info "Spark location: $PROJECT_DIR/$SPARK_DIR"
print_info "To use Spark, run: export SPARK_HOME=$PROJECT_DIR/$SPARK_DIR"
print_info "Or simply run: ./run-read-table.sh"
echo ""
