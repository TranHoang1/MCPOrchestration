package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Extracts Rust signatures: fn, struct, enum, trait, impl.
 */
class RustExtractor : LanguageExtractor {

    override val language = "rust"

    private val patterns = listOf(
        PatternDef(FN_PATTERN, "function"),
        PatternDef(STRUCT_PATTERN, "struct"),
        PatternDef(ENUM_PATTERN, "enum"),
        PatternDef(TRAIT_PATTERN, "interface"),
        PatternDef(IMPL_PATTERN, "class")
    )

    override fun extract(fileContent: String, fileId: Long): List<SymbolEntry> {
        val lines = fileContent.lines()
        val symbols = mutableListOf<SymbolEntry>()

        lines.forEachIndexed { index, line ->
            for (patternDef in patterns) {
                val match = patternDef.regex.find(line) ?: continue
                val name = match.groupValues.last { it.isNotEmpty() }
                symbols.add(
                    SymbolEntry(
                        fileId = fileId, name = name, kind = patternDef.kind,
                        signature = line.trim(), lineStart = index + 1,
                        visibility = if (line.contains("pub")) "public" else "private"
                    )
                )
                break
            }
        }
        return symbols
    }

    companion object {
        private val FN_PATTERN = Regex("""^\s*(?:pub\s+)?(?:async\s+)?fn\s+(\w+)""")
        private val STRUCT_PATTERN = Regex("""^\s*(?:pub\s+)?struct\s+(\w+)""")
        private val ENUM_PATTERN = Regex("""^\s*(?:pub\s+)?enum\s+(\w+)""")
        private val TRAIT_PATTERN = Regex("""^\s*(?:pub\s+)?trait\s+(\w+)""")
        private val IMPL_PATTERN = Regex("""^\s*impl(?:<[^>]+>)?\s+(\w+)""")
    }

    private data class PatternDef(val regex: Regex, val kind: String)
}
