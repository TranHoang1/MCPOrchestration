package com.orchestrator.mcp.routing

import com.orchestrator.mcp.routing.model.RoutingTable
import com.orchestrator.mcp.routing.model.ToolRoute

/**
 * Service interface for building and caching the routing table (MTO-132).
 * Provides tool-to-location mapping for bridge clients.
 */
interface RoutingTableService {
    /** Build or return cached routing table. */
    fun getRoutingTable(): RoutingTable

    /** Resolve a single tool's route. Returns null if not explicitly configured. */
    fun resolve(toolName: String): ToolRoute?

    /** Force rebuild of the routing table (e.g., after config change). */
    fun invalidate()

    /** Get the current ETag for cache validation. */
    fun getETag(): String
}
