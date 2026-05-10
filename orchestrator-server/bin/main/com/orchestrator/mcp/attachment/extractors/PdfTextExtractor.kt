package com.orchestrator.mcp.attachment.extractors

import com.orchestrator.mcp.attachment.ContentExtractor
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Extracts text from PDF files using Apache PDFBox.
 */
class PdfTextExtractor : ContentExtractor {
    override fun extract(bytes: ByteArray): String {
        val document = Loader.loadPDF(bytes)
        return document.use { doc ->
            val stripper = PDFTextStripper()
            stripper.getText(doc)
        }
    }
}
