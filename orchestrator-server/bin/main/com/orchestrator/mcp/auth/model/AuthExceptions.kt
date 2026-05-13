package com.orchestrator.mcp.auth.model

/**
 * Sealed exception hierarchy for Auth module.
 * Each exception carries an error code and HTTP status for API responses.
 */
sealed class AuthException(
    message: String,
    val errorCode: String,
    val httpStatus: Int = 401
) : RuntimeException(message) {

    class InvalidCredentialsException :
        AuthException("Invalid username or password", "INVALID_CREDENTIALS", 401)

    class TokenExpiredException(tokenType: String = "session") :
        AuthException("Token has expired. Please login again", "TOKEN_EXPIRED", 401)

    class InvalidTokenException(reason: String = "Token is malformed") :
        AuthException(reason, "INVALID_TOKEN", 401)

    class AccountDisabledException(email: String) :
        AuthException("Account is disabled. Contact administrator", "ACCOUNT_DISABLED", 403)

    class AccountLockedException(minutesRemaining: Int) :
        AuthException("Account locked. Try again in $minutesRemaining minutes", "ACCOUNT_LOCKED", 423)

    class TokenRevokedException :
        AuthException("Bridge token has been revoked", "TOKEN_REVOKED", 401)

    class InsufficientRoleException(requiredRoles: List<String>) :
        AuthException(
            "Access denied. Required roles: ${requiredRoles.joinToString()}",
            "INSUFFICIENT_ROLE", 403
        )

    class BridgeTokenLimitException(maxDays: Int) :
        AuthException("Bridge token expiry cannot exceed $maxDays days", "BRIDGE_TOKEN_LIMIT", 400)
}
