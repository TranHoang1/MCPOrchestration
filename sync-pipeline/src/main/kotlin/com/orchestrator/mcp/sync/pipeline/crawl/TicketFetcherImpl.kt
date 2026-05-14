package com.orchestrator.mcp.sync.pipeline.crawl

import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Fetches full ticket content from Jira API and maps to CrawledTicket.
 */
class TicketFetcherImpl(
    private val jiraClient: SyncJiraClient,
    private val adfParser: AdfParser,
    private val contentHasher: ContentHasher,
    private val config: SyncPipelineConfig
) : TicketFetcher {

    private val logger = LoggerFactory.getLogger(TicketFetcherImpl::class.java)

    override suspend fun fetchFull(
        issueKey: String,
        fields: Map<String, String?>
    ): CrawledTicket {
        val issue = jiraClient.getIssue(issueKey, FETCH_FIELDS)
        return buildCrawledTicket(issue.key, issue.fields)
    }

    private fun buildCrawledTicket(key: String, f: JsonObject): CrawledTicket {
        val summary = f.getString("summary") ?: ""
        val description = adfParser.toPlainText(f["description"])
        return CrawledTicket(
            key = key,
            projectKey = key.substringBefore('-'),
            summary = summary,
            description = description,
            issueType = f.getNestedString("issuetype", "name") ?: "Unknown",
            status = f.getNestedString("status", "name") ?: "Unknown",
            priority = f.getNestedString("priority", "name"),
            assignee = parseUser(f["assignee"]),
            reporter = parseUser(f["reporter"]),
            parentKey = f.getNestedString("parent", "key"),
            epicKey = f.getString("customfield_10014"),
            labels = parseStringArray(f["labels"]),
            components = parseNamedArray(f["components"]),
            fixVersions = parseNamedArray(f["fixVersions"]),
            storyPoints = f.getDouble("story_points"),
            sprint = extractSprintName(f["sprint"]),
            createdAt = parseInstant(f.getString("created") ?: ""),
            updatedAt = parseInstant(f.getString("updated") ?: ""),
            resolvedAt = f.getString("resolutiondate")?.let { parseInstant(it) },
            comments = parseComments(f),
            links = parseLinks(f),
            attachments = parseAttachments(f),
            contentHash = contentHasher.hashParts(summary, description)
        )
    }

    private fun parseComments(f: JsonObject): List<CrawledComment> {
        val commentObj = f["comment"] as? JsonObject ?: return emptyList()
        val arr = commentObj["comments"] as? JsonArray ?: return emptyList()
        return arr.take(config.pipeline.maxCommentsPerTicket)
            .mapNotNull { parseSingleComment(it) }
    }

    private fun parseSingleComment(el: JsonElement): CrawledComment? {
        val obj = el as? JsonObject ?: return null
        val author = parseUser(obj["author"]) ?: return null
        return CrawledComment(
            commentId = obj.getString("id") ?: return null,
            author = author,
            body = adfParser.toPlainText(obj["body"]),
            createdAt = parseInstant(obj.getString("created") ?: return null),
            updatedAt = obj.getString("updated")?.let { parseInstant(it) }
        )
    }

    private fun parseLinks(f: JsonObject): List<CrawledLink> {
        val arr = f["issuelinks"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { parseSingleLink(it) }
    }

    private fun parseSingleLink(el: JsonElement): CrawledLink? {
        val obj = el as? JsonObject ?: return null
        val type = obj.getNestedString("type", "name") ?: return null
        return when {
            obj.containsKey("outwardIssue") -> CrawledLink(
                type = type, direction = "outward",
                targetKey = obj.getNestedString("outwardIssue", "key") ?: return null
            )
            obj.containsKey("inwardIssue") -> CrawledLink(
                type = type, direction = "inward",
                targetKey = obj.getNestedString("inwardIssue", "key") ?: return null
            )
            else -> null
        }
    }

    private fun parseAttachments(f: JsonObject): List<CrawledAttachment> {
        val arr = f["attachment"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { parseAttachment(it) }
    }

    private fun parseAttachment(el: JsonElement): CrawledAttachment? {
        val obj = el as? JsonObject ?: return null
        return CrawledAttachment(
            attachmentId = obj.getString("id") ?: return null,
            filename = obj.getString("filename") ?: return null,
            mimeType = obj.getString("mimeType"),
            sizeBytes = obj.getString("size")?.toLongOrNull(),
            author = parseUser(obj["author"]),
            createdAt = parseInstant(obj.getString("created") ?: return null),
            downloadUrl = obj.getString("content") ?: return null
        )
    }

    private fun parseUser(el: JsonElement?): JiraUser? {
        val obj = el as? JsonObject ?: return null
        return JiraUser(
            accountId = obj.getString("accountId") ?: return null,
            displayName = obj.getString("displayName") ?: "Unknown",
            email = obj.getString("emailAddress")
        )
    }

    private fun parseStringArray(el: JsonElement?): List<String> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }

    private fun parseNamedArray(el: JsonElement?): List<String> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonObject)?.getString("name") }
    }

    private fun extractSprintName(el: JsonElement?): String? {
        val obj = el as? JsonObject ?: return null
        return obj.getString("name")
    }

    private fun parseInstant(raw: String): Instant {
        val normalized = if (raw.matches(Regex(".*[+-]\\d{4}$"))) {
            raw.dropLast(2) + ":" + raw.takeLast(2)
        } else raw
        return Instant.parse(normalized)
    }

    companion object {
        private val FETCH_FIELDS = listOf(
            "summary", "description", "issuetype", "status", "priority",
            "assignee", "reporter", "parent", "customfield_10014",
            "labels", "components", "fixVersions", "story_points",
            "sprint", "created", "updated", "resolutiondate",
            "comment", "issuelinks", "attachment"
        )
    }
}

private fun JsonObject.getString(key: String): String? {
    val el = this[key] ?: return null
    return (el as? JsonPrimitive)?.contentOrNull
}

private fun JsonObject.getDouble(key: String): Double? {
    val el = this[key] ?: return null
    return (el as? JsonPrimitive)?.doubleOrNull
}

private fun JsonObject.getNestedString(k1: String, k2: String): String? {
    val nested = this[k1] as? JsonObject ?: return null
    return nested.getString(k2)
}
