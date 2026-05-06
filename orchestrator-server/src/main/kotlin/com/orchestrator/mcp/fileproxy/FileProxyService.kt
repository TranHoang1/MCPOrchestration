package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/**
 * Main orchestration interface for file proxy operations.
 * Coordinates detection, wrapping, and I/O handling.
 */
interface FileProxyService {
    /** Initialize file proxy: run detection, generate wrappers */
    suspend fun initialize(sessionId: UUID)

    /** Handle a proxy tool call (input, output, or both) */
    suspend fun handleProxyCall(
        toolName: String,
        serverName: String,
        arguments: JsonObject,
        transportMode: String
    ): ExecuteToolResponse

    /** Check if a tool name is a proxy wrapper */
    fun isProxyTool(toolName: String): Boolean

    /** Re-detect and regenerate wrappers for a specific server */
    suspend fun redetectServer(serverName: String)
}
