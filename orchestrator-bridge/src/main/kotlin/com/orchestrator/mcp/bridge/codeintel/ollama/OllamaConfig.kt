package com.orchestrator.mcp.bridge.codeintel.ollama

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for Ollama integration (Layer 2 + Layer 3).
 */
@Serializable
data class OllamaConfig(
    val endpoint: String = "http://localhost:11434",
    @SerialName("embedding_model")
    val embeddingModel: String = "nomic-embed-text",
    @SerialName("summarization_model")
    val summarizationModel: String = "qwen3:8b",
    @SerialName("health_check_timeout_ms")
    val healthCheckTimeoutMs: Long = 5000,
    @SerialName("retry_interval_ms")
    val retryIntervalMs: Long = 60000
)
