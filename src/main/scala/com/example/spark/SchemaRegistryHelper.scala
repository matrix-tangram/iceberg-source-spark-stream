package com.example.spark

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types._
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.{ObjectNode, ArrayNode}
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.schemaregistry.json.JsonSchema
import scala.collection.JavaConverters._

/**
 * Helper class for Confluent Schema Registry integration.
 * Provides utilities for schema inference, registration, and management.
 */
object SchemaRegistryHelper {
  private val logger = LoggerFactory.getLogger(getClass)
  private val objectMapper = new ObjectMapper()

  /**
   * Creates a Schema Registry client.
   *
   * @param schemaRegistryUrl URL of the Schema Registry
   * @param basicAuthUserInfo Optional basic auth credentials in format "username:password"
   * @return SchemaRegistryClient instance
   */
  def createSchemaRegistryClient(
      schemaRegistryUrl: String,
      basicAuthUserInfo: Option[String]
  ): SchemaRegistryClient = {
    val configs = new java.util.HashMap[String, String]()
    
    basicAuthUserInfo.foreach { userInfo =>
      configs.put("basic.auth.credentials.source", "USER_INFO")
      configs.put("basic.auth.user.info", userInfo)
    }
    
    new CachedSchemaRegistryClient(schemaRegistryUrl, 100, configs)
  }

  /**
   * Infers a JSON Schema from a Spark DataFrame schema.
   *
   * @param df DataFrame to infer schema from
   * @return JSON Schema as a string
   */
  def inferJsonSchemaFromDataFrame(df: DataFrame): String = {
    logger.info("Inferring JSON Schema from DataFrame")
    
    val schema = df.schema
    val jsonSchema = createJsonSchemaObject(schema)
    
    val schemaString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema)
    logger.info(s"Inferred JSON Schema:\n$schemaString")
    
    schemaString
  }

  /**
   * Creates a JSON Schema object from a Spark StructType.
   *
   * @param schema Spark StructType
   * @return JsonNode representing the JSON Schema
   */
  private def createJsonSchemaObject(schema: StructType): ObjectNode = {
    val root = objectMapper.createObjectNode()
    root.put("$schema", "http://json-schema.org/draft-07/schema#")
    root.put("type", "object")
    root.put("additionalProperties", false)
    
    val properties = objectMapper.createObjectNode()
    val required = objectMapper.createArrayNode()
    
    schema.fields.foreach { field =>
      val fieldSchema = sparkTypeToJsonSchema(field.dataType)
      properties.set(field.name, fieldSchema)
      
      // Add to required if not nullable
      if (!field.nullable) {
        required.add(field.name)
      }
    }
    
    root.set("properties", properties)
    
    if (required.size() > 0) {
      root.set("required", required)
    }
    
    root
  }

  /**
   * Converts a Spark DataType to a JSON Schema type definition.
   *
   * @param dataType Spark DataType
   * @return JsonNode representing the JSON Schema type
   */
  private def sparkTypeToJsonSchema(dataType: DataType): JsonNode = {
    dataType match {
      case StringType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "string")
        node
        
      case IntegerType | ShortType | ByteType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "integer")
        node
        
      case LongType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "integer")
        node
        
      case FloatType | DoubleType | _: DecimalType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "number")
        node
        
      case BooleanType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "boolean")
        node
        
      case DateType | TimestampType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "string")
        node.put("format", "date-time")
        node
        
      case ArrayType(elementType, containsNull) =>
        val node = objectMapper.createObjectNode()
        node.put("type", "array")
        node.set("items", sparkTypeToJsonSchema(elementType))
        node
        
      case struct: StructType =>
        val node = objectMapper.createObjectNode()
        node.put("type", "object")
        val properties = objectMapper.createObjectNode()
        struct.fields.foreach { field =>
          properties.set(field.name, sparkTypeToJsonSchema(field.dataType))
        }
        node.set("properties", properties)
        node
        
      case _ =>
        // Default to string for unknown types
        val node = objectMapper.createObjectNode()
        node.put("type", "string")
        node
    }
  }

  /**
   * Registers a JSON Schema with Schema Registry.
   *
   * @param client SchemaRegistryClient
   * @param subject Subject name (typically topic-name-value)
   * @param schemaString JSON Schema as string
   * @return Schema ID
   */
  def registerSchema(
      client: SchemaRegistryClient,
      subject: String,
      schemaString: String
  ): Int = {
    logger.info(s"Registering schema for subject: $subject")
    
    val jsonSchema = new JsonSchema(schemaString)
    val schemaId = client.register(subject, jsonSchema)
    
    logger.info(s"Schema registered with ID: $schemaId for subject: $subject")
    schemaId
  }
}

