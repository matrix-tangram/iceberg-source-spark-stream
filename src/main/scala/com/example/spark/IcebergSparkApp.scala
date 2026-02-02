package com.example.spark

import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

/**
 * Apache Spark application with Apache Iceberg v3 (1.10.1) and AWS support.
 *
 * This application demonstrates how to:
 * - Create a Spark session with Iceberg catalog using SparkSessionFactory
 * - Configure AWS S3 integration (S3 Tables or Glue Data Catalog)
 * - Create and query Iceberg tables
 *
 * Catalog Options:
 * - "s3tables": Use AWS S3 Tables (managed table buckets)
 * - "glue": Use AWS Glue Data Catalog
 */
object IcebergSparkApp {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val catalogName = "iceberg_catalog"

    logger.info("Starting Spark Iceberg AWS application...")

    // Create Spark session using the factory
    val spark = SparkSessionFactory.create(
      appName = "Spark Iceberg AWS Application",
      catalogName = catalogName
    )

    try {
      logger.info("Spark session created successfully")

      // Example: Create a database
      spark.sql(s"CREATE DATABASE IF NOT EXISTS $catalogName.demo_db")
      logger.info("Database created or already exists")

      // Example: Create an Iceberg table
      spark.sql(
        s"""CREATE TABLE IF NOT EXISTS $catalogName.demo_db.sample_table
           |(id BIGINT, name STRING, timestamp TIMESTAMP)
           |USING iceberg
           |PARTITIONED BY (days(timestamp))""".stripMargin)
      logger.info("Table created or already exists")

      // Example: Insert sample data
      spark.sql(
        s"""INSERT INTO $catalogName.demo_db.sample_table
           |VALUES (1, 'Alice', TIMESTAMP '2024-01-01 10:00:00'),
           |       (2, 'Bob', TIMESTAMP '2024-01-02 11:00:00'),
           |       (3, 'Charlie', TIMESTAMP '2024-01-03 12:00:00')""".stripMargin)
      logger.info("Sample data inserted")

      // Example: Query the table
      val result = spark.sql(s"SELECT * FROM $catalogName.demo_db.sample_table")
      logger.info("Query results:")
      result.show()

      // Example: Show table metadata
      spark.sql(s"DESCRIBE EXTENDED $catalogName.demo_db.sample_table").show(truncate = false)

      // Example: Show table history (Iceberg feature)
      spark.sql(s"SELECT * FROM $catalogName.demo_db.sample_table.history").show(truncate = false)

      // Example: Show table snapshots (Iceberg feature)
      spark.sql(s"SELECT * FROM $catalogName.demo_db.sample_table.snapshots").show(truncate = false)

      logger.info("Application completed successfully")

    } catch {
      case e: Exception =>
        logger.error("Error executing Spark job", e)
        throw e
    } finally {
      spark.stop()
      logger.info("Spark session stopped")
    }
  }
}
