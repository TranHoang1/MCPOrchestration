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
            description = "Write content directly to a file on disk without buffering. " +
                "Supports absolute and relative paths (relative resolved from workspace root). " +
                "Modes: 'write' (overwrite/create), 'append' (add to end), 'create' (fail if file exists). " +
                "If file does not exist, it will be created automatically. " +
                "If file already exists and no content is provided, no changes are made (no-op).",
            inputSchema = streamWriteSchema()
        ) { request ->
            handleWrite(request.arguments)
        }
    }

    private fun handleWrite(args: JsonObject?): CallToolResult {
        val rawPath = args?.get("file_path")?.jsonPrimitive?.content
            ?: return errorResult("Missing 'file_path' parameter")
        val content = args["content"]?.jsonPrimitive?.content ?: ""
        val mode = args["mode"]?.jsonPrimitive?.content ?: "write"
        val encoding = args["encoding"]?.jsonPrimitive?.content ?: "utf-8"

        if (mode !in listOf("write", "append", "create")) {
            return errorResult("mode must be 'write', 'append', or 'create'")
        }

        val filePath = WorkspaceContext.resolvePath(rawPath)

        return try {
            val file = File(filePath)
            val fileExists = file.exists()
            val fileSizeBefore = if (fileExists) file.length() else 0L

            // File exists + no content → no-op
            if (fileExists && content.isEmpty()) {
                return buildNoOpResult(filePath, fileSizeBefore)
            }

            file.parentFile?.mkdirs()
            val charset = Charset.forName(encoding)

            when {
                !fileExists -> file.writeText(content, charset)
                mode == "append" -> file.appendText(content, charset)
                else -> file.writeText(content, charset)
            }

            val totalSize = file.length()
            val bytesWritten = totalSize - fileSizeBefore
            logger.debug("Wrote $bytesWritten bytes to $filePath (mode=$mode)")
            val result = buildJsonObject {
                put("file_path", filePath)
                put("bytes_written", bytesWritten)
                put("total_size", totalSize)
                put("file_size_before", fileSizeBefore)
                put("mode", mode)
            }
            CallToolResult(content = listOf(TextContent(text = result.toString())))
        } catch (e: Exception) {
            logger.error("Write failed: ${e.message}")
            errorResult("Write failed: ${e.message}")
        }
    }

    private fun buildNoOpResult(filePath: String, fileSize: Long): CallToolResult {
        val result = buildJsonObject {
            put("file_path", filePath)
            put("bytes_written", 0L)
            put("total_size", fileSize)
            put("file_size_before", fileSize)
            put("mode", "no-op")
            put("message", "File already exists and no content provided")
        }
        return CallToolResult(content = listOf(TextContent(text = result.toString())))
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = message)), isError = true)
    }
}

private fun streamWriteSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put(
                "description",
                "Path to the output file. Supports absolute or relative path (resolved from workspace root)"
            )
        }
        putJsonObject("content") {
            put("type", "string")
            put(
                "description",
                "Text content to write. Optional — if omitted or empty, creates an empty file " +
                    "(or no-op if file already exists)."
            )
        }
        putJsonObject("mode") {
            put("type", "string")
            put(
                "description",
                "write, append, or create. 'create' fails if file already exists. Default: 'write'"
            )
        }
        putJsonObject("encoding") {
            put("type", "string")
            put("description", "Character encoding (default: utf-8)")
        }
    },
    required = listOf("file_path")
)
