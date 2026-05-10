package com.orchestrator.mcp.jira.retry

/**
 * Retry handler interface for transient failure recovery.
 */
interface RetryHandler {

    /**
     * Execute block with retry logic.
     * @param context Descriptive context for logging
     * @param block The suspend function to execute and potentially retry
     * @return Result of successful block execution
     * @throws JiraClientException if all retries exhausted or non-retryable error
     */
    suspend fun <T> withRetry(context: String, block: suspend () -> T): T
}
