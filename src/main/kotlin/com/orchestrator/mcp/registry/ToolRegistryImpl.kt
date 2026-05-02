package com.orchestrator.mcp.registry

import com.orchestrator.mcp.model.ToolEntry
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory tool registry using ConcurrentHashMap.
 */
class ToolRegistryImpl : ToolRegistry {

    // Key: tool_name, Value: ToolEntry
    private val toolMap = ConcurrentHashMap<String, ToolEntry>()
    // Key: server_name, Value: set of tool_names
    private val serverToolMap = ConcurrentHashMap<String, MutableSet<String>>()

    override fun lookupTool(toolName: String): ToolEntry? {
        return toolMap[toolName]
    }

    override fun registerTool(entry: ToolEntry) {
        toolMap[entry.name] = entry
        serverToolMap.getOrPut(entry.serverName) { ConcurrentHashMap.newKeySet() }.add(entry.name)
    }

    override fun removeTool(toolName: String) {
        val entry = toolMap.remove(toolName)
        if (entry != null) {
            serverToolMap[entry.serverName]?.remove(toolName)
        }
    }

    override fun removeServerTools(serverName: String) {
        val toolNames = serverToolMap.remove(serverName) ?: return
        toolNames.forEach { toolMap.remove(it) }
    }

    override fun getAllTools(): List<ToolEntry> {
        return toolMap.values.toList()
    }

    override fun getToolsByServer(serverName: String): List<ToolEntry> {
        val toolNames = serverToolMap[serverName] ?: return emptyList()
        return toolNames.mapNotNull { toolMap[it] }
    }

    override fun getToolCount(): Int {
        return toolMap.size
    }
}
