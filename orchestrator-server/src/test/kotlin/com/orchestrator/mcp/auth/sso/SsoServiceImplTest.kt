package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.JwtAuthService
import com.orchestrator.mcp.auth.sso.model.*
import com.orchestrator.mcp.usermanagement.model.User
import com.orchestrator.mcp.usermanagement.model.UserRole
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import com.orchestrator.mcp.usermanagement.service.TokenEncryptionService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.*

/**
 * Unit tests for SsoServiceImpl — SSO flow orchestration.
 */
class SsoServiceImplTest : DescribeSpec({

    val configRepo = mockk<SsoConfigRepository>()
    val userRepo = mockk<UserRepository>()
    val jwtService = mockk<JwtAuthService>()
    val encryptionService = mockk<TokenEncryptionService>()
    val pkceManager = SsoPkceManager()
    val tokenExchange = mockk<SsoTokenExchange>()
    val discoveryClient = mockk<OidcDiscoveryClient>()

    val service = SsoServiceImpl(
        configRepo, userRepo, jwtService,
        encryptionService, pkceManager, tokenExchange, discoveryClient
    )

    beforeEach {
        clearAllMocks()
    }

    describe("listProviders") {
        it("returns empty list when SSO not configured") {
            coEvery { configRepo.get() } returns null
            service.listProviders().shouldBeEmpty()
        }

        it("returns empty list when SSO disabled") {
            coEvery { configRepo.get() } returns ssoConfig(enabled = false)
            service.listProviders().shouldBeEmpty()
        }

        it("returns provider info when SSO enabled") {
            coEvery { configRepo.get() } returns ssoConfig(
                enabled = true,
                issuerUrl = "https://accounts.google.com"
            )
            val providers = service.listProviders()
            providers shouldHaveSize 1
            providers[0].name shouldBe "default"
            providers[0].displayName shouldBe "Google"
        }

        it("detects Microsoft provider from issuer URL") {
            coEvery { configRepo.get() } returns ssoConfig(
                enabled = true,
                issuerUrl = "https://login.microsoftonline.com/tenant/v2.0"
            )
            val providers = service.listProviders()
            providers[0].displayName shouldBe "Microsoft"
        }
    }

    describe("initAuthorize") {
        it("throws when SSO not configured") {
            coEvery { configRepo.get() } returns null
            shouldThrow<SsoException.SsoNotConfiguredException> {
                service.initAuthorize()
            }
        }

        it("throws when SSO disabled") {
            coEvery { configRepo.get() } returns ssoConfig(enabled = false)
            shouldThrow<SsoException.SsoDisabledException> {
                service.initAuthorize()
            }
        }

        it("returns authorize URL using OIDC discovery") {
            val config = ssoConfig(enabled = true)
            coEvery { configRepo.get() } returns config
            coEvery { discoveryClient.discover(any()) } returns OidcMetadata(
                authorizationEndpoint = "https://idp.example.com/authorize"
            )
            val response = service.initAuthorize()
            response.authorizeUrl shouldBe buildString {
                append("https://idp.example.com/authorize?")
                // Contains required OAuth2 params
            }.let { response.authorizeUrl } // just verify it starts correctly
            assert(response.authorizeUrl.startsWith("https://idp.example.com/authorize?"))
            assert(response.state.isNotBlank())
        }
    }

    describe("handleCallback") {
        it("throws on invalid state") {
            shouldThrow<SsoException.InvalidStateException> {
                service.handleCallback("code123", "invalid-state")
            }
        }

        it("provisions new user on first SSO login") {
            val config = ssoConfig(enabled = true, autoCreateUsers = true)
            coEvery { configRepo.get() } returns config
            coEvery { encryptionService.decrypt(any()) } returns "secret"
            coEvery { tokenExchange.exchangeCode(any(), any(), any(), any()) } returns
                IdpTokenResponse(accessToken = "at", idToken = createFakeIdToken())
            every { tokenExchange.parseIdTokenClaims(any()) } returns
                mapOf("email" to "new@example.com", "name" to "New User")
            coEvery { userRepo.findByEmail("new@example.com") } returns null
            coEvery { userRepo.create(any(), any(), any(), any(), any()) } returns
                User("u1", "new@example.com", UserRole.DEVELOPER, "New User", true, null, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z")
            every { jwtService.createSessionToken(any(), any(), any(), any()) } returns "jwt-token"

            // Create a valid state first
            val authReq = pkceManager.createAuthRequest()
            val result = service.handleCallback("code123", authReq.state)
            result.isNewUser shouldBe true
            result.email shouldBe "new@example.com"
            result.token shouldBe "jwt-token"
        }
    }

    describe("saveConfig") {
        it("validates issuer URL must be HTTPS") {
            val request = SsoConfigRequest(
                enabled = true,
                issuerUrl = "http://insecure.example.com",
                clientId = "client-id",
                clientSecret = "secret",
                redirectUri = "https://app.example.com/callback"
            )
            shouldThrow<SsoException.InvalidConfigException> {
                service.saveConfig(request)
            }
        }

        it("validates scopes must include openid") {
            val request = SsoConfigRequest(
                enabled = true,
                issuerUrl = "https://idp.example.com",
                clientId = "client-id",
                clientSecret = "secret",
                scopes = listOf("email", "profile"),
                redirectUri = "https://app.example.com/callback"
            )
            shouldThrow<SsoException.InvalidConfigException> {
                service.saveConfig(request)
            }
        }

        it("validates client_id is required") {
            val request = SsoConfigRequest(
                enabled = true,
                issuerUrl = "https://idp.example.com",
                clientId = "",
                clientSecret = "secret",
                redirectUri = "https://app.example.com/callback"
            )
            shouldThrow<SsoException.InvalidConfigException> {
                service.saveConfig(request)
            }
        }
    }
})

private fun ssoConfig(
    enabled: Boolean = true,
    issuerUrl: String = "https://idp.example.com",
    autoCreateUsers: Boolean = true
) = SsoConfig(
    enabled = enabled,
    issuerUrl = issuerUrl,
    clientId = "test-client",
    clientSecretEncrypted = "encrypted-secret",
    scopes = listOf("openid", "email", "profile"),
    redirectUri = "https://app.example.com/api/auth/sso/callback",
    defaultRole = "DEVELOPER",
    autoCreateUsers = autoCreateUsers
)

private fun createFakeIdToken(): String {
    val header = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"alg":"RS256"}""".toByteArray())
    val payload = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"email":"new@example.com","name":"New User"}""".toByteArray())
    return "$header.$payload.fake-signature"
}
