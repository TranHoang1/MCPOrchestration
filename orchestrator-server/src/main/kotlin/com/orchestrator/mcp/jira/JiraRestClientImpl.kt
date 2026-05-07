package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.config.JiraClientConfig
import com.orchestrator.mcp.jira.exception.JiraTimeoutException
import com.orchestrator.mcp.jira.model.*
import com.orchestrator.mcp.jira.ratelimit.RateLimiter
import com.orchestrator.mcp.jira.retry.RetryHandler
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Implementation of [JiraRestClient] using Ktor CIO HttpClient.
 * Orchestrates: validate → rate limit → retry → HTTP → deserialize.
 */
class JiraRestClientImpl(
    private val httpClient: HttpClient,
    private val config: JiraClientConfig,
    private val rateLimiter: RateLimiter,
    private val retryHandler: RetryHandler,
    private val responseHandler: JiraResponseHandler
) : JiraRestClient {

    private val logger = LoggerFactory.getLogger(JiraRestClientImpl::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun searchIssues(jql: String, fields: List<String>, startAt: Int, maxResults: Int): JiraSearchResponse {
        val correlationId = newCorrelationId()
        JiraInputValidator.validateSearchParams(jql, fields, startAt, maxResults, correlationId)

        return executeWithMetrics("searchIssues", correlationId) {
            val response = httpClient.post("${config.baseUrl}/rest/api/3/search") {
                configureHeaders(correlationId)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JiraSearchRequest.serializer(), JiraSearchRequest(jql, fields, startAt, maxResults)))
            }
            responseHandler.handle(response, correlationId) { body -> json.decodeFromString<JiraSearchResponse>(body) }
        }
    }

    override suspend fun getIssue(issueKey: String, fields: List<String>, expand: List<String>): JiraIssue {
        val correlationId = newCorrelationId()
        JiraInputValidator.validateIssueKey(issueKey, correlationId)
        if (expand.isNotEmpty()) JiraInputValidator.validateExpand(expand, correlationId)

        return executeWithMetrics("getIssue($issueKey)", correlationId) {
            val response = httpClient.get("${config.baseUrl}/rest/api/3/issue/$issueKey") {
                configureHeaders(correlationId)
                if (fields.isNotEmpty()) parameter("fields", fields.joinToString(","))
                if (expand.isNotEmpty()) parameter("expand", expand.joinToString(","))
            }
            responseHandler.handle(response, correlationId) { body -> json.decodeFromString<JiraIssue>(body) }
        }
    }

    override suspend fun getAttachments(issueKey: String): List<JiraAttachment> {
        val correlationId = newCorrelationId()
        JiraInputValidator.validateIssueKey(issueKey, correlationId)

        return executeWithMetrics("getAttachments($issueKey)", correlationId) {
            val response = httpClient.get("${config.baseUrl}/rest/api/3/issue/$issueKey") {
                configureHeaders(correlationId)
                parameter("fields", "attachment")
            }
            val issue = responseHandler.handle(response, correlationId) { body -> json.decodeFromString<JiraIssue>(body) }
            extractAttachments(issue)
        }
    }

    override suspend fun downloadAttachment(url: String): DownloadResult {
        val correlationId = newCorrelationId()
        JiraInputValidator.validateDownloadUrl(url, config.baseUrl, correlationId)

        return executeWithMetrics("downloadAttachment", correlationId) {
            val response = httpClient.get(url) {
                header("Authorization", basicAuthHeader())
                header("Accept", "*/*")
                header("X-Correlation-ID", correlationId)
            }
            DownloadResult(
                content = response.readRawBytes(),
                contentType = response.contentType()?.toString() ?: "application/octet-stream",
                contentLength = response.contentLength() ?: 0L
            )
        }
    }

    private suspend fun <T> executeWithMetrics(operation: String, correlationId: String, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        rateLimiter.acquire()
        return retryHandler.withRetry(operation) {
            runCatching { block() }.getOrElse { e ->
                if (e is io.ktor.client.plugins.HttpRequestTimeoutException) {
                    throw JiraTimeoutException("Request timeout for $operation", correlationId, e)
                }
                throw e
            }
        }.also {
            logger.info("{} completed [correlationId={}, duration={}ms]", operation, correlationId, System.currentTimeMillis() - startTime)
        }
    }

    private fun HttpRequestBuilder.configureHeaders(correlationId: String) {
        header("Authorization", basicAuthHeader())
        header("X-Correlation-ID", correlationId)
    }

    private fun basicAuthHeader(): String {
        val credentials = "${config.email}:${config.apiToken}"
        return "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
    }

    private fun extractAttachments(issue: JiraIssue): List<JiraAttachment> {
        val attachmentArray = issue.fields["attachment"]?.jsonArray ?: return emptyList()
        return attachmentArray.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<JiraAttachment>(element) }.getOrNull()
                ?.takeIf { it.content.isNotBlank() }
        }
    }

    private fun newCorrelationId(): String = UUID.randomUUID().toString()
}
