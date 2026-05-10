package com.orchestrator.mcp.kb.network.model

import kotlinx.serialization.Serializable

/**
 * A node in the feature network graph.
 */
@Serializable
data class GraphNode(
    val id: String,
    val label: String,
    val type: String = "ticket",
    val properties: Map<String, String> = emptyMap()
)
