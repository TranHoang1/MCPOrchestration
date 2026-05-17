package com.orchestrator.mcp.bridge.codeintel.tools

import com.orchestrator.mcp.bridge.codeintel.query.QueryLayer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*

/**
 * MCP tool: code_symbols — list all symbols in a specific file.
 */
class CodeSymbolsTool(private val queryLayer: QueryLayer) {

    fun register(server: Server) {
        server.addTool(
            name = "code_symbols",
            description = "List all code symbols (classes, functions, properties) in a specific file.",
            inputSchema = schema()
        ) { request -> handle(request.arguments) }
    }

    private fun handle(args: JsonObject?): CallToolResult {
        val filePath = args?.get("file_path")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'file_path' parameter")

        val symbols = queryLayer.getSymbolsByFile(filePath)
        if (symbols.isEmpty()) {
            return errorResult("FILE_NOT_FOUND: File not in index")
        }

        val response = buildJsonObject {
            put("file", filePath)
            putJsonArray("symbols") {
                symbols.forEach { s ->
                    addJsonObject {
                        put("name", s.name)
                        put("kind", s.kind)
                        put("signature", s.signature)
                        put("line_start", s.lineStart)
                        s.lineEnd?.let { put("line_end", it) }
                        s.visibility?.let { put("visibility", it) }
                    }
                }
            }
            put("symbol_count", symbols.size)
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }

    private fun errorResult(msg: String) =
        CallToolResult(content = listOf(TextContent(text = msg)), isError = true)
}

private fun schema() = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put("description", "Relative file path from workspace root")
        }
    },
    required = listOf("file_path")
)
