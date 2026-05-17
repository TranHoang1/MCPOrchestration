package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Extracts TypeScript/JavaScript signatures using regex patterns.
 * Handles: export class, function, const, interface, type, enum.
 */
class TypeScriptExtractor : LanguageExtractor {

    override val language = "typescript"

    private val patterns = listOf(
        PatternDef(CLASS_PATTERN, "class"),
        PatternDef(INTERFACE_PATTERN, "interface"),
        PatternDef(TYPE_PATTERN, "type"),
        PatternDef(ENUM_PATTERN, "enum"),
        PatternDef(FUNCTION_PATTERN, "function"),
        PatternDef(CONST_PATTERN, "property")
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
                        visibility = if (line.contains("export")) "public" else "private"
                    )
                )
                break
            }
        }
        return symbols
    }

    companion object {
        private val CLASS_PATTERN = Regex("""^\s*export\s+(?:abstract\s+)?class\s+(\w+)""")
        private val INTERFACE_PATTERN = Regex("""^\s*export\s+interface\s+(\w+)""")
        private val TYPE_PATTERN = Regex("""^\s*export\s+type\s+(\w+)""")
        private val ENUM_PATTERN = Regex("""^\s*export\s+enum\s+(\w+)""")
        private val FUNCTION_PATTERN = Regex("""^\s*export\s+(?:async\s+)?function\s+(\w+)""")
        private val CONST_PATTERN = Regex("""^\s*export\s+const\s+(\w+)""")
    }

    private data class PatternDef(val regex: Regex, val kind: String)
}
