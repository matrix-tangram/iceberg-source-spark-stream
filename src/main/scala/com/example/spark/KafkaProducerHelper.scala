package com.example.spark

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.{Serializer, StringSerializer}
import org.slf4j.LoggerFactory
import scala.collection.JavaConverters._
import java.util.Properties

/**
 * Helper class for creating and managing Kafka producers with configurable serializers.
 */
object KafkaProducerHelper {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Configuration for Kafka producer serializers.
   *
   * @param keySerializerClass Key serializer class name
   * @param valueSerializerClass Value serializer class name
   * @param additionalConfigs Additional serializer-specific configurations
   */
  case class SerializerConfig(
      keySerializerClass: String,
      valueSerializerClass: String,
      additionalConfigs: Map[String, String] = Map.empty
  )

  /**
   * Creates a Kafka producer with the specified configuration.
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param serializerConfig Serializer configuration
   * @param securityProtocol Security protocol
   * @param saslMechanism Optional SASL mechanism
   * @param saslJaasConfig Optional JAAS configuration
   * @return KafkaProducer instance
   */
  def createProducer[K, V](
      bootstrapServers: String,
      serializerConfig: SerializerConfig,
      securityProtocol: String,
      saslMechanism: Option[String],
      saslJaasConfig: Option[String]
  ): KafkaProducer[K, V] = {
    val props = new Properties()
    
    // Basic Kafka configuration
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializerConfig.keySerializerClass)
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializerConfig.valueSerializerClass)
    props.put("security.protocol", securityProtocol)
    
    // Add SASL configuration if provided
    saslMechanism.foreach(mechanism => props.put("sasl.mechanism", mechanism))
    saslJaasConfig.foreach(config => props.put("sasl.jaas.config", config))
    
    // Add additional serializer-specific configurations
    serializerConfig.additionalConfigs.foreach { case (key, value) =>
      props.put(key, value)
    }
    
    // Producer optimizations
    props.put(ProducerConfig.ACKS_CONFIG, "all")
    props.put(ProducerConfig.RETRIES_CONFIG, "3")
    props.put(ProducerConfig.LINGER_MS_CONFIG, "10")
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
    
    logger.info(s"Creating Kafka producer with key serializer: ${serializerConfig.keySerializerClass}")
    logger.info(s"Creating Kafka producer with value serializer: ${serializerConfig.valueSerializerClass}")
    
    new KafkaProducer[K, V](props)
  }

  /**
   * Sends a record to Kafka and waits for acknowledgment.
   *
   * @param producer KafkaProducer instance
   * @param topic Topic name
   * @param key Record key
   * @param value Record value
   */
  def sendRecord[K, V](
      producer: KafkaProducer[K, V],
      topic: String,
      key: K,
      value: V
  ): Unit = {
    val record = new ProducerRecord[K, V](topic, key, value)
    producer.send(record).get() // Synchronous send for reliability
  }

  /**
   * Parses serializer configuration from environment variables.
   *
   * @param envVars Environment variables map
   * @param schemaRegistryUrl Optional Schema Registry URL
   * @param schemaRegistryAuth Optional Schema Registry auth
   * @param autoRegisterSchemas Whether to auto-register schemas
   * @return SerializerConfig
   */
  def parseSerializerConfig(
      envVars: Map[String, String],
      schemaRegistryUrl: Option[String],
      schemaRegistryAuth: Option[String],
      autoRegisterSchemas: Boolean
  ): SerializerConfig = {
    val keySerializer = envVars.getOrElse(
      "KAFKA_KEY_SERIALIZER",
      "org.apache.kafka.common.serialization.StringSerializer"
    )

    val valueSerializer = envVars.getOrElse(
      "KAFKA_VALUE_SERIALIZER",
      "io.confluent.kafka.serializers.KafkaJsonSerializer"
    )

    // Build additional configs
    var additionalConfigs = Map.empty[String, String]

    // Add Schema Registry configuration if URL is provided
    schemaRegistryUrl.foreach { url =>
      additionalConfigs += ("schema.registry.url" -> url)
      additionalConfigs += ("auto.register.schemas" -> autoRegisterSchemas.toString)

      schemaRegistryAuth.foreach { auth =>
        additionalConfigs += ("basic.auth.credentials.source" -> "USER_INFO")
        additionalConfigs += ("basic.auth.user.info" -> auth)
      }
    }

    // Add inline JSON schema support if enabled
    val jsonSchemasEnabled = envVars.getOrElse("KAFKA_SERIALIZER_JSON_SCHEMAS_ENABLED", "false")
    if (jsonSchemasEnabled.toLowerCase == "true") {
      additionalConfigs += ("json.schemas.enable" -> "true")
    }

    // Parse additional serializer-specific configs from environment
    val configPrefix = "KAFKA_SERIALIZER_"
    envVars.foreach { case (key, value) =>
      if (key.startsWith(configPrefix) && key != "KAFKA_SERIALIZER_JSON_SCHEMAS_ENABLED") {
        val configKey = key.substring(configPrefix.length).toLowerCase.replace('_', '.')
        additionalConfigs += (configKey -> value)
      }
    }

    SerializerConfig(keySerializer, valueSerializer, additionalConfigs)
  }
}

