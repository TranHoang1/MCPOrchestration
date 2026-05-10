package com.orchestrator.mcp.jira

import com.orchestrator.mcp.jira.exception.JiraValidationException

/**
 * Input validation for Jira REST Client API methods.
 * Validates before making HTTP calls to fail fast on invalid input.
 */
object JiraInputValidator {

    private val ISSUE_KEY_PATTERN = Regex("[A-Z][A-Z0-9_]+-\\d+")
    private val ALLOWED_EXPAND = setOf("changelog", "renderedFields", "transitions", "operations", "editmeta")
    private const val MAX_RESULTS_LIMIT = 100
    private const val MAX_JQL_LENGTH = 10_000

    fun validateSearchParams(jql: String, fields: List<String>, startAt: Int, maxResults: Int, correlationId: String) {
        if (jql.isBlank()) throw JiraValidationException("JQL must not be blank", correlationId)
        if (jql.length > MAX_JQL_LENGTH) throw JiraValidationException("JQL exceeds max length ($MAX_JQL_LENGTH)", correlationId)
        if (startAt < 0) throw JiraValidationException("startAt must be >= 0", correlationId)
        if (maxResults !in 1..MAX_RESULTS_LIMIT) throw JiraValidationException("maxResults must be 1..$MAX_RESULTS_LIMIT", correlationId)
    }

    fun validateIssueKey(key: String, correlationId: String) {
        if (!key.matches(ISSUE_KEY_PATTERN)) {
            throw JiraValidationException("Invalid issue key format: $key. Expected: PROJECT-123", correlationId)
        }
    }

    fun validateExpand(expand: List<String>, correlationId: String) {
        val invalid = expand.filter { it !in ALLOWED_EXPAND }
        if (invalid.isNotEmpty()) {
            throw JiraValidationException("Invalid expand values: $invalid. Allowed: $ALLOWED_EXPAND", correlationId)
        }
    }

    fun validateDownloadUrl(url: String, configuredBaseUrl: String, correlationId: String) {
        val downloadHost = runCatching { java.net.URI(url).host }.getOrNull()
        val configuredHost = runCatching { java.net.URI(configuredBaseUrl).host }.getOrNull()

        if (downloadHost == null || configuredHost == null) {
            throw JiraValidationException("Invalid URL format: $url", correlationId)
        }
        if (!downloadHost.equals(configuredHost, ignoreCase = true)) {
            throw JiraValidationException(
                "Download URL domain ($downloadHost) does not match configured Jira base URL ($configuredHost) — SSRF blocked",
                correlationId
            )
        }
    }
}
