package com.orchestrator.mcp.credentials.model

/**
 * Result of credential placeholder resolution.
 * Contains the fully-resolved server config and a unique pool key.
 */
data class ResolvedConfig(
    val command: String,
    val args: List<String>,
    val env: Map<String, String>,
    val poolKey: String
)
