package com.orchestrator.mcp.auth.sso

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*

/**
 * Unit tests for OidcDiscoveryClient — OIDC metadata fetching and caching.
 */
class OidcDiscoveryClientTest : DescribeSpec({

    val sampleMetadata = """
        {
            "issuer": "https://accounts.google.com",
            "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
            "token_endpoint": "https://oauth2.googleapis.com/token",
            "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
            "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
            "scopes_supported": ["openid", "email", "profile"],
            "response_types_supported": ["code"],
            "code_challenge_methods_supported": ["S256"]
        }
    """.trimIndent()

    describe("discover") {
        it("fetches and parses OIDC metadata") {
            val client = createMockClient(sampleMetadata, HttpStatusCode.OK)
            val discovery = OidcDiscoveryClient(client)
            val metadata = discovery.discover("https://accounts.google.com")
            metadata.issuer shouldBe "https://accounts.google.com"
            metadata.authorizationEndpoint shouldBe "https://accounts.google.com/o/oauth2/v2/auth"
            metadata.tokenEndpoint shouldBe "https://oauth2.googleapis.com/token"
        }

        it("caches metadata on subsequent calls") {
            var callCount = 0
            val client = HttpClient(MockEngine { _ ->
                callCount++
                respond(sampleMetadata, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            })
            val discovery = OidcDiscoveryClient(client)
            discovery.discover("https://accounts.google.com")
            discovery.discover("https://accounts.google.com")
            callCount shouldBe 1
        }

        it("throws DiscoveryFailedException on non-200 response") {
            val client = createMockClient("Not Found", HttpStatusCode.NotFound)
            val discovery = OidcDiscoveryClient(client)
            val ex = shouldThrow<SsoException.DiscoveryFailedException> {
                discovery.discover("https://bad-issuer.example.com")
            }
            ex.message shouldContain "404"
        }
    }

    describe("refresh") {
        it("forces re-fetch even if cached") {
            var callCount = 0
            val client = HttpClient(MockEngine { _ ->
                callCount++
                respond(sampleMetadata, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            })
            val discovery = OidcDiscoveryClient(client)
            discovery.discover("https://accounts.google.com")
            discovery.refresh("https://accounts.google.com")
            callCount shouldBe 2
        }
    }

    describe("clearCache") {
        it("clears all cached entries") {
            var callCount = 0
            val client = HttpClient(MockEngine { _ ->
                callCount++
                respond(sampleMetadata, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            })
            val discovery = OidcDiscoveryClient(client)
            discovery.discover("https://accounts.google.com")
            discovery.clearCache()
            discovery.discover("https://accounts.google.com")
            callCount shouldBe 2
        }
    }
})

private fun createMockClient(body: String, status: HttpStatusCode): HttpClient {
    return HttpClient(MockEngine { _ ->
        respond(body, status, headersOf("Content-Type", "application/json"))
    })
}
