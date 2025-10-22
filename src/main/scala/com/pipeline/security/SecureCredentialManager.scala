package com.pipeline.security

import com.pipeline.credentials.{CredentialConfig, CredentialConfigFactory, VaultClient}
import com.pipeline.exceptions.CredentialException
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success}

/**
 * Secure credential manager with policy enforcement and audit logging.
 *
 * Implements Sprint 3-4 Task 2.3: Security Enhancements.
 * Enforces Vault-only mode and logs all credential access.
 */
class SecureCredentialManager(
    vaultClient: VaultClient,
    securityPolicy: SecurityPolicy,
) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  // Validate policy on creation
  securityPolicy.validate()

  /**
   * Retrieves credentials from Vault with policy enforcement and auditing.
   *
   * @param path           Vault secret path
   * @param credentialType Type of credential
   * @param pipelineName   Optional pipeline name for audit context
   * @return CredentialConfig instance
   * @throws CredentialException if retrieval fails or policy is violated
   */
  def getCredentials(
      path: String,
      credentialType: String,
      pipelineName: Option[String] = None,
  ): CredentialConfig = {
    logger.info(s"Retrieving credentials: path=$path, type=$credentialType")

    try {
      // Retrieve from Vault
      val config = CredentialConfigFactory.fromVault(vaultClient, path, credentialType)

      // Log successful access
      if (securityPolicy.shouldAudit) {
        CredentialAudit.logVaultRead(path, credentialType, pipelineName)
      }

      config
    } catch {
      case ex: Exception =>
        // Log failure
        if (securityPolicy.shouldAudit) {
          CredentialAudit.logVaultReadFailure(path, credentialType, ex.getMessage, pipelineName)
        }

        throw new CredentialException(
          s"Failed to retrieve credentials from Vault: path=$path",
          cause = ex,
          credentialPath = Some(path),
        )
    }
  }

  /**
   * Creates credentials from plain text data.
   *
   * Only allowed if security policy permits it.
   *
   * @param credentialType Type of credential
   * @param data           Plain text credential data
   * @param pipelineName   Optional pipeline name for audit context
   * @return CredentialConfig instance
   * @throws CredentialException if plain text is not allowed
   */
  def fromPlainText(
      credentialType: String,
      data: Map[String, Any],
      pipelineName: Option[String] = None,
  ): CredentialConfig = {
    logger.info(s"Creating credentials from plain text: type=$credentialType")

    // Check policy
    if (!securityPolicy.canUsePlainTextCredentials) {
      val violation = "Plain text credentials are not allowed (Vault-only mode enabled)"
      logger.error(violation)

      // Log policy violation
      if (securityPolicy.shouldAudit) {
        CredentialAudit.logPolicyViolation(
          violation,
          "inline",
          credentialType,
          pipelineName,
        )
      }

      throw new CredentialException(
        violation,
        credentialPath = Some("inline"),
      )
    }

    try {
      // Create config from data
      val config = CredentialConfigFactory.create(credentialType, data)

      // Log plain text access
      if (securityPolicy.shouldAudit) {
        CredentialAudit.logPlainTextAccess(credentialType, pipelineName)
      }

      config
    } catch {
      case ex: Exception =>
        throw new CredentialException(
          s"Failed to create credentials from plain text: type=$credentialType",
          cause = ex,
        )
    }
  }

  /**
   * Validates that credential access complies with security policy.
   *
   * @param credentialSource Source of credential ("vault", "plaintext", "environment")
   * @param credentialPath   Path or identifier
   * @return true if allowed
   * @throws CredentialException if not allowed
   */
  def validateAccess(credentialSource: String, credentialPath: String): Boolean =
    credentialSource.toLowerCase match {
      case "vault" =>
        // Vault access always allowed
        true

      case "plaintext" | "inline" =>
        if (!securityPolicy.canUsePlainTextCredentials) {
          val violation = s"Plain text credentials not allowed: $credentialPath"
          logger.error(violation)
          throw new CredentialException(violation, credentialPath = Some(credentialPath))
        }
        true

      case "environment" =>
        if (securityPolicy.requiresVault) {
          val violation = s"Environment variable credentials not allowed in Vault-only mode: $credentialPath"
          logger.error(violation)
          throw new CredentialException(violation, credentialPath = Some(credentialPath))
        }
        true

      case unknown =>
        throw new CredentialException(
          s"Unknown credential source: $unknown",
          credentialPath = Some(credentialPath),
        )
    }

  /**
   * Gets the current security policy.
   *
   * @return SecurityPolicy
   */
  def getPolicy: SecurityPolicy = securityPolicy
}

/**
 * Factory for creating SecureCredentialManager instances.
 */
object SecureCredentialManager {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates manager with default security policy.
   *
   * @param vaultClient VaultClient instance
   * @return SecureCredentialManager
   */
  def apply(vaultClient: VaultClient): SecureCredentialManager = {
    logger.info("Creating SecureCredentialManager with default policy")
    new SecureCredentialManager(vaultClient, SecurityPolicy.default())
  }

  /**
   * Creates manager with strict security policy (Vault-only).
   *
   * @param vaultClient VaultClient instance
   * @return SecureCredentialManager
   */
  def strict(vaultClient: VaultClient): SecureCredentialManager = {
    logger.info("Creating SecureCredentialManager with strict policy")
    new SecureCredentialManager(vaultClient, SecurityPolicy.strict())
  }

  /**
   * Creates manager with permissive security policy.
   *
   * @param vaultClient VaultClient instance
   * @return SecureCredentialManager
   */
  def permissive(vaultClient: VaultClient): SecureCredentialManager = {
    logger.info("Creating SecureCredentialManager with permissive policy")
    new SecureCredentialManager(vaultClient, SecurityPolicy.permissive())
  }

  /**
   * Creates manager from environment variables.
   *
   * @param vaultClient VaultClient instance
   * @return SecureCredentialManager
   */
  def fromEnv(vaultClient: VaultClient): SecureCredentialManager = {
    logger.info("Creating SecureCredentialManager from environment")
    new SecureCredentialManager(vaultClient, SecurityPolicy.fromEnv())
  }
}
