package com.orchestrator.mcp.auth

import org.slf4j.LoggerFactory

/**
 * Authentication configuration.
 * Reads from environment variables with sensible defaults.
 * JWT_SECRET is the only required env var for production.
 */
class AuthConfig {

    private val logger = LoggerFactory.getLogger(javaClass)

    val jwtSecret: String = resolveSecret()
    val algorithm: String = env("AUTH_JWT_ALGORITHM", "HS256")
    val sessionExpiryHours: Int = envInt("AUTH_SESSION_EXPIRY_HOURS", 4)
    val bridgeTokenExpiryDays: Int = envInt("AUTH_BRIDGE_TOKEN_EXPIRY_DAYS", 30)
    val maxBridgeTokenDays: Int = envInt("AUTH_MAX_BRIDGE_TOKEN_DAYS", 365)
    val issuer: String = env("AUTH_JWT_ISSUER", "mcp-orchestrator")

    val maxLoginAttempts: Int = envInt("AUTH_LOCKOUT_MAX_ATTEMPTS", 5)
    val lockoutMinutes: Int = envInt("AUTH_LOCKOUT_MINUTES", 15)

    val allowEmailHeader: Boolean = envBool("AUTH_ALLOW_EMAIL_HEADER", true)
    val emailHeaderName: String = env("AUTH_EMAIL_HEADER_NAME", "X-User-Email")

    private fun resolveSecret(): String {
        val secret = System.getenv("JWT_SECRET")
        if (secret.isNullOrBlank()) {
            logger.warn("JWT_SECRET not set — using insecure default for dev only")
            return "dev-only-insecure-secret-key-change-in-production-32b"
        }
        return secret
    }

    private fun env(key: String, default: String): String =
        System.getenv(key) ?: default

    private fun envInt(key: String, default: Int): Int =
        System.getenv(key)?.toIntOrNull() ?: default

    private fun envBool(key: String, default: Boolean): Boolean =
        System.getenv(key)?.toBooleanStrictOrNull() ?: default
}
