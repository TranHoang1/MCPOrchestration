package com.orchestrator.mcp.sync.pipeline.config

import kotlinx.serialization.Serializable

/**
 * State management configuration.
 */
@Serializable
data class StateConfig(
    val staleTimeoutMinutes: Int = 30,
    val checkpointInterval: Int = 10
)

/**
 * AI provider configuration for feature detection.
 */
@Serializable
data class AiConfig(
    val provider: String = "ollama",
    val model: String = "llama3",
    val baseUrl: String = "http://localhost:11434",
    val apiKey: String? = null,
    val temperature: Double = 0.1,
    val timeoutSeconds: Int = 30,
    val maxTokens: Int = 4000,
    val fallbackProvider: String? = null,
    val fallbackModel: String? = null
)

/**
 * Embedding generation configuration.
 */
@Serializable
data class EmbeddingConfig(
    val provider: String = "ollama",
    val model: String = "nomic-embed-text",
    val baseUrl: String = "http://localhost:11434",
    val dimensions: Int = 768,
    val batchSize: Int = 20
)

/**
 * Vector database configuration.
 */
@Serializable
data class VectorConfig(
    val enabled: Boolean = true,
    val collection: String = "sync_entries"
)

/**
 * Feature detection strategy configuration.
 */
@Serializable
data class FeatureDetectionConfig(
    val enabled: Boolean = true,
    val strategy: String = "ai_hybrid",  // epic_only | ai_hybrid | full_ai
    val confidenceThreshold: Double = 0.7,
    val maxTicketsPerAnalysis: Int = 200
)
