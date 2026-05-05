package com.orchestrator.mcp.registry

import com.orchestrator.mcp.model.ToolEntry

/**
 * Interface for in-memory tool-to-server mapping.
 * Thread-safe for concurrent access.
 */
interface ToolRegistry {
    fun lookupTool(toolName: String): ToolEntry?
    fun registerTool(entry: ToolEntry)
    fun removeTool(toolName: String)
    fun removeServerTools(serverName: String)
    fun getAllTools(): List<ToolEntry>
    fun getToolsByServer(serverName: String): List<ToolEntry>
    fun getToolCount(): Int
    fun setHidden(toolName: String, hidden: Boolean)
    fun isHidden(toolName: String): Boolean
}
