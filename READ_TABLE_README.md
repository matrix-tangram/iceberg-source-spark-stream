# Read Table Application

This directory contains a specialized Spark application for reading and displaying Iceberg tables from AWS S3 Tables.

## Quick Start

### 0. Install Spark (one-time setup)

```bash
./install-spark.sh
```

This installs Apache Spark 3.5.0 locally in the project directory.

### 1. Configure Environment Variables

The application is pre-configured with the required settings. You can optionally edit `.env.read-table`:

```bash
nano .env.read-table
```

### 2. Run the Application

Simply execute the shell script:

```bash
./run-read-table.sh
```

## Configuration

The application is configured via environment variables (already set in `.env.read-table`):

### AWS Credentials
- `AWS_ACCESS_KEY_ID=your-access-key-id`
- `AWS_SECRET_ACCESS_KEY=your-secret-access-key`

### S3 Tables Configuration
- `S3_TABLE_BUCKET_ARN=arn:aws:s3tables:eu-central-1:667654397356:bucket/iceberg-warehouse1`

### Table Configuration
- `TABLE_NAMESPACE=raw_data` (database/namespace name)
- `TABLE_NAME=test_table` (table to read)

### Spark Configuration
- `SPARK_MASTER=local[1]` (run locally with 1 thread)

## Customization

### Change Table to Read

```bash
export TABLE_NAMESPACE=my_database
export TABLE_NAME=my_table
./run-read-table.sh
```

### Change Spark Master

```bash
# Run with more parallelism
export SPARK_MASTER=local[4]
./run-read-table.sh

# Run on YARN cluster
export SPARK_MASTER=yarn
./run-read-table.sh
```

### Add Spark Submit Options

```bash
# Set via environment variable
export SPARK_SUBMIT_OPTIONS="--driver-memory 2g --executor-memory 4g --verbose"
./run-read-table.sh

# Or pass directly to script
./run-read-table.sh --driver-memory 2g --executor-memory 4g
```

### Use Different S3 Table Bucket

```bash
export S3_TABLE_BUCKET_ARN=arn:aws:s3tables:us-east-1:123456789012:bucket/my-bucket
./run-read-table.sh
```

## Running with Maven (Alternative)

If you prefer to use Maven directly:

```bash
# Source environment variables
source .env.read-table

# Run with Maven
mvn exec:java -Dexec.mainClass="com.example.spark.ReadTableApp"
```

## Script Features

The `run-read-table.sh` script provides:

- ✅ Pre-configured environment variables
- ✅ Automatic JAR building if needed
- ✅ Environment validation
- ✅ Colored console output
- ✅ Clear error messages
- ✅ Configurable via environment variables
- ✅ Support for additional spark-submit options

## Troubleshooting

### Build the JAR manually

```bash
mvn clean package
```

### Check AWS credentials

```bash
aws sts get-caller-identity
```

### Enable verbose Spark logging

```bash
./run-read-table.sh --verbose
```

### Check table exists

```bash
# You can modify the app to list tables first
export TABLE_NAME=information_schema.tables
./run-read-table.sh
```

## File Structure

```
.
├── run-read-table.sh                  # Shell script to run the app
├── .env.read-table                    # Environment configuration
├── src/main/scala/com/example/spark/
│   └── ReadTableApp.scala            # Application code
└── target/
    └── spark-iceberg-aws-1.0-SNAPSHOT.jar  # Built JAR
```

## Output Example

```
[INFO] Starting Spark application to read Iceberg table
================================================================================
Spark Master: local[1]
S3 Table Bucket ARN: arn:aws:s3tables:eu-central-1:667654397356:bucket/iceberg-warehouse1
Namespace: raw_data
Table Name: test_table
================================================================================
[INFO] Reading table: iceberg_catalog.raw_data.test_table
[INFO] Table schema:
root
 |-- id: long (nullable = true)
 |-- name: string (nullable = true)
 |-- timestamp: timestamp (nullable = true)

[INFO] Total rows: 3
[INFO] Displaying table contents:
+---+-------+-------------------+
|id |name   |timestamp          |
+---+-------+-------------------+
|1  |Alice  |2024-01-01 10:00:00|
|2  |Bob    |2024-01-02 11:00:00|
|3  |Charlie|2024-01-03 12:00:00|
+---+-------+-------------------+
```
