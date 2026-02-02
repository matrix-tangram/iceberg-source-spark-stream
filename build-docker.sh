#!/bin/bash
# Build script for StreamTableApp Docker image

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
IMAGE_NAME="${IMAGE_NAME:-spark-stream-table}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
REGISTRY="${REGISTRY:-}"
PUSH_IMAGE="${PUSH_IMAGE:-false}"

echo -e "${GREEN}=== Building StreamTableApp Docker Image ===${NC}"

# Step 1: Build the JAR
echo -e "${YELLOW}Step 1: Building application JAR...${NC}"
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: pom.xml not found. Are you in the project root?${NC}"
    exit 1
fi

mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Maven build failed${NC}"
    exit 1
fi

# Check if JAR exists
if [ ! -f "target/spark-iceberg-aws-1.0-SNAPSHOT.jar" ]; then
    echo -e "${RED}Error: JAR file not found after build${NC}"
    exit 1
fi

echo -e "${GREEN}✓ JAR built successfully${NC}"

# Step 2: Build Docker image
echo -e "${YELLOW}Step 2: Building Docker image...${NC}"
FULL_IMAGE_NAME="${IMAGE_NAME}:${IMAGE_TAG}"
if [ -n "${REGISTRY}" ]; then
    FULL_IMAGE_NAME="${REGISTRY}/${FULL_IMAGE_NAME}"
fi

docker build -t "${FULL_IMAGE_NAME}" .
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Docker build failed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker image built: ${FULL_IMAGE_NAME}${NC}"

# Step 3: Push to registry (optional)
if [ "${PUSH_IMAGE}" = "true" ]; then
    if [ -z "${REGISTRY}" ]; then
        echo -e "${RED}Error: REGISTRY must be set to push image${NC}"
        exit 1
    fi
    
    echo -e "${YELLOW}Step 3: Pushing image to registry...${NC}"
    docker push "${FULL_IMAGE_NAME}"
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Docker push failed${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✓ Image pushed to registry: ${FULL_IMAGE_NAME}${NC}"
else
    echo -e "${YELLOW}Skipping push to registry (set PUSH_IMAGE=true to enable)${NC}"
fi

# Summary
echo -e "${GREEN}=== Build Complete ===${NC}"
echo -e "Image: ${FULL_IMAGE_NAME}"
echo -e "\nTest the image locally:"
echo -e "  ${YELLOW}docker run --rm -e SPARK_MODE=local -e PARALLELISM=2 -p 4040:4040 ${FULL_IMAGE_NAME}${NC}"
echo -e "\nDeploy to Kubernetes:"
echo -e "  ${YELLOW}kubectl apply -f k8s-deployment-local.yaml${NC}"
echo -e "  ${YELLOW}kubectl apply -f k8s-deployment.yaml${NC}"
