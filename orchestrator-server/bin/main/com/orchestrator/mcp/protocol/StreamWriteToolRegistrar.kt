package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.fileproxy.FilePathValidator
import com.orchestrator.mcp.core.model.McpOrchestratorException
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Registers the stream_write_file built-in tool.
 * Writes content directly to disk without buffering in RAM.
 * Supports append mode for large file generation in loops.
 */
object StreamWriteToolRegistrar {

    private val logger = LoggerFactory.getLogger(StreamWriteToolRegistrar::class.java)
    private val ioDispatcher = java.util.concurrent.Executors
        .newFixedThreadPool(2).asCoroutineDispatcher()

    fun register(server: Server) {
        server.addTool(
            name = "stream_write_file",
            description = streamWriteDescription(),
            inputSchema = streamWriteSchema()
        ) { request ->
            handleStreamWrite(request.arguments)
        }
    }

    suspend fun handleCall(arguments: JsonObject?): CallToolResult {
        return handleStreamWrite(arguments)
    }

    private suspend fun handleStreamWrite(arguments: JsonObject?): CallToolResult {
        return try {
            val rawPath = arguments?.get("file_path")
                ?.jsonPrimitive?.content
                ?: return errorResult("INVALID_PARAMS", "file_path is required")

            val content = arguments["content"]?.jsonPrimitive?.content ?: ""
            val mode = arguments["mode"]?.jsonPrimitive?.content ?: "write"

            if (mode !in listOf("write", "append", "create")) {
                return errorResult("INVALID_PARAMS", "mode must be 'write', 'append', or 'create'")
            }

            val filePath = FilePathValidator.resolvePath(rawPath)
            FilePathValidator.validateOutputPath(filePath)

            val path = Path.of(filePath)
            val fileExists = withContext(ioDispatcher) { Files.exists(path) }
            val fileSizeBefore = if (fileExists) {
                withContext(ioDispatcher) { Files.size(path) }
            } else 0L

            // File exists + no content → no-op (nothing to do)
            if (fileExists && content.isEmpty()) {
                return buildNoOpResult(filePath, fileSizeBefore)
            }

            val bytesWritten = writeToFile(filePath, content, mode, fileExists)
            val totalSize = withContext(ioDispatcher) { Files.size(path) }

            val response = buildJsonObject {
                put("file_path", JsonPrimitive(filePath))
                put("bytes_written", JsonPrimitive(bytesWritten))
                put("total_size", JsonPrimitive(totalSize))
                put("file_size_before", JsonPrimitive(fileSizeBefore))
                put("mode", JsonPrimitive(mode))
            }

            logger.info("[StreamWrite] {} {} bytes to {}", mode, bytesWritten, filePath)
            CallToolResult(content = listOf(TextContent(text = response.toString())))
        } catch (e: McpOrchestratorException) {
            errorResult(e.errorCode, e.message)
        } catch (e: Exception) {
            errorResult("WRITE_FAILED", e.message ?: "Unknown write error")
        }
    }

    private suspend fun writeToFile(
        filePath: String,
        content: String,
        mode: String,
        fileExists: Boolean
    ): Long {
        return withContext(ioDispatcher) {
            val path = Path.of(filePath)
            path.parent?.let { Files.createDirectories(it) }

            val options = when {
                !fileExists -> arrayOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                mode == "append" -> arrayOf(StandardOpenOption.APPEND)
                else -> arrayOf(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            }
            Files.newBufferedWriter(path, Charsets.UTF_8, *options).use { writer ->
                writer.write(content)
                writer.flush()
            }
            content.toByteArray(Charsets.UTF_8).size.toLong()
        }
    }

    private fun buildNoOpResult(filePath: String, fileSize: Long): CallToolResult {
        val response = buildJsonObject {
            put("file_path", JsonPrimitive(filePath))
            put("bytes_written", JsonPrimitive(0L))
            put("total_size", JsonPrimitive(fileSize))
            put("file_size_before", JsonPrimitive(fileSize))
            put("mode", JsonPrimitive("no-op"))
            put("message", JsonPrimitive("File already exists and no content provided"))
        }
        return CallToolResult(content = listOf(TextContent(text = response.toString())))
    }

    private fun errorResult(code: String, message: String): CallToolResult {
        val errorJson = buildJsonObject {
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()
        return CallToolResult(
            content = listOf(TextContent(text = errorJson)),
            isError = true
        )
    }
}

internal fun streamWriteDescription(): String =
    "Write content directly to a file on disk without buffering. " +
        "Supports absolute and relative paths (relative resolved from workspace root). " +
        "Modes: 'write' (overwrite/create), 'append' (add to end), 'create' (fail if file exists). " +
        "If file does not exist, it will be created automatically. " +
        "If file already exists and no content is provided, no changes are made (no-op)."

internal fun streamWriteSchema(): ToolSchema = ToolSchema(
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
            put("default", "write")
        }
        putJsonObject("encoding") {
            put("type", "string")
            put("description", "Character encoding (default: utf-8)")
            put("default", "utf-8")
        }
    },
    required = listOf("file_path")
)
