package com.orchestrator.mcp.kb.config

import org.slf4j.LoggerFactory

/**
 * Validates KbConfig at startup. Fails fast on critical errors,
 * logs warnings for insecure configurations.
 */
object KbConfigValidator {

    private val logger = LoggerFactory.getLogger(KbConfigValidator::class.java)

    private val KNOWN_DEFAULT_KEYS = setOf(
        "sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w="
    )

    /** Validate config: throw on critical errors, warn on insecure settings. */
    fun validate(config: KbConfig): KbConfig {
        validateCritical(config)
        checkSecurityWarnings(config)
        return config
    }

    private fun validateCritical(config: KbConfig) {
        val db = config.kb.database
        check(db.url.isNotBlank()) {
            "Required config 'kb.database.url' is empty. " +
                "Cannot start without database connection."
        }
    }

    private fun checkSecurityWarnings(config: KbConfig) {
        checkDatabaseCredentials(config.kb.database)
        checkEncryptionKeys(config.kb.security)
    }

    private fun checkDatabaseCredentials(db: KbDatabaseConfig) {
        if (db.username == "postgres" && db.password == "postgres") {
            logger.warn(
                "Using default database credentials (postgres/postgres). " +
                    "This is insecure for production."
            )
        }
    }

    private fun checkEncryptionKeys(security: KbSecurityConfig) {
        if (security.encryptionKey.isBlank() ||
            security.encryptionKey in KNOWN_DEFAULT_KEYS
        ) {
            logger.warn(
                "Encryption key is empty or using default value. " +
                    "PII data will not be properly encrypted."
            )
        }
        if (security.brEncryptionKey.isBlank()) {
            logger.warn(
                "BR encryption key is empty. " +
                    "Business rule masking will not function correctly."
            )
        }
    }
}
