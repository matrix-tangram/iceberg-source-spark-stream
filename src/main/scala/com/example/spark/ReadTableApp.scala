package com.example.spark

import org.slf4j.LoggerFactory

/**
 * Spark application to read and display an Iceberg table from S3 Tables or Glue.
 * 
 * Uses SparkSessionFactory for consistent Spark session configuration.
 * 
 * Configuration via environment variables:
 * - CATALOG_TYPE: "s3tables" or "glue" (default: "s3tables")
 * - S3_TABLE_BUCKET_ARN: ARN of the S3 table bucket (for s3tables)
 * - S3_WAREHOUSE: S3 path for Glue catalog (for glue)
 * - TABLE_NAMESPACE: Namespace/database name (default: "raw_data")
 * - TABLE_NAME: Name of the table to read (default: "test_table")
 * - AWS_ACCESS_KEY_ID: AWS access key (optional)
 * - AWS_SECRET_ACCESS_KEY: AWS secret key (optional)
 * - AWS_SESSION_TOKEN: AWS session token (optional)
 */
object ReadTableApp {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    // Configuration from environment variables
    val tableNamespace = sys.env.getOrElse("TABLE_NAMESPACE", "raw_data")
    val tableName = sys.env.getOrElse("TABLE_NAME", "test_table")
    val catalogName = "iceberg_catalog"

    logger.info("=".repeat(80))
    logger.info("Starting Spark application to read Iceberg table")
    logger.info("=".repeat(80))
    logger.info(s"Namespace: $tableNamespace")
    logger.info(s"Table Name: $tableName")
    logger.info("=".repeat(80))

    // Create Spark session using SparkSessionFactory
    val spark = SparkSessionFactory.create(
      appName = "Read Iceberg Table from S3 Tables",
      catalogName = catalogName
    )

    try {
      logger.info("Spark session created successfully")
      
      // Construct full table path
      val fullTablePath = s"$catalogName.$tableNamespace.$tableName"
      logger.info(s"Reading table: $fullTablePath")

      // Read and display the table
      val df = spark.sql(s"SELECT * FROM $fullTablePath")
      
      logger.info(s"Table schema:")
      df.printSchema()
      
      val rowCount = df.count()
      logger.info(s"Total rows: $rowCount")
      
      logger.info(s"Displaying table contents:")
      df.show(truncate = false)
      
      logger.info("=".repeat(80))
      logger.info("Application completed successfully")
      logger.info("=".repeat(80))

    } catch {
      case e: Exception =>
        logger.error("=".repeat(80))
        logger.error("Error reading table", e)
        logger.error("=".repeat(80))
        throw e
    } finally {
      spark.stop()
      logger.info("Spark session stopped")
    }
  }
}
