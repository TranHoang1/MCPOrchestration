package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.TicketRelation

/**
 * Repository for directed ticket relationship graph operations.
 */
interface TicketGraphRepository {

    suspend fun insertRelation(relation: TicketRelation)

    suspend fun insertBatch(relations: List<TicketRelation>): Int

    suspend fun findOutgoing(sourceKey: String): List<TicketRelation>

    suspend fun findIncoming(targetKey: String): List<TicketRelation>

    suspend fun findAllForProject(projectKey: String): List<TicketRelation>

    suspend fun deleteBySource(sourceKey: String): Int
}
