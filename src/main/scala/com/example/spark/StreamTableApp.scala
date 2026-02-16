package com.example.spark

import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.slf4j.LoggerFactory
import java.nio.file.{Files, Paths}
import java.time.Instant
import scala.util.Try

/**
 * Spark Structured Streaming application to read incremental changes from an Iceberg table.
 *
 * Features:
 * - Reads incremental changes from Iceberg table using Spark Structured Streaming
 * - On first run, reads the entire table
 * - Stores checkpoint data for subsequent restarts
 * - Transforms data into Kafka-compatible format (key-value pairs)
 * - Supports configurable Kafka serializers including Confluent Schema Registry
 * - Outputs results to Kafka with automatic schema registration
 *
 * Configuration via environment variables:
 * - CATALOG_TYPE: "s3tables" or "glue" (default: "s3tables")
 * - S3_TABLE_BUCKET_ARN: ARN of the S3 table bucket (for s3tables)
 * - S3_WAREHOUSE: S3 path for Glue catalog (for glue)
 * - TABLE_NAMESPACE: Namespace/database name (default: "raw_data")
 * - TABLE_NAME: Name of the table to read (default: "test_table")
 * - KEY_FIELD: Field to use as Kafka key (optional, if omitted key will be null)
 * - CHECKPOINT_DIR: Directory to store checkpoint data (default: "./checkpoints")
 * - TRIGGER_INTERVAL: Processing trigger interval (default: "10 seconds")
 * - KAFKA_BOOTSTRAP_SERVERS: Kafka broker addresses (default: "bastion.ocp.tangram-soft.com:31700")
 * - KAFKA_OUTPUT_TOPIC: Kafka topic to write to (default: "iceberg-output-topic")
 * - KAFKA_SECURITY_PROTOCOL: Security protocol (default: "PLAINTEXT")
 * - KAFKA_SASL_MECHANISM: SASL mechanism (optional, for authentication)
 * - KAFKA_SASL_JAAS_CONFIG: JAAS configuration (optional, for authentication)
 * - KAFKA_KEY_SERIALIZER: Key serializer class (default: "org.apache.kafka.common.serialization.StringSerializer")
 * - KAFKA_VALUE_SERIALIZER: Value serializer class (default: "io.confluent.kafka.serializers.KafkaJsonSerializer")
 * - KAFKA_SERIALIZER_JSON_SCHEMAS_ENABLED: Enable inline JSON schemas (default: "false")
 * - SCHEMA_REGISTRY_URL: Schema Registry URL (optional, for Confluent Schema Registry integration)
 * - SCHEMA_REGISTRY_BASIC_AUTH_USER_INFO: Schema Registry auth in format "username:password" (optional)
 * - AUTO_REGISTER_SCHEMAS: Auto-register schemas with Schema Registry (default: "true")
 * - KAFKA_SERIALIZER_*: Additional serializer-specific configs (e.g., KAFKA_SERIALIZER_json_add_type_info=true)
 * - AWS_ACCESS_KEY_ID: AWS access key (optional)
 * - AWS_SECRET_ACCESS_KEY: AWS secret key (optional)
 * - AWS_SESSION_TOKEN: AWS session token (optional)
 */
object StreamTableApp {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    // Configuration from environment variables
    val tableNamespace = sys.env.getOrElse("TABLE_NAMESPACE", "raw_data")
    val tableName = sys.env.getOrElse("TABLE_NAME", "test_table")
    val catalogName = "iceberg_catalog"
    val keyField = sys.env.get("KEY_FIELD") // Optional key field for Kafka output
    val checkpointBase = sys.env.getOrElse("CHECKPOINT_DIR", "./checkpoints")
    val triggerInterval = sys.env.getOrElse("TRIGGER_INTERVAL", "10 seconds")

    // Kafka configuration
    val kafkaBootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "bastion.ocp.tangram-soft.com:31700")
    val kafkaOutputTopic = sys.env.getOrElse("KAFKA_OUTPUT_TOPIC", "iceberg-output-topic")
    val kafkaSecurityProtocol = sys.env.getOrElse("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT")
    val kafkaSaslMechanism = sys.env.get("KAFKA_SASL_MECHANISM")
    val kafkaSaslJaasConfig = sys.env.get("KAFKA_SASL_JAAS_CONFIG")

    // Schema Registry configuration
    val schemaRegistryUrl = sys.env.get("SCHEMA_REGISTRY_URL")
    val schemaRegistryAuth = sys.env.get("SCHEMA_REGISTRY_BASIC_AUTH_USER_INFO")
    val autoRegisterSchemas = sys.env.getOrElse("AUTO_REGISTER_SCHEMAS", "true").toBoolean

    // Serializer configuration
    val serializerConfig = KafkaProducerHelper.parseSerializerConfig(
      sys.env.toMap,
      schemaRegistryUrl,
      schemaRegistryAuth,
      autoRegisterSchemas
    )
    
    // Build checkpoint directory - ensure it's on S3 if using S3 Tables
    val checkpointDir = if (checkpointBase.startsWith("s3://") || checkpointBase.startsWith("s3a://")) {
      checkpointBase
    } else {
      // Use local filesystem for checkpoints
      checkpointBase
    }

    logger.info("=".repeat(80))
    logger.info("Starting Spark Streaming application for Iceberg table")
    logger.info("=".repeat(80))
    logger.info(s"Namespace: $tableNamespace")
    logger.info(s"Table Name: $tableName")
    logger.info(s"Key Field: ${keyField.getOrElse("None (null keys)")}")
    logger.info(s"Checkpoint Directory: $checkpointDir")
    logger.info(s"Trigger Interval: $triggerInterval")
    logger.info(s"Kafka Bootstrap Servers: $kafkaBootstrapServers")
    logger.info(s"Kafka Output Topic: $kafkaOutputTopic")
    logger.info(s"Kafka Security Protocol: $kafkaSecurityProtocol")
    if (kafkaSaslMechanism.isDefined) logger.info(s"Kafka SASL Mechanism: ${kafkaSaslMechanism.get}")
    logger.info(s"Key Serializer: ${serializerConfig.keySerializerClass}")
    logger.info(s"Value Serializer: ${serializerConfig.valueSerializerClass}")
    schemaRegistryUrl.foreach(url => logger.info(s"Schema Registry URL: $url"))
    logger.info(s"Auto Register Schemas: $autoRegisterSchemas")
    logger.info("=".repeat(80))

    // Create Spark session using SparkSessionFactory
    val spark = SparkSessionFactory.create(
      appName = "Stream Iceberg Table Changes",
      catalogName = catalogName
    )
    
    // Configure checkpoint location to use Hadoop FileSystem (not Iceberg's S3FileIO)
    spark.conf.set("spark.sql.streaming.checkpointLocation", checkpointDir)

    try {
      logger.info("Spark session created successfully")
      
      // Construct full table path
      val fullTablePath = s"$catalogName.$tableNamespace.$tableName"
      logger.info(s"Setting up streaming read for table: $fullTablePath")

      // Read from Iceberg table as a stream
      val streamDf = readIcebergStream(spark, fullTablePath)

      logger.info("Stream DataFrame created with schema:")
      streamDf.printSchema()

      // Register schema with Schema Registry if configured
      schemaRegistryUrl.foreach { url =>
        val schemaRegistryClient = SchemaRegistryHelper.createSchemaRegistryClient(url, schemaRegistryAuth)
        val jsonSchema = SchemaRegistryHelper.inferJsonSchemaFromDataFrame(streamDf)
        val subject = s"$kafkaOutputTopic-value"

        if (autoRegisterSchemas) {
          try {
            SchemaRegistryHelper.registerSchema(schemaRegistryClient, subject, jsonSchema)
          } catch {
            case e: Exception =>
              logger.warn(s"Failed to register schema: ${e.getMessage}. Will rely on auto-registration during serialization.")
          }
        }
      }

      // Start streaming query - output to Kafka using foreachBatch
      val query = streamDf.writeStream
        .outputMode(OutputMode.Append())
        .foreachBatch { (batchDf: DataFrame, batchId: Long) =>
          logger.info(s"Processing batch $batchId with ${batchDf.count()} rows")

          // Write to Kafka
          if (!batchDf.isEmpty) {
            logger.info(s"Batch $batchId - Writing to Kafka topic: $kafkaOutputTopic")
            writeToKafkaWithSerializers(
              batchDf,
              kafkaBootstrapServers,
              kafkaOutputTopic,
              keyField,
              serializerConfig,
              kafkaSecurityProtocol,
              kafkaSaslMechanism,
              kafkaSaslJaasConfig
            )
            logger.info(s"Batch $batchId - Successfully written to Kafka")
          } else {
            logger.info(s"Batch $batchId - Empty batch, skipping Kafka write")
          }
        }
        .option("checkpointLocation", s"$checkpointDir/$tableNamespace/$tableName")
        .trigger(Trigger.ProcessingTime(triggerInterval))
        .start()

      logger.info("Streaming query started successfully")
      logger.info(s"Query ID: ${query.id}")
      logger.info(s"Query Name: ${query.name}")
      logger.info("=".repeat(80))
      logger.info("Processing stream and writing to both Console and Kafka...")
      logger.info("(Press Ctrl+C to stop)")
      logger.info("=".repeat(80))

      // Wait for termination
      query.awaitTermination()

    } catch {
      case e: Exception =>
        logger.error("=".repeat(80))
        logger.error("Error in streaming application", e)
        logger.error("=".repeat(80))
        throw e
    } finally {
      spark.stop()
      logger.info("Spark session stopped")
    }
  }

  /**
   * Reads an Iceberg table as a streaming DataFrame.
   * 
   * For incremental reads, Iceberg streaming supports:
   * - "streaming-skip-delete-snapshots" - skip snapshots with delete operations
   * - "streaming-skip-overwrite-snapshots" - skip snapshots with overwrite operations
   * - "start-snapshot-timestamp" - start reading from specific timestamp
   * - "start-snapshot-id" - start reading from specific snapshot ID
   * 
   * On first run, it reads all existing data (full table scan).
   * Subsequent runs read only new data appended since the last checkpoint.
   * 
   * @param spark SparkSession
   * @param tablePath Full path to the Iceberg table
   * @return Streaming DataFrame
   */
  private def readIcebergStream(spark: SparkSession, tablePath: String): DataFrame = {
    logger.info(s"Reading Iceberg table as stream: $tablePath")
    
    // Read Iceberg table as a stream
    // Iceberg streaming will automatically handle incremental reads
    // Note: Don't use stream-from-timestamp as it causes issues with checkpoint locations
    val streamDf = spark.readStream
      .format("iceberg")
      .load(tablePath)

    logger.info("Successfully created streaming DataFrame from Iceberg table")
    streamDf
  }

  /**
   * Converts a Spark Row to a Map for JSON serialization.
   *
   * @param row Spark Row
   * @return Map representation of the row
   */
  private def rowToMap(row: Row): Map[String, Any] = {
    row.schema.fields.map { field =>
      val value = row.getAs[Any](field.name)
      field.name -> (if (value == null) null else value)
    }.toMap
  }

  /**
   * Writes a DataFrame to Kafka topic using configurable serializers.
   * Uses foreachPartition to leverage native Kafka producers with proper serializers.
   *
   * @param df DataFrame to write
   * @param bootstrapServers Kafka bootstrap servers
   * @param topic Kafka topic to write to
   * @param keyFieldOpt Optional key field name
   * @param serializerConfig Serializer configuration
   * @param securityProtocol Security protocol (PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL)
   * @param saslMechanism Optional SASL mechanism
   * @param saslJaasConfig Optional JAAS configuration
   */
  private def writeToKafkaWithSerializers(
      df: DataFrame,
      bootstrapServers: String,
      topic: String,
      keyFieldOpt: Option[String],
      serializerConfig: KafkaProducerHelper.SerializerConfig,
      securityProtocol: String,
      saslMechanism: Option[String],
      saslJaasConfig: Option[String]
  ): Unit = {
    val rowCount = df.count()
    logger.info(s"Writing $rowCount rows to Kafka topic: $topic")

    // Broadcast configuration to executors
    val broadcastBootstrapServers = df.sparkSession.sparkContext.broadcast(bootstrapServers)
    val broadcastTopic = df.sparkSession.sparkContext.broadcast(topic)
    val broadcastKeyField = df.sparkSession.sparkContext.broadcast(keyFieldOpt)
    val broadcastSerializerConfig = df.sparkSession.sparkContext.broadcast(serializerConfig)
    val broadcastSecurityProtocol = df.sparkSession.sparkContext.broadcast(securityProtocol)
    val broadcastSaslMechanism = df.sparkSession.sparkContext.broadcast(saslMechanism)
    val broadcastSaslJaasConfig = df.sparkSession.sparkContext.broadcast(saslJaasConfig)

    // Process each partition
    df.foreachPartition { partition: Iterator[Row] =>
      if (partition.nonEmpty) {
        // Create producer for this partition
        // KafkaJsonSerializer can handle Map[String, Any] objects directly
        val producer = KafkaProducerHelper.createProducer[String, Any](
          broadcastBootstrapServers.value,
          broadcastSerializerConfig.value,
          broadcastSecurityProtocol.value,
          broadcastSaslMechanism.value,
          broadcastSaslJaasConfig.value
        )

        try {
          partition.foreach { row =>
            // Extract key
            val key = broadcastKeyField.value match {
              case Some(keyField) if row.schema.fieldNames.contains(keyField) =>
                val keyValue = row.getAs[Any](keyField)
                if (keyValue != null) keyValue.toString else null
              case _ => null
            }

            // Convert row to Map - serializer will handle JSON conversion
            val value = rowToMap(row)

            // Send to Kafka - serializer handles the conversion
            KafkaProducerHelper.sendRecord(
              producer,
              broadcastTopic.value,
              key,
              value
            )
          }
        } finally {
          producer.close()
        }
      }
    }

    logger.info(s"Successfully wrote $rowCount rows to Kafka topic: $topic")
  }
}
