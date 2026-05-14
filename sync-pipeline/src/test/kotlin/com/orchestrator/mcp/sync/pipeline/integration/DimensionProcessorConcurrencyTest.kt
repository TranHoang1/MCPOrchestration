package com.orchestrator.mcp.sync.pipeline.integration

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionConfigProvider
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionProcessor
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionRegistry
import com.orchestrator.mcp.sync.pipeline.dimension.IndexDimension
import com.orchestrator.mcp.sync.pipeline.model.CrawledTicket
import com.orchestrator.mcp.sync.pipeline.model.DimensionConfig
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import com.orchestrator.mcp.sync.pipeline.model.SourceRef
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.time.measureTime

// STC: IT-007 — DimensionProcessor concurrent execution
class DimensionProcessorConcurrencyTest : FunSpec({

    fun slowDimension(id: String, delayMs: Long) = object : IndexDimension {
        override val dimensionId = id
        override val displayName = "Slow $id"
        override fun supportsVector() = false
        override suspend fun extract(
            ticket: CrawledTicket, config: DimensionConfig
        ): List<IndexEntry> {
            delay(delayMs)
            return listOf(
                IndexEntry(
                    id = "$id-entry",
                    dimensionId = id,
                    projectKey = ticket.projectKey,
                    ticketKey = ticket.key,
                    entryKey = "${ticket.key}:$id",
                    sourceRef = SourceRef("test", "test:path", Clock.System.now()),
                    data = emptyMap(),
                    vectorText = null
                )
            )
        }
    }

    fun configProvider(ids: List<String>) = object : DimensionConfigProvider {
        override suspend fun loadAll() = ids.associateWith { id ->
            SyncTestFixtures.dimensionConfig(id = id, sortOrder = ids.indexOf(id))
        }
    }

    test("5 dimensions with 100ms delay each complete in < 300ms (parallel)") {
        val dimIds = (1..5).map { "dim-$it" }
        val dims = dimIds.map { slowDimension(it, 100L) }
        val registry = DimensionRegistry(dims, configProvider(dimIds))
        val processor = DimensionProcessor(registry)

        val ticket = SyncTestFixtures.crawledTicket()
        val elapsed = measureTime {
            val entries = processor.process(ticket, null)
            entries.size shouldBe 5
        }

        // If sequential: 5 * 100ms = 500ms. Parallel should be ~100-150ms.
        // Allow generous margin for CI: < 300ms
        elapsed.inWholeMilliseconds shouldBeLessThan 300L
    }

    test("dimensions run concurrently, not sequentially") {
        val dimIds = (1..3).map { "dim-$it" }
        val dims = dimIds.map { slowDimension(it, 50L) }
        val registry = DimensionRegistry(dims, configProvider(dimIds))
        val processor = DimensionProcessor(registry)

        val ticket = SyncTestFixtures.crawledTicket()
        val elapsed = measureTime {
            processor.process(ticket, null)
        }

        // Sequential would be 150ms+, parallel should be ~50-80ms
        elapsed.inWholeMilliseconds shouldBeLessThan 120L
    }
})
