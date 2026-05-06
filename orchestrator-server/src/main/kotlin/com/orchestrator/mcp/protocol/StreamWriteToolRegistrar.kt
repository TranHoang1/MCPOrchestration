package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.fileproxy.FilePathValidator
import com.orchestrator.mcp.core.model.McpOrchestratorException
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
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

    fun register(server: Server) {
        server.addTool(
            name = "stream_write_file",
            description = streamWriteDescription(),
            inputSchema = streamWriteSchema()
        ) { request ->
            handleStreamWrite(request.arguments)
        }
    }

    private suspend fun handleStreamWrite(arguments: JsonObject?): CallToolResult {
        return try {
            val filePath = arguments?.get("file_path")
                ?.jsonPrimitive?.content
                ?: return errorResult("INVALID_PARAMS", "file_path is required")

            val content = arguments["content"]
                ?.jsonPrimitive?.content
                ?: return errorResult("INVALID_PARAMS", "content is required")

            val mode = arguments["mode"]
                ?.jsonPrimitive?.content ?: "write"

            if (mode !in listOf("write", "append")) {
                return errorResult("INVALID_PARAMS", "mode must be 'write' or 'append'")
            }

            FilePathValidator.validateOutputPath(filePath)

            val bytesWritten = writeToFile(filePath, content, mode)
            val totalSize = withContext(Dispatchers.IO) { Files.size(Path.of(filePath)) }

            val response = buildJsonObject {
                put("file_path", JsonPrimitive(filePath))
                put("bytes_written", JsonPrimitive(bytesWritten))
                put("total_size", JsonPrimitive(totalSize))
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
        return withContext(Dispatchers.IO) {
            val path = Path.of(filePath)
            val options = if (mode == "append") {
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            } else {
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
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
        "Supports 'write' (overwrite) and 'append' modes. " +
        "Use append mode in loops to build large files incrementally without increasing RAM usage."

internal fun streamWriteSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
        putJsonObject("file_path") {
            put("type", "string")
            put("description", "Absolute path to the output file")
        }
        putJsonObject("content") {
            put("type", "string")
            put("description", "Text content to write to the file")
        }
        putJsonObject("mode") {
            put("type", "string")
            put("description", "Write mode: 'write' (overwrite/create) or 'append' (add to end). Default: 'write'")
            put("default", "write")
        }
        putJsonObject("encoding") {
            put("type", "string")
            put("description", "Character encoding. Default: 'utf-8'")
            put("default", "utf-8")
        }
    },
    required = listOf("file_path", "content")
)
