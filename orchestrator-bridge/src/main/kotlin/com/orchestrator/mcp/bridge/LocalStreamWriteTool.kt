package com.orchestrator.mcp.bridge

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

/**
 * Local stream_write_file tool that writes directly to the local disk.
 * This runs on the bridge side (client machine) for local file operations.
 */
class LocalStreamWriteTool {

    private val logger = LoggerFactory.getLogger(LocalStreamWriteTool::class.java)

    fun register(server: Server) {
        server.addTool(
            name = "stream_write_file",
            description = "Write content directly to a file on disk without buffering",
            inputSchema = streamWriteSchema()
        ) { request ->
            handleWrite(request.arguments)
        }
    }

    private fun handleWrite(args: JsonObject?): CallToolResult {
        val filePath = args?.get("file_path")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'file_path' parameter")
        val content = args["content"]?.jsonPrimitive?.content
            ?: return errorResult("Missing 'content' parameter")
        val mode = args["mode"]?.jsonPrimitive?.content ?: "write"
        val encoding = args["encoding"]?.jsonPrimitive?.content ?: "utf-8"

        return try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            val charset = Charset.forName(encoding)
            when (mode) {
                "append" -> file.appendText(content, charset)
                else -> file.writeText(content, charset)
            }
            val bytes = file.length()
            logger.debug("Wrote $bytes bytes to $filePath (mode=$mode)")
            val result = buildJsonObject {
                put("file_path", filePath)
                put("bytes_written", bytes)
                put("mode", mode)
            }
            CallToolResult(content = listOf(TextContent(text = result.toString())))
        } catch (e: Exception) {
            logger.error("Write failed: ${e.message}")
            errorResult("Write failed: ${e.message}")
        }
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = message)), isError = true)
    }
}

private fun streamWriteSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put("description", "Absolute path to the output file")
        }
        putJsonObject("content") {
            put("type", "string")
            put("description", "Text content to write")
        }
        putJsonObject("mode") {
            put("type", "string")
            put("description", "Write mode: 'write' (overwrite) or 'append'")
        }
        putJsonObject("encoding") {
            put("type", "string")
            put("description", "Character encoding (default: utf-8)")
        }
    },
    required = listOf("file_path", "content")
)
