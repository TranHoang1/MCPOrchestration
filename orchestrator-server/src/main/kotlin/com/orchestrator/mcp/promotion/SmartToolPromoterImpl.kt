package com.orchestrator.mcp.promotion

import com.orchestrator.mcp.execution.ToolExecutionDispatcher
import com.orchestrator.mcp.core.model.ToolEntry
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Implementation of Smart Tool Promotion.
 * Promotes discovered tools to top-level tools/list for direct access.
 */
class SmartToolPromoterImpl(
    private val config: SmartPromotionConfig,
    private val executionDispatcher: ToolExecutionDispatcher,
    private val clock: Clock = Clock.System
) : SmartToolPromoter {

    private val logger = LoggerFactory.getLogger(SmartToolPromoterImpl::class.java)
    private val cache = PromotionCache(config.maxPromoted)

    override suspend fun promoteTools(discoveredTools: List<ToolEntry>): PromotionResult {
        if (!config.enabled) return PromotionResult(emptyList(), emptyList(), cache.size())

        val promoted = mutableListOf<String>()
        val evicted = mutableListOf<String>()

        discoveredTools.forEach { tool ->
            val (compactDesc, compactSchema) = CompactSchemaGenerator.generate(tool)
            val promotedTool = PromotedTool(
                name = tool.name,
                upstreamServer = tool.serverName,
                originalSchema = tool.inputSchema ?: kotlinx.serialization.json.buildJsonObject {},
                compactSchema = compactSchema,
                compactDescription = compactDesc,
                promotedAt = clock.now(),
                lastUsedAt = clock.now()
            )
            val evictedTool = cache.put(promotedTool)
            promoted.add(tool.name)
            evictedTool?.let { evicted.add(it.name) }
        }

        logger.info("Promoted ${promoted.size} tools, evicted ${evicted.size}")
        return PromotionResult(promoted, evicted, cache.size())
    }

    override fun getPromotedTools(): List<PromotedTool> {
        return cache.getAll().filter { it.status == PromotionStatus.ACTIVE }
    }

    override suspend fun executePromotedTool(name: String, args: JsonObject?): CallToolResult {
        val tool = cache.get(name)
            ?: return CallToolResult(
                content = listOf(TextContent(text = "Tool '$name' is not promoted")),
                isError = true
            )
        tool.callCount++
        return try {
            val response = executionDispatcher.execute(name, args)
            val textContents = response.content.map { TextContent(text = it.text) }
            CallToolResult(content = textContents)
        } catch (e: Exception) {
            tool.status = PromotionStatus.FAILED
            CallToolResult(content = listOf(TextContent(text = "Execution failed: ${e.message}")), isError = true)
        }
    }

    override fun demoteTool(name: String) {
        cache.remove(name)?.let {
            logger.info("Demoted tool: $name")
        }
    }

    override fun resetAll(): Int {
        val count = cache.clear()
        logger.info("Reset all promoted tools ($count cleared)")
        return count
    }

    override fun isPromoted(toolName: String): Boolean {
        return cache.get(toolName)?.status == PromotionStatus.ACTIVE
    }

    fun cleanupExpired(): List<PromotedTool> {
        return cache.evictExpired(config.ttlSeconds, clock)
    }
}
