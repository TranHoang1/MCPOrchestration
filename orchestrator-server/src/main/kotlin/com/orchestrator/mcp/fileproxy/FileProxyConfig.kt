package com.orchestrator.mcp.fileproxy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the file proxy feature.
 */
@Serializable
data class FileProxyConfig(
    val enabled: Boolean = true,
    @SerialName("max-size-mb")
    val maxSizeMb: Int = 50,
    @SerialName("temp-directory")
    val tempDirectory: String = "/tmp/mcp-file-proxy",
    @SerialName("ttl-minutes")
    val ttlMinutes: Int = 60,
    @SerialName("cleanup-interval-minutes")
    val cleanupIntervalMinutes: Int = 15,
    @SerialName("shutdown-timeout-seconds")
    val shutdownTimeoutSeconds: Int = 30,
    @SerialName("input-proxy-enabled")
    val inputProxyEnabled: Boolean = true,
    @SerialName("output-proxy-enabled")
    val outputProxyEnabled: Boolean = true,
    @SerialName("runtime-detection-enabled")
    val runtimeDetectionEnabled: Boolean = true,
    val servers: Map<String, ServerFileProxyConfig> = emptyMap()
)

/**
 * Per-server file proxy configuration overrides.
 */
@Serializable
data class ServerFileProxyConfig(
    @SerialName("max-size-mb")
    val maxSizeMb: Int? = null
)
