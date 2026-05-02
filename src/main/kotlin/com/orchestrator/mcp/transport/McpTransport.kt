package com.orchestrator.mcp.transport

/**
 * Interface for MCP transport layer (stdio or HTTP).
 */
interface McpTransport {
    suspend fun start()
    suspend fun stop()
    suspend fun sendMessage(message: String)
    fun onMessage(handler: suspend (String) -> String?)
}
