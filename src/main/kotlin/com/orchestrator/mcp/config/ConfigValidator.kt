package com.orchestrator.mcp.config

import com.orchestrator.mcp.model.ConfigException

/**
 * Validates configuration values against business rules.
 */
object ConfigValidator {

    fun validate(config: OrchestratorConfig): List<String> {
        val errors = mutableListOf<String>()
        val settings = config.orchestrator

        // Discovery validation
        if (settings.discovery.topK !in 1..20) {
            errors.add("discovery.top_k must be between 1 and 20, got: ${settings.discovery.topK}")
        }
        if (settings.discovery.similarityThreshold !in 0.0f..1.0f) {
            errors.add("discovery.similarity_threshold must be between 0.0 and 1.0, got: ${settings.discovery.similarityThreshold}")
        }
        if (settings.discovery.maxQueryLength < 1) {
            errors.add("discovery.max_query_length must be positive, got: ${settings.discovery.maxQueryLength}")
        }

        // Execution validation
        if (settings.execution.timeoutSeconds !in 5..300) {
            errors.add("execution.timeout_seconds must be between 5 and 300, got: ${settings.execution.timeoutSeconds}")
        }

        // Server validation
        if (settings.server.transport.lowercase() !in listOf("stdio", "http", "sse")) {
            errors.add("server.transport must be 'stdio', 'http', or 'sse', got: ${settings.server.transport}")
        }

        // Mode-specific validation (MTO-10 Requirement)
        if (settings.server.transport.lowercase() == "stdio") {
            if (settings.embedding.provider.lowercase() == "openai" && settings.embedding.apiKey.isBlank()) {
                errors.add("In stdio mode, OpenAI embedding provider requires an api_key")
            }
            if (settings.vectorDb.provider.lowercase() == "postgresql" && settings.vectorDb.connectionString.isBlank()) {
                errors.add("In stdio mode, PostgreSQL vector_db provider requires a connection_string")
            }
        }

        // Health validation
        if (settings.health.checkIntervalSeconds < 1) {
            errors.add("health.check_interval_seconds must be positive, got: ${settings.health.checkIntervalSeconds}")
        }
        if (settings.health.maxReconnectAttempts < 0) {
            errors.add("health.max_reconnect_attempts must be non-negative, got: ${settings.health.maxReconnectAttempts}")
        }

        // Upstream server validation
        settings.upstreamServers.forEach { server ->
            if (server.name.isBlank()) {
                errors.add("Upstream server name must not be blank")
            }
            if (server.transport == "stdio" && server.command.isNullOrBlank()) {
                errors.add("Upstream server '${server.name}' with stdio transport must have a command")
            }
            if (server.transport == "http" && server.url.isNullOrBlank()) {
                errors.add("Upstream server '${server.name}' with http transport must have a url")
            }
        }

        return errors
    }

    fun validateOrThrow(config: OrchestratorConfig) {
        val errors = validate(config)
        if (errors.isNotEmpty()) {
            throw ConfigException("Configuration validation failed:\n${errors.joinToString("\n- ", prefix = "- ")}")
        }
    }
}
