package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.config.JiraClientConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for JiraClientConfig validation.
 * STC: TC-313 to TC-320 (Config Validation)
 */
class JiraClientConfigTest : FunSpec({

    test("TC-313: valid config creates successfully") {
        val config = JiraClientConfig(
            baseUrl = "https://test.atlassian.net",
            email = "user@test.com",
            apiToken = "token123"
        )
        config.baseUrl shouldBe "https://test.atlassian.net"
        config.rateLimit shouldBe 10
        config.maxRetries shouldBe 3
    }

    test("TC-314: blank baseUrl throws IllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            JiraClientConfig(baseUrl = "", email = "user@test.com", apiToken = "token")
        }
        ex.message shouldContain "baseUrl"
    }

    test("TC-315: blank email throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            JiraClientConfig(baseUrl = "https://test.atlassian.net", email = "", apiToken = "token")
        }
    }

    test("TC-316: blank apiToken throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            JiraClientConfig(baseUrl = "https://test.atlassian.net", email = "user@test.com", apiToken = "")
        }
    }

    test("TC-317: rateLimit 0 throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            JiraClientConfig(baseUrl = "https://x.net", email = "a@b.com", apiToken = "t", rateLimit = 0)
        }
    }

    test("TC-318: rateLimit 101 throws IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            JiraClientConfig(baseUrl = "https://x.net", email = "a@b.com", apiToken = "t", rateLimit = 101)
        }
    }

    test("TC-319: custom timeouts are preserved") {
        val config = JiraClientConfig(
            baseUrl = "https://test.atlassian.net",
            email = "user@test.com",
            apiToken = "token",
            connectTimeoutMs = 5000L,
            socketTimeoutMs = 15000L,
            timeoutMs = 20000L
        )
        config.connectTimeoutMs shouldBe 5000L
        config.socketTimeoutMs shouldBe 15000L
        config.timeoutMs shouldBe 20000L
    }

    test("TC-320: trailing slash in baseUrl is preserved (no normalization in constructor)") {
        val config = JiraClientConfig(
            baseUrl = "https://test.atlassian.net/",
            email = "user@test.com",
            apiToken = "token"
        )
        config.baseUrl shouldBe "https://test.atlassian.net/"
    }
})
