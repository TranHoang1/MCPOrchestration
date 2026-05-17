package com.orchestrator.mcp.bridge.registry

import com.orchestrator.mcp.bridge.local.ToolDefinition

/**
 * Tool definition with source metadata.
 */
data class RegistryTool(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, Any?>? = null,
    val source: String, // "local" or "remote"
    val serverName: String? = null,
)

/**
 * Merges local and remote tool definitions into a single unified list.
 * Handles conflict resolution (local-first by default).
 */
class UnifiedRegistry(
    private val conflictResolution: String = "local-first",
) {
    private var merged: List<RegistryTool> = emptyList()
    private var localTools: List<RegistryTool> = emptyList()
    private var remoteTools: List<RegistryTool> = emptyList()

    /** Get all merged tools. */
    fun getAll(): List<RegistryTool> = merged.toList()

    /** Get tool definitions formatted for MCP tools/list response. */
    fun getToolDefinitions(): List<ToolDefinition> {
        return merged.map { ToolDefinition(it.name, it.description) }
    }

    /** Update local tools and re-merge. */
    fun setLocalTools(tools: List<ToolDefinition>, serverMap: Map<String, List<String>>? = null) {
        localTools = tools.map { t ->
            RegistryTool(
                name = t.name,
                description = t.description,
                source = "local",
                serverName = serverMap?.let { findServer(t.name, it) },
            )
        }
        rebuild()
    }

    /** Update remote tools and re-merge. */
    fun setRemoteTools(tools: List<ToolDefinition>) {
        remoteTools = tools.map { t ->
            RegistryTool(name = t.name, description = t.description, source = "remote")
        }
        rebuild()
    }

    /** Check if a tool exists in the registry. */
    fun has(toolName: String): Boolean = merged.any { it.name == toolName }

    /** Find a tool by name. */
    fun find(toolName: String): RegistryTool? = merged.firstOrNull { it.name == toolName }

    val localCount: Int get() = localTools.size
    val remoteCount: Int get() = remoteTools.size
    val totalCount: Int get() = merged.size

    private fun rebuild() {
        val map = mutableMapOf<String, RegistryTool>()

        if (conflictResolution == "local-first") {
            // Remote first (will be overwritten by local)
            remoteTools.forEach { map[it.name] = it }
            localTools.forEach { map[it.name] = it }
        } else {
            // Local first (will be overwritten by remote)
            localTools.forEach { map[it.name] = it }
            remoteTools.forEach { map[it.name] = it }
        }

        merged = map.values.toList()
    }

    private fun findServer(toolName: String, serverMap: Map<String, List<String>>): String? {
        for ((server, tools) in serverMap) {
            if (toolName in tools) return server
        }
        return null
    }
}
