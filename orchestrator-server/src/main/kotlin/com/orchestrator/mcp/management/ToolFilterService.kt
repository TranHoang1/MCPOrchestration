package com.orchestrator.mcp.management

import com.orchestrator.mcp.core.config.ToolFilterConfig
import com.orchestrator.mcp.core.model.ToolDefinition

interface ToolFilterService {
    fun filterTools(
        tools: List<ToolDefinition>,
        serverName: String,
        toolFilter: ToolFilterConfig?
    ): List<ToolDefinition>
}

class ToolFilterServiceImpl : ToolFilterService {
    override fun filterTools(
        tools: List<ToolDefinition>,
        serverName: String,
        toolFilter: ToolFilterConfig?
    ): List<ToolDefinition> {
        if (toolFilter == null) return tools
        
        return when (toolFilter.mode.lowercase()) {
            "allowlist" -> {
                tools.filter { it.name in toolFilter.tools }
            }
            "blocklist" -> {
                tools.filter { it.name !in toolFilter.tools }
            }
            else -> tools
        }
    }
}
