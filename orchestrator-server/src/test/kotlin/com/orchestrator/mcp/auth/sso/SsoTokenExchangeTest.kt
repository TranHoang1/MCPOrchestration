package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.OidcMetadata
import com.orchestrator.mcp.auth.sso.model.SsoConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Unit tests for SsoTokenExchange — OAuth2 code exchange and ID token parsing.
 */
class SsoTokenExchangeTest : DescribeSpec({

    val discoveryClient = mockk<OidcDiscoveryClient>()

    describe("exchangeCode") {
        it("exchanges code successfully") {
            val tokenResponse = """
                {
                    "access_token": "access-123",
                    "id_token": "header.payload.sig",
                    "token_type": "Bearer",
                    "expires_in": 3600
                }
            """.trimIndent()
            val client = HttpClient(MockEngine { _ ->
                respond(tokenResponse, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            })
            coEvery { discoveryClient.discover(any()) } returns OidcMetadata(
                tokenEndpoint = "https://idp.example.com/token"
            )
            val exchange = SsoTokenExchange(client, discoveryClient)
            val config = testConfig()
            val result = exchange.exchangeCode(config, "auth-code", "verifier", "secret")
            result.accessToken shouldBe "access-123"
            result.idToken shouldBe "header.payload.sig"
        }

        it("throws on non-200 response from IdP") {
            val client = HttpClient(MockEngine { _ ->
                respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest)
            })
            coEvery { discoveryClient.discover(any()) } returns OidcMetadata(
                tokenEndpoint = "https://idp.example.com/token"
            )
            val exchange = SsoTokenExchange(client, discoveryClient)
            val ex = shouldThrow<SsoException.TokenExchangeFailedException> {
                exchange.exchangeCode(testConfig(), "bad-code", "verifier", "secret")
            }
            ex.message shouldContain "400"
        }
    }

    describe("parseIdTokenClaims") {
        it("parses valid JWT payload") {
            val client = HttpClient(MockEngine { respond("") })
            val exchange = SsoTokenExchange(client, discoveryClient)
            val payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("""{"email":"user@test.com","name":"Test"}""".toByteArray())
            val idToken = "header.$payload.signature"
            val claims = exchange.parseIdTokenClaims(idToken)
            claims["email"] shouldBe "user@test.com"
            claims["name"] shouldBe "Test"
        }

        it("throws on malformed JWT (not 3 parts)") {
            val client = HttpClient(MockEngine { respond("") })
            val exchange = SsoTokenExchange(client, discoveryClient)
            shouldThrow<SsoException.InvalidIdTokenException> {
                exchange.parseIdTokenClaims("not-a-jwt")
            }
        }
    }
})

private fun testConfig() = SsoConfig(
    enabled = true,
    issuerUrl = "https://idp.example.com",
    clientId = "test-client",
    clientSecretEncrypted = "encrypted",
    redirectUri = "https://app.example.com/callback"
)
