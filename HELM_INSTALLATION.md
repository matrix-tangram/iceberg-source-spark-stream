# Helm Chart Installation Guide

This guide provides instructions for deploying the spark-stream-table application using Helm.

## Prerequisites

1. **Kubernetes Cluster**: Access to a Kubernetes cluster (v1.19+)
2. **Helm**: Helm 3.0+ installed ([Installation Guide](https://helm.sh/docs/intro/install/))
3. **kubectl**: Configured to access your cluster
4. **Docker Image**: The `spark-stream-table:latest` image available in your cluster
5. **AWS Access**: Credentials or IAM roles configured for accessing S3 Tables/Glue and S3
6. **Kafka Access**: Network access to your Kafka cluster

## Quick Start

### 1. Validate the Helm Chart

```bash
helm lint helm/spark-stream-table
```

### 2. Install with Default Values

```bash
helm install spark-stream-app helm/spark-stream-table
```

### 3. Install with Custom Configuration

For **AWS S3 Tables**:

```bash
helm install spark-stream-app helm/spark-stream-table \
  --set table.s3TableBucketArn="arn:aws:s3tables:eu-central-1:667654397356:bucket/iceberg-warehouse1" \
  --set table.namespace="raw_data" \
  --set table.name="test_table" \
  --set kafka.bootstrapServers="bastion.ocp.tangram-soft.com:31700" \
  --set kafka.outputTopic="iceberg-output-topic"
```

For **AWS Glue Catalog**:

```bash
helm install spark-stream-app helm/spark-stream-table \
  --set table.catalogType="glue" \
  --set table.s3Warehouse="s3a://my-bucket/warehouse" \
  --set table.namespace="my_database" \
  --set table.name="my_table" \
  --set kafka.bootstrapServers="kafka.example.com:9092" \
  --set kafka.outputTopic="iceberg-output-topic"
```

### 4. Install with Values File

```bash
# Using S3 Tables example
helm install spark-stream-app helm/spark-stream-table \
  -f helm/spark-stream-table/examples/values-s3tables.yaml

# Using Glue Catalog example
helm install spark-stream-app helm/spark-stream-table \
  -f helm/spark-stream-table/examples/values-glue.yaml

# Using Kafka with SASL
helm install spark-stream-app helm/spark-stream-table \
  -f helm/spark-stream-table/examples/values-kafka-sasl.yaml
```

## Configuration Examples

### Example 1: S3 Tables with IRSA (EKS)

For EKS clusters using IAM Roles for Service Accounts:

```yaml
# custom-values.yaml
serviceAccount:
  create: true
  name: spark
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/spark-s3-access

table:
  catalogType: s3tables
  s3TableBucketArn: "arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket"
  namespace: "raw_data"
  name: "test_table"

kafka:
  bootstrapServers: "kafka.example.com:9092"
  outputTopic: "iceberg-output-topic"

aws:
  region: "eu-central-1"
  credentials:
    useSecret: false  # Using IRSA instead
```

Install:
```bash
helm install spark-stream-app helm/spark-stream-table -f custom-values.yaml
```

### Example 2: Using AWS Credentials Secret

First, create the secret:

```bash
kubectl create secret generic aws-credentials \
  --from-literal=access-key-id="YOUR_ACCESS_KEY" \
  --from-literal=secret-access-key="YOUR_SECRET_KEY"
```

Then install:

```bash
helm install spark-stream-app helm/spark-stream-table \
  --set aws.credentials.useSecret=true \
  --set aws.credentials.secretName="aws-credentials" \
  --set table.s3TableBucketArn="arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket"
```

### Example 3: High-Performance Configuration

```yaml
# high-performance-values.yaml
replicaCount: 1

spark:
  parallelism: 5
  driver:
    memory: "4g"
    cores: 2
  executor:
    memory: "4g"
    cores: 2

resources:
  limits:
    cpu: 4
    memory: 6Gi
  requests:
    cpu: 3
    memory: 5Gi

table:
  catalogType: s3tables
  s3TableBucketArn: "arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket"
  namespace: "raw_data"
  name: "test_table"

kafka:
  bootstrapServers: "kafka.example.com:9092"
  outputTopic: "iceberg-output-topic"

app:
  triggerInterval: "5 seconds"
```

## Post-Installation

### Check Deployment Status

```bash
helm status spark-stream-app
kubectl get deployment spark-stream-app-spark-stream-table
kubectl get pods -l app=spark-stream-table-app
```

### View Application Logs

```bash
kubectl logs -f deployment/spark-stream-app-spark-stream-table
```

### Access Spark UI

For NodePort service (default):

```bash
export NODE_PORT=$(kubectl get svc spark-stream-app-spark-stream-table-ui -o jsonpath='{.spec.ports[0].nodePort}')
export NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[0].address}')
echo "Spark UI: http://$NODE_IP:$NODE_PORT"
```

For port-forwarding:

```bash
kubectl port-forward svc/spark-stream-app-spark-stream-table-ui 4040:4040
# Then open http://localhost:4040
```

### Monitor Executor Pods

```bash
kubectl get pods -l app=spark-stream-table-app,component=executor -w
```

## Upgrading

### Upgrade with New Values

```bash
helm upgrade spark-stream-app helm/spark-stream-table \
  --set spark.parallelism=5 \
  --set spark.executor.memory="2g"
```

### Upgrade with Values File

```bash
helm upgrade spark-stream-app helm/spark-stream-table -f updated-values.yaml
```

## Uninstalling

```bash
helm uninstall spark-stream-app
```

## Troubleshooting

### Chart Validation Failed

```bash
# Validate the chart
helm lint helm/spark-stream-table

# Dry-run to see what will be deployed
helm install spark-stream-app helm/spark-stream-table --dry-run --debug
```

### Pods Not Starting

```bash
# Check pod status
kubectl describe pod -l app=spark-stream-table-app

# Check events
kubectl get events --sort-by='.lastTimestamp'
```

### AWS Credentials Issues

```bash
# Verify secret exists
kubectl get secret aws-credentials

# Check if IRSA annotation is correct
kubectl describe serviceaccount spark
```

### Kafka Connection Issues

```bash
# Check logs for Kafka errors
kubectl logs -f deployment/spark-stream-app-spark-stream-table | grep -i kafka

# Test Kafka connectivity from a pod
kubectl run kafka-test --rm -it --image=confluentinc/cp-kafka:latest -- \
  kafka-broker-api-versions --bootstrap-server bastion.ocp.tangram-soft.com:31700
```

## Additional Resources

- [Helm Chart README](helm/spark-stream-table/README.md)
- [Example Values Files](helm/spark-stream-table/examples/)
- [Kubernetes Deployment Guide](k8s-deployment.yaml)
- [Docker Deployment Guide](DOCKER_DEPLOYMENT.md)

