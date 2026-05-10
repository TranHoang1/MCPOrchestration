package com.orchestrator.mcp.kb.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KbConfig(
    val kb: KbSettings = KbSettings()
)

@Serializable
data class KbSettings(
    val server: KbServerConfig = KbServerConfig(),
    val database: KbDatabaseConfig = KbDatabaseConfig(),
    val embedding: KbEmbeddingConfig = KbEmbeddingConfig(),
    @SerialName("vector_db")
    val vectorDb: KbVectorDbConfig = KbVectorDbConfig(),
    val segmentation: KbSegmentationConfig = KbSegmentationConfig(),
    val masking: KbMaskingConfig = KbMaskingConfig(),
    val security: KbSecurityConfig = KbSecurityConfig(),
    val queue: KbQueueConfig = KbQueueConfig(),
    val sync: KbSyncConfig = KbSyncConfig(),
    val audit: KbAuditConfig = KbAuditConfig()
)

@Serializable
data class KbServerConfig(
    val port: Int = 9181,
    val transport: String = "stdio"
)

@Serializable
data class KbDatabaseConfig(
    val url: String = "jdbc:postgresql://localhost:5432/mcp_orchestrator",
    val schema: String = "kb",
    val username: String = "kb_app",
    val password: String = "",
    val pool: KbPoolConfig = KbPoolConfig()
)

@Serializable
data class KbPoolConfig(
    @SerialName("maximum_size")
    val maximumSize: Int = 10,
    @SerialName("minimum_idle")
    val minimumIdle: Int = 2,
    @SerialName("idle_timeout_ms")
    val idleTimeoutMs: Long = 600000,
    @SerialName("connection_timeout_ms")
    val connectionTimeoutMs: Long = 30000
)
