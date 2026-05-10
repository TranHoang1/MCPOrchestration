package com.orchestrator.mcp.kb.graph.repository

import com.orchestrator.mcp.kb.graph.model.TicketCache

/**
 * Repository interface for querying cached ticket data.
 * Used by graph visualization to build node data.
 */
interface TicketCacheRepository {

    suspend fun findByProject(projectKey: String): List<TicketCache>
}
