package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.model.JiraTicketMetadata
import com.orchestrator.mcp.sync.TicketCacheRepository
import com.orchestrator.mcp.sync.model.TicketCache
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Converts parsed metadata to TicketCache and delegates to repository for upsert.
 */
interface BatchUpserter {
    suspend fun upsertBatch(tickets: List<JiraTicketMetadata>): Int
}

class BatchUpserterImpl(
    private val ticketCacheRepository: TicketCacheRepository
) : BatchUpserter {

    private val logger = LoggerFactory.getLogger(BatchUpserterImpl::class.java)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun upsertBatch(tickets: List<JiraTicketMetadata>): Int {
        if (tickets.isEmpty()) return 0
        val cacheEntries = tickets.map { it.toTicketCache() }
        val count = ticketCacheRepository.upsertBatch(cacheEntries)
        logger.debug("Upserted {} tickets for project {}", count, tickets.first().projectKey)
        return count
    }

    private fun JiraTicketMetadata.toTicketCache(): TicketCache {
        val linksJson = json.encodeToString(links)
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
