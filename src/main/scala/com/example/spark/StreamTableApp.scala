package com.example.spark

import org.apache.spark.sql.{DataFrame, SparkSession}
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
 * - Outputs results to console (can be extended to write to Kafka)
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
    logger.info(s"Trigger Interval: $triggerInterval")    logger.info(s"Kafka Bootstrap Servers: $kafkaBootstrapServers")
    logger.info(s"Kafka Output Topic: $kafkaOutputTopic")
    logger.info(s"Kafka Security Protocol: $kafkaSecurityProtocol")
    if (kafkaSaslMechanism.isDefined) logger.info(s"Kafka SASL Mechanism: ${kafkaSaslMechanism.get}")    logger.info("=".repeat(80))

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

      // Transform to Kafka-compatible format (key-value)
      val kafkaFormatDf = transformToKafkaFormat(streamDf, keyField)
      
      logger.info("Transformed DataFrame schema (Kafka format):")
      kafkaFormatDf.printSchema()

      // Start streaming query - output to both console and Kafka using foreachBatch
      val query = kafkaFormatDf.writeStream
        .outputMode(OutputMode.Append())
        .foreachBatch { (batchDf: DataFrame, batchId: Long) =>
          logger.info(s"Processing batch $batchId with ${batchDf.count()} rows")
          
          // Write to console for debugging
          logger.info(s"Batch $batchId - Console Output:")
          batchDf.show(truncate = false)
          
          // Write to Kafka
          if (!batchDf.isEmpty) {
            logger.info(s"Batch $batchId - Writing to Kafka topic: $kafkaOutputTopic")
            writeToKafka(
              batchDf,
              kafkaBootstrapServers,
              kafkaOutputTopic,
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
   * Transforms a DataFrame into Kafka-compatible format with key and value columns.
   * 
   * Output schema:
   * - key: Binary (extracted from specified field, or null if not specified)
   * - value: Binary (JSON representation of all fields)
   * 
   * @param df Input DataFrame
   * @param keyFieldOpt Optional field name to use as key
   * @return Transformed DataFrame with key and value columns
   */
  private def transformToKafkaFormat(df: DataFrame, keyFieldOpt: Option[String]): DataFrame = {
    logger.info("Transforming DataFrame to Kafka format (key-value pairs)")
    
    // Create value: Convert entire row to JSON
    val dfWithValue = df.withColumn("value", to_json(struct(df.columns.map(col): _*)))
    
    // Create key: Use specified field or null
    val dfWithKeyValue = keyFieldOpt match {
      case Some(keyField) if df.columns.contains(keyField) =>
        logger.info(s"Using field '$keyField' as Kafka key")
        dfWithValue.withColumn("key", col(keyField).cast("string"))
      
      case Some(keyField) =>
        logger.warn(s"Specified key field '$keyField' not found in DataFrame. Using null keys.")
        logger.warn(s"Available fields: ${df.columns.mkString(", ")}")
        dfWithValue.withColumn("key", lit(null).cast("string"))
      
      case None =>
        logger.info("No key field specified. Using null keys.")
        dfWithValue.withColumn("key", lit(null).cast("string"))
    }
    
    // Select only key and value columns (standard Kafka format)
    val result = dfWithKeyValue.select("key", "value")
    
    logger.info("DataFrame successfully transformed to Kafka format")
    result
  }

  /**
   * Writes a DataFrame to Kafka topic.
   * 
   * @param df DataFrame with key and value columns
   * @param bootstrapServers Kafka bootstrap servers
   * @param topic Kafka topic to write to
   * @param securityProtocol Security protocol (PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL)
   * @param saslMechanism Optional SASL mechanism
   * @param saslJaasConfig Optional JAAS configuration
   */
  private def writeToKafka(
      df: DataFrame,
      bootstrapServers: String,
      topic: String,
      securityProtocol: String,
      saslMechanism: Option[String],
      saslJaasConfig: Option[String]
  ): Unit = {
    logger.info(s"Writing ${df.count()} rows to Kafka topic: $topic")
    
    // Build Kafka options
    var kafkaOptions = Map(
      "kafka.bootstrap.servers" -> bootstrapServers,
      "topic" -> topic,
      "kafka.security.protocol" -> securityProtocol
    )
    
    // Add SASL configuration if provided
    if (saslMechanism.isDefined) {
      kafkaOptions = kafkaOptions + ("kafka.sasl.mechanism" -> saslMechanism.get)
    }
    if (saslJaasConfig.isDefined) {
      kafkaOptions = kafkaOptions + ("kafka.sasl.jaas.config" -> saslJaasConfig.get)
    }
    
    // Write to Kafka
    df.write
      .format("kafka")
      .options(kafkaOptions)
      .save()
    
    logger.info(s"Successfully wrote batch to Kafka topic: $topic")
  }
}
