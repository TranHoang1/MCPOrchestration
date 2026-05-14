package com.orchestrator.mcp.sync.pipeline.integration

import com.orchestrator.mcp.sync.pipeline.SyncOrchestratorImpl
import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.crawl.JiraCrawlService
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionProcessor
import com.orchestrator.mcp.sync.pipeline.model.*
import com.orchestrator.mcp.sync.pipeline.state.SyncStateTracker
import com.orchestrator.mcp.sync.pipeline.storage.BatchIndexWriter
import com.orchestrator.mcp.sync.pipeline.storage.IndexWriter
import com.orchestrator.mcp.sync.pipeline.storage.TicketSummaryRow
import com.orchestrator.mcp.sync.pipeline.storage.VectorIndexWriter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// STC: IT-011 — SyncOrchestratorImpl full pipeline integration
class SyncOrchestratorImplTest : FunSpec({

    lateinit var crawlService: JiraCrawlService
    lateinit var dimensionProcessor: DimensionProcessor
    lateinit var batchWriter: BatchIndexWriter
    lateinit var vectorWriter: VectorIndexWriter
    lateinit var stateTracker: SyncStateTracker

    beforeTest {
        crawlService = mockk()
        dimensionProcessor = mockk()
        vectorWriter = mockk(relaxed = true)
        stateTracker = mockk(relaxed = true)
    }

    test("sync processes all tickets and transitions to COMPLETED") {
        val tickets = (1..5).map { SyncTestFixtures.crawledTicket(key = "T-$it") }
        val entries = listOf(
            IndexEntry(
                id = "e1", dimensionId = "test", projectKey = "PROJ",
                ticketKey = "T-1", entryKey = "T-1:test",
                sourceRef = SourceRef("test", "test:T-1", Clock.System.now()),
                data = emptyMap(), vectorText = null
            )
        )

        every { crawlService.crawlProject(any(), any(), any()) } returns flowOf(*tickets.toTypedArray())
        coEvery { dimensionProcessor.process(any(), any()) } returns entries
        coEvery { dimensionProcessor.runPostProcessors(any(), any()) } returns emptyList()
        coEvery { stateTracker.getLastSyncAt(any()) } returns null

        // Use a real BatchIndexWriter with a mock delegate
        val mockDelegate = mockk<IndexWriter>(relaxed = true)
        batchWriter = BatchIndexWriter(mockDelegate, bufferSize = 100)

        val orchestrator = SyncOrchestratorImpl(
            crawlService, dimensionProcessor, batchWriter, vectorWriter, stateTracker
        )

        val result = orchestrator.sync("PROJ", SyncOptions(fullSync = true))

        result.status shouldBe SyncStatus.COMPLETED
        coVerify { stateTracker.markRunning("PROJ") }
        coVerify { stateTracker.markCompleted("PROJ") }
        coVerify(exactly = 5) { dimensionProcessor.process(any(), any()) }
    }

    test("sync marks FAILED on exception") {
        every { crawlService.crawlProject(any(), any(), any()) } throws RuntimeException("Network error")
        coEvery { stateTracker.getLastSyncAt(any()) } returns null

        val mockDelegate = mockk<IndexWriter>(relaxed = true)
        batchWriter = BatchIndexWriter(mockDelegate, bufferSize = 100)

        val orchestrator = SyncOrchestratorImpl(
            crawlService, dimensionProcessor, batchWriter, vectorWriter, stateTracker
        )

        try {
            orchestrator.sync("PROJ", SyncOptions(fullSync = true))
        } catch (_: RuntimeException) {
            // expected
        }

        coVerify { stateTracker.markFailed("PROJ", "Network error") }
    }

    test("sync with fullSync=false uses lastSyncAt from tracker") {
        val lastSync = Clock.System.now()
        coEvery { stateTracker.getLastSyncAt("PROJ") } returns lastSync
        every { crawlService.crawlProject("PROJ", lastSync, any()) } returns flowOf()
        coEvery { dimensionProcessor.runPostProcessors(any(), any()) } returns emptyList()

        val mockDelegate = mockk<IndexWriter>(relaxed = true)
        batchWriter = BatchIndexWriter(mockDelegate, bufferSize = 100)

        val orchestrator = SyncOrchestratorImpl(
            crawlService, dimensionProcessor, batchWriter, vectorWriter, stateTracker
        )

        orchestrator.sync("PROJ", SyncOptions(fullSync = false))
        coVerify { stateTracker.getLastSyncAt("PROJ") }
    }
})
