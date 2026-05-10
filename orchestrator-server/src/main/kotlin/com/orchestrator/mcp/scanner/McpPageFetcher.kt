package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.client.upstream.UpstreamServerManager
import com.orchestrator.mcp.jira.model.JiraIssue
import com.orchestrator.mcp.jira.model.JiraSearchResponse
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * PageFetcher implementation that routes through MCP upstream "atlassian" server.
 * Calls jira_search tool via UpstreamServerManager instead of direct HTTP.
 *
 * MCP atlassian server returns FLAT issue format (not nested under "fields"):
 *   { "key": "X-1", "summary": "...", "status": {...}, "issue_type": {...} }
 * This class reconstructs the standard Jira "fields" JsonObject expected by
 * downstream MetadataParser.
 */
class McpPageFetcher(
    private val serverManager: UpstreamServerManager,
    private val serverName: String = ATLASSIAN_SERVER
) : PageFetcher {

    private val logger = LoggerFactory.getLogger(McpPageFetcher::class.java)

    override suspend fun fetchPage(
        jql: String,
        startAt: Int,
        maxResults: Int
    ): JiraSearchResponse {
        logger.debug("MCP fetchPage: jql='{}', startAt={}, maxResults={}", jql, startAt, maxResults)

        val connection = serverManager.getConnection(serverName)
            ?: throw McpPageFetchException("No connection to upstream server '$serverName'")

        val params = buildToolCallParams(jql, startAt, maxResults)
        val result = connection.sendRequest("tools/call", params)
        return parseResponse(result, startAt, maxResults)
    }

    private fun buildToolCallParams(jql: String, startAt: Int, maxResults: Int): JsonObject {
        return buildJsonObject {
            put("name", TOOL_NAME)
            putJsonObject("arguments") {
                put("jql", jql)
                put("fields", FETCH_FIELDS)
                put("limit", maxResults)
                put("start_at", startAt)
            }
        }
    }

    private fun parseResponse(
        result: JsonObject,
        startAt: Int,
        maxResults: Int
    ): JiraSearchResponse {
        val textContent = extractTextContent(result)
        val responseJson = Json.parseToJsonElement(textContent).jsonObject
        return mapToSearchResponse(responseJson, startAt, maxResults)
    }

    private fun extractTextContent(result: JsonObject): String {
        val contentArray = result["content"]?.jsonArray
            ?: throw McpPageFetchException("MCP response missing 'content' array")

        val textItem = contentArray.firstOrNull { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "text"
        } ?: throw McpPageFetchException("MCP response has no text content item")

        return textItem.jsonObject["text"]?.jsonPrimitive?.content
            ?: throw McpPageFetchException("MCP text content item has no 'text' field")
    }

    private fun mapToSearchResponse(
        json: JsonObject,
        startAt: Int,
        maxResults: Int
    ): JiraSearchResponse {
        val issues = json["issues"]?.jsonArray ?: JsonArray(emptyList())
        val rawTotal = json["total"]?.jsonPrimitive?.intOrNull ?: -1

        // MCP tool returns -1 when total is unknown (token-based pagination).
        // Heuristic: if page is full (issues.size == maxResults), assume more exist.
        // Use startAt + issues.size + 1 so ProjectScannerImpl fetches next page.
        // If page is NOT full, this is the last page → total = startAt + issues.size.
        val total = when {
            rawTotal > 0 -> rawTotal
            issues.size >= maxResults -> startAt + issues.size + 1
            else -> startAt + issues.size
        }

        val parsedIssues = issues.mapNotNull { element ->
            parseIssueElement(element.jsonObject)
        }

        logger.debug("Parsed {}/{} issues from MCP response (total={})", parsedIssues.size, issues.size, total)

        return JiraSearchResponse(
            startAt = startAt,
            maxResults = maxResults,
            total = total,
            issues = parsedIssues
        )
    }

    /**
     * Parses a flat MCP issue object into JiraIssue with reconstructed "fields".
     *
     * MCP format:  { key, summary, status:{name}, issue_type:{name}, ... }
     * Target:      JiraIssue(key, fields: { summary, status:{name}, issuetype:{name}, ... })
     */
    private fun parseIssueElement(obj: JsonObject): JiraIssue? {
        val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: key
        val self = obj["self"]?.jsonPrimitive?.contentOrNull ?: ""

        val fields = reconstructFields(obj)

        return JiraIssue(id = id, key = key, self = self, fields = fields)
    }

    /**
     * Reconstructs a standard Jira "fields" JsonObject from flat MCP response.
     * Maps: issue_type → issuetype, and preserves all other known fields.
     */
    private fun reconstructFields(obj: JsonObject): JsonObject {
        val knownKeys = mutableSetOf<String>()

        return buildJsonObject {
            // Direct string fields
            obj["summary"]?.let { put("summary", it); knownKeys += "summary" }
            obj["description"]?.let { put("description", it); knownKeys += "description" }
            obj["created"]?.let { put("created", it); knownKeys += "created" }
            obj["updated"]?.let { put("updated", it); knownKeys += "updated" }

            // Nested object fields (pass through as-is)
            obj["status"]?.let { put("status", it); knownKeys += "status" }
            obj["priority"]?.let { put("priority", it); knownKeys += "priority" }
            obj["parent"]?.let { put("parent", it); knownKeys += "parent" }
            obj["labels"]?.let { put("labels", it); knownKeys += "labels" }
            obj["issuelinks"]?.let { put("issuelinks", it); knownKeys += "issuelinks" }
            obj["assignee"]?.let { put("assignee", it); knownKeys += "assignee" }
            obj["reporter"]?.let { put("reporter", it); knownKeys += "reporter" }
            obj["comment"]?.let { put("comment", it); knownKeys += "comment" }

            // Key mapping: MCP uses "issue_type", Jira REST uses "issuetype"
            val issueType = obj["issue_type"] ?: obj["issuetype"]
            issueType?.let { put("issuetype", it); knownKeys += "issuetype" }

            // If MCP already has a "fields" object (fallback for future format changes)
            obj["fields"]?.jsonObject?.forEach { (k, v) ->
                if (k !in knownKeys) put(k, v)
            }
        }
    }

    companion object {
        const val ATLASSIAN_SERVER = "atlassian"
        private const val TOOL_NAME = "jira_search"
        private const val FETCH_FIELDS =
            "summary,status,issuetype,priority,labels,parent,issuelinks,updated,created,description,comment,assignee,reporter"
    }
}

/**
 * Exception thrown when MCP page fetch fails.
 */
class McpPageFetchException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
