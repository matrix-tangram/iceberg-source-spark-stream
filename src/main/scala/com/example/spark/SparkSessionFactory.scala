package com.example.spark

import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory

/**
 * Factory for creating configured Spark sessions with Iceberg and AWS support.
 * 
 * This factory handles:
 * - AWS credentials configuration (explicit or default provider chain)
 * - Iceberg catalog setup (S3 Tables or Glue)
 * - S3 file system configuration
 * 
 * Usage:
 * {{{
 *   val spark = SparkSessionFactory.create()
 *   // or with custom app name
 *   val spark = SparkSessionFactory.create("My Custom App")
 * }}}
 */
object SparkSessionFactory {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates a configured Spark session with Iceberg and AWS support.
   * 
   * Configuration is read from environment variables:
   * - CATALOG_TYPE: "s3tables" or "glue" (default: "s3tables")
   * - S3_TABLE_BUCKET_ARN: ARN for S3 Tables catalog
   * - S3_WAREHOUSE: S3 path for Glue catalog
   * - AWS_ACCESS_KEY_ID: AWS access key (optional)
   * - AWS_SECRET_ACCESS_KEY: AWS secret key (optional)
   * - AWS_SESSION_TOKEN: AWS session token for temporary credentials (optional)
   * 
   * @param appName Optional application name (default: "Spark Iceberg AWS Application")
   * @param catalogName Optional catalog name (default: "iceberg_catalog")
   * @return Configured SparkSession
   */
  def create(
    appName: String = "Spark Iceberg AWS Application",
    catalogName: String = "iceberg_catalog"
  ): SparkSession = {
    
    // Configuration: Choose catalog type ("s3tables" or "glue")
    val catalogType = sys.env.getOrElse("CATALOG_TYPE", "s3tables")

    // AWS Credentials (optional - if not provided, uses default provider chain)
    val awsAccessKey = sys.env.get("AWS_ACCESS_KEY_ID")
    val awsSecretKey = sys.env.get("AWS_SECRET_ACCESS_KEY")
    val awsSessionToken = sys.env.get("AWS_SESSION_TOKEN")

    // AWS Region
    val awsRegion = sys.env.getOrElse("AWS_REGION", "us-east-1")

    // AWS Configuration based on catalog type
    val (catalogImpl, warehouseOrTableBucketArn) = catalogType match {
      case "s3tables" =>
        // AWS S3 Tables configuration
        val tableBucketArn = sys.env.getOrElse(
          "S3_TABLE_BUCKET_ARN",
          "arn:aws:s3tables:us-east-1:123456789012:bucket/your-table-bucket"
        )
        ("software.amazon.s3tables.iceberg.S3TablesCatalog", tableBucketArn)
      
      case "glue" | _ =>
        // AWS Glue Data Catalog configuration
        val s3Warehouse = sys.env.getOrElse("S3_WAREHOUSE", "s3a://your-bucket/warehouse")
        ("org.apache.iceberg.aws.glue.GlueCatalog", s3Warehouse)
    }

    logger.info(s"Creating Spark session with $catalogType catalog...")
    logger.info(s"Catalog implementation: $catalogImpl")
    logger.info(s"Warehouse/TableBucket: $warehouseOrTableBucketArn")
    logger.info(s"AWS Region: $awsRegion")

    // Create Spark session with Iceberg configuration
    val sparkBuilder = SparkSession.builder()
      .appName(appName)
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .config(s"spark.sql.catalog.$catalogName", "org.apache.iceberg.spark.SparkCatalog")
      .config(s"spark.sql.catalog.$catalogName.catalog-impl", catalogImpl)
      .config(s"spark.sql.catalog.$catalogName.io-impl", "org.apache.iceberg.aws.s3.S3FileIO")
      .config(s"spark.sql.catalog.$catalogName.client.region", awsRegion)
      // AWS S3 configuration
      .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
      .config("spark.hadoop.fs.s3a.endpoint.region", awsRegion)

    // Configure AWS credentials if provided
    val sparkBuilderWithCreds = (awsAccessKey, awsSecretKey) match {
      case (Some(accessKey), Some(secretKey)) =>
        logger.info("Using provided AWS credentials (access key/secret key)")
        val builder = sparkBuilder
          .config("spark.hadoop.fs.s3a.access.key", accessKey)
          .config("spark.hadoop.fs.s3a.secret.key", secretKey)
        // Add session token if available (for temporary credentials)
        awsSessionToken.map { token =>
          builder.config("spark.hadoop.fs.s3a.session.token", token)
        }.getOrElse(builder)
      
      case _ =>
        logger.info("Using default AWS credentials provider chain")
        sparkBuilder
          .config("spark.hadoop.fs.s3a.aws.credentials.provider",
            "com.amazonaws.auth.DefaultAWSCredentialsProviderChain")
    }

    // Add catalog-specific configuration
    val spark = catalogType match {
      case "s3tables" =>
        // S3TablesCatalog expects "warehouse" property set to the table bucket ARN
        sparkBuilderWithCreds
          .config(s"spark.sql.catalog.$catalogName.warehouse", warehouseOrTableBucketArn)
          .getOrCreate()
      
      case "glue" | _ =>
        sparkBuilderWithCreds
          .config(s"spark.sql.catalog.$catalogName.warehouse", warehouseOrTableBucketArn)
          .getOrCreate()
    }

    logger.info("Spark session created successfully")
    spark
  }

  /**
   * Creates a Spark session builder pre-configured with Iceberg and AWS settings.
   * This allows for additional customization before calling getOrCreate().
   * 
   * @param appName Application name
   * @param catalogName Catalog name
   * @return Pre-configured SparkSession.Builder
   */
  def builder(
    appName: String = "Spark Iceberg AWS Application",
    catalogName: String = "iceberg_catalog"
  ): SparkSession.Builder = {
    create(appName, catalogName).newSession().sparkContext.getConf
    SparkSession.builder().config(create(appName, catalogName).sparkContext.getConf)
  }
}
