package com.orchestrator.mcp.brmasking.model

/**
 * Sealed exception hierarchy for BR masking errors.
 */
sealed class BrMaskingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class InvalidKeyException(message: String) :
        BrMaskingException(message)

    class DecryptionException(message: String, cause: Throwable? = null) :
        BrMaskingException(message, cause)

    class UnauthorizedAccessException(role: String) :
        BrMaskingException("Unauthorized unmask attempt by role: $role")

    class LlmFailureException(message: String, cause: Throwable? = null) :
        BrMaskingException(message, cause)
}
