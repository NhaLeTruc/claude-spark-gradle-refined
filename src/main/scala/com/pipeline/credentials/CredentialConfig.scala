package com.pipeline.credentials

/**
 * Sealed trait for credential configurations.
 *
 * Implements FR-011: Secure credential retrieval from HashiCorp Vault.
 * Validates Constitution Section I: SOLID principles (Interface Segregation).
 */
sealed trait CredentialConfig

/**
 * JDBC credential configuration for PostgreSQL and MySQL.
 *
 * @param host            Database host
 * @param port            Database port
 * @param database        Database name
 * @param username        Database username
 * @param password        Database password
 * @param credentialType  Type: "postgres" or "mysql"
 * @param properties      Additional connection properties
 */
case class JdbcConfig(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String,
    credentialType: String,
    properties: Map[String, String] = Map.empty,
) extends CredentialConfig {

  require(host.nonEmpty, "Host cannot be empty")
  require(database.nonEmpty, "Database cannot be empty")
  require(username.nonEmpty, "Username cannot be empty")
  require(password.nonEmpty, "Password cannot be empty")

  /**
   * Constructs JDBC URL based on credential type.
   */
  def jdbcUrl: String = credentialType.toLowerCase match {
    case "postgres" | "postgresql" =>
      s"jdbc:postgresql://$host:$port/$database"
    case "mysql"                   =>
      s"jdbc:mysql://$host:$port/$database"
    case other                     =>
      throw new IllegalArgumentException(s"Unsupported JDBC type: $other")
  }
}

object JdbcConfig {
  val RequiredKeys: Set[String] = Set("host", "port", "database", "username", "password")

  /**
   * Creates JdbcConfig from Vault secret data.
   *
   * @param data           Secret data from Vault
   * @param credentialType Type of JDBC connection
   * @return JdbcConfig instance
   */
  def fromVaultData(data: Map[String, Any], credentialType: String): JdbcConfig = {
    // Validate required keys
    RequiredKeys.foreach { key =>
      if (!data.contains(key)) {
        throw new IllegalArgumentException(s"Missing required field: $key")
      }
    }

    val port = data("port") match {
      case i: Int    => i
      case s: String => s.toInt
      case other     => throw new IllegalArgumentException(s"Invalid port type: $other")
    }

    JdbcConfig(
      host = data("host").toString,
      port = port,
      database = data("database").toString,
      username = data("username").toString,
      password = data("password").toString,
      credentialType = credentialType,
    )
  }
}

/**
 * AWS IAM credential configuration for S3 access.
 *
 * @param accessKeyId     AWS access key ID
 * @param secretAccessKey AWS secret access key
 * @param sessionToken    Optional session token for temporary credentials
 * @param region          AWS region
 */
case class IAMConfig(
    accessKeyId: String,
    secretAccessKey: String,
    sessionToken: Option[String],
    region: String,
) extends CredentialConfig {

  require(accessKeyId.nonEmpty, "Access key ID cannot be empty")
  require(secretAccessKey.nonEmpty, "Secret access key cannot be empty")
}

object IAMConfig {
  val RequiredKeys: Set[String] = Set("accessKeyId", "secretAccessKey")

  /**
   * Creates IAMConfig from Vault secret data.
   *
   * @param data Secret data from Vault
   * @return IAMConfig instance
   */
  def fromVaultData(data: Map[String, Any]): IAMConfig = {
    // Validate required keys
    RequiredKeys.foreach { key =>
      if (!data.contains(key)) {
        throw new IllegalArgumentException(s"Missing required field: $key")
      }
    }

    IAMConfig(
      accessKeyId = data("accessKeyId").toString,
      secretAccessKey = data("secretAccessKey").toString,
      sessionToken = data.get("sessionToken").map(_.toString),
      region = data.getOrElse("region", "us-east-1").toString,
    )
  }
}

/**
 * Flexible credential configuration for Kafka, DeltaLake, and other systems.
 *
 * @param properties Key-value pairs for configuration
 */
case class OtherConfig(
    properties: Map[String, String],
) extends CredentialConfig {

  /**
   * Retrieves a property by key.
   *
   * @param key Property key
   * @return Option containing the value if present
   */
  def get(key: String): Option[String] = properties.get(key)
}

object OtherConfig {

  /**
   * Creates OtherConfig from Vault secret data.
   *
   * @param data Secret data from Vault
   * @return OtherConfig instance
   */
  def fromVaultData(data: Map[String, Any]): OtherConfig = {
    val stringProperties = data.map { case (key, value) =>
      key -> value.toString
    }
    OtherConfig(stringProperties)
  }
}
