package com.orchestrator.mcp.kb.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KbEmbeddingConfig(
    val provider: String = "ollama",
    val model: String = "nomic-embed-text",
    @SerialName("base_url")
    val baseUrl: String = "http://localhost:11434",
    val dimensions: Int = 768,
    @SerialName("cache_enabled")
    val cacheEnabled: Boolean = true,
    @SerialName("cache_max_size")
    val cacheMaxSize: Int = 200,
    @SerialName("cache_ttl_minutes")
    val cacheTtlMinutes: Int = 10
)

@Serializable
data class KbVectorDbConfig(
    val provider: String = "pgvector",
    @SerialName("collection_name")
    val collectionName: String = "kb_entries"
)

@Serializable
data class KbSegmentationConfig(
    val provider: String = "ollama",
    @SerialName("model_name")
    val modelName: String = "llama3",
    val temperature: Double = 0.1,
    @SerialName("timeout_seconds")
    val timeoutSeconds: Int = 30,
    @SerialName("max_segment_length")
    val maxSegmentLength: Int = 2000,
    @SerialName("br_local_only")
    val brLocalOnly: Boolean = true
)

@Serializable
data class KbMaskingConfig(
    val strategies: List<String> = listOf(
        "email", "phone", "bank_account", "id_card", "name"
    ),
    @SerialName("placeholder_format")
    val placeholderFormat: String = "[{TYPE}_{INDEX}]"
)

@Serializable
data class KbSecurityConfig(
    @SerialName("encryption_key")
    val encryptionKey: String = "",
    @SerialName("br_encryption_key")
    val brEncryptionKey: String = "",
    @SerialName("default_role")
    val defaultRole: String = "developer",
    @SerialName("session_ttl_minutes")
    val sessionTtlMinutes: Int = 30,
    @SerialName("rate_limit")
    val rateLimit: KbRateLimitConfig = KbRateLimitConfig()
)

@Serializable
data class KbRateLimitConfig(
    @SerialName("pii_unmask_per_hour")
    val piiUnmaskPerHour: Int = 10,
    @SerialName("br_level1_per_hour")
    val brLevel1PerHour: Int = 5,
    @SerialName("br_level2_per_hour")
    val brLevel2PerHour: Int = 15,
    @SerialName("br_level3_per_hour")
    val brLevel3PerHour: Int = 30
)

@Serializable
data class KbQueueConfig(
    @SerialName("hpq_capacity")
    val hpqCapacity: Int = 100,
    @SerialName("npq_capacity")
    val npqCapacity: Int = 1000,
    @SerialName("worker_count")
    val workerCount: Int = 2,
    @SerialName("watchdog_interval_seconds")
    val watchdogIntervalSeconds: Int = 60,
    @SerialName("stuck_threshold_minutes")
    val stuckThresholdMinutes: Int = 5,
    @SerialName("max_retries")
    val maxRetries: Int = 3,
    @SerialName("base_delay_ms")
    val baseDelayMs: Long = 1000
)

@Serializable
data class KbSyncConfig(
    @SerialName("batch_size")
    val batchSize: Int = 50
)

@Serializable
data class KbAuditConfig(
    val enabled: Boolean = true,
    @SerialName("retention_days")
    val retentionDays: Int = 90
)
