package com.orchestrator.mcp.fileproxy

import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * HTTP routes for file upload/download.
 * Enables bridge to transfer files to server without volume mounts.
 */
class FileUploadRoutes(
    private val config: FileProxyConfig,
    private val registry: FileProxyRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun handleUpload(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            sendError(exchange, 405, "Method not allowed")
            return
        }
        runBlocking { doUpload(exchange) }
    }

    fun handleDownload(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            sendError(exchange, 405, "Method not allowed")
            return
        }
        runBlocking { doDownload(exchange) }
    }

    private suspend fun doUpload(exchange: HttpExchange) {
        try {
            val fileName = exchange.requestHeaders
                .getFirst("X-File-Name") ?: "upload_${System.currentTimeMillis()}"
            val bytes = withContext(Dispatchers.IO) {
                exchange.requestBody.readBytes()
            }

            val maxBytes = config.maxSizeMb.toLong() * 1024 * 1024
            if (bytes.size > maxBytes) {
                sendError(exchange, 413, "File too large")
                return
            }

            val fileId = UUID.randomUUID()
            val tempDir = Path.of(config.tempDirectory)
            withContext(Dispatchers.IO) { Files.createDirectories(tempDir) }
            val targetPath = tempDir.resolve("${fileId}_$fileName")
            withContext(Dispatchers.IO) { Files.write(targetPath, bytes) }

            val sessionId = exchange.requestHeaders
                .getFirst("Mcp-Session-Id")
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: UUID.randomUUID()

            val entry = com.orchestrator.mcp.fileproxy.model.FileProxyEntry(
                fileId = fileId,
                sessionId = sessionId,
                filePath = targetPath.toString(),
                fileName = fileName,
                fileSize = bytes.size.toLong(),
                direction = com.orchestrator.mcp.fileproxy.model.ProxyDirection.INPUT,
                status = com.orchestrator.mcp.fileproxy.model.FileProxyStatus.PENDING,
                createdAt = kotlinx.datetime.Clock.System.now()
            )
            try { registry.createEntry(entry) } catch (_: Exception) { }

            logger.info("[FileProxy] Upload: id={}, name={}, size={}",
                fileId, fileName, bytes.size)

            val response = buildJsonObject {
                put("file_id", fileId.toString())
                put("file_name", fileName)
                put("file_size", bytes.size.toLong())
            }.toString()
            sendJson(exchange, 200, response)
        } catch (e: Exception) {
            logger.error("[FileProxy] Upload error: {}", e.message)
            sendError(exchange, 500, e.message ?: "Upload failed")
        }
    }

    private suspend fun doDownload(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            val fileId = path.substringAfterLast("/")
            val uuid = UUID.fromString(fileId)

            val entry = registry.findByFileId(uuid)
            if (entry == null) {
                sendError(exchange, 404, "File not found")
                return
            }

            val filePath = Path.of(entry.filePath)
            if (!withContext(Dispatchers.IO) { Files.exists(filePath) }) {
                sendError(exchange, 404, "File missing on disk")
                return
            }

            val bytes = withContext(Dispatchers.IO) {
                Files.readAllBytes(filePath)
            }
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.responseHeaders.add("X-File-Name", entry.fileName)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: IllegalArgumentException) {
            sendError(exchange, 400, "Invalid file ID")
        } catch (e: Exception) {
            logger.error("[FileProxy] Download error: {}", e.message)
            sendError(exchange, 500, e.message ?: "Download failed")
        }
    }

    private fun sendJson(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendError(exchange: HttpExchange, status: Int, msg: String) {
        val body = """{"error":"$msg"}"""
        sendJson(exchange, status, body)
    }
}
