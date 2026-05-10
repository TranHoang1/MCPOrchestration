package com.orchestrator.mcp.network.model

/**
 * Complete network graph with nodes, edges, and metadata.
 * Suitable for frontend visualization (D3.js, Cytoscape.js).
 */
data class NetworkGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val metadata: GraphMetadata
)

data class GraphMetadata(
    val totalNodes: Int,
    val totalEdges: Int,
    val centerNode: String? = null
)
