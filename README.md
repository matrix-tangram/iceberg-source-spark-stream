# Spark Iceberg AWS Project

Apache Spark project with Apache Iceberg v3 (1.10.1) and AWS support.

## Features

- **Scala 2.12** for functional programming
- **Apache Spark 3.5.0** for distributed data processing
- **Apache Iceberg 1.10.1** for table format with ACID transactions
- **AWS Integration** with S3 and Glue Catalog support
- **Hadoop AWS** for S3 file system integration
- **Reusable SparkSessionFactory** for consistent session configuration across applications

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- AWS credentials configured (via AWS CLI or environment variables)
- Access to AWS S3 bucket and Glue Catalog
- Apache Spark 3.5.0 (can be installed locally via `./install-spark.sh`)

## Project Structure

```
.
├── pom.xml
├── .env.example          # Example environment variables configuration
├── .env.read-table       # Configuration for ReadTableApp
├── run-read-table.sh     # Script to run ReadTableApp
├── READ_TABLE_README.md  # Documentation for ReadTableApp
├── src
│   ├── main
│   │   ├── scala
│   │   │   └── com
│   │   │       └── example
│   │   │           └── spark
│   │   │               ├── SparkSessionFactory.scala  # Reusable session factory
│   │   │               ├── IcebergSparkApp.scala      # Main application
│   │   │               ├── AnotherSparkApp.scala      # Example second app
│   │   │               └── ReadTableApp.scala         # Read table utility
│   │   └── resources
│   │       └── spark-defaults.conf
│   └── test
│       └── scala
│           └── com
│               └── example
│                   └── spark
└── README.md
```

## Quick Start

### Option 1: Read an Existing Table (Simplest)

If you just want to read and display an Iceberg table:

```bash
# Install Spark locally (one-time setup)
./install-spark.sh

# Configure your table details in .env.read-table, then:
./run-read-table.sh
```

See [READ_TABLE_README.md](READ_TABLE_README.md) for details.

### Option 2: Full Application Setup

1. **Install Apache Spark (one-time setup):**
   ```bash
   ./install-spark.sh
   ```

2. **Copy the example environment file:**
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` file with your AWS credentials and configuration:**
   ```bash
   nano .env  # or use your preferred editor
   ```

3. **Build the project:**
   ```bash
   mvn clean package
   ```

4. **Run the application:**
   ```bash
   source .env
   mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
   ```

## Spark Installation

The project includes a script to install Apache Spark 3.5.0 locally:

```bash
./install-spark.sh
```

This will:
- Download Apache Spark 3.5.0 with Hadoop 3 support
- Extract it to `spark-3.5.0-bin-hadoop3/` in the project directory
- The `run-read-table.sh` script automatically uses this installation

**Manual Installation:**

If you prefer to install Spark elsewhere:
```bash
# Download and extract to a custom location
wget https://archive.apache.org/dist/spark/spark-3.5.0/spark-3.5.0-bin-hadoop3.tgz
tar -xzf spark-3.5.0-bin-hadoop3.tgz -C /opt/

# Set SPARK_HOME
export SPARK_HOME=/opt/spark-3.5.0-bin-hadoop3
export PATH=$SPARK_HOME/bin:$PATH
```

## Configuration

### AWS Setup

#### 1. Configure AWS Credentials

Choose one of the following methods:

**Method 1: Environment File (.env) - Recommended for Local Development**
```bash
# Copy the example file
cp .env.example .env

# Edit with your credentials
nano .env

# Load environment variables
source .env
# or
export $(cat .env | xargs)
```

**Method 2: AWS CLI (Default Provider Chain)**
```bash
aws configure
```
This stores credentials in `~/.aws/credentials` and uses the default provider chain.

**Method 3: Environment Variables (Explicit Credentials)**
```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
# Optional: For temporary credentials
export AWS_SESSION_TOKEN=your-session-token
```

**Method 4: Spark Configuration File**

Edit `spark-defaults.conf`:
```properties
spark.hadoop.fs.s3a.access.key=your-access-key
spark.hadoop.fs.s3a.secret.key=your-secret-key
# Optional: For temporary credentials
spark.hadoop.fs.s3a.session.token=your-session-token
```

#### 2. Choose Catalog Type and Configure

##### Option A: AWS S3 Tables (Recommended)

AWS S3 Tables provides a managed table bucket for Iceberg tables with built-in governance and access control.

**Set environment variables:**
```bash
export CATALOG_TYPE=s3tables
export S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/your-table-bucket
```

**Update `spark-defaults.conf`:**
```properties
spark.sql.catalog.iceberg_catalog.catalog-impl=org.apache.iceberg.aws.s3.S3TablesCatalog
spark.sql.catalog.iceberg_catalog.table-bucket-arn=arn:aws:s3tables:us-east-1:123456789012:bucket/your-table-bucket
```

##### Option B: AWS Glue Data Catalog (Classic)

Use AWS Glue for catalog metadata with S3 for table storage.

**Set environment variables:**
```bash
export CATALOG_TYPE=glue
export S3_WAREHOUSE=s3a://your-bucket/warehouse
```

**Update `spark-defaults.conf`:**
```properties
spark.sql.catalog.iceberg_catalog.catalog-impl=org.apache.iceberg.aws.glue.GlueCatalog
spark.sql.catalog.iceberg_catalog.warehouse=s3a://your-bucket/warehouse
```

### Building the Project

```bash
mvn clean package
```

This will create a fat JAR with all dependencies in the `target/` directory.

## Running the Application

### Local Mode with .env File (Recommended)

```bash
# Load environment variables from .env file
source .env
# or
export $(cat .env | xargs)

# Run the application
mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
```

### Local Mode (Default Credentials)

```bash
mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
```

### With Explicit AWS Credentials

#### Using Environment Variables

```bash
AWS_ACCESS_KEY_ID=your-access-key \
AWS_SECRET_ACCESS_KEY=your-secret-key \
CATALOG_TYPE=s3tables \
S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
```

#### Using Temporary Credentials (with Session Token)

```bash
AWS_ACCESS_KEY_ID=your-temp-access-key \
AWS_SECRET_ACCESS_KEY=your-temp-secret-key \
AWS_SESSION_TOKEN=your-session-token \
CATALOG_TYPE=s3tables \
S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
```

### With S3 Tables Catalog

```bash
CATALOG_TYPE=s3tables \
S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
```

### With Glue Catalog

```bash
CATALOG_TYPE=glue \
S3_WAREHOUSE=s3a://my-bucket/warehouse \
mvn exec:java -Dexec.mainClass="com.example.spark.IcebergSparkApp"
```

### Submit to Spark Cluster

#### With Default Credentials

```bash
spark-submit \
  --class com.example.spark.IcebergSparkApp \
  --master yarn \
  --deploy-mode cluster \
  --conf spark.executorEnv.CATALOG_TYPE=s3tables \
  --conf spark.executorEnv.S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
  target/spark-iceberg-aws-1.0-SNAPSHOT.jar
```

#### With Explicit Credentials

```bash
spark-submit \
  --class com.example.spark.IcebergSparkApp \
  --master yarn \
  --deploy-mode cluster \
  --conf spark.executorEnv.AWS_ACCESS_KEY_ID=your-access-key \
  --conf spark.executorEnv.AWS_SECRET_ACCESS_KEY=your-secret-key \
  --conf spark.executorEnv.CATALOG_TYPE=s3tables \
  --conf spark.executorEnv.S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
  target/spark-iceberg-aws-1.0-SNAPSHOT.jar
```

#### Alternative: Using Spark Configuration

```bash
spark-submit \
  --class com.example.spark.IcebergSparkApp \
  --master yarn \
  --deploy-mode cluster \
  --conf spark.hadoop.fs.s3a.access.key=your-access-key \
  --conf spark.hadoop.fs.s3a.secret.key=your-secret-key \
  --conf spark.executorEnv.CATALOG_TYPE=s3tables \
  --conf spark.executorEnv.S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
  target/spark-iceberg-aws-1.0-SNAPSHOT.jar
```

### Running Alternative Applications

The project includes `AnotherSparkApp.scala` as an example of reusing the `SparkSessionFactory`:

```bash
# With environment file
source .env
mvn exec:java -Dexec.mainClass="com.example.spark.AnotherSparkApp"

# Or with explicit configuration
CATALOG_TYPE=s3tables \
S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-table-bucket \
mvn exec:java -Dexec.mainClass="com.example.spark.AnotherSparkApp"
```

## Architecture

### SparkSessionFactory Module

The `SparkSessionFactory` is a reusable module that encapsulates all Spark session configuration logic:

**Key Features:**
- Centralized configuration management
- Support for multiple catalog types (S3 Tables, Glue)
- Flexible credential handling (explicit or default provider chain)
- Environment-based configuration
- Reusable across multiple applications

**Usage Example:**
```scala
// In any Spark application
import com.example.spark.SparkSessionFactory

object MySparkApp {
  def main(args: Array[String]): Unit = {
    // Get configured session with one line
    val spark = SparkSessionFactory.create(
      appName = "My Custom Application",
      catalogName = "iceberg_catalog"
    )
    
    try {
      // Your business logic here
      spark.sql("SELECT * FROM my_table").show()
    } finally {
      spark.stop()
    }
  }
}
```

**Configuration Parameters:**
- `CATALOG_TYPE`: Catalog implementation ("s3tables" or "glue")
- `S3_TABLE_BUCKET_ARN`: ARN for S3 Tables catalog
- `S3_WAREHOUSE`: S3 path for Glue catalog
- `AWS_ACCESS_KEY_ID`: AWS access key (optional)
- `AWS_SECRET_ACCESS_KEY`: AWS secret key (optional)
- `AWS_SESSION_TOKEN`: Session token for temporary credentials (optional)

## Key Features Demonstrated

1. **Reusable SparkSessionFactory**: Modular session creation for multiple applications
2. **Dual Catalog Support**: Configurable support for AWS S3 Tables or AWS Glue Data Catalog
3. **S3 Integration**: Stores Iceberg table data in S3
4. **Table Operations**: Create, insert, and query Iceberg tables
5. **Time Travel**: Access table history and snapshots
6. **Partitioning**: Demonstrates partitioning by date

## Dependencies

- Scala 2.12.18
- Apache Spark 3.5.0
- Apache Iceberg 1.10.1 (iceberg-spark-runtime-3.5, iceberg-aws)
- AWS SDK v2 (S3, Glue, DynamoDB, STS)
- Hadoop AWS 3.3.6

## Catalog Comparison

### AWS S3 Tables
- **Pros**: Managed service, built-in governance, automatic scaling, integrated access control
- **Cons**: Newer service (may have less tooling support)
- **Use Case**: New projects, enterprise environments requiring strong governance

### AWS Glue Data Catalog
- **Pros**: Mature service, broad ecosystem support, integrates with Athena/EMR/Redshift
- **Cons**: Requires separate S3 bucket management, additional configuration
- **Use Case**: Existing AWS analytics workloads, multi-engine access (Athena, Presto, etc.)

## Notes

- Default catalog type is AWS S3 Tables (set via CATALOG_TYPE environment variable)
- S3A file system is used for S3 access
- **Environment Configuration**: Use `.env` file for local development (see `.env.example`)
- **Credential Priority**: 
  1. Explicit credentials via `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables
  2. Explicit credentials in `spark-defaults.conf` 
  3. Default AWS credentials provider chain (AWS CLI, instance profiles, etc.)
- Table data is stored in Parquet format with Zstandard compression
- Session tokens are supported for temporary AWS credentials (IAM roles, STS)
- **Security**: Never commit `.env` file to version control (it's in `.gitignore`)

## Troubleshooting

### AWS Credentials Issues
- Verify credentials are set correctly (environment variables or AWS CLI)
- Check that access key and secret key are valid and not expired
- For temporary credentials, ensure session token is provided
- Test credentials: `aws sts get-caller-identity`

### S3 Access Issues
- Verify AWS credentials are configured correctly
- Check S3 bucket or S3 Table bucket permissions
- Ensure the IAM role/user has necessary permissions for S3
- Verify S3 endpoint configuration if using non-standard regions

### S3 Tables Catalog Issues
- Verify S3 Tables permissions in IAM (s3tables:* actions)
- Check if the table bucket ARN is correct
- Ensure proper region configuration
- Verify the table bucket exists and is accessible

### Glue Catalog Issues
- Verify Glue permissions in IAM (glue:CreateDatabase, glue:CreateTable, etc.)
- Check if the Glue database exists
- Ensure proper region configuration
- Verify S3 warehouse path permissions

## License

This project is provided as an example and can be modified for your needs.
