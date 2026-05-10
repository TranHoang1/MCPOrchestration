package com.orchestrator.mcp.brmasking.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the BR Masking service.
 */
@Serializable
data class BrMaskingConfig(
    val enabled: Boolean = true,
    @SerialName("encryption-key")
    val encryptionKey: String = "",
    val provider: String = "openai",
    @SerialName("model-name")
    val modelName: String = "gpt-4o-mini",
    val temperature: Double = 0.0,
    @SerialName("timeout-seconds")
    val timeoutSeconds: Int = 15
)
