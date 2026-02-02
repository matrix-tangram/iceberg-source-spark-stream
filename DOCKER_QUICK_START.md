# StreamTableApp Docker Quick Reference

## Build & Run Locally

```bash
# Build everything
./build-docker.sh

# Run in local mode (single container, 4 threads)
docker run --rm \
  -e SPARK_MODE=local \
  -e PARALLELISM=4 \
  -p 4040:4040 \
  spark-stream-table:latest
```

## Deploy to Kubernetes

### Option 1: Local Mode (Single Pod)
```bash
# Apply deployment
kubectl apply -f k8s-deployment-local.yaml

# View logs
kubectl logs -f deployment/spark-stream-table-local

# Access UI via port-forward
kubectl port-forward deployment/spark-stream-table-local 4040:4040
# Open http://localhost:4040
```

**Scaling**: Edit `PARALLELISM` in `k8s-deployment-local.yaml` to control thread count.

### Option 2: Distributed Mode (Driver + Executors)
```bash
# Apply deployment
kubectl apply -f k8s-deployment.yaml

# View driver logs
kubectl logs -f deployment/spark-stream-table-app

# View executor pods
kubectl get pods -l component=executor

# View executor logs
kubectl logs -l component=executor

# Access UI via port-forward
kubectl port-forward deployment/spark-stream-table-app 4040:4040
# Open http://localhost:4040
```

**Scaling**: Edit `PARALLELISM` in `k8s-deployment.yaml` to control executor count.

## Environment Variables

### Spark Control
- `SPARK_MODE`: `local` or `driver` (K8s)
- `PARALLELISM`: Thread count (local) or executor count (K8s)
- `SPARK_DRIVER_MEMORY`: Driver memory (e.g., `2g`)
- `SPARK_EXECUTOR_MEMORY`: Executor memory (e.g., `2g`)

### Application Config
- `TABLE_NAMESPACE`: Database name (default: `raw_data`)
- `TABLE_NAME`: Table name (default: `test_table`)
- `CHECKPOINT_DIR`: Checkpoint path
- `CATALOG_TYPE`: `s3tables` or `glue`

### AWS
- `AWS_ACCESS_KEY_ID`: AWS access key
- `AWS_SECRET_ACCESS_KEY`: AWS secret key
- `S3_TABLE_BUCKET_ARN`: S3 Tables bucket ARN

## Common Commands

```bash
# Build and push to registry
REGISTRY=your-registry.com PUSH_IMAGE=true ./build-docker.sh

# Test with different parallelism
docker run --rm -e SPARK_MODE=local -e PARALLELISM=8 spark-stream-table:latest

# Scale K8s deployment
kubectl set env deployment/spark-stream-table-app PARALLELISM=10
kubectl rollout restart deployment/spark-stream-table-app

# Clean up K8s
kubectl delete -f k8s-deployment.yaml
kubectl delete -f k8s-deployment-local.yaml
```

## Ports

- `4040`: Spark UI
- `7078`: Driver RPC port
- `7079`: Driver block manager port

See [DOCKER_DEPLOYMENT.md](DOCKER_DEPLOYMENT.md) for detailed documentation.
