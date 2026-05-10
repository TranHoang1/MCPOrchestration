package com.orchestrator.mcp.protocol

import com.orchestrator.mcp.core.model.ToolEntry
import com.orchestrator.mcp.fileproxy.FilePathValidator
import com.orchestrator.mcp.registry.ToolRegistry
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Registers hidden utility tools that are discoverable via find_tools
 * but NOT listed in tools/list response.
 *
 * Hidden tools: get_drawio_reference, export_drawio
 */
object HiddenToolRegistrar {

    private val logger = LoggerFactory.getLogger(HiddenToolRegistrar::class.java)

    /** Tool names that should be hidden from tools/list */
    val hiddenToolNames = setOf("get_drawio_reference", "export_drawio")

    /** All builtin tool names (hidden + sync + user management) */
    private val builtinToolNames = mutableSetOf(
        "get_drawio_reference", "export_drawio",
        "jira_project_sync", "jira_sync_status",
        "approve_document", "get_approval_status", "list_pending_approvals"
    )

    /** Register additional builtin tool names (called by HttpStreamableServer after registering sync/user tools) */
    fun registerBuiltinToolName(name: String) {
        builtinToolNames.add(name)
    }

    /** Check if a tool is a builtin tool (no upstream server) */
    fun isHiddenTool(toolName: String): Boolean {
        return builtinToolNames.contains(toolName)
    }

    fun registerHiddenTools(toolRegistry: ToolRegistry) {
        registerDrawioReference(toolRegistry)
        registerDrawioExport(toolRegistry)
        logger.info("Registered ${hiddenToolNames.size} hidden utility tools")
    }

    private fun registerDrawioReference(toolRegistry: ToolRegistry) {
        val entry = ToolEntry(
            name = "get_drawio_reference",
            description = "Returns draw.io XML reference documentation for generating diagrams",
            inputSchema = emptyJsonSchema(),
            serverName = "__builtin__"
        )
        toolRegistry.registerTool(entry)
        toolRegistry.setHidden("get_drawio_reference", true)
    }

    private fun registerDrawioExport(toolRegistry: ToolRegistry) {
        val entry = ToolEntry(
            name = "export_drawio",
            description = "Export a .drawio diagram file to PNG, SVG, or PDF format",
            inputSchema = exportDrawioSchema(),
            serverName = "__builtin__"
        )
        toolRegistry.registerTool(entry)
        toolRegistry.setHidden("export_drawio", true)
    }

    suspend fun executeHiddenTool(name: String, args: JsonObject?): CallToolResult {
        return when (name) {
            "get_drawio_reference" -> executeGetDrawioReference()
            "export_drawio" -> executeExportDrawio(args)
            "jira_project_sync" -> executeSyncTool(name, args)
            "jira_sync_status" -> executeSyncTool(name, args)
            "approve_document", "get_approval_status", "list_pending_approvals" -> executeSyncTool(name, args)
            else -> CallToolResult(content = listOf(TextContent(text = "Unknown hidden tool: $name")), isError = true)
        }
    }

    /** Route sync/user-management tools through Koin-managed handlers */
    private suspend fun executeSyncTool(name: String, args: JsonObject?): CallToolResult {
        return try {
            val koin = org.koin.java.KoinJavaComponent.getKoin()
            when (name) {
                "jira_project_sync" -> {
                    val handler = koin.get<com.orchestrator.mcp.synctools.SyncToolHandler>()
                    handler.handle(args)
                }
                "jira_sync_status" -> {
                    val handler = koin.get<com.orchestrator.mcp.synctools.StatusToolHandler>()
                    handler.handle(args)
                }
                "approve_document", "get_approval_status", "list_pending_approvals" -> {
                    // User management tools — not yet routed in HTTP mode
                    CallToolResult(content = listOf(TextContent(text = "User management tools not available in HTTP mode yet")), isError = true)
                }
                else -> CallToolResult(content = listOf(TextContent(text = "Unknown builtin tool: $name")), isError = true)
            }
        } catch (e: Exception) {
            logger.error("Builtin tool execution failed: $name — ${e.message}")
            CallToolResult(content = listOf(TextContent(text = "Builtin tool error: ${e.message}")), isError = true)
        }
    }

    private fun executeGetDrawioReference(): CallToolResult {
        val refFile = File(".antigravity/steering/drawio.md")
        val content = if (refFile.exists()) refFile.readText() else "drawio.md reference file not found"
        return CallToolResult(content = listOf(TextContent(text = content)))
    }

    private suspend fun executeExportDrawio(args: JsonObject?): CallToolResult {
        val rawPath = args?.get("file_path")?.jsonPrimitive?.content
            ?: return CallToolResult(content = listOf(TextContent(text = "file_path is required")), isError = true)
        val format = args["format"]?.jsonPrimitive?.content ?: "png"
        val filePath = FilePathValidator.resolvePath(rawPath)
        return doExportDrawio(filePath, format)
    }

    private fun emptyJsonSchema(): JsonObject {
        return kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("object"))
            put("properties", kotlinx.serialization.json.buildJsonObject {})
            put("required", kotlinx.serialization.json.buildJsonArray {})
        }
    }

    private fun exportDrawioSchema(): JsonObject {
        return kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("object"))
            put("properties", kotlinx.serialization.json.buildJsonObject {
                put("file_path", kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("string"))
                    put("description", kotlinx.serialization.json.JsonPrimitive("Path to the .drawio file. Supports absolute or relative path (resolved from workspace root)"))
                })
                put("format", kotlinx.serialization.json.buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("string"))
                    put("enum", kotlinx.serialization.json.buildJsonArray {
                        add(kotlinx.serialization.json.JsonPrimitive("png"))
                        add(kotlinx.serialization.json.JsonPrimitive("svg"))
                        add(kotlinx.serialization.json.JsonPrimitive("pdf"))
                    })
                })
            })
            put("required", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("file_path"))
                add(kotlinx.serialization.json.JsonPrimitive("format"))
            })
        }
    }
}
