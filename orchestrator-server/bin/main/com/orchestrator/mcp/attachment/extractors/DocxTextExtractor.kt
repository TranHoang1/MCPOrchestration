package com.orchestrator.mcp.attachment.extractors

import com.orchestrator.mcp.attachment.ContentExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream

/**
 * Extracts text from DOCX files using Apache POI.
 */
class DocxTextExtractor : ContentExtractor {
    override fun extract(bytes: ByteArray): String {
        val doc = XWPFDocument(ByteArrayInputStream(bytes))
        return doc.use { document ->
            document.paragraphs.joinToString("\n") { it.text }
        }
    }
}
