package com.orchestrator.mcp.attachment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Strategy-based text extraction from binary content.
 * Routes to appropriate extractor based on MIME type.
 */
class TextExtractor(private val extractors: Map<String, ContentExtractor>) {

    private val logger = LoggerFactory.getLogger(TextExtractor::class.java)

    suspend fun extract(bytes: ByteArray, mimeType: String): String {
        val extractor = extractors[mimeType]
            ?: throw UnsupportedMimeTypeException(mimeType)
        return withContext(Dispatchers.IO) {
            extractor.extract(bytes)
        }
    }

    fun supports(mimeType: String): Boolean = extractors.containsKey(mimeType)
}

/**
 * Interface for content extractors (Strategy pattern).
 */
interface ContentExtractor {
    fun extract(bytes: ByteArray): String
}

class UnsupportedMimeTypeException(mimeType: String) :
    Exception("Unsupported MIME type: $mimeType")
