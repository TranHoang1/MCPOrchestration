package com.orchestrator.mcp.auth.sso.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SSO configuration stored in database.
 * Defines IdP connection parameters for OAuth2/OIDC flow.
 */
@Serializable
data class SsoConfig(
    val enabled: Boolean = false,
    @SerialName("issuer_url") val issuerUrl: String = "",
    @SerialName("client_id") val clientId: String = "",
    @SerialName("client_secret_encrypted") val clientSecretEncrypted: String = "",
    val scopes: List<String> = listOf("openid", "profile", "email"),
    @SerialName("redirect_uri") val redirectUri: String = "",
    @SerialName("default_role") val defaultRole: String = "DEVELOPER",
    @SerialName("claims_mapping") val claimsMapping: Map<String, String> = defaultClaimsMapping(),
    @SerialName("auto_create_users") val autoCreateUsers: Boolean = true,
    @SerialName("updated_at") val updatedAt: String = ""
) {
    companion object {
        fun defaultClaimsMapping(): Map<String, String> = mapOf(
            "email" to "email",
            "name" to "displayName",
            "preferred_username" to "email"
        )
    }
}

/** Response for GET /api/auth/sso/authorize — redirect URL. */
@Serializable
data class SsoAuthorizeResponse(
    @SerialName("authorize_url") val authorizeUrl: String,
    val state: String
)

/** Result of SSO callback processing. */
@Serializable
data class SsoCallbackResult(
    val token: String,
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_new_user") val isNewUser: Boolean,
    @SerialName("redirect_url") val redirectUrl: String = "/portal"
)

/** Admin request to save SSO config. */
@Serializable
data class SsoConfigRequest(
    val enabled: Boolean,
    @SerialName("issuer_url") val issuerUrl: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String? = null,
    val scopes: List<String> = listOf("openid", "profile", "email"),
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("default_role") val defaultRole: String = "DEVELOPER",
    @SerialName("claims_mapping") val claimsMapping: Map<String, String>? = null,
    @SerialName("auto_create_users") val autoCreateUsers: Boolean = true
)

/** Admin response for GET config (secret masked). */
@Serializable
data class SsoConfigResponse(
    val enabled: Boolean,
    @SerialName("issuer_url") val issuerUrl: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("has_client_secret") val hasClientSecret: Boolean,
    val scopes: List<String>,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("default_role") val defaultRole: String,
    @SerialName("claims_mapping") val claimsMapping: Map<String, String>,
    @SerialName("auto_create_users") val autoCreateUsers: Boolean,
    @SerialName("updated_at") val updatedAt: String
)

/** Token response from IdP token endpoint. */
@Serializable
data class IdpTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("refresh_token") val refreshToken: String? = null
)

/** Provider info for login page — public-safe fields only. */
@Serializable
data class SsoProviderInfo(
    val name: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("authorize_url") val authorizeUrl: String
)
