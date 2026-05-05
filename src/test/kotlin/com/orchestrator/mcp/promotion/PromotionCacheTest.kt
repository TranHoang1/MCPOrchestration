package com.orchestrator.mcp.promotion

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration.Companion.seconds

class PromotionCacheTest : DescribeSpec({

    fun createTool(name: String, clock: Clock = Clock.System): PromotedTool {
        return PromotedTool(
            name = name,
            upstreamServer = "test-server",
            originalSchema = buildJsonObject {},
            compactSchema = buildJsonObject {},
            compactDescription = "Test tool $name",
            promotedAt = clock.now(),
            lastUsedAt = clock.now()
        )
    }

    describe("put") {
        it("should add tool to cache") {
            val cache = PromotionCache(10)
            cache.put(createTool("tool1"))
            cache.size() shouldBe 1
        }

        it("should evict LRU tool when at capacity") {
            val cache = PromotionCache(2)
            cache.put(createTool("tool1"))
            cache.put(createTool("tool2"))
            val evicted = cache.put(createTool("tool3"))
            evicted shouldNotBe null
            cache.size() shouldBe 2
        }
    }

    describe("get") {
        it("should return tool by name") {
            val cache = PromotionCache(10)
            cache.put(createTool("tool1"))
            val tool = cache.get("tool1")
            tool shouldNotBe null
            tool!!.name shouldBe "tool1"
        }

        it("should return null for unknown tool") {
            val cache = PromotionCache(10)
            cache.get("unknown") shouldBe null
        }
    }

    describe("evictExpired") {
        it("should remove tools past TTL") {
            val fixedClock = object : Clock {
                var now = Instant.fromEpochMilliseconds(0)
                override fun now() = now
            }
            val cache = PromotionCache(10)
            cache.put(createTool("tool1", fixedClock))

            // Advance time past TTL (300 seconds)
            fixedClock.now = Instant.fromEpochMilliseconds(301.seconds.inWholeMilliseconds)
            val expired = cache.evictExpired(300, fixedClock)
            expired.size shouldBe 1
            cache.size() shouldBe 0
        }
    }

    describe("clear") {
        it("should remove all tools and return count") {
            val cache = PromotionCache(10)
            cache.put(createTool("tool1"))
            cache.put(createTool("tool2"))
            val count = cache.clear()
            count shouldBe 2
            cache.size() shouldBe 0
        }
    }
})
