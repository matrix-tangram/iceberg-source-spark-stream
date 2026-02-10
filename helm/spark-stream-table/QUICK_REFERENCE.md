# Spark Stream Table Helm Chart - Quick Reference

## Installation Commands

### Install with S3 Tables
```bash
helm install spark-app helm/spark-stream-table \
  --set table.s3TableBucketArn="arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket" \
  --set table.namespace="raw_data" \
  --set table.name="test_table"
```

### Install with Glue Catalog
```bash
helm install spark-app helm/spark-stream-table \
  --set table.catalogType="glue" \
  --set table.s3Warehouse="s3a://my-bucket/warehouse" \
  --set table.namespace="my_database" \
  --set table.name="my_table"
```

### Install with Custom Image
```bash
helm install spark-app helm/spark-stream-table \
  --set image.repository="my-registry/spark-stream-table" \
  --set image.tag="v1.2.3"
```

### Install with High Resources
```bash
helm install spark-app helm/spark-stream-table \
  --set spark.parallelism=5 \
  --set spark.driver.memory="4g" \
  --set spark.executor.memory="4g" \
  --set resources.limits.memory="6Gi"
```

## Management Commands

### Check Status
```bash
helm status spark-app
helm list
kubectl get all -l app.kubernetes.io/instance=spark-app
```

### Upgrade
```bash
helm upgrade spark-app helm/spark-stream-table -f new-values.yaml
helm upgrade spark-app helm/spark-stream-table --set spark.parallelism=3
```

### Rollback
```bash
helm rollback spark-app
helm rollback spark-app 1  # Rollback to specific revision
```

### Uninstall
```bash
helm uninstall spark-app
```

## Monitoring Commands

### View Logs
```bash
# Driver logs
kubectl logs -f deployment/spark-app-spark-stream-table

# Executor logs
kubectl logs -l app=spark-stream-table-app,component=executor

# All logs
kubectl logs -l app.kubernetes.io/instance=spark-app
```

### Watch Pods
```bash
# Watch all pods
kubectl get pods -l app.kubernetes.io/instance=spark-app -w

# Watch executor pods
kubectl get pods -l app=spark-stream-table-app,component=executor -w
```

### Access Spark UI
```bash
# Port forward
kubectl port-forward svc/spark-app-spark-stream-table-ui 4040:4040

# Get NodePort URL
export NODE_PORT=$(kubectl get svc spark-app-spark-stream-table-ui -o jsonpath='{.spec.ports[0].nodePort}')
export NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[0].address}')
echo "http://$NODE_IP:$NODE_PORT"
```

## Debugging Commands

### Describe Resources
```bash
kubectl describe deployment spark-app-spark-stream-table
kubectl describe pod -l app=spark-stream-table-app
kubectl describe svc spark-app-spark-stream-table-ui
```

### Check ConfigMap
```bash
kubectl get configmap spark-app-spark-stream-table-config -o yaml
```

### Check ServiceAccount & RBAC
```bash
kubectl get serviceaccount spark
kubectl get role spark-app-spark-stream-table-role
kubectl get rolebinding spark-app-spark-stream-table-rolebinding
```

### Check Events
```bash
kubectl get events --sort-by='.lastTimestamp' | grep spark
```

## Common Parameter Overrides

### Kafka Configuration
```bash
--set kafka.bootstrapServers="kafka.example.com:9092"
--set kafka.outputTopic="my-topic"
--set kafka.securityProtocol="SASL_SSL"
--set kafka.saslMechanism="SCRAM-SHA-256"
```

### AWS Configuration
```bash
--set aws.region="us-east-1"
--set aws.credentials.useSecret=true
--set aws.credentials.secretName="aws-creds"
```

### Spark Resources
```bash
--set spark.parallelism=4
--set spark.driver.memory="2g"
--set spark.driver.cores=2
--set spark.executor.memory="2g"
--set spark.executor.cores=2
```

### Application Settings
```bash
--set app.checkpointDir="/tmp/checkpoints"
--set app.triggerInterval="5 seconds"
```

### Service Configuration
```bash
--set service.type="LoadBalancer"
--set service.type="ClusterIP"
--set service.nodePort=30050
```

## Template Testing

### Dry Run
```bash
helm install spark-app helm/spark-stream-table --dry-run --debug
```

### Template Rendering
```bash
helm template spark-app helm/spark-stream-table
helm template spark-app helm/spark-stream-table -f my-values.yaml
```

### Lint Chart
```bash
helm lint helm/spark-stream-table
helm lint helm/spark-stream-table -f my-values.yaml
```

## Values File Examples

### Minimal S3 Tables
```yaml
table:
  s3TableBucketArn: "arn:aws:s3tables:eu-central-1:123456789012:bucket/my-bucket"
kafka:
  bootstrapServers: "kafka:9092"
  outputTopic: "my-topic"
```

### Production Configuration
```yaml
replicaCount: 1
image:
  repository: my-registry/spark-stream-table
  tag: "v1.0.0"
  pullPolicy: Always

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
  namespace: "production"
  name: "events"

kafka:
  bootstrapServers: "kafka-prod.example.com:9093"
  outputTopic: "production-events"
  securityProtocol: "SASL_SSL"
  saslMechanism: "SCRAM-SHA-256"

service:
  type: LoadBalancer
```

## Useful Aliases

```bash
# Add to ~/.bashrc or ~/.zshrc
alias hls='helm list'
alias hst='helm status'
alias hug='helm upgrade'
alias hrb='helm rollback'
alias hun='helm uninstall'
alias kgp='kubectl get pods'
alias kgd='kubectl get deployments'
alias kgs='kubectl get services'
alias kl='kubectl logs -f'
alias kd='kubectl describe'
```

## Quick Troubleshooting

| Issue | Command |
|-------|---------|
| Pods not starting | `kubectl describe pod -l app=spark-stream-table-app` |
| Check driver logs | `kubectl logs -f deployment/spark-app-spark-stream-table` |
| Check executor logs | `kubectl logs -l component=executor` |
| Verify ConfigMap | `kubectl get cm spark-app-spark-stream-table-config -o yaml` |
| Check RBAC | `kubectl auth can-i create pods --as=system:serviceaccount:default:spark` |
| Test Kafka connection | `kubectl run kafka-test --rm -it --image=confluentinc/cp-kafka -- kafka-broker-api-versions --bootstrap-server <servers>` |
| Check AWS credentials | `kubectl get secret aws-credentials` |
| View all resources | `kubectl get all -l app.kubernetes.io/instance=spark-app` |

