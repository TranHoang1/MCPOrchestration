package com.orchestrator.mcp.discovery

import com.orchestrator.mcp.discovery.model.ToolResult
import com.orchestrator.mcp.registry.ToolRegistry

/**
 * In-memory keyword-based search engine as fallback when Vector DB or Embedding service is unavailable.
 * Uses simple token matching with TF-IDF-like scoring.
 */
class KeywordSearchEngine(
    private val toolRegistry: ToolRegistry
) {

    fun search(query: String, topK: Int, threshold: Float): List<ToolResult> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        return toolRegistry.getAllTools()
            .map { entry ->
                val nameTokens = tokenize(entry.name)
                val descTokens = tokenize(entry.description)
                val allTokens = nameTokens + descTokens

                // Simple keyword matching score
                val matchCount = queryTokens.count { qt ->
                    allTokens.any { it.contains(qt, ignoreCase = true) }
                }
                val score = if (allTokens.isNotEmpty()) {
                    matchCount.toFloat() / queryTokens.size.toFloat()
                } else 0f

                ToolResult(
                    name = entry.name,
                    description = entry.description,
                    inputSchema = entry.inputSchema,
                    serverName = entry.serverName,
                    serverStatus = entry.serverStatus,
                    similarityScore = score
                )
            }
            .filter { it.similarityScore >= threshold.coerceAtMost(0.1f) } // Lower threshold for keyword
            .sortedByDescending { it.similarityScore }
            .take(topK)
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s_-]"), " ")
            .split(Regex("[\\s_-]+"))
            .filter { it.isNotBlank() && it.length > 1 }
    }
}
