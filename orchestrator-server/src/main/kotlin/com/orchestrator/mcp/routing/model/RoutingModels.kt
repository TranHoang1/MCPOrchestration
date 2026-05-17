package com.orchestrator.mcp.routing.model

import kotlinx.serialization.Serializable

/**
 * Routing table response sent to bridge clients (MTO-132).
 * Tells bridges which tools should be executed locally vs remotely.
 */
@Serializable
data class RoutingTable(
    val version: String,
    val updatedAt: String,
    val defaultLocation: String,
    val tools: Map<String, ToolRoute>
)

/**
 * Routing information for a single tool.
 */
@Serializable
data class ToolRoute(
    val location: String,
    val server: String,
    val fallback: String? = null,
    val priority: Int? = null
)
