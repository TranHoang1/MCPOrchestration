package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.model.JiraTicketMetadata
import com.orchestrator.mcp.scanner.model.LinkDirection
import com.orchestrator.mcp.sync.TicketCacheRepository
import com.orchestrator.mcp.sync.TicketGraphRepository
import com.orchestrator.mcp.sync.model.RelationCategory
import com.orchestrator.mcp.sync.model.TicketCache
import com.orchestrator.mcp.sync.model.TicketRelation
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Converts parsed metadata to TicketCache and delegates to repository for upsert.
 */
interface BatchUpserter {
    suspend fun upsertBatch(tickets: List<JiraTicketMetadata>): Int
    suspend fun countByProject(projectKey: String): Int
}

class BatchUpserterImpl(
    private val ticketCacheRepository: TicketCacheRepository,
    private val ticketGraphRepository: TicketGraphRepository
) : BatchUpserter {

    private val logger = LoggerFactory.getLogger(BatchUpserterImpl::class.java)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun upsertBatch(tickets: List<JiraTicketMetadata>): Int {
        if (tickets.isEmpty()) return 0
        val cacheEntries = tickets.map { it.toTicketCache() }
        val count = ticketCacheRepository.upsertBatch(cacheEntries)
        buildGraphEdges(tickets)
        logger.debug("Upserted {} tickets for project {}", count, tickets.first().projectKey)
        return count
    }

    override suspend fun countByProject(projectKey: String): Int {
        return ticketCacheRepository.countByProject(projectKey)
    }

    private suspend fun buildGraphEdges(tickets: List<JiraTicketMetadata>) {
        val relations = mutableListOf<TicketRelation>()
        for (ticket in tickets) {
            // Parent → child edges
            ticket.parentKey?.let { parent ->
                relations.add(TicketRelation(ticket.issueKey, parent, "child-of", RelationCategory.SUBTASK))
                relations.add(TicketRelation(parent, ticket.issueKey, "parent-of", RelationCategory.SUBTASK))
            }
            // Issue link edges
            for (link in ticket.links) {
                val category = if (link.direction == LinkDirection.OUTWARD) RelationCategory.OUTWARD else RelationCategory.INWARD
                val reverse = if (category == RelationCategory.OUTWARD) RelationCategory.INWARD else RelationCategory.OUTWARD
                relations.add(TicketRelation(ticket.issueKey, link.targetKey, link.type, category))
                relations.add(TicketRelation(link.targetKey, ticket.issueKey, link.type, reverse))
            }
        }
        if (relations.isNotEmpty()) {
            ticketGraphRepository.insertBatch(relations)
        }
    }

    private fun JiraTicketMetadata.toTicketCache(): TicketCache {
        val contentHash = computeHash()
        return TicketCache(
            ticketKey = issueKey,
            projectKey = projectKey,
            summary = summary,
            issueType = issueType,
            status = status,
            priority = priority,
            parentKey = parentKey,
            epicKey = null,
            labels = labels,
            createdAt = null,
            updatedAtJira = updatedAt,
            syncedAt = Clock.System.now(),
            contentHash = contentHash,
            kbIngested = false
        )
    }

    private fun JiraTicketMetadata.computeHash(): String {
        val raw = "$issueKey|$summary|$status|$issueType|$priority|$assignee|$updatedAt"
        return raw.hashCode().toUInt().toString(16).padStart(8, '0')
    }
}
