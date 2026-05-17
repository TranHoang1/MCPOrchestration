package com.orchestrator.mcp.kb.graph.repository

import com.orchestrator.mcp.kb.graph.model.TicketRelation

/**
 * Repository interface for querying ticket relationship graph.
 * Used by graph visualization and jira_ticket_graph tool.
 */
interface TicketGraphRepository {

    suspend fun findAllForProject(projectKey: String): List<TicketRelation>

    /** Find outgoing edges from a source ticket key. */
    suspend fun findOutgoing(sourceKey: String): List<TicketRelation>

    /** Find incoming edges to a target ticket key. */
    suspend fun findIncoming(targetKey: String): List<TicketRelation>
}
