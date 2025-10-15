package com.pipeline.security

import org.slf4j.{Logger, LoggerFactory}

/**
 * Security policy configuration for pipeline execution.
 *
 * Implements Sprint 3-4 Task 2.3: Security Enhancements.
 * Enforces Vault-only credential access and audit logging.
 */
case class SecurityPolicy(
    vaultOnlyMode: Boolean = false,
    enableAuditLogging: Boolean = true,
    allowPlainTextCredentials: Boolean = true,
    requireCredentialEncryption: Boolean = false,
    maxCredentialAge: Option[Long] = None, // milliseconds
) {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Validates the security policy configuration.
   *
   * @throws IllegalArgumentException if configuration is invalid
   */
  def validate(): Unit = {
    if (vaultOnlyMode && allowPlainTextCredentials) {
      throw new IllegalArgumentException(
        "vaultOnlyMode=true is incompatible with allowPlainTextCredentials=true",
      )
    }

    if (requireCredentialEncryption && allowPlainTextCredentials) {
      logger.warn(
        "requireCredentialEncryption=true but allowPlainTextCredentials=true. " +
          "Plain text credentials will be rejected.",
      )
    }

    logger.info(s"Security policy validated: vaultOnly=$vaultOnlyMode, audit=$enableAuditLogging")
  }

  /**
   * Checks if plain text credentials are allowed.
   *
   * @return true if plain text credentials can be used
   */
  def canUsePlainTextCredentials: Boolean = {
    !vaultOnlyMode && allowPlainTextCredentials
  }

  /**
   * Checks if Vault is required for credentials.
   *
   * @return true if Vault must be used
   */
  def requiresVault: Boolean = vaultOnlyMode

  /**
   * Checks if audit logging is enabled.
   *
   * @return true if credential access should be audited
   */
  def shouldAudit: Boolean = enableAuditLogging
}

/**
 * Factory for creating SecurityPolicy instances.
 */
object SecurityPolicy {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates a strict security policy (Vault-only, audit enabled).
   *
   * @return Strict SecurityPolicy
   */
  def strict(): SecurityPolicy = {
    logger.info("Creating strict security policy")
    SecurityPolicy(
      vaultOnlyMode = true,
      enableAuditLogging = true,
      allowPlainTextCredentials = false,
      requireCredentialEncryption = true,
    )
  }

  /**
   * Creates a permissive security policy (allows plain text).
   *
   * @return Permissive SecurityPolicy
   */
  def permissive(): SecurityPolicy = {
    logger.info("Creating permissive security policy")
    SecurityPolicy(
      vaultOnlyMode = false,
      enableAuditLogging = false,
      allowPlainTextCredentials = true,
      requireCredentialEncryption = false,
    )
  }

  /**
   * Creates security policy from environment variables.
   *
   * Reads:
   * - PIPELINE_VAULT_ONLY: Enable Vault-only mode
   * - PIPELINE_AUDIT_CREDENTIALS: Enable audit logging
   * - PIPELINE_ALLOW_PLAINTEXT: Allow plain text credentials
   *
   * @return SecurityPolicy from environment
   */
  def fromEnv(): SecurityPolicy = {
    val vaultOnly = sys.env.get("PIPELINE_VAULT_ONLY").exists(_.toLowerCase == "true")
    val auditEnabled = sys.env.get("PIPELINE_AUDIT_CREDENTIALS").forall(_.toLowerCase != "false")
    val allowPlainText = sys.env.get("PIPELINE_ALLOW_PLAINTEXT").forall(_.toLowerCase != "false")

    logger.info(
      s"Creating security policy from environment: vaultOnly=$vaultOnly, " +
        s"audit=$auditEnabled, allowPlainText=$allowPlainText",
    )

    val policy = SecurityPolicy(
      vaultOnlyMode = vaultOnly,
      enableAuditLogging = auditEnabled,
      allowPlainTextCredentials = allowPlainText,
    )

    policy.validate()
    policy
  }

  /**
   * Default security policy (balanced approach).
   *
   * @return Default SecurityPolicy
   */
  def default(): SecurityPolicy = {
    logger.info("Creating default security policy")
    SecurityPolicy(
      vaultOnlyMode = false,
      enableAuditLogging = true,
      allowPlainTextCredentials = true,
      requireCredentialEncryption = false,
    )
  }
}
