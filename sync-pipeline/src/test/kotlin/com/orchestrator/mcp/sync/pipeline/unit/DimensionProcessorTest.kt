package com.orchestrator.mcp.sync.pipeline.unit

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
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

// STC: UT-030, UT-031 — DimensionProcessor concurrent processing
class DimensionProcessorTest : FunSpec({

    fun fakeDimension(id: String, entryCount: Int = 1, shouldThrow: Boolean = false) =
        object : IndexDimension {
            override val dimensionId = id
            override val displayName = "Dim $id"
            override fun supportsVector() = false
            override suspend fun extract(
                ticket: CrawledTicket, config: DimensionConfig
            ): List<IndexEntry> {
                if (shouldThrow) throw RuntimeException("Dimension $id failed")
                return (1..entryCount).map { i ->
                    IndexEntry(
                        id = "$id-entry-$i",
                        dimensionId = id,
                        projectKey = ticket.projectKey,
                        ticketKey = ticket.key,
                        entryKey = "${ticket.key}:$id:$i",
                        sourceRef = SourceRef(
                            type = "test",
                            path = "test:${ticket.key}",
                            syncedAt = Clock.System.now()
                        ),
                        data = emptyMap(),
                        vectorText = null
                    )
                }
            }
        }

    fun configProvider(ids: List<String>) = object : DimensionConfigProvider {
        override suspend fun loadAll() = ids.associateWith { id ->
            SyncTestFixtures.dimensionConfig(id = id, sortOrder = ids.indexOf(id))
        }
    }

    test("process ticket calls all dimensions and collects entries") {
        val dims = listOf(
            fakeDimension("dim-a", entryCount = 2),
            fakeDimension("dim-b", entryCount = 3)
        )
        val registry = DimensionRegistry(dims, configProvider(listOf("dim-a", "dim-b")))
        val processor = DimensionProcessor(registry)

        val ticket = SyncTestFixtures.crawledTicket()
        val entries = processor.process(ticket, null)
        entries.size shouldBe 5 // 2 + 3
    }

    test("one dimension throws, others still succeed") {
        val dims = listOf(
            fakeDimension("good-1", entryCount = 2),
            fakeDimension("bad", shouldThrow = true),
            fakeDimension("good-2", entryCount = 1)
        )
        val registry = DimensionRegistry(
            dims, configProvider(listOf("good-1", "bad", "good-2"))
        )
        val processor = DimensionProcessor(registry)

        val ticket = SyncTestFixtures.crawledTicket()
        val entries = processor.process(ticket, null)
        entries.size shouldBe 3 // 2 + 0 (failed) + 1
    }

    test("empty dimension list returns empty entries") {
        val registry = DimensionRegistry(emptyList(), configProvider(emptyList()))
        val processor = DimensionProcessor(registry)

        val ticket = SyncTestFixtures.crawledTicket()
        val entries = processor.process(ticket, null)
        entries.size shouldBe 0
    }

    test("dimension filter restricts which dimensions run") {
        val dims = listOf(
            fakeDimension("dim-a", entryCount = 1),
            fakeDimension("dim-b", entryCount = 1),
            fakeDimension("dim-c", entryCount = 1)
        )
        val registry = DimensionRegistry(
            dims, configProvider(listOf("dim-a", "dim-b", "dim-c"))
        )
        val processor = DimensionProcessor(registry)

        val ticket = SyncTestFixtures.crawledTicket()
        val entries = processor.process(ticket, listOf("dim-b"))
        entries.size shouldBe 1
        entries.first().dimensionId shouldBe "dim-b"
    }
})
