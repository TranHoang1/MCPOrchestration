package com.orchestrator.mcp.bridge

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Handles HTTP binary file transfer between bridge and orchestrator.
 * Uses raw HTTP POST/GET for file content (not base64 encoding).
 */
class FileTransferHandler(private val config: BridgeConfig) {

    private val logger = LoggerFactory.getLogger(FileTransferHandler::class.java)

    private val httpClient = HttpClient(CIO) {
        engine { requestTimeout = config.requestTimeoutMs }
    }

    /**
     * Upload a local file to the orchestrator via HTTP binary transfer.
     */
    suspend fun uploadFile(localPath: String, sessionId: String): UploadResult {
        val file = File(localPath)
        if (!file.exists()) return UploadResult(success = false, error = "File not found: $localPath")

        return try {
            val response = httpClient.post("${config.orchestratorUrl}/files/upload") {
                header("Mcp-Session-Id", sessionId)
                header("X-File-Name", file.name)
                contentType(ContentType.Application.OctetStream)
                setBody(file.readBytes())
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                UploadResult(success = true, fileId = body)
            } else {
                UploadResult(success = false, error = "Upload failed: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("File upload failed: ${e.message}")
            UploadResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    /**
     * Download a file from the orchestrator to local disk.
     */
    suspend fun downloadFile(fileId: String, localPath: String, sessionId: String): Boolean {
        return try {
            val response = httpClient.get("${config.orchestratorUrl}/files/$fileId") {
                header("Mcp-Session-Id", sessionId)
            }
            if (response.status == HttpStatusCode.OK) {
                val file = File(localPath)
                file.parentFile?.mkdirs()
                file.writeBytes(response.readRawBytes())
                true
            } else {
                logger.error("Download failed: ${response.status}")
                false
            }
        } catch (e: Exception) {
            logger.error("File download failed: ${e.message}")
            false
        }
    }

    suspend fun close() {
        httpClient.close()
    }
}

data class UploadResult(
    val success: Boolean,
    val fileId: String? = null,
    val error: String? = null
)
