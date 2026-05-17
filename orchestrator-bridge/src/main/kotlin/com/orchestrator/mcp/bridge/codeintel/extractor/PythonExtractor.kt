package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Extracts Python signatures: class and def/async def declarations.
 */
class PythonExtractor : LanguageExtractor {

    override val language = "python"

    override fun extract(fileContent: String, fileId: Long): List<SymbolEntry> {
        val lines = fileContent.lines()
        val symbols = mutableListOf<SymbolEntry>()

        lines.forEachIndexed { index, line ->
            val classMatch = CLASS_PATTERN.find(line)
            if (classMatch != null) {
                symbols.add(createSymbol(classMatch, line, index + 1, "class", fileId))
                return@forEachIndexed
            }
            val funcMatch = FUNCTION_PATTERN.find(line)
            if (funcMatch != null) {
                val kind = if (line.trimStart().startsWith("async")) "function" else "function"
                symbols.add(createSymbol(funcMatch, line, index + 1, kind, fileId))
            }
        }
        return symbols
    }

    private fun createSymbol(
        match: MatchResult, line: String, lineNum: Int, kind: String, fileId: Long
    ): SymbolEntry {
        val name = match.groupValues.last { it.isNotEmpty() }
        return SymbolEntry(
            fileId = fileId, name = name, kind = kind,
            signature = line.trim(), lineStart = lineNum
        )
    }

    companion object {
        private val CLASS_PATTERN = Regex("""^class\s+(\w+)""")
        private val FUNCTION_PATTERN = Regex("""^(?:async\s+)?def\s+(\w+)""")
    }
}
