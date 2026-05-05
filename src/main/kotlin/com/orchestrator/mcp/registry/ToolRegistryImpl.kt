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
    // Hidden tools (original tools replaced by proxy wrappers)
    private val hiddenTools = ConcurrentHashMap.newKeySet<String>()

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
        hiddenTools.remove(toolName)
    }

    override fun removeServerTools(serverName: String) {
        val toolNames = serverToolMap.remove(serverName) ?: return
        toolNames.forEach { toolMap.remove(it); hiddenTools.remove(it) }
    }

    override fun getAllTools(): List<ToolEntry> {
        return toolMap.values.filter { !hiddenTools.contains(it.name) }
    }

    override fun getToolsByServer(serverName: String): List<ToolEntry> {
        val toolNames = serverToolMap[serverName] ?: return emptyList()
        return toolNames.mapNotNull { toolMap[it] }
    }

    override fun getToolCount(): Int {
        return toolMap.size
    }

    override fun setHidden(toolName: String, hidden: Boolean) {
        if (hidden) hiddenTools.add(toolName) else hiddenTools.remove(toolName)
    }

    override fun isHidden(toolName: String): Boolean {
        return hiddenTools.contains(toolName)
    }
}
