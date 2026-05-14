package com.orchestrator.mcp.kb.sync

import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.crawl.JiraIssueDetail
import com.orchestrator.mcp.sync.pipeline.crawl.JiraIssueRef
import com.orchestrator.mcp.sync.pipeline.crawl.JiraSearchResult
import com.orchestrator.mcp.sync.pipeline.crawl.SyncJiraClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.Base64

/**
 * Ktor-based SyncJiraClient for kb-server.
 * Uses direct HTTP calls since kb-server doesn't have JiraRestClient.
 */
class KbSyncJiraClient(
    private val httpClient: HttpClient,
    private val config: SyncPipelineConfig
) : SyncJiraClient {

    private val logger = LoggerFactory.getLogger(KbSyncJiraClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun searchIssues(
        jql: String,
        fields: List<String>,
        startAt: Int,
        maxResults: Int
    ): JiraSearchResult {
        val body = buildSearchBody(jql, fields, startAt, maxResults)
        val response: String = httpClient.post(searchUrl()) {
            contentType(ContentType.Application.Json)
            header("Authorization", authHeader())
            setBody(body)
        }.body()
        return parseSearchResponse(response)
    }

    override suspend fun getIssue(
        issueKey: String,
        fields: List<String>
    ): JiraIssueDetail {
        val url = "${baseUrl()}/rest/api/3/issue/$issueKey"
        val response: String = httpClient.get(url) {
            header("Authorization", authHeader())
            if (fields.isNotEmpty()) parameter("fields", fields.joinToString(","))
        }.body()
        return parseIssueResponse(response)
    }

    private fun baseUrl(): String = config.jira.baseUrl.trimEnd('/')

    private fun searchUrl(): String = "${baseUrl()}/rest/api/3/search"

    private fun authHeader(): String {
        val credentials = "${config.jira.email}:${config.jira.apiToken}"
        return "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
    }

    private fun buildSearchBody(
        jql: String,
        fields: List<String>,
        startAt: Int,
        maxResults: Int
    ): String {
        val obj = buildJsonObject {
            put("jql", jql)
            put("startAt", startAt)
            put("maxResults", maxResults)
            putJsonArray("fields") { fields.forEach { add(it) } }
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun parseSearchResponse(body: String): JiraSearchResult {
        val obj = json.parseToJsonElement(body).jsonObject
        val issues = obj["issues"]?.jsonArray?.map { issueEl ->
            val issue = issueEl.jsonObject
            JiraIssueRef(
                key = issue["key"]!!.jsonPrimitive.content,
                fields = issue["fields"]?.jsonObject ?: JsonObject(emptyMap())
            )
        } ?: emptyList()
        return JiraSearchResult(
            startAt = obj["startAt"]?.jsonPrimitive?.int ?: 0,
            total = obj["total"]?.jsonPrimitive?.int ?: 0,
            issues = issues
        )
    }

    private fun parseIssueResponse(body: String): JiraIssueDetail {
        val obj = json.parseToJsonElement(body).jsonObject
        return JiraIssueDetail(
            key = obj["key"]!!.jsonPrimitive.content,
            fields = obj["fields"]?.jsonObject ?: JsonObject(emptyMap())
        )
    }
}
