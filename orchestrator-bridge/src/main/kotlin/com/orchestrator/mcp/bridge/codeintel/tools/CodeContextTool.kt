package com.orchestrator.mcp.bridge.codeintel.tools

import com.orchestrator.mcp.bridge.codeintel.query.QueryLayer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

/**
 * MCP tool: code_context — semantic search with FTS5 fallback.
 * When embeddings are available (Layer 2), uses cosine similarity.
 * Otherwise falls back to FTS5 keyword search.
 */
class CodeContextTool(private val queryLayer: QueryLayer) {

    fun register(server: Server) {
        server.addTool(
            name = "code_context",
            description = "Find code contextually related to a natural language query. " +
                "Uses semantic search (embeddings) when available, falls back to FTS5.",
            inputSchema = schema()
        ) { request -> handle(request.arguments) }
    }

    private fun handle(args: JsonObject?): CallToolResult {
        val query = args?.get("query")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'query' parameter")
        if (query.isBlank()) return errorResult("Query cannot be empty")

        val topK = args["top_k"]?.jsonPrimitive?.intOrNull ?: 5

        // Layer 2 not yet implemented — always use FTS5 fallback
        val results = queryLayer.searchFTS(query, null, null, topK.coerceIn(1, 50))
        val response = buildJsonObject {
            putJsonArray("results") {
                results.forEach { r ->
                    addJsonObject {
                        put("file", r.file)
                        put("summary", r.signature)
                        putJsonArray("symbols") { add(r.symbol) }
                        put("relevance", r.relevance)
                        put("search_method", "fts5")
                    }
                }
            }
            put("search_method", "fts5")
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }

    private fun errorResult(msg: String) =
        CallToolResult(content = listOf(TextContent(text = msg)), isError = true)
}

private fun schema() = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("query") { put("type", "string"); put("description", "Natural language query") }
        putJsonObject("top_k") { put("type", "integer"); put("description", "Number of results (1-50, default 5)") }
    },
    required = listOf("query")
)
