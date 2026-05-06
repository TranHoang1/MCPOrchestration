package com.orchestrator.mcp.core.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("framing_mode")
    val framingMode: String = "newline"
)

@Serializable
data class SessionConfig(
    val id: String = "orch-default"
)

@Serializable
data class HttpSessionConfig(
    @SerialName("max_sessions")
    val maxSessions: Int = 100,
    @SerialName("session_ttl_minutes")
    val sessionTtlMinutes: Int = 30,
    @SerialName("event_buffer_size")
    val eventBufferSize: Int = 1000,
    @SerialName("cleanup_interval_seconds")
    val cleanupIntervalSeconds: Int = 60
)

@Serializable
data class ToolFilterConfig(
    val mode: String,
    val tools: List<String>
)

@Serializable
data class FileProxyConfig(
    val enabled: Boolean = true,
    @SerialName("max_file_size_mb")
    val maxFileSizeMb: Int = 50,
    @SerialName("temp_dir")
    val tempDir: String = "/tmp/mcp-proxy",
    @SerialName("ttl_minutes")
    val ttlMinutes: Int = 60,
    @SerialName("cleanup_interval_seconds")
    val cleanupIntervalSeconds: Int = 300
)
