package com.orchestrator.mcp.graph.model

import kotlinx.serialization.Serializable

/**
 * Response DTO for graph API endpoints.
 */
@Serializable
data class GraphResponse(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val metadata: GraphMetadata
)

@Serializable
data class GraphNode(
    val id: String,
    val label: String,
    val type: String,
    val status: String,
    val priority: String?,
    val assignee: String? = null,
    val group: String,
    val color: String,
    val size: Float
)

@Serializable
data class GraphEdge(
    val source: String,
    val target: String,
    val label: String,
    val color: String = "#666666",
    val width: Float = 1.0f
)

@Serializable
data class GraphMetadata(
    val projectKey: String,
    val viewMode: String,
    val nodeCount: Int,
    val edgeCount: Int
)
