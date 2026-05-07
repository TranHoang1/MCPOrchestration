package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.exception.*
import com.orchestrator.mcp.jira.ratelimit.RateLimiter
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Handles HTTP response status mapping to typed exceptions.
 * Extracted from JiraRestClientImpl to respect SRP and file size limits.
 */
class JiraResponseHandler(
    private val rateLimiter: RateLimiter,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(JiraResponseHandler::class.java)

    /**
     * Map HTTP response to success or typed exception.
     * @return deserialized body on 2xx
     * @throws JiraClientException on error status codes
     */
    suspend fun <T> handle(response: HttpResponse, correlationId: String, deserializer: (String) -> T): T {
        return when (response.status.value) {
            in 200..299 -> deserializer(response.bodyAsText())
            400 -> throw parseValidationError(response, correlationId)
            401 -> throw JiraAuthException("Invalid credentials", 401, correlationId)
            403 -> throw JiraAuthException("Insufficient permissions", 403, correlationId)
            404 -> throw JiraNotFoundException("Resource not found", correlationId)
            429 -> throw handleRateLimit(response, correlationId)
            in 500..599 -> throw JiraServerException(
                "Server error: ${response.status}", response.status.value, correlationId
            )
            else -> throw JiraServerException(
                "Unexpected status: ${response.status}", response.status.value, correlationId
            )
        }
    }

    private suspend fun parseValidationError(response: HttpResponse, correlationId: String): JiraValidationException {
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val errorMsg = extractErrorMessages(body)
        return JiraValidationException(errorMsg, correlationId)
    }

    private fun extractErrorMessages(body: String): String {
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val messages = obj["errorMessages"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            messages?.joinToString("; ") ?: "Validation error"
        }.getOrDefault("Validation error: $body")
    }

    private suspend fun handleRateLimit(response: HttpResponse, correlationId: String): JiraRateLimitException {
        val retryAfter = response.headers["Retry-After"]?.toLongOrNull() ?: 60L
        rateLimiter.pauseUntil(System.currentTimeMillis() + retryAfter * 1000)
        logger.warn("Rate limited — pausing for {}s [correlationId={}]", retryAfter, correlationId)
        return JiraRateLimitException("Rate limited", retryAfter, correlationId)
    }
}
