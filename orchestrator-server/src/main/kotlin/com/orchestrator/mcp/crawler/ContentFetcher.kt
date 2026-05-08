package com.orchestrator.mcp.crawler

import com.orchestrator.mcp.crawler.model.*
import com.orchestrator.mcp.jira.JiraRestClient
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Fetches full ticket content from Jira API and parses into TicketContent.
 */
interface ContentFetcher {
    suspend fun fetch(issueKey: String): TicketContent
}

class ContentFetcherImpl(
    private val jiraRestClient: JiraRestClient,
    private val adfParser: AdfParser,
    private val maxComments: Int = 50
) : ContentFetcher {

    private val logger = LoggerFactory.getLogger(ContentFetcherImpl::class.java)

    private val fetchFields = listOf(
        "summary", "description", "comment", "issuelinks",
        "attachment", "parent", "project"
    )

    override suspend fun fetch(issueKey: String): TicketContent {
        val issue = jiraRestClient.getIssue(issueKey, fields = fetchFields)
        val fields = issue.fields
        return TicketContent(
            issueKey = issue.key,
            projectKey = extractProjectKey(issue.key),
            summary = fields.getString("summary") ?: "",
            description = adfParser.toPlainText(fields["description"]),
            comments = parseComments(fields),
            links = parseLinks(fields),
            attachments = parseAttachments(fields),
            parentKey = fields.getNestedString("parent", "key")
        )
    }

    private fun parseComments(fields: JsonObject): List<TicketComment> {
        val commentObj = fields["comment"] as? JsonObject ?: return emptyList()
        val comments = commentObj["comments"] as? JsonArray ?: return emptyList()
        return comments.take(maxComments).mapNotNull { parseComment(it) }
    }

    private fun parseComment(element: JsonElement): TicketComment? {
        val obj = element as? JsonObject ?: return null
        val author = obj.getNestedString("author", "displayName") ?: "Unknown"
        val body = adfParser.toPlainText(obj["body"])
        val created = obj.getString("created")?.let { parseInstant(it) }
            ?: return null
        return TicketComment(author = author, body = body, created = created)
    }

    private fun parseLinks(fields: JsonObject): List<IssueLink> {
        val arr = fields["issuelinks"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { parseSingleLink(it) }
    }

    private fun parseSingleLink(element: JsonElement): IssueLink? {
        val obj = element as? JsonObject ?: return null
        val type = obj.getNestedString("type", "name") ?: return null
        return when {
            obj.containsKey("outwardIssue") -> IssueLink(
                type = type, direction = "outward",
                targetKey = obj.getNestedString("outwardIssue", "key") ?: return null
            )
            obj.containsKey("inwardIssue") -> IssueLink(
                type = type, direction = "inward",
                targetKey = obj.getNestedString("inwardIssue", "key") ?: return null
            )
            else -> null
        }
    }

    private fun parseAttachments(fields: JsonObject): List<AttachmentInfo> {
        val arr = fields["attachment"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { parseAttachment(it) }
    }

    private fun parseAttachment(element: JsonElement): AttachmentInfo? {
        val obj = element as? JsonObject ?: return null
        return AttachmentInfo(
            id = obj.getString("id") ?: return null,
            filename = obj.getString("filename") ?: return null,
            mimeType = obj.getString("mimeType"),
            sizeBytes = obj.getString("size")?.toLongOrNull(),
            downloadUrl = obj.getString("content") ?: return null
        )
    }

    private fun parseInstant(raw: String): Instant {
        val normalized = if (raw.matches(Regex(".*[+-]\\d{4}$"))) {
            raw.dropLast(2) + ":" + raw.takeLast(2)
        } else raw
        return Instant.parse(normalized)
    }

    private fun extractProjectKey(issueKey: String) = issueKey.substringBefore('-')
}

private fun JsonObject.getString(key: String): String? {
    val el = this[key] ?: return null
    return (el as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.getNestedString(k1: String, k2: String): String? {
    val nested = this[k1] as? JsonObject ?: return null
    return nested.getString(k2)
}
