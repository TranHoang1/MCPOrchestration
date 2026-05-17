package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Interface for language-specific signature extraction.
 */
interface LanguageExtractor {
    val language: String
    fun extract(fileContent: String, fileId: Long): List<SymbolEntry>
}

/**
 * Dispatches extraction to the appropriate language-specific extractor.
 */
object SignatureExtractor {

    private val extractors: Map<String, LanguageExtractor> = listOf(
        KotlinExtractor(),
        TypeScriptExtractor(),
        PythonExtractor(),
        GoExtractor(),
        RustExtractor(),
        ShellExtractor()
    ).associateBy { it.language }

    fun extract(language: String, fileContent: String, fileId: Long): List<SymbolEntry> {
        val extractor = extractors[language] ?: return emptyList()
        val symbols = extractor.extract(fileContent, fileId)
        return symbols.take(MAX_SYMBOLS_PER_FILE)
    }

    private const val MAX_SYMBOLS_PER_FILE = 1000
}
