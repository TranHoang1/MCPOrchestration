package com.orchestrator.mcp.protocol

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Stateless JSON-RPC router for HTTP Streamable mode.
 * Parses incoming JSON-RPC, routes to the correct handler,
 * and returns JSON-RPC response. No SDK session needed.
 */
class HttpToolRouter(factory: McpServerFactory) {

    private val logger = LoggerFactory.getLogger(
        HttpToolRouter::class.java
    )
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Expose factory's internal handlers via reflection-free
    // approach: factory creates a ToolDispatch interface
    private val dispatcher = factory.createDispatcher()

    suspend fun handle(body: String): String {
        logger.info("Router received: ${body.take(200)}")
        return try {
            val obj = json.parseToJsonElement(body).jsonObject
            val id = obj["id"]
            val method = obj["method"]
                ?.jsonPrimitive?.content
            val params = obj["params"]?.jsonObject

            logger.info("Routing method: $method")

            when (method) {
                "initialize" -> buildResponse(
                    id, buildInitResult()
                )
                "notifications/initialized" -> buildResponse(
                    id, null
                )
                "tools/list" -> buildResponse(
                    id, dispatcher.listTools()
                )
                "tools/call" -> {
                    val name = params?.get("name")
                        ?.jsonPrimitive?.content
                    val args = params?.get("arguments")
                        ?.jsonObject
                    logger.info("tools/call: $name")
                    if (name == null) {
                        buildErrorResponse(
                            id, -32602,
                            "Missing 'name' in params"
                        )
                    } else {
                        val result = dispatcher.callTool(
                            name, args
                        )
                        logger.info(
                            "tools/call done: $name"
                        )
                        buildToolResponse(id, result)
                    }
                }
                "ping" -> buildResponse(id, buildJsonObject {})
                else -> buildErrorResponse(
                    id, -32601,
                    "Method not found: $method"
                )
            }
        } catch (e: Exception) {
            logger.error("Router error: ${e.message}", e)
            buildErrorResponse(null, -32603, e.message ?: "")
        }
    }

    private fun buildInitResult(): JsonElement {
        return buildJsonObject {
            put("protocolVersion", "2025-03-26")
            putJsonObject("capabilities") {
                putJsonObject("tools") {
                    put("listChanged", false)
                }
            }
            putJsonObject("serverInfo") {
                put("name", "mcp-orchestrator")
                put("version", "1.0.0")
            }
        }
    }

    private fun buildResponse(
        id: JsonElement?,
        result: JsonElement?
    ): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            if (result != null) {
                put("result", result)
            } else {
                put("result", buildJsonObject {})
            }
        }.toString()
    }

    private fun buildToolResponse(
        id: JsonElement?,
        result: CallToolResult
    ): String {
        val content = buildJsonArray {
            result.content.forEach { c ->
                add(buildJsonObject {
                    put("type", "text")
                    put("text", (c as TextContent).text)
                })
            }
        }
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            putJsonObject("result") {
                put("content", content)
                if (result.isError == true) {
                    put("isError", true)
                }
            }
        }.toString()
    }

    private fun buildErrorResponse(
        id: JsonElement?,
        code: Int,
        message: String
    ): String {
        return buildJsonObject {
            put("jsonrpc", "2.0")
            if (id != null) put("id", id)
            else put("id", JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        }.toString()
    }
}
