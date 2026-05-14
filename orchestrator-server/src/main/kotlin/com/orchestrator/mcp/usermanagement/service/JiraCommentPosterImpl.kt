package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.jira.config.JiraClientConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.koin.core.qualifier.named
import org.slf4j.LoggerFactory

/**
 * Ktor-based implementation of JiraCommentPoster.
 * Posts comments to Jira issues via REST API v3.
 */
class JiraCommentPosterImpl(
    private val httpClient: HttpClient,
    private val config: JiraClientConfig
) : JiraCommentPoster {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun postComment(ticketKey: String, body: String) {
        val url = "${config.baseUrl}/rest/api/3/issue/$ticketKey/comment"
        val adfBody = buildAdfComment(body)

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            basicAuth(config.email, config.apiToken)
            setBody(adfBody)
        }

        if (response.status.isSuccess()) {
            logger.info("Posted comment to Jira issue {}", ticketKey)
        } else {
            val errorBody = response.bodyAsText()
            logger.warn("Failed to post comment to {}: {} — {}", ticketKey, response.status, errorBody)
        }
    }

    private fun buildAdfComment(text: String): String {
        // Jira Cloud API v3 requires ADF format for comments
        return """{"body":{"version":1,"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"$text"}]}]}}"""
    }
}
