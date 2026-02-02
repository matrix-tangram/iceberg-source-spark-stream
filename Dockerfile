FROM apache/spark:3.5.0-scala2.12-java11-python3-ubuntu

# Switch to root to install packages
USER root

# Install required packages
RUN apt-get update && \
    apt-get install -y netcat && \
    rm -rf /var/lib/apt/lists/*

# Set environment variables (SPARK_HOME already set in base image)
ENV SPARK_NO_DAEMONIZE=true \
    PATH=$PATH:${SPARK_HOME}/bin:${SPARK_HOME}/sbin

# Create application directory
RUN mkdir -p /opt/spark-app/jars /opt/spark-app/conf

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
    APP_JAR=/opt/spark-app/jars/app.jar

# Expose common Spark ports
EXPOSE 4040 6066 7077 7078 7079 8080 8081

# Use the entrypoint script
ENTRYPOINT ["/opt/spark-app/docker-entrypoint.sh"]
