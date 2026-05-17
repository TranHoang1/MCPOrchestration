package com.orchestrator.mcp.bridge.codeintel.tools

import com.orchestrator.mcp.bridge.codeintel.query.QueryLayer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

/**
 * MCP tool: code_index_status — report current index health and layer availability.
 */
class CodeIndexStatusTool(
    private val queryLayer: QueryLayer,
    private val statusProvider: () -> Pair<String, Int> // (status, progress)
) {

    fun register(server: Server) {
        server.addTool(
            name = "code_index_status",
            description = "Get current status of the code intelligence index including " +
                "file/symbol counts, layer availability, and indexing progress.",
            inputSchema = schema()
        ) { request -> handle() }
    }

    private fun handle(): CallToolResult {
        val (status, progress) = statusProvider()
        val stats = queryLayer.getStats(status, progress)

        val response = buildJsonObject {
            put("status", stats.status)
            put("files_indexed", stats.filesIndexed)
            put("symbols_indexed", stats.symbolsIndexed)
            put("modules_detected", stats.modulesDetected)
            stats.lastIndexed?.let { put("last_indexed", it) }
            put("indexing_progress", stats.indexingProgress)
            putJsonObject("layers") {
                put("fts5", stats.layers.fts5)
                put("embeddings", stats.layers.embeddings)
                put("summaries", stats.layers.summaries)
            }
            put("db_size_mb", stats.dbSizeMb)
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }
}

private fun schema() = ToolSchema(
    properties = buildJsonObject {},
    required = emptyList()
)
