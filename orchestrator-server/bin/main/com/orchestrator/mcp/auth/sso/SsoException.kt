package com.orchestrator.mcp.auth.sso

/**
 * SSO-specific exceptions with HTTP status codes and error codes.
 */
sealed class SsoException(
    message: String,
    val errorCode: String,
    val httpStatus: Int = 400
) : RuntimeException(message) {

    class SsoNotConfiguredException :
        SsoException("SSO is not configured", "SSO_NOT_CONFIGURED", 503)

    class SsoDisabledException :
        SsoException("SSO is disabled", "SSO_DISABLED", 503)

    class InvalidStateException :
        SsoException("Invalid or expired state parameter", "INVALID_STATE", 400)

    class TokenExchangeFailedException(status: Int, body: String) :
        SsoException("Token exchange failed: HTTP $status — $body", "TOKEN_EXCHANGE_FAILED", 502)

    class InvalidIdTokenException(reason: String) :
        SsoException("Invalid ID token: $reason", "INVALID_ID_TOKEN", 502)

    class MissingClaimException(claim: String) :
        SsoException("Required claim '$claim' missing from IdP response", "MISSING_CLAIM", 502)

    class InvalidConfigException(reason: String) :
        SsoException("Invalid SSO config: $reason", "INVALID_SSO_CONFIG", 400)
}
