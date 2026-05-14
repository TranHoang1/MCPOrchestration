package com.orchestrator.mcp.sync.pipeline.crawl

import kotlinx.serialization.json.*

/**
 * Parses Atlassian Document Format (ADF) JSON into plain text.
 * Handles nested content nodes recursively.
 */
class AdfParser {

    /** Convert ADF JsonElement to plain text. Returns empty string for null/invalid. */
    fun toPlainText(adf: JsonElement?): String {
        if (adf == null || adf is JsonNull) return ""
        val obj = adf as? JsonObject ?: return ""
        return extractText(obj).trim()
    }

    private fun extractText(node: JsonObject): String {
        val sb = StringBuilder()
        val type = node.getString("type")

        when (type) {
            "text" -> sb.append(node.getString("text") ?: "")
            "hardBreak" -> sb.append("\n")
            "mention" -> sb.append(extractMention(node))
            "inlineCard" -> sb.append(extractInlineCard(node))
        }

        appendChildContent(node, sb)
        appendBlockSuffix(type, sb)
        return sb.toString()
    }

    private fun appendChildContent(node: JsonObject, sb: StringBuilder) {
        val content = node["content"] as? JsonArray ?: return
        for (child in content) {
            val childObj = child as? JsonObject ?: continue
            sb.append(extractText(childObj))
        }
    }

    private fun appendBlockSuffix(type: String?, sb: StringBuilder) {
        when (type) {
            "paragraph", "heading", "bulletList",
            "orderedList", "listItem", "blockquote",
            "codeBlock", "table", "tableRow" -> sb.append("\n")
        }
    }

    private fun extractMention(node: JsonObject): String {
        val attrs = node["attrs"] as? JsonObject ?: return ""
        return "@${attrs.getString("text") ?: "user"}"
    }

    private fun extractInlineCard(node: JsonObject): String {
        val attrs = node["attrs"] as? JsonObject ?: return ""
        return attrs.getString("url") ?: ""
    }
}

private fun JsonObject.getString(key: String): String? {
    val el = this[key] ?: return null
    return (el as? JsonPrimitive)?.contentOrNull
}
