package com.orchestrator.mcp.config

import com.orchestrator.mcp.core.config.UpstreamServerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory

/**
 * Validates mcp-servers.json structure and content on startup.
 * Detects malformed config before attempting upstream connections.
 */
object McpServersConfigValidator {

    private val logger = LoggerFactory.getLogger(
        McpServersConfigValidator::class.java
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validate raw JSON content for syntax correctness.
     * Returns list of validation errors (empty = valid).
     */
    fun validateJsonSyntax(content: String): List<String> {
        val errors = mutableListOf<String>()
        if (content.isBlank()) {
            errors.add("Config file is empty")
            return errors
        }
        try {
            json.parseToJsonElement(content).jsonObject
        } catch (e: Exception) {
            errors.add(
                "Invalid JSON syntax: ${e.message?.take(200)}"
            )
        }
        return errors
    }

    /**
     * Validate parsed server configs for required fields
     * and logical consistency.
     */
    fun validateServerConfigs(
        servers: List<UpstreamServerConfig>
    ): List<String> {
        val errors = mutableListOf<String>()
        servers.forEachIndexed { index, server ->
            errors.addAll(validateSingleServer(server, index))
        }
        return errors
    }

    /**
     * Full validation: syntax + semantic checks.
     * Throws ConfigValidationException on failure.
     */
    fun validateOrThrow(
        content: String,
        sourcePath: String
    ) {
        val syntaxErrors = validateJsonSyntax(content)
        if (syntaxErrors.isNotEmpty()) {
            val msg = buildErrorMessage(
                sourcePath, syntaxErrors
            )
            logger.error(msg)
            throw ConfigValidationException(msg)
        }
    }

    private fun validateSingleServer(
        server: UpstreamServerConfig,
        index: Int
    ): List<String> {
        val errors = mutableListOf<String>()
        val label = server.name.ifBlank { "server[$index]" }

        if (server.name.isBlank()) {
            errors.add("[$label] name must not be blank")
        }

        when (server.transport.lowercase()) {
            "stdio" -> {
                if (server.command.isNullOrBlank()) {
                    errors.add(
                        "[$label] stdio transport requires " +
                            "'command' field"
                    )
                }
            }
            "http", "sse" -> {
                if (server.url.isNullOrBlank()) {
                    errors.add(
                        "[$label] ${server.transport} transport " +
                            "requires 'url' field"
                    )
                }
            }
        }

        // Validate env keys are non-empty strings
        server.env.forEach { (key, value) ->
            if (key.isBlank()) {
                errors.add("[$label] env contains blank key")
            }
        }

        return errors
    }

    private fun buildErrorMessage(
        sourcePath: String,
        errors: List<String>
    ): String {
        return "MCP servers config validation failed " +
            "($sourcePath):\n" +
            errors.joinToString("\n") { "  - $it" }
    }
}

/**
 * Thrown when mcp-servers.json fails validation on startup.
 */
class ConfigValidationException(
    message: String
) : RuntimeException(message)
