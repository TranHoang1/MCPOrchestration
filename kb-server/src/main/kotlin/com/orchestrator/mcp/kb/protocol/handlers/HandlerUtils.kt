package com.orchestrator.mcp.kb.protocol.handlers

import com.orchestrator.mcp.kb.KbException
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Shared utilities for KB tool handlers.
 */
object HandlerUtils {

    /** Build a success CallToolResult from a JSON string */
    fun successResult(json: String): CallToolResult =
        CallToolResult(content = listOf(TextContent(text = json)), isError = false)

    /** Build an error CallToolResult from a KbException */
    fun errorResult(e: KbException): CallToolResult {
        val errorJson = buildJsonObject {
            putJsonObject("error") {
                put("code", e.errorCode)
                put("message", e.message)
            }
        }
        return CallToolResult(content = listOf(TextContent(text = errorJson.toString())), isError = true)
    }

    /** Build a generic error CallToolResult */
    fun errorResult(code: String, message: String): CallToolResult {
        val errorJson = buildJsonObject {
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }
        return CallToolResult(content = listOf(TextContent(text = errorJson.toString())), isError = true)
    }

    /** Extract a required string parameter from arguments */
    fun requireString(args: JsonObject?, key: String): String? =
        args?.get(key)?.jsonPrimitive?.content

    /** Extract an optional string parameter from arguments */
    fun optionalString(args: JsonObject?, key: String): String? =
        args?.get(key)?.jsonPrimitive?.content

    /** Extract an optional int parameter with default */
    fun optionalInt(args: JsonObject?, key: String, default: Int): Int =
        args?.get(key)?.jsonPrimitive?.content?.toIntOrNull() ?: default

    /** Extract an optional boolean parameter with default */
    fun optionalBoolean(args: JsonObject?, key: String, default: Boolean): Boolean =
        args?.get(key)?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default
}
