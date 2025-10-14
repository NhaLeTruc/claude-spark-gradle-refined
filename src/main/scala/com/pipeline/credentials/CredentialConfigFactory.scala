package com.pipeline.credentials

import org.slf4j.{Logger, LoggerFactory}

/**
 * Factory for creating CredentialConfig instances based on type.
 *
 * Implements FR-011: Factory pattern for credential type resolution.
 * Validates Constitution Section I: SOLID principles (Factory Pattern).
 */
object CredentialConfigFactory {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Supported credential types.
   */
  val supportedTypes: Set[String] = Set("postgres", "mysql", "s3", "kafka", "deltalake")

  /**
   * Creates a CredentialConfig based on the credential type.
   *
   * @param credentialType Type of credential (case-insensitive)
   * @param data           Secret data from Vault
   * @return CredentialConfig instance
   * @throws IllegalArgumentException if credential type is unsupported
   */
  def create(credentialType: String, data: Map[String, Any]): CredentialConfig = {
    val normalizedType = credentialType.toLowerCase

    logger.info(s"Creating credential config for type: $normalizedType")

    normalizedType match {
      case "postgres" | "postgresql" =>
        JdbcConfig.fromVaultData(data, "postgres")

      case "mysql" =>
        JdbcConfig.fromVaultData(data, "mysql")

      case "s3" | "aws" =>
        IAMConfig.fromVaultData(data)

      case "kafka" =>
        OtherConfig.fromVaultData(data)

      case "deltalake" | "delta" =>
        OtherConfig.fromVaultData(data)

      case unsupported =>
        val message = s"Unsupported credential type: $unsupported. " +
          s"Supported types: ${supportedTypes.mkString(", ")}"
        logger.error(message)
        throw new IllegalArgumentException(message)
    }
  }

  /**
   * Creates a CredentialConfig from Vault using VaultClient.
   *
   * @param vaultClient    VaultClient instance
   * @param path           Vault secret path
   * @param credentialType Type of credential
   * @return CredentialConfig instance or throws exception
   */
  def fromVault(
      vaultClient: VaultClient,
      path: String,
      credentialType: String,
  ): CredentialConfig = {
    vaultClient.readSecret(path) match {
      case scala.util.Success(data) =>
        create(credentialType, data)

      case scala.util.Failure(exception) =>
        val message = s"Failed to retrieve credentials from Vault at path $path: ${exception.getMessage}"
        logger.error(message, exception)
        throw new RuntimeException(message, exception)
    }
  }
}
