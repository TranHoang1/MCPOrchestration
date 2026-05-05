package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse

/**
 * Interface for handling input file proxy operations.
 * Reads files from disk, encodes to base64, and forwards to upstream.
 */
interface InputFileProxyHandler {
    /**
     * Process an input proxy call: validate path, read file, encode, forward.
     */
    suspend fun processInputProxy(
        toolName: String,
        serverName: String,
        filePath: String,
        fileParamName: String,
        otherArgs: Map<String, Any?>
    ): ExecuteToolResponse
}
