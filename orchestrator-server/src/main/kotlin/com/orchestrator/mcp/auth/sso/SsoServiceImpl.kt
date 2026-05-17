package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.JwtAuthService
import com.orchestrator.mcp.auth.sso.model.*
import com.orchestrator.mcp.usermanagement.model.UserRole
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import com.orchestrator.mcp.usermanagement.service.TokenEncryptionService
import org.slf4j.LoggerFactory
import java.net.URLEncoder

/**
 * OAuth2/OIDC SSO implementation with PKCE, OIDC Discovery,
 * JIT provisioning, and JWT issuance.
 */
class SsoServiceImpl(
    private val configRepo: SsoConfigRepository,
    private val userRepo: UserRepository,
    private val jwtService: JwtAuthService,
    private val encryptionService: TokenEncryptionService,
    private val pkceManager: SsoPkceManager,
    private val tokenExchange: SsoTokenExchange,
    private val discoveryClient: OidcDiscoveryClient
) : SsoService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun listProviders(): List<SsoProviderInfo> {
        val config = configRepo.get() ?: return emptyList()
        if (!config.enabled) return emptyList()
        val displayName = extractProviderName(config.issuerUrl)
        return listOf(
            SsoProviderInfo(
                name = "default",
                displayName = displayName,
                authorizeUrl = "/api/auth/sso/authorize"
            )
        )
    }

    override suspend fun initAuthorize(): SsoAuthorizeResponse {
        val config = loadEnabledConfig()
        val authReq = pkceManager.createAuthRequest()
        val authorizeUrl = buildAuthorizeUrl(config, authReq)
        logger.info("SSO authorize initiated, state={}", authReq.state)
        return SsoAuthorizeResponse(authorizeUrl, authReq.state)
    }

    override suspend fun handleCallback(code: String, state: String): SsoCallbackResult {
        val codeVerifier = pkceManager.consumeState(state)
            ?: throw SsoException.InvalidStateException()
        val config = loadEnabledConfig()
        val clientSecret = encryptionService.decrypt(config.clientSecretEncrypted)
        val tokenResponse = tokenExchange.exchangeCode(config, code, codeVerifier, clientSecret)
        val claims = extractUserClaims(tokenResponse, config)
        return provisionAndIssueToken(claims, config)
    }

    override suspend fun getConfig(): SsoConfig? = configRepo.get()

    override suspend fun saveConfig(request: SsoConfigRequest): SsoConfig {
        validateConfigRequest(request)
        validateDiscovery(request.issuerUrl)
        val existing = configRepo.get()
        val encryptedSecret = resolveSecret(request, existing)
        val config = buildSsoConfig(request, encryptedSecret)
        return configRepo.save(config)
    }

    private suspend fun loadEnabledConfig(): SsoConfig {
        val config = configRepo.get() ?: throw SsoException.SsoNotConfiguredException()
        if (!config.enabled) throw SsoException.SsoDisabledException()
        return config
    }

    private suspend fun buildAuthorizeUrl(
        config: SsoConfig,
        authReq: SsoPkceManager.AuthRequest
    ): String {
        val metadata = discoveryClient.discover(config.issuerUrl)
        val baseUrl = metadata.authorizationEndpoint.ifBlank {
            "${config.issuerUrl.trimEnd('/')}/protocol/openid-connect/auth"
        }
        val params = buildAuthorizeParams(config, authReq)
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$baseUrl?$query"
    }

    private fun buildAuthorizeParams(
        config: SsoConfig,
        authReq: SsoPkceManager.AuthRequest
    ): Map<String, String> = mapOf(
        "response_type" to "code",
        "client_id" to config.clientId,
        "redirect_uri" to config.redirectUri,
        "scope" to config.scopes.joinToString(" "),
        "state" to authReq.state,
        "code_challenge" to authReq.codeChallenge,
        "code_challenge_method" to "S256"
    )

    private fun extractUserClaims(
        tokenResponse: IdpTokenResponse,
        config: SsoConfig
    ): UserClaims {
        val idToken = tokenResponse.idToken
            ?: throw SsoException.InvalidIdTokenException("No id_token in response")
        val rawClaims = tokenExchange.parseIdTokenClaims(idToken)
        val emailKey = config.claimsMapping["email"] ?: "email"
        val nameKey = config.claimsMapping["name"] ?: "name"
        val email = rawClaims[emailKey]
            ?: throw SsoException.MissingClaimException(emailKey)
        val displayName = rawClaims[nameKey] ?: email.substringBefore("@")
        return UserClaims(email, displayName)
    }

    private suspend fun provisionAndIssueToken(
        claims: UserClaims,
        config: SsoConfig
    ): SsoCallbackResult {
        val existing = userRepo.findByEmail(claims.email)
        val (userId, isNew) = if (existing != null) {
            existing.id to false
        } else {
            createNewUser(claims, config)
        }
        val roles = listOf(existing?.role?.name ?: config.defaultRole)
        val token = jwtService.createSessionToken(userId, claims.email, roles)
        return SsoCallbackResult(token, userId, claims.email, claims.displayName, isNew)
    }

    private suspend fun createNewUser(
        claims: UserClaims,
        config: SsoConfig
    ): Pair<String, Boolean> {
        if (!config.autoCreateUsers) {
            throw SsoException.MissingClaimException("User not found and auto-create disabled")
        }
        val role = UserRole.fromString(config.defaultRole)
        val user = userRepo.create(claims.email, "", role, claims.displayName, null)
        logger.info("JIT provisioned user: email={}, role={}", claims.email, role)
        return user.id to true
    }

    private suspend fun validateDiscovery(issuerUrl: String) {
        try {
            discoveryClient.refresh(issuerUrl)
        } catch (e: Exception) {
            logger.warn("OIDC discovery validation failed for {}: {}", issuerUrl, e.message)
            throw SsoException.InvalidConfigException(
                "Cannot reach OIDC discovery endpoint at $issuerUrl"
            )
        }
    }

    private fun resolveSecret(request: SsoConfigRequest, existing: SsoConfig?): String {
        if (!request.clientSecret.isNullOrBlank()) {
            return encryptionService.encrypt(request.clientSecret)
        }
        return existing?.clientSecretEncrypted
            ?: throw SsoException.InvalidConfigException("client_secret is required")
    }

    private fun buildSsoConfig(request: SsoConfigRequest, encryptedSecret: String) =
        SsoConfig(
            enabled = request.enabled,
            issuerUrl = request.issuerUrl,
            clientId = request.clientId,
            clientSecretEncrypted = encryptedSecret,
            scopes = request.scopes,
            redirectUri = request.redirectUri,
            defaultRole = request.defaultRole,
            claimsMapping = request.claimsMapping ?: SsoConfig.defaultClaimsMapping(),
            autoCreateUsers = request.autoCreateUsers
        )

    private fun validateConfigRequest(request: SsoConfigRequest) {
        if (!request.issuerUrl.startsWith("https://")) {
            throw SsoException.InvalidConfigException("issuer_url must use HTTPS")
        }
        if (!request.scopes.contains("openid")) {
            throw SsoException.InvalidConfigException("scopes must include 'openid'")
        }
        if (request.clientId.isBlank()) {
            throw SsoException.InvalidConfigException("client_id is required")
        }
    }

    private fun extractProviderName(issuerUrl: String): String {
        return when {
            "google" in issuerUrl -> "Google"
            "microsoftonline" in issuerUrl -> "Microsoft"
            "okta" in issuerUrl -> "Okta"
            "auth0" in issuerUrl -> "Auth0"
            "keycloak" in issuerUrl -> "Keycloak"
            else -> "SSO"
        }
    }

    private data class UserClaims(val email: String, val displayName: String)
}
