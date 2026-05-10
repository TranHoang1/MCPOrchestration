package com.orchestrator.mcp.ocr

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.execution.model.ExecuteToolResponse
import com.orchestrator.mcp.ocr.model.OcrConfig
import com.orchestrator.mcp.ocr.model.OcrException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * OCR implementation that delegates to MarkItDown MCP tool
 * via the existing ToolExecutionDispatcher infrastructure.
 */
class OcrServiceImpl(
    private val dispatcher: ToolExecutionDispatcher,
    private val config: OcrConfig
) : OcrService {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun extractText(fileUri: String): String {
        if (!config.enabled) return ""
        validateUri(fileUri)
        return callMarkItDown(fileUri)
    }

    private fun validateUri(uri: String) {
        if (uri.isBlank()) {
            throw OcrException.FileNotFoundException(uri)
        }
    }

    private suspend fun callMarkItDown(uri: String): String {
        return try {
            withTimeout(config.timeoutSeconds * 1000L) {
                executeToolCall(uri)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.warn("OCR timeout for URI: $uri")
            ""
        } catch (e: OcrException) {
            throw e
        } catch (e: Exception) {
            logger.warn("OCR failed for URI: $uri — ${e.message}")
            ""
        }
    }

    private suspend fun executeToolCall(uri: String): String {
        val arguments = buildJsonObject {
            put("uri", JsonPrimitive(uri))
        }
        val toolName = "${config.serverName}/${config.toolName}"
        val response = dispatcher.execute(toolName, arguments)
        return extractTextFromResponse(response)
    }

    private fun extractTextFromResponse(response: ExecuteToolResponse): String {
        return response.content
            .filter { it.type == "text" }
            .joinToString("\n") { it.text }
    }
}
