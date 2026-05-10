package com.orchestrator.mcp.ocr.extractor

import com.orchestrator.mcp.attachment.ContentExtractor
import com.orchestrator.mcp.ocr.OcrService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * ContentExtractor implementation for image MIME types.
 * Delegates OCR processing to OcrService (MarkItDown MCP tool).
 *
 * Note: Uses runBlocking because ContentExtractor.extract() is not suspend.
 * This is acceptable since TextExtractor already wraps in Dispatchers.IO.
 */
class ImageTextExtractor(
    private val ocrService: OcrService,
    private val fileUriResolver: FileUriResolver? = null
) : ContentExtractor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun extract(bytes: ByteArray): String {
        val uri = fileUriResolver?.resolve(bytes) ?: return ""
        return try {
            runBlocking { ocrService.extractText(uri) }
        } catch (e: Exception) {
            logger.warn("Image text extraction failed: ${e.message}")
            ""
        }
    }
}

/**
 * Resolves byte content to a file URI for MCP tool access.
 * Implementation writes bytes to temp file and returns file:// URI.
 */
interface FileUriResolver {
    fun resolve(bytes: ByteArray): String?
}
