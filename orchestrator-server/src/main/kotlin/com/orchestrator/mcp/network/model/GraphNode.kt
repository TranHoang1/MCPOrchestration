package com.orchestrator.mcp.network.model

/**
 * A node in the feature network graph.
 */
data class GraphNode(
    val id: String,
    val label: String,
    val type: String = "ticket",
    val properties: Map<String, String> = emptyMap()
)
