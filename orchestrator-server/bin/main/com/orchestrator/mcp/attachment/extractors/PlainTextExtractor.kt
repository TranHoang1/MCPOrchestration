package com.orchestrator.mcp.attachment.extractors

import com.orchestrator.mcp.attachment.ContentExtractor

/**
 * Extracts text from plain text and markdown files.
 */
class PlainTextExtractor : ContentExtractor {
    override fun extract(bytes: ByteArray): String {
        return String(bytes, Charsets.UTF_8)
    }
}
