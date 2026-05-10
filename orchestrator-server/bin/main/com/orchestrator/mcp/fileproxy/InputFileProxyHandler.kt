package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse

/**
 * Interface for handling input file proxy operations.
 * Reads files from disk, encodes to base64 (or raw text), and forwards to upstream.
 */
interface InputFileProxyHandler {
    /**
     * Process an input proxy call: validate path, read file, encode/read, forward.
     * @param encodeBase64 if true, encode file as base64; if false, read as raw text
     */
    suspend fun processInputProxy(
        toolName: String,
        serverName: String,
        filePath: String,
        fileParamName: String,
        otherArgs: Map<String, Any?>,
        encodeBase64: Boolean = true
    ): ExecuteToolResponse
}
