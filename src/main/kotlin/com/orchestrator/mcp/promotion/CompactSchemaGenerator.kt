package com.orchestrator.mcp.promotion

import com.orchestrator.mcp.model.ToolEntry
import kotlinx.serialization.json.*

/**
 * Generates compact tool schemas for promoted tools.
 * Truncates descriptions to ≤100 chars and strips optional parameters.
 */
object CompactSchemaGenerator {

    private const val MAX_DESCRIPTION_LENGTH = 100

    fun generate(tool: ToolEntry): Pair<String, JsonObject> {
        val compactDesc = truncateDescription(tool.description)
        val compactSchema = stripOptionalParams(tool.inputSchema ?: buildJsonObject {})
        return Pair(compactDesc, compactSchema)
    }

    private fun truncateDescription(description: String): String {
        if (description.length <= MAX_DESCRIPTION_LENGTH) return description
        return description.take(MAX_DESCRIPTION_LENGTH - 3) + "..."
    }

    private fun stripOptionalParams(schema: JsonObject): JsonObject {
        val properties = schema["properties"]?.jsonObject ?: return schema
        val required = schema["required"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet() ?: emptySet()

        if (required.isEmpty()) return schema

        val filteredProps = buildJsonObject {
            properties.forEach { (key, value) ->
                if (key in required) put(key, value)
            }
        }

        return buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", filteredProps)
            put("required", schema["required"] ?: buildJsonArray {})
        }
    }
}
