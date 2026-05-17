package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Extracts Go signatures: func, type struct, type interface.
 */
class GoExtractor : LanguageExtractor {

    override val language = "go"

    override fun extract(fileContent: String, fileId: Long): List<SymbolEntry> {
        val lines = fileContent.lines()
        val symbols = mutableListOf<SymbolEntry>()

        lines.forEachIndexed { index, line ->
            val funcMatch = FUNC_PATTERN.find(line)
            if (funcMatch != null) {
                val name = funcMatch.groupValues[1]
                symbols.add(
                    SymbolEntry(
                        fileId = fileId, name = name, kind = "function",
                        signature = line.trim(), lineStart = index + 1,
                        visibility = if (name[0].isUpperCase()) "public" else "private"
                    )
                )
                return@forEachIndexed
            }
            val typeMatch = TYPE_PATTERN.find(line)
            if (typeMatch != null) {
                val name = typeMatch.groupValues[1]
                val kind = typeMatch.groupValues[2]
                symbols.add(
                    SymbolEntry(
                        fileId = fileId, name = name, kind = kind,
                        signature = line.trim(), lineStart = index + 1,
                        visibility = if (name[0].isUpperCase()) "public" else "private"
                    )
                )
            }
        }
        return symbols
    }

    companion object {
        private val FUNC_PATTERN = Regex("""^func\s+(?:\(\w+\s+\*?\w+\)\s+)?(\w+)""")
        private val TYPE_PATTERN = Regex("""^type\s+(\w+)\s+(struct|interface)""")
    }
}
