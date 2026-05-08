package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.fileproxy.FilePathValidator
import com.orchestrator.mcp.core.model.FileWriteException
import com.orchestrator.mcp.core.model.McpOrchestratorException
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
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
    private val json = Json { encodeDefaults = true }
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

            val mode = arguments["mode"]
                ?.jsonPrimitive?.content ?: "write"

            if (mode !in listOf("write", "append", "create")) {
                return errorResult("INVALID_PARAMS", "mode must be 'write', 'append', or 'create'")
            }

            val content = if (mode == "create") {
                arguments["content"]?.jsonPrimitive?.content ?: ""
            } else {
                arguments["content"]?.jsonPrimitive?.content
                    ?: return errorResult("INVALID_PARAMS", "content is required")
            }

            val filePath = FilePathValidator.resolvePath(rawPath)
            FilePathValidator.validateOutputPath(filePath)

            val fileSizeBefore = withContext(ioDispatcher) {
                val p = Path.of(filePath)
                if (Files.exists(p)) Files.size(p) else 0L
            }

            val bytesWritten = writeToFile(filePath, content, mode)
            val totalSize = withContext(ioDispatcher) { Files.size(Path.of(filePath)) }

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
            errorResult(e.errorCode, e.message ?: "Write failed")
        } catch (e: Exception) {
            errorResult("WRITE_FAILED", e.message ?: "Unknown write error")
        }
    }

    private suspend fun writeToFile(filePath: String, content: String, mode: String): Long {
        return withContext(ioDispatcher) {
            val path = Path.of(filePath)

            if (mode == "create" && Files.exists(path)) {
                throw FileWriteException(
                    "File already exists: $filePath. Use mode='write' to overwrite or mode='append' to add content."
                )
            }

            val options = when (mode) {
                "append" -> arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                "create" -> arrayOf(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                else -> arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            }
            Files.newBufferedWriter(path, Charsets.UTF_8, *options).use { writer ->
                writer.write(content)
                writer.flush()
            }
            content.toByteArray(Charsets.UTF_8).size.toLong()
        }
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
        "Supports both absolute and relative paths (relative paths resolved from workspace root). " +
        "Supports 'write' (overwrite), 'append', and 'create' (fail if file exists) modes. " +
        "Use 'create' mode to safely create new files without risk of overwriting. " +
        "Use append mode in loops to build large files incrementally without increasing RAM usage."

internal fun streamWriteSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put("description", "Path to the output file. Supports absolute or relative path (resolved from workspace root)")
        }
        putJsonObject("content") {
            put("type", "string")
            put("description", "Text content to write to the file. Optional when mode='create' (defaults to empty string)")
        }
        putJsonObject("mode") {
            put("type", "string")
            put("description", "Write mode: 'write' (overwrite/create), 'append' (add to end), or 'create' (fail if file exists). Default: 'write'")
            put("default", "write")
        }
        putJsonObject("encoding") {
            put("type", "string")
            put("description", "Character encoding. Default: 'utf-8'")
            put("default", "utf-8")
        }
    },
    required = listOf("file_path")
)