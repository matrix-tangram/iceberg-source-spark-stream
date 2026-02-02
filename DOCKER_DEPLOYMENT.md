# Docker Deployment Guide for StreamTableApp

This guide explains how to build and deploy the StreamTableApp using Docker in various modes.

## Prerequisites

- Docker installed
- Maven (for building the JAR)
- Kubernetes cluster (for K8s deployments)
- kubectl configured (for K8s deployments)

## Building

### 1. Build the Application JAR

```bash
mvn clean package
```

This creates `target/spark-iceberg-aws-1.0-SNAPSHOT.jar`.

### 2. Build the Docker Image

```bash
docker build -t spark-stream-table:latest .
```

## Deployment Modes

The Docker image supports multiple deployment modes configured via the `SPARK_MODE` environment variable.

### Mode 1: Local Mode (Single Container)

Runs Spark in `local[PARALLELISM]` mode - all processing happens in one container.

#### Docker Run

```bash
docker run --rm \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e TABLE_NAMESPACE=raw_data \
  -e TABLE_NAME=test_table \
  -e CHECKPOINT_DIR=/tmp/checkpoints \
  -e CATALOG_TYPE=s3tables \
  -e AWS_ACCESS_KEY_ID=your_key \
  -e AWS_SECRET_ACCESS_KEY=your_secret \
  -e S3_TABLE_BUCKET_ARN=your_bucket_arn \
  -p 4040:4040 \
  spark-stream-table:latest
```

#### Docker Compose

```yaml
version: '3.8'
services:
  spark-local:
    image: spark-stream-table:latest
    environment:
      SPARK_MODE: local
      PARALLELISM: 4
      TABLE_NAMESPACE: raw_data
      TABLE_NAME: test_table
      CHECKPOINT_DIR: /tmp/checkpoints
      CATALOG_TYPE: s3tables
      AWS_ACCESS_KEY_ID: your_key
      AWS_SECRET_ACCESS_KEY: your_secret
      S3_TABLE_BUCKET_ARN: your_bucket_arn
    ports:
      - "4040:4040"
```

#### Kubernetes

```bash
kubectl apply -f k8s-deployment-local.yaml
```

Access Spark UI:
```bash
# Port forward
kubectl port-forward deployment/spark-stream-table-local 4040:4040

# Or via NodePort
# http://<node-ip>:30041
```

### Mode 2: Kubernetes Mode (Driver + Executors)

Runs Spark driver in the main pod, which spawns executor pods dynamically.

#### Build and Push to Registry

```bash
# Build the image
docker build -t your-registry/spark-stream-table:latest .

# Push to registry (accessible by K8s cluster)
docker push your-registry/spark-stream-table:latest
```

#### Deploy to Kubernetes

1. **Update the image in the deployment file:**

Edit `k8s-deployment.yaml` and set:
```yaml
- name: K8S_IMAGE
  value: "your-registry/spark-stream-table:latest"
```

2. **Apply the deployment:**

```bash
kubectl apply -f k8s-deployment.yaml
```

3. **Monitor the deployment:**

```bash
# Watch driver pod
kubectl logs -f deployment/spark-stream-table-app

# List all pods (driver + executors)
kubectl get pods -l app=spark-stream-table-app

# View executor logs
kubectl logs -l component=executor
```

4. **Access Spark UI:**

```bash
# Port forward
kubectl port-forward deployment/spark-stream-table-app 4040:4040

# Or via NodePort
# http://<node-ip>:30040
```

## Configuration

### Environment Variables

#### Spark Configuration

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SPARK_MODE` | Deployment mode: `local`, `driver`, `submit`, `master`, `worker` | `local` | Yes |
| `PARALLELISM` | Number of threads (local) or executors (K8s) | `2` | Yes |
| `SPARK_DRIVER_MEMORY` | Driver memory | `1g` | No |
| `SPARK_EXECUTOR_MEMORY` | Executor memory (K8s only) | `1g` | No |
| `SPARK_DRIVER_CORES` | Driver CPU cores | `1` | No |
| `SPARK_EXECUTOR_CORES` | Executor CPU cores (K8s only) | `1` | No |

#### Kubernetes-Specific

| Variable | Description | Default |
|----------|-------------|---------|
| `K8S_IMAGE` | Docker image for executors | `spark-app:latest` |
| `K8S_NAMESPACE` | Kubernetes namespace | `default` |
| `K8S_SERVICE_ACCOUNT` | Service account for Spark | `spark` |
| `APP_NAME` | Application name | `spark-stream-table-app` |

#### Application Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `TABLE_NAMESPACE` | Iceberg table namespace/database | `raw_data` |
| `TABLE_NAME` | Iceberg table name | `test_table` |
| `CHECKPOINT_DIR` | Checkpoint directory path | `./checkpoints` |
| `TRIGGER_INTERVAL` | Streaming trigger interval | `10 seconds` |
| `CATALOG_TYPE` | Catalog type: `s3tables` or `glue` | `s3tables` |
| `KEY_FIELD` | Field for Kafka key (optional) | - |

#### AWS Credentials

| Variable | Description |
|----------|-------------|
| `AWS_ACCESS_KEY_ID` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key |
| `AWS_SESSION_TOKEN` | AWS session token (optional) |
| `S3_TABLE_BUCKET_ARN` | S3 Tables bucket ARN (for s3tables) |
| `S3_WAREHOUSE` | S3 warehouse path (for glue) |

### Advanced Spark Configuration

Additional Spark configurations can be passed via `SPARK_SUBMIT_OPTS`:

```bash
docker run --rm \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -e SPARK_SUBMIT_OPTS="--conf spark.sql.shuffle.partitions=200 --conf spark.default.parallelism=100" \
  spark-stream-table:latest
```

## Scaling

### Local Mode

Adjust the `PARALLELISM` parameter to control the number of threads:

```bash
docker run -e SPARK_MODE=local -e PARALLELISM=8 spark-stream-table:latest
```

### Kubernetes Mode

Adjust the `PARALLELISM` parameter to control the number of executor pods:

```yaml
- name: PARALLELISM
  value: "5"  # Creates 5 executor pods
```

Or scale via kubectl:

```bash
# Update the deployment
kubectl set env deployment/spark-stream-table-app PARALLELISM=10

# Restart the driver pod to apply changes
kubectl rollout restart deployment/spark-stream-table-app
```

## Troubleshooting

### Check Logs

```bash
# Local Docker
docker logs <container-id>

# Kubernetes driver
kubectl logs deployment/spark-stream-table-app

# Kubernetes executors
kubectl logs -l component=executor --tail=100
```

### Access Spark UI

The Spark UI provides detailed information about jobs, stages, and executors.

- **Local**: http://localhost:4040
- **K8s**: Port forward to access UI

### Common Issues

1. **Out of Memory**: Increase `SPARK_DRIVER_MEMORY` or `SPARK_EXECUTOR_MEMORY`
2. **Executor pods not starting**: Check RBAC permissions and service account
3. **Image pull errors**: Ensure the image is pushed to a registry accessible by K8s
4. **AWS authentication errors**: Verify AWS credentials are set correctly

## Examples

### Example 1: Local Development

```bash
docker run --rm \
  -e SPARK_MODE=local \
  -e PARALLELISM=2 \
  -e TABLE_NAME=my_table \
  -e CHECKPOINT_DIR=/tmp/checkpoints \
  -v $(pwd)/checkpoints:/tmp/checkpoints \
  spark-stream-table:latest
```

### Example 2: Production K8s with S3 Tables

```bash
# Deploy with custom configuration
cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spark-prod
spec:
  replicas: 1
  selector:
    matchLabels:
      app: spark-prod
  template:
    metadata:
      labels:
        app: spark-prod
    spec:
      serviceAccountName: spark
      containers:
      - name: spark-driver
        image: your-registry/spark-stream-table:latest
        env:
        - name: SPARK_MODE
          value: "driver"
        - name: PARALLELISM
          value: "10"
        - name: SPARK_DRIVER_MEMORY
          value: "4g"
        - name: SPARK_EXECUTOR_MEMORY
          value: "4g"
        - name: SPARK_EXECUTOR_CORES
          value: "2"
        - name: K8S_IMAGE
          value: "your-registry/spark-stream-table:latest"
        # Add your application-specific environment variables
EOF
```

### Example 3: Testing with Different Parallelism

```bash
# Test with 1 thread
docker run --rm -e SPARK_MODE=local -e PARALLELISM=1 spark-stream-table:latest

# Test with 8 threads
docker run --rm -e SPARK_MODE=local -e PARALLELISM=8 spark-stream-table:latest
```

## Clean Up

### Docker

```bash
docker stop <container-id>
docker rm <container-id>
```

### Kubernetes

```bash
# Delete local deployment
kubectl delete -f k8s-deployment-local.yaml

# Delete K8s deployment
kubectl delete -f k8s-deployment.yaml

# Delete all resources
kubectl delete deployment,service,configmap,role,rolebinding,serviceaccount -l app=spark-stream-table-app
```
