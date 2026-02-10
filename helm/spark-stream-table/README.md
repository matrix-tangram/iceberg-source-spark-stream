# Spark Stream Table Helm Chart

A Helm chart for deploying a Spark streaming application that reads from Apache Iceberg tables (via AWS S3 Tables or Glue Catalog) and writes to Kafka.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.0+
- Docker image `spark-stream-table:latest` (or your custom image)
- AWS credentials configured (via IRSA, instance profiles, or Kubernetes secrets)
- Access to Kafka cluster
- Access to AWS S3 Tables or Glue Catalog

## Installation

### Basic Installation

```bash
helm install my-spark-app ./helm/spark-stream-table
```

### Installation with Custom Values

```bash
helm install my-spark-app ./helm/spark-stream-table \
  --set table.s3TableBucketArn="arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket" \
  --set kafka.bootstrapServers="kafka.example.com:9092" \
  --set kafka.outputTopic="my-topic"
```

### Installation with Values File

```bash
helm install my-spark-app ./helm/spark-stream-table -f my-values.yaml
```

## Configuration

The following table lists the configurable parameters of the chart and their default values.

### Image Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `image.repository` | Docker image repository | `spark-stream-table` |
| `image.tag` | Docker image tag | `latest` |
| `image.pullPolicy` | Image pull policy | `IfNotPresent` |
| `imagePullSecrets` | Image pull secrets | `[]` |

### Deployment Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `replicaCount` | Number of replicas | `1` |
| `nameOverride` | Override chart name | `""` |
| `fullnameOverride` | Override full name | `""` |

### ServiceAccount & RBAC

| Parameter | Description | Default |
|-----------|-------------|---------|
| `serviceAccount.create` | Create service account | `true` |
| `serviceAccount.name` | Service account name | `spark` |
| `serviceAccount.annotations` | Service account annotations | `{}` |
| `rbac.create` | Create RBAC resources | `true` |

### Spark Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `spark.mode` | Spark mode (driver, local, submit) | `driver` |
| `spark.appName` | Spark application name | `spark-stream-table-app` |
| `spark.parallelism` | Number of executor pods | `2` |
| `spark.driver.memory` | Driver memory | `1g` |
| `spark.driver.cores` | Driver cores | `1` |
| `spark.executor.memory` | Executor memory | `1g` |
| `spark.executor.cores` | Executor cores | `1` |
| `spark.ui.port` | Spark UI port | `4040` |

### Table Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `table.namespace` | Table namespace/database | `raw_data` |
| `table.name` | Table name | `test_table` |
| `table.catalogType` | Catalog type (s3tables or glue) | `s3tables` |
| `table.s3TableBucketArn` | S3 Table Bucket ARN (for s3tables) | `""` |
| `table.s3Warehouse` | S3 warehouse path (for glue) | `""` |

### Kafka Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `kafka.bootstrapServers` | Kafka bootstrap servers | `bastion.ocp.tangram-soft.com:31700` |
| `kafka.outputTopic` | Output topic name | `iceberg-output-topic` |
| `kafka.securityProtocol` | Security protocol | `PLAINTEXT` |
| `kafka.saslMechanism` | SASL mechanism | `""` |
| `kafka.saslJaasConfig` | SASL JAAS config | `""` |

### AWS Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `aws.region` | AWS region | `eu-central-1` |
| `aws.credentials.useSecret` | Use secret for credentials | `false` |
| `aws.credentials.secretName` | Secret name | `aws-credentials` |

### Application Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `app.checkpointDir` | Checkpoint directory | `./checkpoints` |
| `app.triggerInterval` | Trigger interval | `10 seconds` |

### Service Configuration

| Parameter | Description | Default |
|-----------|-------------|---------|
| `service.type` | Service type | `NodePort` |
| `service.port` | Service port | `4040` |
| `service.nodePort` | NodePort (if type is NodePort) | `30040` |

### Resources

| Parameter | Description | Default |
|-----------|-------------|---------|
| `resources.limits.cpu` | CPU limit | `2` |
| `resources.limits.memory` | Memory limit | `2Gi` |
| `resources.requests.cpu` | CPU request | `1` |
| `resources.requests.memory` | Memory request | `1.5Gi` |

## Examples

### Using S3 Tables Catalog

```yaml
table:
  namespace: "raw_data"
  name: "my_table"
  catalogType: "s3tables"
  s3TableBucketArn: "arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket"

aws:
  region: "eu-central-1"
```

### Using Glue Catalog

```yaml
table:
  namespace: "my_database"
  name: "my_table"
  catalogType: "glue"
  s3Warehouse: "s3a://my-bucket/warehouse"

aws:
  region: "us-east-1"
```

### Using AWS Credentials from Secret

First, create the secret:

```bash
kubectl create secret generic aws-credentials \
  --from-literal=access-key-id=YOUR_ACCESS_KEY \
  --from-literal=secret-access-key=YOUR_SECRET_KEY
```

Then configure the chart:

```yaml
aws:
  credentials:
    useSecret: true
    secretName: "aws-credentials"
```

### Kafka with SASL Authentication

```yaml
kafka:
  bootstrapServers: "kafka.example.com:9093"
  outputTopic: "my-topic"
  securityProtocol: "SASL_SSL"
  saslMechanism: "SCRAM-SHA-256"
  saslJaasConfig: "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"user\" password=\"pass\";"
```

## Upgrading

```bash
helm upgrade my-spark-app ./helm/spark-stream-table -f my-values.yaml
```

## Uninstalling

```bash
helm uninstall my-spark-app
```

## Troubleshooting

### Check Application Logs

```bash
kubectl logs -f deployment/my-spark-app-spark-stream-table
```

### Check Executor Pods

```bash
kubectl get pods -l app=spark-stream-table-app,component=executor
```

### Access Spark UI

```bash
kubectl port-forward svc/my-spark-app-spark-stream-table-ui 4040:4040
```

Then open http://localhost:4040 in your browser.

