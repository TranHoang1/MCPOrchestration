package com.orchestrator.mcp.ocr.model

/**
 * Sealed exception hierarchy for OCR errors.
 */
sealed class OcrException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    class ServerUnavailableException(server: String, cause: Throwable? = null) :
        OcrException("OCR server unavailable: $server", cause)

    class TimeoutException(timeoutMs: Long) :
        OcrException("OCR timeout after ${timeoutMs}ms")

    class UnsupportedFormatException(format: String) :
        OcrException("Unsupported image format: $format")

    class FileNotFoundException(uri: String) :
        OcrException("File not found: $uri")
}
