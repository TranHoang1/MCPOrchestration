package com.orchestrator.mcp.bridge.codeintel.tools

import com.orchestrator.mcp.bridge.codeintel.query.QueryLayer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

/**
 * MCP tool: code_search — FTS5 keyword search across indexed symbols.
 */
class CodeSearchTool(private val queryLayer: QueryLayer) {

    fun register(server: Server) {
        server.addTool(
            name = "code_search",
            description = "Search code symbols by keyword using FTS5 full-text search. " +
                "Returns ranked results with file path, symbol name, kind, and signature.",
            inputSchema = schema()
        ) { request -> handle(request.arguments) }
    }

    private fun handle(args: JsonObject?): CallToolResult {
        val query = args?.get("query")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'query' parameter")
        if (query.isBlank()) return errorResult("Query cannot be empty")

        val language = args["language"]?.jsonPrimitive?.contentOrNull
        val module = args["module"]?.jsonPrimitive?.contentOrNull
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 20

        val results = queryLayer.searchFTS(query, language, module, limit.coerceIn(1, 100))
        val response = buildJsonObject {
            putJsonArray("results") {
                results.forEach { r ->
                    addJsonObject {
                        put("file", r.file)
                        put("symbol", r.symbol)
                        put("kind", r.kind)
                        put("signature", r.signature)
                        put("line", r.line)
                        put("module", r.module)
                        put("relevance", r.relevance)
                    }
                }
            }
            put("total_matches", results.size)
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }

    private fun errorResult(msg: String) =
        CallToolResult(content = listOf(TextContent(text = msg)), isError = true)
}

private fun schema() = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("query") { put("type", "string"); put("description", "Search query") }
        putJsonObject("language") { put("type", "string"); put("description", "Filter by language") }
        putJsonObject("module") { put("type", "string"); put("description", "Filter by module") }
        putJsonObject("limit") { put("type", "integer"); put("description", "Max results (1-100, default 20)") }
    },
    required = listOf("query")
)
