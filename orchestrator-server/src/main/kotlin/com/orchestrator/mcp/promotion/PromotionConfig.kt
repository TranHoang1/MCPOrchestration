package com.orchestrator.mcp.promotion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for Smart Tool Promotion behavior.
 */
@Serializable
data class SmartPromotionConfig(
    @SerialName("enabled")
    val enabled: Boolean = true,
    @SerialName("max_promoted")
    val maxPromoted: Int = 50,
    @SerialName("ttl_seconds")
    val ttlSeconds: Long = 300,
    @SerialName("auto_promote_threshold")
    val autoPromoteThreshold: Float = 0.8f,
    @SerialName("cleanup_interval_seconds")
    val cleanupIntervalSeconds: Int = 60
)
