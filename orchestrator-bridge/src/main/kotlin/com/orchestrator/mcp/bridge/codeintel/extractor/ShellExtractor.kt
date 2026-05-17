package com.orchestrator.mcp.bridge.codeintel.extractor

import com.orchestrator.mcp.bridge.codeintel.model.SymbolEntry

/**
 * Extracts Bash and PowerShell function signatures.
 * Bash: function name() or name()
 * PowerShell: function Verb-Noun, filter, class
 */
class ShellExtractor : LanguageExtractor {

    override val language = "bash" // Also handles powershell via dispatch

    override fun extract(fileContent: String, fileId: Long): List<SymbolEntry> {
        val lines = fileContent.lines()
        val symbols = mutableListOf<SymbolEntry>()

        lines.forEachIndexed { index, line ->
            val bashMatch = BASH_FUNC.find(line)
            if (bashMatch != null) {
                val name = bashMatch.groupValues[1].ifEmpty { bashMatch.groupValues[2] }
                symbols.add(
                    SymbolEntry(
                        fileId = fileId, name = name, kind = "function",
                        signature = line.trim(), lineStart = index + 1
                    )
                )
                return@forEachIndexed
            }
            val psMatch = PS_FUNC.find(line)
            if (psMatch != null) {
                symbols.add(
                    SymbolEntry(
                        fileId = fileId, name = psMatch.groupValues[1],
                        kind = "function", signature = line.trim(), lineStart = index + 1
                    )
                )
            }
        }
        return symbols
    }

    companion object {
        private val BASH_FUNC = Regex("""^(?:function\s+(\w+)|(\w+)\s*\(\))""")
        private val PS_FUNC = Regex("""^\s*function\s+([\w-]+)""")
    }
}
