package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse

/**
 * Interface for handling output file proxy operations.
 * Saves upstream file responses to agent-specified output paths.
 */
interface OutputFileProxyHandler {
    /**
     * Process output proxy: save upstream response file to output_path.
     */
    suspend fun processOutputProxy(
        upstreamResponse: ExecuteToolResponse,
        outputPath: String
    ): ExecuteToolResponse

    /**
     * Check if upstream response contains file content.
     */
    fun containsFileContent(response: ExecuteToolResponse): Boolean
}
