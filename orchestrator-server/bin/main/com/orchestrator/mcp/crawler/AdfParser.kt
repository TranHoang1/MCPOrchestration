package com.orchestrator.mcp.crawler

import kotlinx.serialization.json.*

/**
 * Parses Atlassian Document Format (ADF) JSON into plain text.
 * Handles nested content nodes recursively.
 */
class AdfParser {

    fun toPlainText(adfJson: JsonElement?): String {
        if (adfJson == null || adfJson is JsonNull) return ""
        return extractText(adfJson).trim()
    }

    private fun extractText(node: JsonElement): String = when {
        node is JsonPrimitive -> node.contentOrNull ?: ""
        node is JsonArray -> node.joinToString("\n") { extractText(it) }
        node is JsonObject -> extractFromObject(node)
        else -> ""
    }

    private fun extractFromObject(obj: JsonObject): String {
        // Text node
        val text = obj["text"]
        if (text is JsonPrimitive) return text.content

        // Content array (paragraph, heading, etc.)
        val content = obj["content"]
        if (content is JsonArray) {
            return content.joinToString("") { extractText(it) }
        }

        return ""
    }
}
