package com.orchestrator.mcp.promotion

import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * LRU-based cache for promoted tools with TTL expiry.
 * Thread-safe via ConcurrentHashMap.
 */
class PromotionCache(private val maxSize: Int) {

    private val tools = ConcurrentHashMap<String, PromotedTool>()

    fun put(tool: PromotedTool): PromotedTool? {
        var evicted: PromotedTool? = null
        if (tools.size >= maxSize && !tools.containsKey(tool.name)) {
            evicted = evictLru()
        }
        tools[tool.name] = tool
        return evicted
    }

    fun get(name: String): PromotedTool? {
        return tools[name]?.also { it.lastUsedAt = Clock.System.now() }
    }

    fun remove(name: String): PromotedTool? = tools.remove(name)

    fun getAll(): List<PromotedTool> = tools.values.toList()

    fun evictExpired(ttlSeconds: Long, clock: Clock = Clock.System): List<PromotedTool> {
        val now = clock.now()
        val expired = tools.values.filter { (now - it.lastUsedAt) > ttlSeconds.seconds }
        expired.forEach { tools.remove(it.name) }
        return expired
    }

    fun clear(): Int {
        val count = tools.size
        tools.clear()
        return count
    }

    fun size(): Int = tools.size

    private fun evictLru(): PromotedTool? {
        val lru = tools.values.minByOrNull { it.lastUsedAt } ?: return null
        return tools.remove(lru.name)
    }
}
