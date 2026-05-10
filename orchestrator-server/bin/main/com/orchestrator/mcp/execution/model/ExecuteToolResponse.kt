package com.orchestrator.mcp.execution.model

import kotlinx.serialization.Serializable

/**
 * Response from tool execution, containing upstream content and metadata.
 */
data class ExecuteToolResponse(
    val content: List<ExecutionContentItem>,
    val meta: ExecutionMeta? = null
)

/**
 * A single content item from upstream tool execution.
 */
@Serializable
data class ExecutionContentItem(
    val type: String = "text",
    val text: String
)

/**
 * Metadata about the tool execution (server, duration).
 */
@Serializable
data class ExecutionMeta(
    val upstreamServer: String? = null,
    val executionTimeMs: Long? = null
)
