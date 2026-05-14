package com.orchestrator.mcp.sync.pipeline.dimension.builtin

import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * Extracts full ticket metadata. Produces 1 entry per ticket.
 */
class TicketMetadataDimension : IndexDimension {

    override val dimensionId = "ticket_metadata"
    override val displayName = "Ticket Metadata"
    override fun supportsVector() = true

    override suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry> {
        val entry = IndexEntry(
            id = deterministicId("${ticket.key}:metadata"),
            dimensionId = dimensionId,
            projectKey = ticket.projectKey,
            ticketKey = ticket.key,
            entryKey = "${ticket.key}:metadata",
            sourceRef = SourceRef(
                type = "jira_ticket",
                path = "jira:${ticket.projectKey}/${ticket.key}",
                syncedAt = Clock.System.now(),
                contentHash = ticket.contentHash
            ),
            data = buildDataMap(ticket),
            vectorText = buildVectorText(ticket)
        )
        return listOf(entry)
    }

    private fun buildDataMap(ticket: CrawledTicket): Map<String, String?> = mapOf(
        "summary" to ticket.summary,
        "description" to ticket.description.take(MAX_DESC_LENGTH),
        "issue_type" to ticket.issueType,
        "status" to ticket.status,
        "priority" to ticket.priority,
        "assignee_id" to ticket.assignee?.accountId,
        "assignee_name" to ticket.assignee?.displayName,
        "reporter_id" to ticket.reporter?.accountId,
        "reporter_name" to ticket.reporter?.displayName,
        "parent_key" to ticket.parentKey,
        "epic_key" to ticket.epicKey,
        "labels" to ticket.labels.joinToString(",").ifEmpty { null },
        "components" to ticket.components.joinToString(",").ifEmpty { null },
        "fix_versions" to ticket.fixVersions.joinToString(",").ifEmpty { null },
        "story_points" to ticket.storyPoints?.toString(),
        "sprint" to ticket.sprint,
        "created_at" to ticket.createdAt.toString(),
        "updated_at" to ticket.updatedAt.toString(),
        "resolved_at" to ticket.resolvedAt?.toString()
    )

    private fun buildVectorText(ticket: CrawledTicket): String {
        val parts = mutableListOf<String>()
        parts.add("[${ticket.issueType}] ${ticket.key}: ${ticket.summary}")
        if (ticket.description.isNotBlank()) {
            parts.add(ticket.description.take(VECTOR_TEXT_LIMIT))
        }
        if (ticket.labels.isNotEmpty()) {
            parts.add("Labels: ${ticket.labels.joinToString(", ")}")
        }
        return parts.joinToString("\n")
    }

    companion object {
        private const val MAX_DESC_LENGTH = 10_000
        private const val VECTOR_TEXT_LIMIT = 500
    }
}

/** Generate deterministic UUID from a string key. */
internal fun deterministicId(key: String): String {
    return UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
}
