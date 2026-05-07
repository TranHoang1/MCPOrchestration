package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.TicketCache

/**
 * Repository for Jira ticket cache operations.
 * Supports UPSERT and batch operations for efficient sync.
 */
interface TicketCacheRepository {

    suspend fun upsert(ticket: TicketCache)

    suspend fun upsertBatch(tickets: List<TicketCache>): Int

    suspend fun findByProject(projectKey: String): List<TicketCache>

    suspend fun findNotIngested(projectKey: String): List<TicketCache>

    suspend fun markIngested(ticketKey: String)

    suspend fun findByHash(projectKey: String, hash: String): TicketCache?
}
