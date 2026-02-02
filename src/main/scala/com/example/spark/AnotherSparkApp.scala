package com.example.spark

import org.slf4j.LoggerFactory

/**
 * Example of a second Spark application using the reusable SparkSessionFactory.
 * 
 * This demonstrates how multiple applications can share the same session
 * configuration logic while having different business logic.
 */
object AnotherSparkApp {
  private val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info("Starting Another Spark Application...")

    // Reuse the SparkSessionFactory for consistent configuration
    val spark = SparkSessionFactory.create(
      appName = "Another Spark Application",
      catalogName = "iceberg_catalog"
    )

    try {
      // Example: List all databases
      logger.info("Listing all databases:")
      spark.sql("SHOW DATABASES").show()

      // Example: Query system tables
      logger.info("Checking Iceberg catalog configuration:")
      val catalogs = spark.catalog.listCatalogs()
      catalogs.foreach { catalog =>
        logger.info(s"Available catalog: ${catalog.name}")
      }

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
