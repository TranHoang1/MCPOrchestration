package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.fileproxy.model.FileProxyEntry
import com.orchestrator.mcp.fileproxy.model.FileProxyStatus
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import com.orchestrator.mcp.core.model.FileTooLargeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * Implementation of InputFileProxyHandler.
 * Handles file_path → base64 conversion for STDIO mode.
 */
class InputFileProxyHandlerImpl(
    private val registry: FileProxyRegistry,
    private val config: FileProxyConfig,
    private val executionDispatcher: ToolExecutionDispatcher,
    private val sessionId: UUID
) : InputFileProxyHandler {

    private val logger = LoggerFactory.getLogger(InputFileProxyHandlerImpl::class.java)

    override suspend fun processInputProxy(
        toolName: String,
        serverName: String,
        filePath: String,
        fileParamName: String,
        otherArgs: Map<String, Any?>,
        encodeBase64: Boolean
    ): ExecuteToolResponse {
        FilePathValidator.validateInputPath(filePath)
        validateFileSize(filePath, serverName)

        val fileId = UUID.randomUUID()
        val path = Path.of(filePath)
        val fileSize = withContext(Dispatchers.IO) { Files.size(path) }

        logger.info("[FileProxy] INPUT proxy: tool={}, server={}, file_size={}, mode={}",
            toolName, serverName, fileSize, if (encodeBase64) "base64" else "text")

        // Create registry entry
        val entry = FileProxyEntry(
            fileId = fileId,
            sessionId = sessionId,
            filePath = filePath,
            fileName = path.fileName.toString(),
            fileSize = fileSize,
            realToolName = toolName,
            upstreamServer = serverName,
            direction = ProxyDirection.INPUT,
            status = FileProxyStatus.PENDING,
            createdAt = Clock.System.now()
        )
        createRegistryEntrySafe(entry)

        return try {
            val content = if (encodeBase64) readAndEncode(path) else readAsText(path)
            val upstreamArgs = buildUpstreamArgs(fileParamName, content, otherArgs)
            val response = executionDispatcher.execute(toolName, upstreamArgs)

            updateStatusSafe(fileId, FileProxyStatus.PROCESSED)
            deleteEntrySafe(fileId)

            logger.info("[FileProxy] Completed: tool={}, status=PROCESSED", toolName)
            response
        } catch (e: Exception) {
            updateStatusSafe(fileId, FileProxyStatus.FAILED)
            throw e
        }
    }

    private suspend fun validateFileSize(filePath: String, serverName: String) {
        val size = withContext(Dispatchers.IO) { Files.size(Path.of(filePath)) }
        val maxBytes = getMaxSizeBytes(serverName)
        if (size > maxBytes) {
            val maxMb = maxBytes / (1024 * 1024)
            val actualMb = String.format("%.2f", size.toDouble() / (1024 * 1024))
            throw FileTooLargeException(maxMb.toInt(), actualMb)
        }
    }

    private fun getMaxSizeBytes(serverName: String): Long {
        val serverMax = config.servers[serverName]?.maxSizeMb
        val maxMb = serverMax ?: config.maxSizeMb
        return maxMb.toLong() * 1024 * 1024
    }

    private suspend fun readAndEncode(path: Path): String {
        return withContext(Dispatchers.IO) {
            val bytes = Files.readAllBytes(path)
            Base64.getEncoder().encodeToString(bytes)
        }
    }

    private suspend fun readAsText(path: Path): String {
        return withContext(Dispatchers.IO) {
            Files.readString(path, Charsets.UTF_8)
        }
    }

    private fun buildUpstreamArgs(
        fileParamName: String,
        base64Content: String,
        otherArgs: Map<String, Any?>
    ): JsonObject {
        return buildJsonObject {
            put(fileParamName, JsonPrimitive(base64Content))
            otherArgs.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    null -> put(key, JsonNull)
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
    }

    private suspend fun createRegistryEntrySafe(entry: FileProxyEntry) {
        try {
            registry.createEntry(entry)
        } catch (e: Exception) {
            logger.warn("[FileProxy] Registry unavailable: {}. Operating in degraded mode.", e.message)
        }
    }

    private suspend fun updateStatusSafe(fileId: UUID, status: FileProxyStatus) {
        try {
            registry.updateStatus(fileId, status, Clock.System.now())
        } catch (e: Exception) {
            logger.warn("[FileProxy] Registry update failed: {}", e.message)
        }
    }

    private suspend fun deleteEntrySafe(fileId: UUID) {
        try {
            registry.deleteEntry(fileId)
        } catch (e: Exception) {
            logger.warn("[FileProxy] Registry delete failed: {}", e.message)
        }
    }
}
