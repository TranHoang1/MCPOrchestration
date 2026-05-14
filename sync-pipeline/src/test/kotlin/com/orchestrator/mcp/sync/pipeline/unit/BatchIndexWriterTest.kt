package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.deterministicId
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import com.orchestrator.mcp.sync.pipeline.model.SourceRef
import com.orchestrator.mcp.sync.pipeline.storage.BatchIndexWriter
import com.orchestrator.mcp.sync.pipeline.storage.IndexWriter
import com.orchestrator.mcp.sync.pipeline.storage.TicketSummaryRow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock

// STC: UT-024 — BatchIndexWriter buffer and flush behavior
class BatchIndexWriterTest : FunSpec({

    fun testEntry(i: Int) = IndexEntry(
        id = deterministicId("entry-$i"),
        dimensionId = "test",
        projectKey = "PROJ",
        ticketKey = "PROJ-$i",
        entryKey = "PROJ-$i:test",
        sourceRef = SourceRef(
            type = "jira_ticket",
            path = "jira:PROJ/PROJ-$i",
            syncedAt = Clock.System.now()
        ),
        data = mapOf("index" to i.toString()),
        vectorText = null
    )

    // Tracking delegate that records flush calls
    class TrackingWriter : IndexWriter {
        val flushedBatches = mutableListOf<List<IndexEntry>>()
        override suspend fun writeBatch(entries: List<IndexEntry>) {
            flushedBatches.add(entries.toList())
        }
        override suspend fun deleteByDimension(dimensionId: String, projectKey: String) {}
        override suspend fun getTicketSummaries(projectKey: String) = emptyList<TicketSummaryRow>()
    }

    test("buffer size 10: write 9 entries does NOT flush") {
        val delegate = TrackingWriter()
        val writer = BatchIndexWriter(delegate, bufferSize = 10)

        writer.writeBatch((1..9).map { testEntry(it) })
        delegate.flushedBatches.size shouldBe 0
        writer.pendingCount() shouldBe 9
    }

    test("buffer size 10: write 10 entries triggers flush") {
        val delegate = TrackingWriter()
        val writer = BatchIndexWriter(delegate, bufferSize = 10)

        writer.writeBatch((1..10).map { testEntry(it) })
        delegate.flushedBatches.size shouldBe 1
        delegate.flushedBatches.first().size shouldBe 10
        writer.pendingCount() shouldBe 0
    }

    test("buffer size 10: write 9 then 1 more triggers flush") {
        val delegate = TrackingWriter()
        val writer = BatchIndexWriter(delegate, bufferSize = 10)

        writer.writeBatch((1..9).map { testEntry(it) })
        delegate.flushedBatches.size shouldBe 0

        writer.writeBatch(listOf(testEntry(10)))
        delegate.flushedBatches.size shouldBe 1
        delegate.flushedBatches.first().size shouldBe 10
    }

    test("manual flush() flushes remaining entries") {
        val delegate = TrackingWriter()
        val writer = BatchIndexWriter(delegate, bufferSize = 100)

        writer.writeBatch((1..7).map { testEntry(it) })
        delegate.flushedBatches.size shouldBe 0

        writer.flush()
        delegate.flushedBatches.size shouldBe 1
        delegate.flushedBatches.first().size shouldBe 7
        writer.pendingCount() shouldBe 0
    }

    test("flush on empty buffer is no-op") {
        val delegate = TrackingWriter()
        val writer = BatchIndexWriter(delegate, bufferSize = 10)

        writer.flush()
        delegate.flushedBatches.size shouldBe 0
    }

    test("write 25 entries in batches of 9 and 16 with buffer 10 triggers correct flushes") {
        val delegate = TrackingWriter()
        val writer = BatchIndexWriter(delegate, bufferSize = 10)

        // First batch: 9 entries → buffer=9, no flush
        writer.writeBatch((1..9).map { testEntry(it) })
        delegate.flushedBatches.size shouldBe 0

        // Second batch: 16 entries → buffer=25, flush (25 entries)
        writer.writeBatch((10..25).map { testEntry(it) })
        delegate.flushedBatches.size shouldBe 1
        delegate.flushedBatches.first().size shouldBe 25
        writer.pendingCount() shouldBe 0
    }
})
