# Spark Stream Table Helm Chart - Summary

## Overview

This Helm chart deploys a Spark streaming application that reads from Apache Iceberg tables (via AWS S3 Tables or Glue Catalog) and writes to Kafka topics.

## Chart Structure

```
helm/spark-stream-table/
├── Chart.yaml                      # Chart metadata
├── values.yaml                     # Default configuration values
├── README.md                       # Comprehensive documentation
├── .helmignore                     # Files to ignore when packaging
├── templates/                      # Kubernetes manifest templates
│   ├── _helpers.tpl               # Template helper functions
│   ├── serviceaccount.yaml        # ServiceAccount for Spark
│   ├── role.yaml                  # RBAC Role for executor pod management
│   ├── rolebinding.yaml           # RBAC RoleBinding
│   ├── configmap.yaml             # Application configuration
│   ├── deployment.yaml            # Main Spark driver deployment
│   ├── service.yaml               # Service for Spark UI
│   └── NOTES.txt                  # Post-installation instructions
└── examples/                       # Example values files
    ├── values-s3tables.yaml       # S3 Tables catalog example
    ├── values-glue.yaml           # Glue catalog example
    ├── values-kafka-sasl.yaml     # Kafka with SASL authentication
    └── values-with-aws-secret.yaml # AWS credentials from secret
```

## Key Features

### 1. Flexible Catalog Support
- **AWS S3 Tables**: Native support for AWS S3 Tables catalog
- **AWS Glue**: Support for AWS Glue Data Catalog
- Configurable via `table.catalogType` parameter

### 2. Comprehensive Parameterization
All critical parameters are configurable:
- Source table configuration (namespace, name, catalog type)
- Kafka destination (bootstrap servers, topic, security)
- Spark resources (driver/executor memory, cores, parallelism)
- AWS configuration (region, credentials)
- Application settings (checkpoint directory, trigger interval)

### 3. Security & RBAC
- ServiceAccount creation with configurable annotations (for IRSA)
- RBAC Role and RoleBinding for executor pod management
- Support for AWS credentials via Kubernetes secrets
- Optional SASL authentication for Kafka

### 4. Production-Ready Features
- ConfigMap checksum annotation for automatic rolling updates
- Resource requests and limits
- Configurable service types (NodePort, ClusterIP, LoadBalancer)
- Node selectors, tolerations, and affinity rules
- Pod and container security contexts

### 5. Observability
- Spark UI exposed via Kubernetes Service
- Comprehensive NOTES.txt with post-installation instructions
- Easy access to driver and executor logs

## Configuration Highlights

### Default Values
- **Spark Mode**: `driver` (Kubernetes client mode)
- **Parallelism**: 2 executor pods
- **Driver Resources**: 1g memory, 1 core
- **Executor Resources**: 1g memory, 1 core
- **Catalog Type**: `s3tables`
- **Kafka Security**: `PLAINTEXT`
- **Service Type**: `NodePort` on port 30040

### Customizable Parameters
- Image repository, tag, and pull policy
- Replica count for the deployment
- All Spark configuration (memory, cores, ports)
- Table configuration (namespace, name, catalog type, ARN/warehouse)
- Kafka configuration (servers, topic, security protocol, SASL)
- AWS region and credentials
- Application settings (checkpoint dir, trigger interval)
- Service configuration (type, ports, annotations)
- Resource limits and requests
- Node selectors, tolerations, affinity

## Installation Methods

### 1. Default Installation
```bash
helm install spark-stream-app helm/spark-stream-table
```

### 2. With Command-Line Overrides
```bash
helm install spark-stream-app helm/spark-stream-table \
  --set table.s3TableBucketArn="arn:aws:s3tables:..." \
  --set kafka.bootstrapServers="kafka:9092"
```

### 3. With Values File
```bash
helm install spark-stream-app helm/spark-stream-table \
  -f helm/spark-stream-table/examples/values-s3tables.yaml
```

### 4. With Custom Values File
```bash
helm install spark-stream-app helm/spark-stream-table \
  -f my-custom-values.yaml
```

## Example Use Cases

### Use Case 1: S3 Tables with IRSA (EKS)
Perfect for AWS EKS clusters using IAM Roles for Service Accounts.
See: `examples/values-s3tables.yaml`

### Use Case 2: Glue Catalog
For existing Glue-based data lakes.
See: `examples/values-glue.yaml`

### Use Case 3: Secure Kafka with SASL
For production Kafka clusters with authentication.
See: `examples/values-kafka-sasl.yaml`

### Use Case 4: AWS Credentials from Secret
For non-EKS clusters or when IRSA is not available.
See: `examples/values-with-aws-secret.yaml`

## Best Practices

1. **Use IRSA for EKS**: Configure ServiceAccount annotations for IAM roles
2. **Store Secrets Securely**: Use Kubernetes secrets or external secret managers
3. **Set Resource Limits**: Always configure appropriate resource requests/limits
4. **Monitor Executor Pods**: Watch executor pod creation and logs
5. **Use ConfigMap Checksums**: Enabled by default for automatic rolling updates
6. **Configure Health Checks**: Consider adding liveness/readiness probes
7. **Use Namespaces**: Deploy to dedicated namespaces for isolation

## Troubleshooting

Common issues and solutions are documented in:
- `README.md` - Chart-specific troubleshooting
- `HELM_INSTALLATION.md` - Installation and deployment issues
- `NOTES.txt` - Post-installation verification steps

## Upgrading

```bash
# Upgrade with new values
helm upgrade spark-stream-app helm/spark-stream-table -f updated-values.yaml

# Rollback if needed
helm rollback spark-stream-app
```

## Uninstalling

```bash
helm uninstall spark-stream-app
```

Note: This will delete all resources created by the chart, including executor pods.

## Version Information

- **Chart Version**: 1.0.0
- **App Version**: 1.0.0
- **Kubernetes**: 1.19+
- **Helm**: 3.0+

## Support

For issues, questions, or contributions, please refer to the project repository.

