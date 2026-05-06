package com.orchestrator.mcp.client.upstream

import com.orchestrator.mcp.client.upstream.model.ServerState
import com.orchestrator.mcp.client.upstream.model.UpstreamServerInfo

/**
 * Interface for managing connections to upstream MCP servers.
 */
interface UpstreamServerManager {
    suspend fun connectAll()
    suspend fun connect(serverName: String)
    suspend fun disconnect(serverName: String)
    fun getConnection(serverName: String): McpConnection?
    fun getServerState(serverName: String): ServerState
    fun getAllServerStates(): Map<String, UpstreamServerInfo>
}
