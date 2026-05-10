package com.orchestrator.mcp.kb.graph.repository

import com.orchestrator.mcp.kb.graph.model.TicketRelation

/**
 * Repository interface for querying ticket relationship graph.
 * Used by graph visualization to build edge data.
 */
interface TicketGraphRepository {

    suspend fun findAllForProject(projectKey: String): List<TicketRelation>
}
