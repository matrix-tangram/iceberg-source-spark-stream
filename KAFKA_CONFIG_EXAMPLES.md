# Kafka Login Mechanisms (Environment Variables)

## PLAINTEXT (no auth)
- KAFKA_SECURITY_PROTOCOL=PLAINTEXT
- KAFKA_BOOTSTRAP_SERVERS=host:port

## SSL + User (Confluent – SASL/SSL PLAIN)
- KAFKA_SECURITY_PROTOCOL=SASL_SSL
- KAFKA_SASL_MECHANISM=PLAIN
- KAFKA_SASL_JAAS_CONFIG=org.apache.kafka.common.security.plain.PlainLoginModule required username="YOUR_KEY" password="YOUR_SECRET";
- KAFKA_SSL_TRUSTSTORE_LOCATION=/path/to/truststore.jks
- KAFKA_SSL_TRUSTSTORE_PASSWORD=changeit

## KERBEROS (SASL/GSSAPI)
- KAFKA_SECURITY_PROTOCOL=SASL_PLAINTEXT (or SASL_SSL)
- KAFKA_SASL_MECHANISM=GSSAPI
- KAFKA_SASL_KERBEROS_SERVICE_NAME=kafka
- KAFKA_SASL_JAAS_CONFIG=com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true keyTab="/path/to/user.keytab" principal="user@REALM";
- KRB5_CONFIG=/etc/krb5.conf
