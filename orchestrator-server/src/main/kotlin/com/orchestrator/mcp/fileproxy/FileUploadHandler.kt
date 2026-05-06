package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.execution.model.ExecutionContentItem
import com.orchestrator.mcp.fileproxy.model.FileProxyEntry
import com.orchestrator.mcp.fileproxy.model.FileProxyStatus
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import com.orchestrator.mcp.core.model.FileTooLargeException
import com.orchestrator.mcp.core.model.FileIdNotFoundException
import com.orchestrator.mcp.core.model.FileExpiredException
import com.orchestrator.mcp.core.model.FileMissingOnDiskException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.time.Duration.Companion.minutes

/**
 * Handles the upload_file MCP tool for HTTP/SSE mode.
 * Copies file to temp directory and returns a file_id UUID.
 */
class FileUploadHandler(
    private val registry: FileProxyRegistry,
    private val config: FileProxyConfig,
    private val sessionId: UUID
) {
    private val logger = LoggerFactory.getLogger(FileUploadHandler::class.java)

    suspend fun handleUpload(filePath: String): ExecuteToolResponse {
        FilePathValidator.validateInputPath(filePath)
        validateFileSize(filePath)

        val path = Path.of(filePath)
        val fileSize = withContext(Dispatchers.IO) { Files.size(path) }
        val fileName = path.fileName.toString()
        val fileId = UUID.randomUUID()

        // Copy to temp directory
        val tempPath = copyToTemp(path, fileId, fileName)

        // Register in DB
        val entry = FileProxyEntry(
            fileId = fileId,
            sessionId = sessionId,
            filePath = tempPath.toString(),
            fileName = fileName,
            fileSize = fileSize,
            direction = ProxyDirection.INPUT,
            status = FileProxyStatus.PENDING,
            createdAt = Clock.System.now()
        )
        registry.createEntry(entry)

        val expiresAt = Clock.System.now() + config.ttlMinutes.minutes
        val responseText = buildJsonObject {
            put("file_id", JsonPrimitive(fileId.toString()))
            put("file_name", JsonPrimitive(fileName))
            put("file_size", JsonPrimitive(fileSize))
            put("expires_in", JsonPrimitive("${config.ttlMinutes}m"))
            put("expires_at", JsonPrimitive(expiresAt.toString()))
        }.toString()

        logger.info("[FileProxy] File uploaded: file_id={}, name={}, size={}", fileId, fileName, fileSize)

        return ExecuteToolResponse(
            content = listOf(ExecutionContentItem(type = "text", text = responseText))
        )
    }

    /**
     * Resolve a file_id to its base64 content.
     */
    suspend fun resolveFileId(fileId: UUID): String {
        val entry = registry.findByFileId(fileId)
            ?: throw com.orchestrator.mcp.core.model.FileIdNotFoundException(fileId.toString())

        if (entry.status == FileProxyStatus.EXPIRED) {
            throw com.orchestrator.mcp.core.model.FileExpiredException(fileId.toString())
        }

        val path = Path.of(entry.filePath)
        if (!withContext(Dispatchers.IO) { Files.exists(path) }) {
            throw com.orchestrator.mcp.core.model.FileMissingOnDiskException(fileId.toString())
        }

        return withContext(Dispatchers.IO) {
            val bytes = Files.readAllBytes(path)
            Base64.getEncoder().encodeToString(bytes)
        }
    }

    private suspend fun copyToTemp(source: Path, fileId: UUID, fileName: String): Path {
        return withContext(Dispatchers.IO) {
            val tempDir = Path.of(config.tempDirectory)
            Files.createDirectories(tempDir)
            val target = tempDir.resolve("${fileId}_$fileName")
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            target
        }
    }

    private suspend fun validateFileSize(filePath: String) {
        val size = withContext(Dispatchers.IO) { Files.size(Path.of(filePath)) }
        val maxBytes = config.maxSizeMb.toLong() * 1024 * 1024
        if (size > maxBytes) {
            val actualMb = String.format("%.2f", size.toDouble() / (1024 * 1024))
            throw FileTooLargeException(config.maxSizeMb, actualMb)
        }
    }
}
