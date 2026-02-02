#!/bin/bash
set -e

# Print configuration for debugging
echo "=== Spark Container Configuration ==="
echo "SPARK_MODE: ${SPARK_MODE}"
echo "PARALLELISM: ${PARALLELISM}"
echo "APP_CLASS: ${APP_CLASS}"
echo "APP_JAR: ${APP_JAR}"
echo "===================================="

# Function to wait for Spark master
wait_for_master() {
    echo "Waiting for Spark master at ${SPARK_MASTER_HOST}:${SPARK_MASTER_PORT}..."
    until nc -z ${SPARK_MASTER_HOST} ${SPARK_MASTER_PORT} 2>/dev/null; do
        echo "Master not ready, retrying in 2 seconds..."
        sleep 2
    done
    echo "Master is ready!"
}

# Configure Spark based on mode
case "${SPARK_MODE}" in
    local)
        echo "Starting Spark application in local[${PARALLELISM}] mode..."
        exec ${SPARK_HOME}/bin/spark-submit \
            --master local[${PARALLELISM}] \
            --class ${APP_CLASS} \
            --driver-memory ${SPARK_DRIVER_MEMORY} \
            --driver-cores ${SPARK_DRIVER_CORES} \
            --conf spark.ui.port=${SPARK_UI_PORT} \
            --conf spark.driver.bindAddress=0.0.0.0 \
            --conf spark.driver.host=$(hostname -i) \
            ${SPARK_SUBMIT_OPTS} \
            ${APP_JAR} \
            ${APP_ARGS}
        ;;
    
    submit)
        echo "Starting Spark submit in client mode for K8s..."
        exec ${SPARK_HOME}/bin/spark-submit \
            --master k8s://https://kubernetes.default.svc:443 \
            --deploy-mode cluster \
            --class ${APP_CLASS} \
            --name ${APP_NAME:-spark-stream-table-app} \
            --conf spark.executor.instances=${PARALLELISM} \
            --conf spark.kubernetes.container.image=${K8S_IMAGE:-spark-app:latest} \
            --conf spark.kubernetes.driver.pod.name=${HOSTNAME} \
            --conf spark.kubernetes.namespace=${K8S_NAMESPACE:-default} \
            --conf spark.kubernetes.authenticate.driver.serviceAccountName=${K8S_SERVICE_ACCOUNT:-spark} \
            --conf spark.driver.memory=${SPARK_DRIVER_MEMORY} \
            --conf spark.executor.memory=${SPARK_EXECUTOR_MEMORY} \
            --conf spark.driver.cores=${SPARK_DRIVER_CORES} \
            --conf spark.executor.cores=${SPARK_EXECUTOR_CORES} \
            --conf spark.kubernetes.driver.label.app=${APP_NAME:-spark-stream-table-app} \
            --conf spark.kubernetes.driver.label.component=driver \
            --conf spark.kubernetes.executor.label.app=${APP_NAME:-spark-stream-table-app} \
            --conf spark.kubernetes.executor.label.component=executor \
            ${SPARK_SUBMIT_OPTS} \
            ${APP_JAR} \
            ${APP_ARGS}
        ;;
    
    driver)
        echo "Starting Spark application as K8s driver..."
        exec ${SPARK_HOME}/bin/spark-submit \
            --master k8s://https://kubernetes.default.svc:443 \
            --deploy-mode client \
            --class ${APP_CLASS} \
            --name ${APP_NAME:-spark-stream-table-app} \
            --conf spark.executor.instances=${PARALLELISM} \
            --conf spark.kubernetes.container.image=${K8S_IMAGE:-spark-app:latest} \
            --conf spark.kubernetes.namespace=${K8S_NAMESPACE:-default} \
            --conf spark.kubernetes.authenticate.driver.serviceAccountName=${K8S_SERVICE_ACCOUNT:-spark} \
            --conf spark.driver.bindAddress=0.0.0.0 \
            --conf spark.driver.host=${SPARK_DRIVER_HOST:-$(hostname -i)} \
            --conf spark.driver.port=${SPARK_DRIVER_PORT} \
            --conf spark.driver.blockManager.port=${SPARK_DRIVER_BLOCK_MANAGER_PORT} \
            --conf spark.driver.memory=${SPARK_DRIVER_MEMORY} \
            --conf spark.executor.memory=${SPARK_EXECUTOR_MEMORY} \
            --conf spark.driver.cores=${SPARK_DRIVER_CORES} \
            --conf spark.executor.cores=${SPARK_EXECUTOR_CORES} \
            --conf spark.ui.port=${SPARK_UI_PORT} \
            --conf spark.kubernetes.driver.label.app=${APP_NAME:-spark-stream-table-app} \
            --conf spark.kubernetes.driver.label.component=driver \
            --conf spark.kubernetes.executor.label.app=${APP_NAME:-spark-stream-table-app} \
            --conf spark.kubernetes.executor.label.component=executor \
            ${SPARK_SUBMIT_OPTS} \
            ${APP_JAR} \
            ${APP_ARGS}
        ;;
    
    master)
        echo "Starting Spark master..."
        exec ${SPARK_HOME}/sbin/start-master.sh && \
        tail -f ${SPARK_HOME}/logs/*
        ;;
    
    worker)
        echo "Starting Spark worker..."
        if [ -z "${SPARK_MASTER_URL}" ]; then
            export SPARK_MASTER_URL="spark://${SPARK_MASTER_HOST}:${SPARK_MASTER_PORT}"
        fi
        echo "Connecting to master: ${SPARK_MASTER_URL}"
        exec ${SPARK_HOME}/sbin/start-worker.sh ${SPARK_MASTER_URL} \
            --cores ${SPARK_EXECUTOR_CORES} \
            --memory ${SPARK_EXECUTOR_MEMORY} \
            --webui-port ${SPARK_WORKER_UI_PORT} && \
        tail -f ${SPARK_HOME}/logs/*
        ;;
    
    *)
        echo "Unknown SPARK_MODE: ${SPARK_MODE}"
        echo "Valid modes: local, submit, driver, master, worker"
        exit 1
        ;;
esac
