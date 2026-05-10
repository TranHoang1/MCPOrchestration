package com.orchestrator.mcp.segmentation.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for the Content Segmentation Service.
 * Loaded from application.yml under orchestrator.segmentation.
 */
@Serializable
data class SegmentationConfig(
    val enabled: Boolean = true,
    val provider: String = "openai",
    @SerialName("model-name")
    val modelName: String = "gpt-4o-mini",
    val temperature: Double = 0.1,
    @SerialName("max-tokens")
    val maxTokens: Int = 2000,
    @SerialName("api-key")
    val apiKey: String? = null,
    @SerialName("base-url")
    val baseUrl: String? = null,
    @SerialName("timeout-seconds")
    val timeoutSeconds: Int = 10,
    @SerialName("br-local-only")
    val brLocalOnly: Boolean = false,
    @SerialName("ollama-url")
    val ollamaUrl: String = "http://localhost:11434",
    @SerialName("ollama-model")
    val ollamaModel: String = "llama3"
)
