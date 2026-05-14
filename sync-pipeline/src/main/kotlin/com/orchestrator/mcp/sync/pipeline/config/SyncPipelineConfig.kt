package com.orchestrator.mcp.sync.pipeline.config

import kotlinx.serialization.Serializable

/**
 * Top-level configuration for the sync pipeline.
 */
@Serializable
data class SyncPipelineConfig(
    val jira: JiraConfig = JiraConfig(),
    val pipeline: PipelineConfig = PipelineConfig(),
    val state: StateConfig = StateConfig(),
    val ai: AiConfig = AiConfig(),
    val embedding: EmbeddingConfig = EmbeddingConfig(),
    val vector: VectorConfig = VectorConfig(),
    val featureDetection: FeatureDetectionConfig = FeatureDetectionConfig()
)

/**
 * Jira connection configuration.
 */
@Serializable
data class JiraConfig(
    val baseUrl: String = "",
    val email: String = "",
    val apiToken: String = "",
    val rateLimit: Int = 10
)

/**
 * Pipeline processing configuration.
 */
@Serializable
data class PipelineConfig(
    val batchSize: Int = 50,
    val batchDelayMs: Long = 500,
    val maxConcurrentFetches: Int = 5,
    val maxCommentsPerTicket: Int = 100,
    val contentMaxLength: Int = 50_000,
    val writeBufferSize: Int = 100
)
