package com.orchestrator.mcp.jira.config

import com.orchestrator.mcp.jira.exception.JiraValidationException

/**
 * Immutable configuration for Jira REST Client.
 * Loaded from environment variables with fail-fast validation at startup.
 */
data class JiraClientConfig(
    val baseUrl: String,
    val email: String,
    val apiToken: String,
    val rateLimit: Int = 10,
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30_000L,
    val connectTimeoutMs: Long = 10_000L,
    val socketTimeoutMs: Long = 30_000L,
    val timeoutMs: Long = 30_000L
) {
    init {
        validate()
    }

    private fun validate() {
        require(baseUrl.isNotBlank()) { "Jira baseUrl must not be blank" }
        require(email.isNotBlank()) { "Jira email must not be blank" }
        require(apiToken.isNotBlank()) { "Jira apiToken must not be blank" }
        require(rateLimit in 1..100) { "rateLimit must be 1..100" }
        require(maxRetries in 0..10) { "maxRetries must be 0..10" }
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(socketTimeoutMs > 0) { "socketTimeoutMs must be positive" }
        require(timeoutMs > 0) { "timeoutMs must be positive" }
    }

    companion object {
        /**
         * Create config from environment variables.
         * @throws JiraValidationException if required env vars are missing
         */
        fun fromEnvironment(): JiraClientConfig {
            val baseUrl = getEnvOrThrow("JIRA_BASE_URL")
            val email = getEnvOrThrow("JIRA_EMAIL")
            val apiToken = getEnvOrThrow("JIRA_API_TOKEN")

            return JiraClientConfig(
                baseUrl = baseUrl.trimEnd('/'),
                email = email,
                apiToken = apiToken,
                rateLimit = getEnvInt("JIRA_RATE_LIMIT", 10),
                maxRetries = getEnvInt("JIRA_MAX_RETRIES", 3),
                connectTimeoutMs = getEnvLong("JIRA_CONNECT_TIMEOUT_MS", 10_000L),
                socketTimeoutMs = getEnvLong("JIRA_SOCKET_TIMEOUT_MS", 30_000L),
                timeoutMs = getEnvLong("JIRA_TIMEOUT_MS", 30_000L)
            )
        }

        private fun getEnvOrThrow(key: String): String {
            return System.getenv(key)
                ?: throw JiraValidationException(
                    "Required environment variable '$key' is not set",
                    correlationId = "startup"
                )
        }

        private fun getEnvInt(key: String, default: Int): Int =
            System.getenv(key)?.toIntOrNull() ?: default

        private fun getEnvLong(key: String, default: Long): Long =
            System.getenv(key)?.toLongOrNull() ?: default
    }
}
