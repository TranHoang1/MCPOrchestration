package com.orchestrator.mcp.discovery

import com.orchestrator.mcp.discovery.model.FindToolsResponse

/**
 * Interface for tool discovery via semantic search.
 */
interface ToolDiscoveryService {
    suspend fun findTools(query: String, topK: Int = 5, threshold: Float = 0.7f): FindToolsResponse
}
