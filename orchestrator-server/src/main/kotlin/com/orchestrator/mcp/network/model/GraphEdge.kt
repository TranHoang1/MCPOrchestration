package com.orchestrator.mcp.network.model

/**
 * An edge in the feature network graph.
 * Weight represents similarity score (0.0 to 1.0).
 */
data class GraphEdge(
    val source: String,
    val target: String,
    val weight: Double,
    val type: String = "semantic"
)
