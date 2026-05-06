package com.orchestrator.mcp.promotion

import com.orchestrator.mcp.core.model.ToolEntry
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.JsonObject

/**
 * Interface for Smart Tool Promotion (Part F).
 * Promotes frequently-used discovered tools to the top-level tools/list
 * for faster access without re-discovery.
 */
interface SmartToolPromoter {
    suspend fun promoteTools(discoveredTools: List<ToolEntry>): PromotionResult
    fun getPromotedTools(): List<PromotedTool>
    suspend fun executePromotedTool(name: String, args: JsonObject?): CallToolResult
    fun demoteTool(name: String)
    fun resetAll(): Int
    fun isPromoted(toolName: String): Boolean
}

data class PromotionResult(
    val promoted: List<String>,
    val evicted: List<String>,
    val totalActive: Int
)
