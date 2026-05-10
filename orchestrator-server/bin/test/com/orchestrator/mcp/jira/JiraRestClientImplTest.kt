package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.config.JiraClientConfig
import com.orchestrator.mcp.jira.exception.*
import com.orchestrator.mcp.jira.ratelimit.TokenBucketRateLimiter
import com.orchestrator.mcp.jira.retry.ExponentialBackoffRetryHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Unit tests for JiraRestClientImpl using Ktor MockEngine.
 * STC: TC-001 to TC-017 (Happy Path), TC-100 to TC-109 (Alternative Flows)
 */
class JiraRestClientImplTest : FunSpec({

    val config = JiraClientConfig(
        baseUrl = "https://test.atlassian.net",
        email = "test@example.com",
        apiToken = "test-token",
        rateLimit = 100,
        maxRetries = 1,
        initialDelayMs = 10L,
        maxDelayMs = 50L,
        connectTimeoutMs = 5000L,
        socketTimeoutMs = 5000L,
        timeoutMs = 5000L
    )

    fun createClient(handler: MockRequestHandler): JiraRestClient {
        val httpClient = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        }
        val rateLimiter = TokenBucketRateLimiter(ratePerSecond = 100, burstCapacity = 100)
        val retryHandler = ExponentialBackoffRetryHandler(maxRetries = 1, initialDelayMs = 10L, maxDelayMs = 50L)
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val responseHandler = JiraResponseHandler(rateLimiter, json)
        return JiraRestClientImpl(httpClient, config, rateLimiter, retryHandler, responseHandler)
    }

    test("TC-001: searchIssues returns paginated results") {
        val client = createClient { request ->
            request.url.encodedPath shouldBe "/rest/api/3/search"
            request.method shouldBe HttpMethod.Post
            respond(SEARCH_RESPONSE_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = client.searchIssues("project = MTO", listOf("summary"), 0, 50)
        result.total shouldBe 120
        result.issues shouldHaveSize 1
        result.issues[0].key shouldBe "MTO-1"
    }

    test("TC-002: getIssue returns issue with fields") {
        val client = createClient { request ->
            request.url.encodedPath shouldBe "/rest/api/3/issue/MTO-16"
            respond(ISSUE_RESPONSE_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = client.getIssue("MTO-16", listOf("summary", "status"), listOf("changelog"))
        result.key shouldBe "MTO-16"
    }

    test("TC-003: getAttachments returns attachment list") {
        val client = createClient { respond(ATTACHMENT_RESPONSE_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val result = client.getAttachments("MTO-16")
        result shouldHaveSize 1
        result[0].filename shouldBe "design.pdf"
    }

    test("TC-004: downloadAttachment returns binary content") {
        val content = "file-content".toByteArray()
        val client = createClient { respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/pdf")) }
        val result = client.downloadAttachment("https://test.atlassian.net/rest/api/3/attachment/content/123")
        result.content.decodeToString() shouldBe "file-content"
        result.contentType shouldBe "application/pdf"
    }

    test("TC-100: 401 throws JiraAuthException") {
        val client = createClient { respond("", HttpStatusCode.Unauthorized) }
        shouldThrow<JiraAuthException> { client.getIssue("MTO-16") }
    }

    test("TC-101: 404 throws JiraNotFoundException") {
        val client = createClient { respond("", HttpStatusCode.NotFound) }
        shouldThrow<JiraNotFoundException> { client.getIssue("MTO-999") }
    }

    test("TC-102: blank JQL throws JiraValidationException") {
        val client = createClient { respond("", HttpStatusCode.OK) }
        shouldThrow<JiraValidationException> { client.searchIssues("") }
    }

    test("TC-103: SSRF blocked for mismatched download URL") {
        val client = createClient { respond("", HttpStatusCode.OK) }
        shouldThrow<JiraValidationException> { client.downloadAttachment("https://evil.com/steal") }
    }
})
