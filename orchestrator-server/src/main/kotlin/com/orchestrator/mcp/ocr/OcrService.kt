package com.orchestrator.mcp.ocr

/**
 * Service for extracting text from images using OCR.
 * Delegates to MarkItDown MCP tool via the orchestrator's tool execution infrastructure.
 */
interface OcrService {

    /**
     * Extracts text content from an image file.
     *
     * @param fileUri URI of the image file (file:// or http://)
     * @return Extracted text as markdown string, or empty string on failure
     */
    suspend fun extractText(fileUri: String): String
}
