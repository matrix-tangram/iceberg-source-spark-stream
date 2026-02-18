FROM apache/spark:3.5.0-scala2.12-java11-python3-ubuntu

# Switch to root to install packages
USER root

# Install required packages (including curl for downloading JARs)
RUN apt-get update && \
    apt-get install -y netcat curl && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables (SPARK_HOME already set in base image)
ENV SPARK_NO_DAEMONIZE=true \
    PATH=$PATH:${SPARK_HOME}/bin:${SPARK_HOME}/sbin

# Create application directory
RUN mkdir -p /opt/spark-app/jars /opt/spark-app/conf

# Download Kafka connector and its transitive dependencies at build time
# This ensures the container is self-contained and doesn't need internet access at runtime
RUN cd /opt/spark-app/jars && \
    # Main Kafka connector JAR
    curl -L -o spark-sql-kafka-0-10_2.12-3.5.0.jar \
        https://repo1.maven.org/maven2/org/apache/spark/spark-sql-kafka-0-10_2.12/3.5.0/spark-sql-kafka-0-10_2.12-3.5.0.jar && \
    # Kafka clients library
    curl -L -o kafka-clients-3.4.1.jar \
        https://repo1.maven.org/maven2/org/apache/kafka/kafka-clients/3.4.1/kafka-clients-3.4.1.jar && \
    # Spark token provider for Kafka
    curl -L -o spark-token-provider-kafka-0-10_2.12-3.5.0.jar \
        https://repo1.maven.org/maven2/org/apache/spark/spark-token-provider-kafka-0-10_2.12/3.5.0/spark-token-provider-kafka-0-10_2.12-3.5.0.jar && \
    # Commons Pool2 (required by Kafka clients)
    curl -L -o commons-pool2-2.11.1.jar \
        https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.11.1/commons-pool2-2.11.1.jar && \
    # Verify all JARs were downloaded successfully
    ls -lh *.jar

# Copy the application JAR
COPY target/spark-iceberg-aws-1.0-SNAPSHOT.jar /opt/spark-app/jars/app.jar

# Copy entrypoint script
COPY docker-entrypoint.sh /opt/spark-app/docker-entrypoint.sh
RUN chmod +x /opt/spark-app/docker-entrypoint.sh

# Set working directory
WORKDIR /opt/spark-app

# Environment variables with defaults
ENV SPARK_MODE=local \
    PARALLELISM=2 \
    SPARK_MASTER_HOST=spark-master \
    SPARK_MASTER_PORT=7077 \
    SPARK_DRIVER_PORT=7078 \
    SPARK_DRIVER_BLOCK_MANAGER_PORT=7079 \
    SPARK_UI_PORT=4040 \
    SPARK_WORKER_UI_PORT=8081 \
    SPARK_DRIVER_MEMORY=1g \
    SPARK_EXECUTOR_MEMORY=1g \
    SPARK_DRIVER_CORES=1 \
    SPARK_EXECUTOR_CORES=1 \
    APP_CLASS=com.example.spark.StreamTableApp \
    APP_JAR=/opt/spark-app/jars/app.jar \
    AWS_REGION=eu-central-1 \
    KAFKA_BOOTSTRAP_SERVERS=bastion.ocp.tangram-soft.com:31700 \
    KAFKA_OUTPUT_TOPIC=iceberg-output-topic \
    KAFKA_SECURITY_PROTOCOL=PLAINTEXT \
    TABLE_NAMESPACE=raw_data \
    TABLE_NAME=test_table \
    CHECKPOINT_DIR=./checkpoints \
    TRIGGER_INTERVAL="10 seconds" \
    CATALOG_TYPE=s3tables \
    AWS_JAVA_V1_DISABLE_DEPRECATION_ANNOUNCEMENT=true

# Expose common Spark ports
EXPOSE 4040 6066 7077 7078 7079 8080 8081

# Use the entrypoint script
ENTRYPOINT ["/opt/spark-app/docker-entrypoint.sh"]
