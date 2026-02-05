# Docker Deployment with AWS Credentials

## Overview
The Docker container has been successfully built and can be run with AWS credentials properly mounted.

## What Was Done

1. **Built Docker Image**: `spark-stream-table:latest`
   - Contains the Spark application JAR
   - Configured with all necessary dependencies
   - Includes entrypoint script for easy configuration

2. **AWS Credentials Integration**: 
   - Created script `run-docker-with-aws.sh` that automatically:
     - Fetches temporary AWS credentials using STS AssumeRole
     - Passes credentials to the container as environment variables
     - Starts the container with proper configuration

3. **Updated docker-compose.yaml**:
   - Added AWS credentials volume mount: `~/.aws:/root/.aws:ro`

## Running the Container

### Quick Start (Recommended)
```bash
./run-docker-with-aws.sh
```

This script will:
- Fetch AWS credentials for role `AlexeyLocalAppRole`
- Start the container with credentials as environment variables
- Credentials are valid for 1 hour

### Manual Run
If you need to run manually with specific environment variables:

```bash
# First, get AWS credentials
CREDS=$(aws sts assume-role \
  --role-arn arn:aws:iam::667654397356:role/AlexeyLocalAppRole \
  --role-session-name docker-session \
  --duration-seconds 3600 \
  --output json)

AWS_ACCESS_KEY_ID=$(echo $CREDS | jq -r '.Credentials.AccessKeyId')
AWS_SECRET_ACCESS_KEY=$(echo $CREDS | jq -r '.Credentials.SecretAccessKey')
AWS_SESSION_TOKEN=$(echo $CREDS | jq -r '.Credentials.SessionToken')

# Then run the container
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
  -e S3_TABLE_BUCKET_ARN="<your-s3-tables-bucket-arn>" \
  -e TABLE_NAMESPACE=raw_data \
  -e TABLE_NAME=test_table \
  -e CHECKPOINT_DIR=/app/checkpoints \
  -e TRIGGER_INTERVAL="10 seconds" \
  -e KAFKA_BOOTSTRAP_SERVERS=bastion.ocp.tangram-soft.com:31700 \
  -e KAFKA_OUTPUT_TOPIC=iceberg-output-topic \
  -e KAFKA_SECURITY_PROTOCOL=PLAINTEXT \
  -p 4040:4040 \
  -v "$PWD/checkpoints:/app/checkpoints:Z" \
  spark-stream-table:latest
```

## Important Configuration Notes

### Region Mismatch Issue
The current error indicates a region mismatch:
```
BadRequestException: The provided Region does not match the Region of the resource
```

**Solution**: Make sure the `AWS_REGION` environment variable matches the region of your S3 Tables bucket.

If your S3 Tables bucket is in `eu-central-1`, you need to set the correct bucket ARN:
```bash
-e AWS_REGION=eu-central-1 \
-e S3_TABLE_BUCKET_ARN="arn:aws:s3tables:eu-central-1:<account-id>:bucket/<bucket-name>"
```

### AWS Credentials Options

1. **Using STS Temporary Credentials (Recommended)**:
   - The script `run-docker-with-aws.sh` handles this automatically
   - Credentials expire after 1 hour
   - More secure than long-lived credentials

2. **Using AWS Profile**:
   - Mount the AWS directory: `-v ~/.aws:/root/.aws:ro`
   - Set the profile: `-e AWS_PROFILE=AlexeyLocalAppRole`
   - **Note**: This may not work with role-based profiles that require SSO

3. **Using Environment Variables**:
   - Set `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_SESSION_TOKEN`
   - Best for temporary credentials

## Container Management

### View Logs
```bash
docker logs -f spark-local
```

### Check Status
```bash
docker ps | grep spark-local
```

### Stop Container
```bash
docker stop spark-local
```

### Remove Container
```bash
docker rm spark-local
```

### Rebuild Image
```bash
./build-docker.sh
```

## Spark UI
Once the container is running successfully, access the Spark UI at:
```
http://localhost:4040
```

## Troubleshooting

### Container Stops Immediately
Check the logs:
```bash
docker logs spark-local
```

Common issues:
1. **Region Mismatch**: Ensure `AWS_REGION` matches your S3 Tables bucket region
2. **Invalid Credentials**: Credentials might have expired (valid for 1 hour)
3. **Missing S3 Tables Bucket**: Set the correct `S3_TABLE_BUCKET_ARN`

### Refresh Credentials
If credentials expire, simply run the script again:
```bash
./run-docker-with-aws.sh
```

### Podman vs Docker
The system is using Podman (Docker emulation). Commands work the same, but note:
- Volume mounts may need `:Z` suffix for SELinux (already included in scripts)
- Some docker-compose features may not work (use the run script instead)

## Files Created

1. `run-docker-with-aws.sh` - Script to run container with AWS credentials
2. `DOCKER_AWS_DEPLOYMENT.md` - This documentation file

## Next Steps

To run the application successfully, you need to:
1. Verify your S3 Tables bucket ARN and region
2. Update the environment variables in `run-docker-with-aws.sh` if needed
3. Ensure the table `raw_data.test_table` exists in your S3 Tables catalog
