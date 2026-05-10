package com.orchestrator.mcp.jira.exception

/**
 * Sealed base class for all Jira client exceptions.
 * Enables exhaustive `when` matching for structured error handling.
 */
sealed class JiraClientException(
    override val message: String,
    val correlationId: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

/** 401/403 — Invalid credentials or insufficient permissions. */
class JiraAuthException(
    message: String,
    val statusCode: Int,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)

/** 404 — Issue or attachment does not exist. */
class JiraNotFoundException(
    message: String,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)

/** 429 — Rate limit exceeded. */
class JiraRateLimitException(
    message: String,
    val retryAfterSeconds: Long,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)

/** 5xx — Jira server error. */
class JiraServerException(
    message: String,
    val statusCode: Int,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)

/** Connection or socket timeout. */
class JiraTimeoutException(
    message: String,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)

/** Input validation failure (blank JQL, invalid key, SSRF). */
class JiraValidationException(
    message: String,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)

/** All retry attempts exhausted. */
class RetryExhaustedException(
    message: String,
    val attempts: Int,
    correlationId: String,
    cause: Throwable? = null
) : JiraClientException(message, correlationId, cause)
