package com.orchestrator.mcp.sync.pipeline.dimension.generic

import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.deterministicId
import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Generic dimension for UI-configured field extraction.
 * Extracts specified fields from ticket data based on DimensionConfig.
 */
class GenericFieldDimension(
    override val dimensionId: String,
    override val displayName: String,
    private val vectorEnabled: Boolean
) : IndexDimension {

    override fun supportsVector() = vectorEnabled

    override suspend fun extract(
        ticket: CrawledTicket,
        config: DimensionConfig
    ): List<IndexEntry> {
        val fieldNames = extractFieldNames(config)
        if (fieldNames.isEmpty()) return emptyList()

        val data = extractFields(ticket, fieldNames)
        val entryKey = "${ticket.key}:$dimensionId"

        return listOf(
            IndexEntry(
                id = deterministicId(entryKey),
                dimensionId = dimensionId,
                projectKey = ticket.projectKey,
                ticketKey = ticket.key,
                entryKey = entryKey,
                sourceRef = SourceRef(
                    type = "jira_fields",
                    path = "jira:${ticket.projectKey}/${ticket.key}",
                    syncedAt = Clock.System.now(),
                    contentHash = null
                ),
                data = data,
                vectorText = if (vectorEnabled) buildVectorText(ticket, data) else null
            )
        )
    }

    private fun extractFieldNames(config: DimensionConfig): List<String> {
        val fieldsObj = config.fields ?: return emptyList()
        val arr = fieldsObj["extract"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }

    private fun extractFields(
        ticket: CrawledTicket,
        fieldNames: List<String>
    ): Map<String, String?> {
        return fieldNames.associateWith { field -> resolveField(ticket, field) }
    }

    private fun resolveField(ticket: CrawledTicket, field: String): String? {
        return when (field) {
            "summary" -> ticket.summary
            "description" -> ticket.description.take(5000)
            "status" -> ticket.status
            "issue_type" -> ticket.issueType
            "priority" -> ticket.priority
            "assignee" -> ticket.assignee?.displayName
            "reporter" -> ticket.reporter?.displayName
            "labels" -> ticket.labels.joinToString(",").ifEmpty { null }
            "components" -> ticket.components.joinToString(",").ifEmpty { null }
            "sprint" -> ticket.sprint
            "epic_key" -> ticket.epicKey
            else -> null
        }
    }

    private fun buildVectorText(
        ticket: CrawledTicket,
        data: Map<String, String?>
    ): String {
        val values = data.values.filterNotNull().joinToString(" ")
        return "${ticket.key}: $values".take(500)
    }
}
