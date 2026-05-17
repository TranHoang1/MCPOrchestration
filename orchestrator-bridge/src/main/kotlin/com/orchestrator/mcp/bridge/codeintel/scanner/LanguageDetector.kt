package com.orchestrator.mcp.bridge.codeintel.scanner

/**
 * Detects programming language from file extension.
 * Returns null for unsupported/unknown extensions.
 */
object LanguageDetector {

    private val extensionMap = mapOf(
        "kt" to "kotlin", "kts" to "kotlin",
        "java" to "java",
        "ts" to "typescript", "tsx" to "typescript",
        "js" to "javascript", "jsx" to "javascript", "mjs" to "javascript",
        "py" to "python",
        "go" to "go",
        "rs" to "rust",
        "sh" to "bash",
        "ps1" to "powershell", "psm1" to "powershell"
    )

    val supportedLanguages: Set<String> = extensionMap.values.toSet()

    fun detect(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return extensionMap[ext]
    }

    fun isSupported(fileName: String): Boolean = detect(fileName) != null
}
