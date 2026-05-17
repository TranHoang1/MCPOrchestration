package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Extracts Kotlin code signatures using regex patterns.
 * Handles: class, data class, sealed class, object, interface, fun, val, var.
 */
class KotlinExtractor : LanguageExtractor {

    override val language = "kotlin"

    private val patterns = listOf(
        PatternDef(CLASS_PATTERN, "class"),
        PatternDef(INTERFACE_PATTERN, "interface"),
        PatternDef(OBJECT_PATTERN, "object"),
        PatternDef(FUNCTION_PATTERN, "function"),
        PatternDef(PROPERTY_PATTERN, "property")
    )

    override fun extract(fileContent: String, fileId: Long): List<SymbolEntry> {
        val lines = fileContent.lines()
        val symbols = mutableListOf<SymbolEntry>()

        lines.forEachIndexed { index, line ->
            for (patternDef in patterns) {
                val match = patternDef.regex.find(line) ?: continue
                symbols.add(createSymbol(match, line, index + 1, patternDef.kind, fileId))
                break
            }
        }
        return symbols
    }

    private fun createSymbol(
        match: MatchResult, line: String, lineNum: Int, kind: String, fileId: Long
    ): SymbolEntry {
        val name = match.groupValues.last { it.isNotEmpty() && !it.contains(' ') }
        val visibility = extractVisibility(line)
        return SymbolEntry(
            fileId = fileId, name = name, kind = kind,
            signature = line.trim(), lineStart = lineNum, visibility = visibility
        )
    }

    private fun extractVisibility(line: String): String? {
        val trimmed = line.trimStart()
        return when {
            trimmed.startsWith("private") -> "private"
            trimmed.startsWith("internal") -> "internal"
            trimmed.startsWith("protected") -> "protected"
            trimmed.startsWith("public") -> "public"
            else -> null
        }
    }

    companion object {
        private val CLASS_PATTERN = Regex(
            """^\s*(?:public|private|internal|protected)?\s*(?:data|sealed|abstract|open|enum)?\s*class\s+(\w+)"""
        )
        private val INTERFACE_PATTERN = Regex(
            """^\s*(?:public|private|internal)?\s*interface\s+(\w+)"""
        )
        private val OBJECT_PATTERN = Regex(
            """^\s*(?:public|private|internal)?\s*(?:companion\s+)?object\s+(\w+)"""
        )
        private val FUNCTION_PATTERN = Regex(
            """^\s*(?:public|private|internal|protected)?\s*(?:suspend\s+)?(?:override\s+)?fun\s+(\w+)"""
        )
        private val PROPERTY_PATTERN = Regex(
            """^\s*(?:public|private|internal|protected)?\s*(?:override\s+)?(?:val|var)\s+(\w+)"""
        )
    }

    private data class PatternDef(val regex: Regex, val kind: String)
}
