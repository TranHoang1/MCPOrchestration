package com.orchestrator.mcp.execution.model

import com.orchestrator.mcp.protocol.model.ContentItem
import com.orchestrator.mcp.protocol.model.ToolCallMeta

/**
 * Response from tool execution, containing upstream content and metadata.
 */
data class ExecuteToolResponse(
    val content: List<ContentItem>,
    val meta: ToolCallMeta? = null
)
