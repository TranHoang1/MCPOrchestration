package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.jira.model.JiraIssue
import com.orchestrator.mcp.scanner.model.JiraTicketMetadata
import com.orchestrator.mcp.scanner.model.LinkDirection
import com.orchestrator.mcp.scanner.model.TicketLink
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Parses Jira issue JSON into lightweight JiraTicketMetadata.
 * Handles missing/null fields gracefully with sensible defaults.
 */
class MetadataParser {

    private val logger = LoggerFactory.getLogger(MetadataParser::class.java)

    fun parse(issues: List<JiraIssue>): List<JiraTicketMetadata> {
        return issues.mapNotNull { issue -> parseIssue(issue) }
    }

    private fun parseIssue(issue: JiraIssue): JiraTicketMetadata? {
        return try {
            val fields = issue.fields
            JiraTicketMetadata(
                issueKey = issue.key,
                projectKey = extractProjectKey(issue.key),
                summary = fields.getString("summary") ?: "",
                status = fields.getNestedString("status", "name") ?: "Unknown",
                issueType = fields.getNestedString("issuetype", "name") ?: "Unknown",
                priority = fields.getNestedString("priority", "name") ?: "Medium",
                assignee = fields.getNestedString("assignee", "displayName"),
                parentKey = fields.getNestedString("parent", "key"),
                labels = extractLabels(fields),
                links = extractLinks(fields),
                updatedAt = parseUpdatedAt(fields)
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse issue ${issue.key}: ${e.message}")
            null
        }
    }

    private fun extractProjectKey(issueKey: String): String =
        issueKey.substringBefore('-')

    private fun extractLabels(fields: JsonObject): List<String> {
        val labelsArray = fields["labels"] as? JsonArray ?: return emptyList()
        return labelsArray.mapNotNull { (it as? JsonPrimitive)?.content }
    }

    private fun extractLinks(fields: JsonObject): List<TicketLink> {
        val linksArray = fields["issuelinks"] as? JsonArray ?: return emptyList()
        return linksArray.mapNotNull { parseSingleLink(it) }
    }

    private fun parseSingleLink(element: JsonElement): TicketLink? {
        val obj = element as? JsonObject ?: return null
        val type = obj.getNestedString("type", "name") ?: return null
        return when {
            obj.containsKey("outwardIssue") -> TicketLink(
                type = type,
                direction = LinkDirection.OUTWARD,
                targetKey = obj.getNestedString("outwardIssue", "key") ?: return null
            )
            obj.containsKey("inwardIssue") -> TicketLink(
                type = type,
                direction = LinkDirection.INWARD,
                targetKey = obj.getNestedString("inwardIssue", "key") ?: return null
            )
            else -> null
        }
    }

    private fun parseUpdatedAt(fields: JsonObject): Instant {
        val raw = fields.getString("updated")
            ?: return kotlinx.datetime.Clock.System.now()
        return Instant.parse(normalizeIsoDate(raw))
    }

    private fun normalizeIsoDate(raw: String): String {
        // Jira returns "2024-01-15T10:30:00.000+0000" — need colon in offset
        return if (raw.matches(Regex(".*[+-]\\d{4}$"))) {
            raw.dropLast(2) + ":" + raw.takeLast(2)
        } else {
            raw
        }
    }
}

private fun JsonObject.getString(key: String): String? {
    val element = this[key] ?: return null
    return (element as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.getNestedString(key1: String, key2: String): String? {
    val nested = this[key1] as? JsonObject ?: return null
    return nested.getString(key2)
}
