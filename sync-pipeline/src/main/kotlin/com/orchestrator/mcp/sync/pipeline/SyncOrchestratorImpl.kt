package com.orchestrator.mcp.sync.pipeline

import com.orchestrator.mcp.sync.pipeline.crawl.JiraCrawlService
import com.orchestrator.mcp.sync.pipeline.dimension.DimensionProcessor
import com.orchestrator.mcp.sync.pipeline.model.*
import com.orchestrator.mcp.sync.pipeline.state.SyncStateTracker
import com.orchestrator.mcp.sync.pipeline.storage.BatchIndexWriter
import com.orchestrator.mcp.sync.pipeline.storage.VectorIndexWriter
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.time.TimeSource

/**
 * Main sync orchestration: crawl → process → write → post-process.
 * Implements streaming pipeline with graceful error handling.
 */
class SyncOrchestratorImpl(
    private val crawlService: JiraCrawlService,
    private val dimensionProcessor: DimensionProcessor,
    private val batchWriter: BatchIndexWriter,
    private val vectorWriter: VectorIndexWriter,
    private val stateTracker: SyncStateTracker
) : SyncOrchestrator {

    private val logger = LoggerFactory.getLogger(SyncOrchestratorImpl::class.java)

    override suspend fun sync(projectKey: String, options: SyncOptions): SyncResult {
        validateProjectKey(projectKey)
        stateTracker.markRunning(projectKey)

        val mark = TimeSource.Monotonic.markNow()
        val stats = SyncStats()

        try {
            val lastSyncAt = resolveLastSync(projectKey, options)
            executePipeline(projectKey, lastSyncAt, options, stats)
            executePostProcessing(projectKey, options, stats)
            batchWriter.flush()
            vectorWriter.flush()
            stateTracker.markCompleted(projectKey)
            return stats.toResult(projectKey, mark.elapsedNow())
        } catch (e: CancellationException) {
            stateTracker.markFailed(projectKey, "Cancelled")
            throw e
        } catch (e: Exception) {
            stateTracker.markFailed(projectKey, e.message ?: "Unknown error")
            throw e
        }
    }

    override suspend fun getProgress(projectKey: String): SyncProgress? {
        return stateTracker.getProgress(projectKey)
    }

    override suspend fun cancel(projectKey: String): Boolean {
        return try {
            stateTracker.markCancelled(projectKey)
            true
        } catch (e: Exception) {
            logger.warn("Cancel failed for {}: {}", projectKey, e.message)
            false
        }
    }

    private suspend fun executePipeline(
        projectKey: String,
        lastSyncAt: kotlinx.datetime.Instant?,
        options: SyncOptions,
        stats: SyncStats
    ) {
        crawlService.crawlProject(projectKey, lastSyncAt, options)
            .collect { ticket ->
                processTicket(ticket, options, stats)
                stateTracker.updateProgress(projectKey, stats.processed, stats.total)
            }
    }

    private suspend fun processTicket(
        ticket: CrawledTicket,
        options: SyncOptions,
        stats: SyncStats
    ) {
        if (!options.fullSync && isUnchanged(ticket)) {
            stats.skipped++
            return
        }
        val entries = dimensionProcessor.process(ticket, options.dimensions)
        batchWriter.writeBatch(entries)
        queueVectorEntries(entries)
        stats.processed++
        stats.addEntries(entries)
    }

    private suspend fun isUnchanged(ticket: CrawledTicket): Boolean {
        return try {
            val existingHash = batchWriter.getContentHash(ticket.key, "ticket_metadata")
            existingHash != null && existingHash == ticket.contentHash
        } catch (e: Exception) {
            logger.warn("Hash check failed for {}: {}", ticket.key, e.message)
            false
        }
    }

    private suspend fun queueVectorEntries(entries: List<IndexEntry>) {
        entries.filter { it.vectorText != null }
            .forEach { vectorWriter.queue(it) }
    }

    private suspend fun executePostProcessing(
        projectKey: String,
        options: SyncOptions,
        stats: SyncStats
    ) {
        val postEntries = dimensionProcessor.runPostProcessors(projectKey, options.dimensions)
        if (postEntries.isNotEmpty()) {
            batchWriter.writeBatch(postEntries)
            queueVectorEntries(postEntries)
            stats.addEntries(postEntries)
        }
    }

    private suspend fun resolveLastSync(
        projectKey: String,
        options: SyncOptions
    ): kotlinx.datetime.Instant? {
        return if (options.fullSync) null else stateTracker.getLastSyncAt(projectKey)
    }

    private fun validateProjectKey(key: String) {
        require(key.isNotBlank()) { "Project key must not be blank" }
        require(key.length <= 20) { "Project key exceeds 20 characters" }
    }
}

/** Internal stats accumulator for sync progress. */
internal class SyncStats {
    var total: Int = 0
    var processed: Int = 0
    var skipped: Int = 0
    private val entriesByDimension = mutableMapOf<String, Int>()

    fun addEntries(entries: List<IndexEntry>) {
        for (entry in entries) {
            entriesByDimension.merge(entry.dimensionId, 1) { a, b -> a + b }
        }
    }

    fun toResult(projectKey: String, duration: kotlin.time.Duration) = SyncResult(
        projectKey = projectKey,
        totalTickets = total,
        processedTickets = processed,
        skippedTickets = skipped,
        entriesCreated = entriesByDimension.toMap(),
        duration = duration,
        status = SyncStatus.COMPLETED
    )
}
