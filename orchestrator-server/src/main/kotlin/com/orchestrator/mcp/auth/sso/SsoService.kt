package com.orchestrator.mcp.auth.sso

import com.orchestrator.mcp.auth.sso.model.SsoAuthorizeResponse
import com.orchestrator.mcp.auth.sso.model.SsoCallbackResult
import com.orchestrator.mcp.auth.sso.model.SsoConfig
import com.orchestrator.mcp.auth.sso.model.SsoConfigRequest
import com.orchestrator.mcp.auth.sso.model.SsoProviderInfo

/**
 * SSO service interface for OAuth2/OIDC integration.
 * Handles authorization flow, token exchange, and JIT user provisioning.
 */
interface SsoService {

    /** List available SSO providers for the login page. */
    suspend fun listProviders(): List<SsoProviderInfo>

    /** Build IdP authorization URL with PKCE challenge and state. */
    suspend fun initAuthorize(): SsoAuthorizeResponse

    /** Handle IdP callback: validate state, exchange code, provision user, issue JWT. */
    suspend fun handleCallback(code: String, state: String): SsoCallbackResult

    /** Get current SSO configuration (for admin display). */
    suspend fun getConfig(): SsoConfig?

    /** Save SSO configuration (admin only). */
    suspend fun saveConfig(request: SsoConfigRequest): SsoConfig
}
