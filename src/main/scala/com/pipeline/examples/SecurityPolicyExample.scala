package com.pipeline.examples

import com.pipeline.credentials.VaultClient
import com.pipeline.security.{CredentialAudit, SecureCredentialManager, SecurityPolicy}
import org.slf4j.{Logger, LoggerFactory}

/**
 * Example demonstrating security policy enforcement and credential auditing.
 *
 * Shows how to:
 * 1. Configure different security policies
 * 2. Enforce Vault-only credential access
 * 3. Audit credential access
 * 4. Handle security policy violations
 */
object SecurityPolicyExample {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    logger.info("=" * 80)
    logger.info("SECURITY POLICY DEMONSTRATION")
    logger.info("=" * 80)

    // Demonstrate different security policies
    demonstratePermissivePolicy()
    demonstrateDefaultPolicy()
    demonstrateStrictPolicy()
    demonstrateAuditLogging()
    demonstrateFromEnvironment()
  }

  /**
   * Demonstrates permissive security policy (allows plain text).
   */
  def demonstratePermissivePolicy(): Unit = {
    logger.info("\n1. PERMISSIVE POLICY (Development Mode)")
    logger.info("-" * 80)

    val vaultClient = VaultClient.fromConfig(
      address = Some("http://localhost:8200"),
      token = Some("dev-token"),
    )

    val manager = SecureCredentialManager.permissive(vaultClient)
    val policy = manager.getPolicy

    logger.info(s"Vault-only mode: ${policy.vaultOnlyMode}")
    logger.info(s"Allow plain text: ${policy.allowPlainTextCredentials}")
    logger.info(s"Audit logging: ${policy.enableAuditLogging}")

    // Plain text credentials are allowed
    try {
      val credentials = manager.fromPlainText(
        credentialType = "postgres",
        data = Map(
          "host" -> "localhost",
          "port" -> "5432",
          "database" -> "testdb",
          "username" -> "user",
          "password" -> "password",
        ),
        pipelineName = Some("dev-pipeline"),
      )
      logger.info("✓ Plain text credentials allowed in permissive mode")
    } catch {
      case ex: Exception =>
        logger.error("✗ Plain text credentials rejected", ex)
    }
  }

  /**
   * Demonstrates default security policy (balanced).
   */
  def demonstrateDefaultPolicy(): Unit = {
    logger.info("\n2. DEFAULT POLICY (Balanced Mode)")
    logger.info("-" * 80)

    val policy = SecurityPolicy.default()

    logger.info(s"Vault-only mode: ${policy.vaultOnlyMode}")
    logger.info(s"Allow plain text: ${policy.allowPlainTextCredentials}")
    logger.info(s"Audit logging: ${policy.enableAuditLogging}")
    logger.info(s"Requires Vault: ${policy.requiresVault}")
    logger.info(s"Should audit: ${policy.shouldAudit}")

    // Policy validation
    try {
      policy.validate()
      logger.info("✓ Policy validation passed")
    } catch {
      case ex: Exception =>
        logger.error("✗ Policy validation failed", ex)
    }
  }

  /**
   * Demonstrates strict security policy (Vault-only).
   */
  def demonstrateStrictPolicy(): Unit = {
    logger.info("\n3. STRICT POLICY (Production Mode - Vault Only)")
    logger.info("-" * 80)

    val vaultClient = VaultClient.fromConfig(
      address = Some("http://localhost:8200"),
      token = Some("prod-token"),
    )

    val manager = SecureCredentialManager.strict(vaultClient)
    val policy = manager.getPolicy

    logger.info(s"Vault-only mode: ${policy.vaultOnlyMode}")
    logger.info(s"Allow plain text: ${policy.allowPlainTextCredentials}")
    logger.info(s"Audit logging: ${policy.enableAuditLogging}")
    logger.info(s"Require encryption: ${policy.requireCredentialEncryption}")

    // Try to use plain text credentials (should fail)
    try {
      val credentials = manager.fromPlainText(
        credentialType = "postgres",
        data = Map(
          "host" -> "localhost",
          "port" -> "5432",
        ),
        pipelineName = Some("prod-pipeline"),
      )
      logger.error("✗ Plain text credentials should have been rejected!")
    } catch {
      case ex: com.pipeline.exceptions.CredentialException =>
        logger.info("✓ Plain text credentials correctly rejected in strict mode")
        logger.info(s"  Error: ${ex.getMessage}")
      case ex: Exception =>
        logger.error("✗ Unexpected error", ex)
    }

    // Validate access patterns
    try {
      manager.validateAccess("vault", "secret/data/postgres")
      logger.info("✓ Vault access allowed")
    } catch {
      case ex: Exception =>
        logger.error("✗ Vault access rejected", ex)
    }

    try {
      manager.validateAccess("plaintext", "inline")
      logger.error("✗ Plain text access should have been rejected!")
    } catch {
      case ex: com.pipeline.exceptions.CredentialException =>
        logger.info("✓ Plain text access correctly rejected")
    }

    try {
      manager.validateAccess("environment", "DATABASE_PASSWORD")
      logger.error("✗ Environment variable access should have been rejected!")
    } catch {
      case ex: com.pipeline.exceptions.CredentialException =>
        logger.info("✓ Environment variable access correctly rejected in Vault-only mode")
    }
  }

  /**
   * Demonstrates audit logging functionality.
   */
  def demonstrateAuditLogging(): Unit = {
    logger.info("\n4. AUDIT LOGGING")
    logger.info("-" * 80)

    // Clear previous audit log
    CredentialAudit.clearAuditLog()

    // Log various credential access events
    CredentialAudit.logVaultRead(
      path = "secret/data/postgres",
      credentialType = "postgres",
      pipelineName = Some("example-pipeline"),
    )

    CredentialAudit.logVaultReadFailure(
      path = "secret/data/missing",
      credentialType = "mysql",
      error = "Secret not found",
      pipelineName = Some("example-pipeline"),
    )

    CredentialAudit.logPlainTextAccess(
      credentialType = "s3",
      pipelineName = Some("dev-pipeline"),
    )

    CredentialAudit.logEnvironmentAccess(
      envVar = "DATABASE_PASSWORD",
      credentialType = "postgres",
      pipelineName = Some("test-pipeline"),
    )

    CredentialAudit.logPolicyViolation(
      violation = "Plain text credentials not allowed in Vault-only mode",
      credentialPath = "inline",
      credentialType = "postgres",
      pipelineName = Some("prod-pipeline"),
    )

    // Display audit log
    val auditLog = CredentialAudit.getAuditLog
    logger.info(s"Audit log entries: ${auditLog.size}")

    auditLog.foreach { entry =>
      logger.info(
        s"  [${entry.accessType}] ${entry.credentialType} from ${entry.source} - " +
          s"${if (entry.success) "SUCCESS" else "FAILED"}",
      )
    }

    // Export audit log
    try {
      val jsonPath = "/tmp/credential-audit.json"
      CredentialAudit.exportToJson(jsonPath)
      logger.info(s"✓ Audit log exported to JSON: $jsonPath")
    } catch {
      case ex: Exception =>
        logger.warn(s"Could not export audit log: ${ex.getMessage}")
    }

    try {
      val jsonlPath = "/tmp/credential-audit.jsonl"
      CredentialAudit.exportToJsonLines(jsonlPath, append = false)
      logger.info(s"✓ Audit log exported to JSONL: $jsonlPath")
    } catch {
      case ex: Exception =>
        logger.warn(s"Could not export audit log: ${ex.getMessage}")
    }
  }

  /**
   * Demonstrates security policy from environment variables.
   */
  def demonstrateFromEnvironment(): Unit = {
    logger.info("\n5. POLICY FROM ENVIRONMENT")
    logger.info("-" * 80)

    // Show environment variable configuration
    logger.info("Environment variables:")
    logger.info(s"  PIPELINE_VAULT_ONLY: ${sys.env.getOrElse("PIPELINE_VAULT_ONLY", "(not set)")}")
    logger.info(
      s"  PIPELINE_AUDIT_CREDENTIALS: ${sys.env.getOrElse("PIPELINE_AUDIT_CREDENTIALS", "(not set)")}",
    )
    logger.info(
      s"  PIPELINE_ALLOW_PLAINTEXT: ${sys.env.getOrElse("PIPELINE_ALLOW_PLAINTEXT", "(not set)")}",
    )

    try {
      val policy = SecurityPolicy.fromEnv()
      logger.info("\n✓ Policy created from environment:")
      logger.info(s"  Vault-only mode: ${policy.vaultOnlyMode}")
      logger.info(s"  Allow plain text: ${policy.allowPlainTextCredentials}")
      logger.info(s"  Audit logging: ${policy.enableAuditLogging}")
    } catch {
      case ex: Exception =>
        logger.error("✗ Failed to create policy from environment", ex)
    }

    logger.info("\nExample environment variable setup:")
    logger.info("  # Enable strict mode for production:")
    logger.info("  export PIPELINE_VAULT_ONLY=true")
    logger.info("  export PIPELINE_AUDIT_CREDENTIALS=true")
    logger.info("  export PIPELINE_ALLOW_PLAINTEXT=false")
    logger.info("")
    logger.info("  # Permissive mode for development:")
    logger.info("  export PIPELINE_VAULT_ONLY=false")
    logger.info("  export PIPELINE_AUDIT_CREDENTIALS=false")
    logger.info("  export PIPELINE_ALLOW_PLAINTEXT=true")
  }

  /**
   * Demonstrates integration with pipeline execution.
   */
  def demonstrateWithPipeline(): Unit = {
    logger.info("\n6. PIPELINE INTEGRATION")
    logger.info("-" * 80)

    val vaultClient = VaultClient.fromConfig()
    val manager = SecureCredentialManager.strict(vaultClient)

    logger.info("In pipeline configuration, reference Vault paths:")
    logger.info("""
      |{
      |  "name": "secure-pipeline",
      |  "mode": "batch",
      |  "steps": [
      |    {
      |      "type": "extract",
      |      "method": "fromPostgres",
      |      "config": {
      |        "credentialPath": "secret/data/postgres"
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin)

    logger.info("\nAll credential access is:")
    logger.info("  ✓ Retrieved from Vault only")
    logger.info("  ✓ Audited automatically")
    logger.info("  ✓ Validated against security policy")
    logger.info("  ✓ Logged with pipeline context")
  }
}
