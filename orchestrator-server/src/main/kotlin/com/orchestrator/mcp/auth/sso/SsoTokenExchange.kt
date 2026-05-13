package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.IdpTokenResponse
import com.orchestrator.mcp.auth.sso.model.SsoConfig
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Handles OAuth2 token exchange with the Identity Provider.
 * Exchanges authorization code + PKCE verifier for tokens.
 */
class SsoTokenExchange(private val httpClient: HttpClient) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Exchange authorization code for tokens at IdP token endpoint. */
    suspend fun exchangeCode(
        config: SsoConfig,
        code: String,
        codeVerifier: String,
        clientSecret: String
    ): IdpTokenResponse {
        val tokenUrl = resolveTokenEndpoint(config.issuerUrl)
        logger.debug("Exchanging code at: {}", tokenUrl)

        val response = httpClient.submitForm(
            url = tokenUrl,
            formParameters = buildExchangeParams(config, code, codeVerifier, clientSecret)
        )
        if (response.status != HttpStatusCode.OK) {
            val body = response.bodyAsText()
            logger.error("Token exchange failed: status={}, body={}", response.status, body)
            throw SsoException.TokenExchangeFailedException(response.status.value, body)
        }
        return json.decodeFromString<IdpTokenResponse>(response.bodyAsText())
    }

    /** Parse ID token claims (JWT payload without signature verification for user info). */
    fun parseIdTokenClaims(idToken: String): Map<String, String> {
        val parts = idToken.split(".")
        if (parts.size != 3) throw SsoException.InvalidIdTokenException("Malformed JWT")
        val payload = String(
            java.util.Base64.getUrlDecoder().decode(parts[1]),
            Charsets.UTF_8
        )
        val jsonObj = json.decodeFromString<JsonObject>(payload)
        return jsonObj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
    }

    private fun buildExchangeParams(
        config: SsoConfig, code: String, codeVerifier: String, clientSecret: String
    ): Parameters = parameters {
        append("grant_type", "authorization_code")
        append("code", code)
        append("redirect_uri", config.redirectUri)
        append("client_id", config.clientId)
        append("client_secret", clientSecret)
        append("code_verifier", codeVerifier)
    }

    private fun resolveTokenEndpoint(issuerUrl: String): String {
        val base = issuerUrl.trimEnd('/')
        return "$base/protocol/openid-connect/token"
    }
}
