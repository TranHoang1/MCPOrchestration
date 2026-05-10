package com.orchestrator.mcp.kb.network.model

import kotlinx.serialization.Serializable

/**
 * An edge in the feature network graph.
 * Weight represents similarity score (0.0 to 1.0).
 */
@Serializable
data class GraphEdge(
    val source: String,
    val target: String,
    val weight: Double,
    val type: String = "semantic"
)
