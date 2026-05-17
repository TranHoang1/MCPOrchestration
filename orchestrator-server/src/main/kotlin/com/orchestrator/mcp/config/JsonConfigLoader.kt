package com.orchestrator.mcp.config

import com.orchestrator.mcp.core.config.OrchestratorSettings
import com.orchestrator.mcp.core.config.UpstreamServerConfig
import com.orchestrator.mcp.core.config.ToolFilterConfig
import com.orchestrator.mcp.core.config.EmbeddingConfig
import com.orchestrator.mcp.core.config.VectorDbConfig
import com.orchestrator.mcp.core.config.DiscoveryConfig
import com.orchestrator.mcp.core.config.ExecutionConfig
import com.orchestrator.mcp.core.config.ServerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Loads upstream server configuration from JSON files
 * (config.json or application.json).
 *
 * JSON config is a simpler format focused on upstream_servers,
 * complementing the full YAML configuration.
 */
object JsonConfigLoader {

    private val logger = LoggerFactory.getLogger(
        JsonConfigLoader::class.java
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse JSON content and extract upstream server configs.
     * Supports two formats:
     * - Root-level: { "upstream_servers": [...] }
     * - Nested: { "orchestrator": { "upstream_servers": [...] } }
     *
     * Validates JSON syntax first — throws ConfigValidationException
     * on malformed input to fail fast on startup.
     */
    fun parseUpstreamServers(
        content: String,
        sourcePath: String = "mcp-servers.json"
    ): List<UpstreamServerConfig> {
        // Fail fast on malformed JSON (MTO-113)
        McpServersConfigValidator.validateOrThrow(
            content, sourcePath
        )
        return try {
            val resolved = ConfigurationManagerImpl
                .resolveEnvVars(content)
            val root = json.parseToJsonElement(resolved)
                .jsonObject
            // Try upstream_servers format first, then mcpServers format
            val servers = findServersArray(root)
            val parsed = if (servers.isNotEmpty()) {
                servers.map { parseServerEntry(it.jsonObject) }
            } else {
                parseMcpServersFormat(content)
            }
            // Validate semantic correctness (MTO-113)
            val configErrors = McpServersConfigValidator
                .validateServerConfigs(parsed)
            if (configErrors.isNotEmpty()) {
                configErrors.forEach { logger.warn(it) }
            }
            parsed
        } catch (e: ConfigValidationException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "Failed to parse JSON config: ${e.message}"
            )
            emptyList()
        }
    }

    /**
     * Parse JSON content in MCP setting format (mcpServers key).
     * Format: { "mcpServers": { "name": { config } } }
     * Each key under mcpServers becomes the server name.
     * Transport is inferred: url → http, command → stdio.
     */
    fun parseMcpServersFormat(
        content: String
    ): List<UpstreamServerConfig> {
        return try {
            val resolved = ConfigurationManagerImpl
                .resolveEnvVars(content)
            val root = json.parseToJsonElement(resolved)
                .jsonObject
            val mcpServers = root["mcpServers"]
                ?.jsonObject ?: return emptyList()
            val parsed = mcpServers.entries.map { (name, config) ->
                parseMcpServerEntry(name, config.jsonObject)
            }
            // Validate semantic correctness (MTO-113)
            val configErrors = McpServersConfigValidator
                .validateServerConfigs(parsed)
            if (configErrors.isNotEmpty()) {
                configErrors.forEach { logger.warn(it) }
            }
            parsed
        } catch (e: ConfigValidationException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "Failed to parse mcpServers config: " +
                    "${e.message}"
            )
            emptyList()
        }
    }

    /**
     * Parse JSON content and extract full Orchestrator configuration sections
     * if present (embedding, vector_db, etc).
     * Returns null if JSON does not contain an explicit "orchestrator" block.
     */
    fun parseOrchestratorSettings(content: String): OrchestratorSettings? {
        return try {
            val resolved = ConfigurationManagerImpl.resolveEnvVars(content)
            val root = json.parseToJsonElement(resolved).jsonObject
            
            // Only parse if there's an explicit "orchestrator" block
            val settingsObj = root["orchestrator"]?.jsonObject ?: return null
            
            json.decodeFromJsonElement(OrchestratorSettings.serializer(), settingsObj)
        } catch (e: Exception) {
            logger.debug("JSON does not contain full orchestrator settings: ${e.message}")
            null
        }
    }

    private fun findServersArray(
        root: JsonObject
    ): List<kotlinx.serialization.json.JsonElement> {
        // Try root-level: { "upstream_servers": [...] }
        root["upstream_servers"]?.jsonArray?.let {
            return it.toList()
        }
        // Try nested: { "orchestrator": { "upstream_servers": [...] } }
        root["orchestrator"]?.jsonObject
            ?.get("upstream_servers")?.jsonArray?.let {
                return it.toList()
            }
        return emptyList()
    }

    private fun parseServerEntry(
        obj: JsonObject
    ): UpstreamServerConfig {
        val name = obj["name"]?.jsonPrimitive?.content ?: ""
        val transport = obj["transport"]
            ?.jsonPrimitive?.content ?: "stdio"
        val command = obj["command"]
            ?.jsonPrimitive?.content
        val args = obj["args"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()
        val env = obj["env"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content }
            ?: emptyMap()
        val url = obj["url"]?.jsonPrimitive?.content
        return UpstreamServerConfig(
            name = name,
            transport = transport,
            command = command,
            args = args,
            env = env,
            url = url
        )
    }

    /**
     * Parse a single server entry from mcpServers format.
     * Transport inferred: url present → http, else → stdio.
     */
    private fun parseMcpServerEntry(
        name: String,
        obj: JsonObject
    ): UpstreamServerConfig {
        val url = obj["url"]?.jsonPrimitive?.content
        val command = obj["command"]
            ?.jsonPrimitive?.content
        val transport = if (url != null) "http" else "stdio"
        val args = obj["args"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: emptyList()
        val env = obj["env"]?.jsonObject
            ?.mapValues { it.value.jsonPrimitive.content }
            ?: emptyMap()
        val cwd = obj["cwd"]?.jsonPrimitive?.content
        return UpstreamServerConfig(
            name = name,
            transport = transport,
            command = command,
            args = args,
            env = env,
            cwd = cwd,
            url = url
        )
    }
}
