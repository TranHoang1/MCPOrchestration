package com.orchestrator.mcp.kb

/**
 * Sealed exception hierarchy for KB Server.
 * Maps to MCP error responses with specific error codes.
 */
sealed class KbException(
    val errorCode: String,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

class KbNotFoundException(issueKey: String) :
    KbException("KB_NOT_FOUND", "No entry found for '$issueKey'")

class KbAccessDeniedException(reason: String) :
    KbException("KB_ACCESS_DENIED", "Access denied: $reason")

class KbRateLimitedException(limit: Int, window: String) :
    KbException("KB_RATE_LIMITED", "Rate limit exceeded: $limit per $window")

class KbValidationException(details: String) :
    KbException("KB_VALIDATION_ERROR", "Validation failed: $details")

class KbEncryptionException(cause: Throwable) :
    KbException("KB_ENCRYPTION_ERROR", "Encryption/decryption failure", cause)

class KbLlmTimeoutException(timeoutSeconds: Int) :
    KbException("KB_LLM_TIMEOUT", "LLM provider timed out after ${timeoutSeconds}s")

class KbQueueFullException(channel: String, capacity: Int) :
    KbException("KB_QUEUE_FULL", "$channel queue at capacity ($capacity)")

class KbInternalException(message: String, cause: Throwable? = null) :
    KbException("KB_INTERNAL_ERROR", message, cause)
