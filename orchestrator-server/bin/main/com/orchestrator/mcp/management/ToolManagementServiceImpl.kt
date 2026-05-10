package com.orchestrator.mcp.management

import com.orchestrator.mcp.core.config.ConfigurationManager
import com.orchestrator.mcp.registry.ToolRegistry
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.Connection

class ToolManagementServiceImpl(
    private val dataSource: HikariDataSource,
    private val toolRegistry: ToolRegistry,
    private val configManager: ConfigurationManager
) : ToolManagementService {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun toggleTool(sessionId: String, request: ToggleToolRequest): ToggleToolResponse = withContext(Dispatchers.IO) {
        if (request.tool_name == null && request.server_name == null) {
            return@withContext ToggleToolResponse(false, "At least one of tool_name or server_name is required.")
        }

        try {
            dataSource.connection.use { conn ->
                val sql = """
                    INSERT INTO tool_toggle_state (session_id, tool_name, server_name, enabled)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (session_id, tool_name) WHERE tool_name IS NOT NULL DO UPDATE SET enabled = EXCLUDED.enabled
                    ON CONFLICT (session_id, server_name) WHERE server_name IS NOT NULL DO UPDATE SET enabled = EXCLUDED.enabled;
                """.trimIndent()
                
                // Note: The SQL above is simplified. In real PostgreSQL, ON CONFLICT with partial indexes requires matching the WHERE clause exactly.
                // We'll use two separate calls or a more robust UPSERT for simplicity here.
                
                if (request.tool_name != null) {
                    val upsertTool = """
                        INSERT INTO tool_toggle_state (session_id, tool_name, server_name, enabled)
                        VALUES (?, ?, NULL, ?)
                        ON CONFLICT (session_id, tool_name) WHERE tool_name IS NOT NULL DO UPDATE SET enabled = EXCLUDED.enabled;
                    """.trimIndent()
                    conn.prepareStatement(upsertTool).use { pstmt ->
                        pstmt.setString(1, sessionId)
                        pstmt.setString(2, request.tool_name)
                        pstmt.setBoolean(3, request.enabled)
                        pstmt.executeUpdate()
                    }
                } else if (request.server_name != null) {
                    val upsertServer = """
                        INSERT INTO tool_toggle_state (session_id, tool_name, server_name, enabled)
                        VALUES (?, NULL, ?, ?)
                        ON CONFLICT (session_id, server_name) WHERE server_name IS NOT NULL DO UPDATE SET enabled = EXCLUDED.enabled;
                    """.trimIndent()
                    conn.prepareStatement(upsertServer).use { pstmt ->
                        pstmt.setString(1, sessionId)
                        pstmt.setString(2, request.server_name)
                        pstmt.setBoolean(3, request.enabled)
                        pstmt.executeUpdate()
                    }
                }
            }
            
            val target = request.tool_name ?: request.server_name
            val action = if (request.enabled) "enabled" else "disabled"
            ToggleToolResponse(true, "Successfully $action $target", 1, request.tool_name)
        } catch (e: Exception) {
            log.error("Failed to toggle tool state", e)
            ToggleToolResponse(false, "Failed to persist toggle state: ${e.message}")
        }
    }

    override suspend fun resetTools(sessionId: String, request: ResetToolsRequest): ResetToolsResponse = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val sql = if (request.server_name != null) {
                    "DELETE FROM tool_toggle_state WHERE session_id = ? AND server_name = ?"
                } else {
                    "DELETE FROM tool_toggle_state WHERE session_id = ?"
                }
                
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, sessionId)
                    if (request.server_name != null) pstmt.setString(2, request.server_name)
                    val affected = pstmt.executeUpdate()
                    ResetToolsResponse(true, "Reset $affected tool toggle states.", affected, false)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to reset tool states", e)
            ResetToolsResponse(false, "Failed to reset states: ${e.message}")
        }
    }

    override suspend fun manageAutoApprove(request: ManageAutoApproveRequest): ManageAutoApproveResponse {
        val config = configManager.getConfig()
        val servers = config.orchestrator.upstreamServers.toMutableList()
        val affectedNames = mutableListOf<String>()

        var updated = false
        for (i in servers.indices) {
            val server = servers[i]
            if (request.server_name != null && server.name == request.server_name) {
                // If it's a server-wide auto-approve, this logic depends on how we define it.
                // For now, let's say it adds all current tools of that server to the list.
                val tools = toolRegistry.getToolsByServer(server.name)
                val newList = if (request.auto_approve) {
                    (server.autoApprove + tools.map { it.name }).distinct()
                } else {
                    emptyList()
                }
                servers[i] = server.copy(autoApprove = newList)
                affectedNames.addAll(newList)
                updated = true
            } else if (request.tool_name != null) {
                val toolEntry = toolRegistry.lookupTool(request.tool_name)
                if (toolEntry != null && toolEntry.serverName == server.name) {
                    val newList = if (request.auto_approve) {
                        (server.autoApprove + request.tool_name).distinct()
                    } else {
                        server.autoApprove.filter { it != request.tool_name }
                    }
                    servers[i] = server.copy(autoApprove = newList)
                    affectedNames.add(request.tool_name)
                    updated = true
                }
            }
        }

        if (!updated) {
            return ManageAutoApproveResponse(false, "No matching tool or server found in registry.")
        }

        return try {
            val newConfig = config.copy(
                orchestrator = config.orchestrator.copy(upstreamServers = servers)
            )
            configManager.saveConfig(newConfig)
            ManageAutoApproveResponse(true, "Successfully updated auto-approve list.", true, false, affectedNames)
        } catch (e: Exception) {
            ManageAutoApproveResponse(false, "Failed to save configuration: ${e.message}")
        }
    }

    override suspend fun isToolDisabled(toolName: String, serverName: String, sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT 1 FROM tool_toggle_state 
                    WHERE session_id = ? AND enabled = false 
                    AND (tool_name = ? OR server_name = ?)
                """.trimIndent()
                
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, sessionId)
                    pstmt.setString(2, toolName)
                    pstmt.setString(3, serverName)
                    pstmt.executeQuery().next()
                }
            }
        } catch (e: Exception) {
            log.error("Error checking tool toggle state", e)
            false // Fail-open: assume enabled if DB error
        }
    }
}
