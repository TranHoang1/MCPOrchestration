package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.config.ScannerConfig
import com.orchestrator.mcp.scanner.model.*
import com.orchestrator.mcp.sync.SyncStateManager
import com.orchestrator.mcp.sync.model.SyncState
import com.orchestrator.mcp.sync.model.SyncStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Main orchestrator for project scanning.
 * Coordinates page fetching, parsing, upserting, and checkpoint management.
 */
class ProjectScannerImpl(
    private val syncStateManager: SyncStateManager,
    private val pageFetcher: PageFetcher,
    private val batchUpserter: BatchUpserter,
    private val metadataParser: MetadataParser,
    private val jqlBuilder: JqlBuilder,
    private val config: ScannerConfig
) : ProjectScanner {

    private val logger = LoggerFactory.getLogger(ProjectScannerImpl::class.java)
    private val runningScans = ConcurrentHashMap<String, Job>()

    override suspend fun scan(projectKey: String, options: ScanOptions): ScanResult {
        validateProjectKey(projectKey)
        val state = syncStateManager.getOrCreate(projectKey)
        checkNotAlreadyRunning(projectKey, state)

        val scanType = determineScanType(state, options)
        val jql = jqlBuilder.build(projectKey, scanType, state.lastSyncAt)
        val startOffset = if (scanType == ScanType.RESUMED) state.lastOffset else 0

        logger.info("Starting {} scan for project '{}' from offset {}", scanType, projectKey, startOffset)
        return executeScan(projectKey, jql, startOffset, options, scanType)
    }

    override suspend fun getProgress(projectKey: String): ScanProgress? {
        val state = syncStateManager.getOrCreate(projectKey)
        if (state.status == SyncStatus.IDLE && state.totalIssues == 0) return null
        val pct = if (state.totalIssues > 0) (state.syncedIssues * 100) / state.totalIssues else 0
        return ScanProgress(
            projectKey = projectKey,
            status = state.status,
            totalIssues = state.totalIssues,
            syncedIssues = state.syncedIssues,
            percentage = pct.coerceIn(0, 100),
            startedAt = null,
            lastSyncTime = state.lastSyncAt
        )
    }

    override suspend fun cancelScan(projectKey: String): Boolean {
        val job = runningScans.remove(projectKey) ?: return false
        job.cancel("Scan cancelled by user")
        syncStateManager.markFailed(projectKey, "Cancelled by user")
        logger.info("Scan cancelled for project '{}'", projectKey)
        return true
    }

    private suspend fun executeScan(
        projectKey: String, jql: String, startOffset: Int,
        options: ScanOptions, scanType: ScanType
    ): ScanResult {
        val mark = TimeSource.Monotonic.markNow()
        val syncedCount = AtomicInteger(0)
        val skippedCount = AtomicInteger(0)

        return coroutineScope {
            val job = coroutineContext.job
            runningScans[projectKey] = job

            try {
                val firstPage = pageFetcher.fetchPage(jql, startOffset, options.pageSize)
                val totalIssues = firstPage.total
                syncStateManager.markRunning(projectKey)
                syncStateManager.updateProgress(projectKey, startOffset, 0)

                processPage(firstPage.issues, projectKey, startOffset, syncedCount, skippedCount)
                fetchRemainingPages(projectKey, jql, startOffset + options.pageSize, totalIssues, options, syncedCount, skippedCount)

                syncStateManager.markCompleted(projectKey)
                logger.info("Scan completed for '{}': {} synced, {} skipped", projectKey, syncedCount.get(), skippedCount.get())

                ScanResult(totalIssues, syncedCount.get(), skippedCount.get(), mark.elapsedNow(), scanType, ScanStatus.COMPLETED)
            } catch (e: CancellationException) {
                ScanResult(0, syncedCount.get(), skippedCount.get(), mark.elapsedNow(), scanType, ScanStatus.CANCELLED)
            } catch (e: Exception) {
                syncStateManager.markFailed(projectKey, e.message ?: "Unknown error")
                logger.error("Scan failed for '{}': {}", projectKey, e.message)
                ScanResult(0, syncedCount.get(), skippedCount.get(), mark.elapsedNow(), scanType, ScanStatus.FAILED)
            } finally {
                runningScans.remove(projectKey)
            }
        }
    }

    private suspend fun fetchRemainingPages(
        projectKey: String, jql: String, startOffset: Int, totalIssues: Int,
        options: ScanOptions, syncedCount: AtomicInteger, skippedCount: AtomicInteger
    ) = coroutineScope {
        val semaphore = Semaphore(options.concurrency)
        val pageOffsets = (startOffset until totalIssues step options.pageSize).toList()

        pageOffsets.map { offset ->
            async(SupervisorJob()) {
                semaphore.acquire()
                try {
                    val page = pageFetcher.fetchPage(jql, offset, options.pageSize)
                    processPage(page.issues, projectKey, offset, syncedCount, skippedCount)
                } finally {
                    semaphore.release()
                }
            }
        }.forEach { it.await() }
    }

    private suspend fun processPage(
        issues: List<com.orchestrator.mcp.jira.model.JiraIssue>,
        projectKey: String, offset: Int,
        syncedCount: AtomicInteger, skippedCount: AtomicInteger
    ) {
        val metadata = metadataParser.parse(issues)
        val skipped = issues.size - metadata.size
        skippedCount.addAndGet(skipped)

        val upserted = batchUpserter.upsertBatch(metadata)
        syncedCount.addAndGet(upserted)
        syncStateManager.updateProgress(projectKey, offset + issues.size, syncedCount.get())
    }

    private fun determineScanType(state: SyncState, options: ScanOptions): ScanType = when {
        state.status == SyncStatus.RUNNING && !isStale(state) -> ScanType.RESUMED
        options.forceFullScan || state.lastSyncAt == null -> ScanType.FULL
        else -> ScanType.INCREMENTAL
    }

    private fun isStale(state: SyncState): Boolean {
        val elapsed = Clock.System.now() - state.updatedAt
        return elapsed > config.staleTimeoutSeconds.seconds
    }

    private fun checkNotAlreadyRunning(projectKey: String, state: SyncState) {
        if (state.status == SyncStatus.RUNNING && !isStale(state) && runningScans.containsKey(projectKey)) {
            throw ScanAlreadyRunningException(projectKey)
        }
    }

    companion object {
        private val PROJECT_KEY_PATTERN = Regex("^[A-Z][A-Z0-9_]+$")

        fun validateProjectKey(projectKey: String) {
            if (!PROJECT_KEY_PATTERN.matches(projectKey)) {
                throw InvalidProjectKeyException(projectKey)
            }
        }
    }
}
