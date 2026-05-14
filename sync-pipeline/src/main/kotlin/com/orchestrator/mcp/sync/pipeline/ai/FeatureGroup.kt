package com.orchestrator.mcp.sync.pipeline.ai

import kotlinx.serialization.Serializable

/**
 * AI-detected feature grouping across multiple tickets.
 */
@Serializable
data class FeatureGroup(
    val featureId: String,
    val featureName: String,
    val ticketKeys: List<String>,
    val detectionMethod: String,  // epic_hierarchy, label_based, ai_clustering
    val confidence: Double,
    val epicKey: String? = null,
    val description: String? = null
)
