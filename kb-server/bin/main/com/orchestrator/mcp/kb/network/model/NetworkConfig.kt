package com.orchestrator.mcp.kb.network.model

/**
 * Configuration for feature network mapping.
 */
data class NetworkConfig(
    val defaultHops: Int = 2,
    val maxNodes: Int = 100,
    val minEdgeWeight: Double = 0.5
)
