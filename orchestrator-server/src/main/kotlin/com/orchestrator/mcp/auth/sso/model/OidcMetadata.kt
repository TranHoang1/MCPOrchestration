package com.orchestrator.mcp.auth.sso.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OIDC Discovery metadata from .well-known/openid-configuration.
 * Contains IdP endpoint URLs discovered automatically.
 */
@Serializable
data class OidcMetadata(
    val issuer: String = "",
    @SerialName("authorization_endpoint") val authorizationEndpoint: String = "",
    @SerialName("token_endpoint") val tokenEndpoint: String = "",
    @SerialName("userinfo_endpoint") val userinfoEndpoint: String = "",
    @SerialName("jwks_uri") val jwksUri: String = "",
    @SerialName("end_session_endpoint") val endSessionEndpoint: String = "",
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
    @SerialName("response_types_supported") val responseTypesSupported: List<String> = emptyList(),
    @SerialName("grant_types_supported") val grantTypesSupported: List<String> = emptyList(),
    @SerialName("code_challenge_methods_supported")
    val codeChallengeMethodsSupported: List<String> = emptyList()
)
