package com.orchestrator.mcp.bridge.codeintel.tools

import com.orchestrator.mcp.bridge.codeintel.query.QueryLayer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

/**
 * MCP tool: code_modules — list all detected project modules with stats.
 */
class CodeModulesTool(private val queryLayer: QueryLayer) {

    fun register(server: Server) {
        server.addTool(
            name = "code_modules",
            description = "List all detected project modules with file count, symbol count, and AI summary.",
            inputSchema = schema()
        ) { request -> handle() }
    }

    private fun handle(): CallToolResult {
        val modules = queryLayer.getModules()
        val response = buildJsonObject {
            putJsonArray("modules") {
                modules.forEach { m ->
                    addJsonObject {
                        put("name", m.name)
                        put("path", m.path)
                        put("file_count", m.fileCount)
                        put("symbol_count", m.symbolCount)
                        m.summary?.let { put("summary", it) }
                    }
                }
            }
            put("total_modules", modules.size)
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }
}

private fun schema() = ToolSchema(
    properties = buildJsonObject {},
    required = emptyList()
)
