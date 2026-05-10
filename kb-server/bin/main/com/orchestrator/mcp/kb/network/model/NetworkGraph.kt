package com.orchestrator.mcp.kb.network.model

import kotlinx.serialization.Serializable

/**
 * Complete network graph with nodes, edges, and metadata.
 * Suitable for frontend visualization (D3.js, Cytoscape.js).
 */
@Serializable
data class NetworkGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val metadata: GraphMetadata
)

@Serializable
data class GraphMetadata(
    val totalNodes: Int,
    val totalEdges: Int,
    val centerNode: String? = null
)
