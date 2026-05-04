package com.orchestrator.mcp.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root configuration for the MCP Orchestration Server.
 * Parsed from application.yml using kaml.
 */
@Serializable
data class OrchestratorConfig(
    val orchestrator: OrchestratorSettings = OrchestratorSettings()
)

@Serializable
data class OrchestratorSettings(
    val server: ServerConfig = ServerConfig(),
    val discovery: DiscoveryConfig = DiscoveryConfig(),
    val execution: ExecutionConfig = ExecutionConfig(),
    val embedding: EmbeddingConfig = EmbeddingConfig(),
    @SerialName("vector_db")
    val vectorDb: VectorDbConfig = VectorDbConfig(),
    val health: HealthConfig = HealthConfig(),
    val session: SessionConfig = SessionConfig(),
    @SerialName("upstream_servers")
    val upstreamServers: List<UpstreamServerConfig> = emptyList()
)

@Serializable
data class ServerConfig(
    val port: Int = 8080,
    val transport: String = "stdio",
    val protocol: String = "stdio" // "stdio" or "sse"
)

@Serializable
data class DiscoveryConfig(
    @SerialName("top_k")
    val topK: Int = 5,
    @SerialName("similarity_threshold")
    val similarityThreshold: Float = 0.7f,
    @SerialName("max_query_length")
    val maxQueryLength: Int = 2000,
    @SerialName("fallback_to_keyword")
    val fallbackToKeyword: Boolean = true
)

@Serializable
data class ExecutionConfig(
    @SerialName("timeout_seconds")
    val timeoutSeconds: Int = 30,
    @SerialName("validate_arguments")
    val validateArguments: Boolean = true,
    @SerialName("max_retries")
    val maxRetries: Int = 1
)

@Serializable
data class EmbeddingConfig(
    val provider: String = "openai",
    val model: String = "text-embedding-3-small",
    @SerialName("api_key")
    val apiKey: String = "",
    val dimensions: Int = 768,
    @SerialName("base_url")
    val baseUrl: String = "",
    @SerialName("cache_enabled")
    val cacheEnabled: Boolean = true,
    @SerialName("cache_max_size")
    val cacheMaxSize: Int = 100,
    @SerialName("cache_ttl_minutes")
    val cacheTtlMinutes: Int = 5
)

@Serializable
data class VectorDbConfig(
    val provider: String = "qdrant",
    val host: String = "localhost",
    val port: Int = 6333,
    @SerialName("collection_name")
    val collectionName: String = "mcp_tools",
    @SerialName("connection_string")
    val connectionString: String = "",
    val user: String = "",
    val password: String = "",
    @SerialName("hnsw_m")
    val hnswM: Int = 16,
    @SerialName("hnsw_ef_construction")
    val hnswEfConstruction: Int = 64
)

@Serializable
data class HealthConfig(
    @SerialName("check_interval_seconds")
    val checkIntervalSeconds: Int = 30,
    @SerialName("auto_reconnect")
    val autoReconnect: Boolean = true,
    @SerialName("max_reconnect_attempts")
    val maxReconnectAttempts: Int = 5
)

@Serializable
data class UpstreamServerConfig(
    val name: String,
    val transport: String = "stdio",
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String? = null,
    val disabled: Boolean = false,
    @SerialName("tool_filter")
    val toolFilter: ToolFilterConfig? = null,
    @SerialName("auto_approve")
    val autoApprove: List<String> = emptyList(),
    /**
     * Framing mode for stdio communication.
     * - "newline"        : one JSON object per line (Python mcp / FastMCP, default)
     * - "content-length" : HTTP-style Content-Length header (Kotlin/Node SDK)
     */
    @SerialName("framing_mode")
    val framingMode: String = "newline"
)

@Serializable
data class SessionConfig(
    val id: String = System.getenv("HOSTNAME") ?: "orch-default"
)

@Serializable
data class ToolFilterConfig(
    val mode: String, // "allowlist" or "blocklist"
    val tools: List<String>
)
