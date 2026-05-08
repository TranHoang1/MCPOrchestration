package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.jira.model.JiraIssue
import com.orchestrator.mcp.jira.model.JiraSearchResponse
import com.orchestrator.mcp.scanner.config.ScannerConfig
import com.orchestrator.mcp.scanner.model.*
import com.orchestrator.mcp.sync.SyncStateManager
import com.orchestrator.mcp.sync.model.SyncState
import com.orchestrator.mcp.sync.model.SyncStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProjectScannerImplTest : DescribeSpec({

    val syncStateManager = mockk<SyncStateManager>(relaxed = true)
    val pageFetcher = mockk<PageFetcher>()
    val batchUpserter = mockk<BatchUpserter>()
    val metadataParser = MetadataParser()
    val jqlBuilder = JqlBuilder()
    val config = ScannerConfig()

    fun createScanner() = ProjectScannerImpl(
        syncStateManager, pageFetcher, batchUpserter, metadataParser, jqlBuilder, config
    )

    beforeEach { clearMocks(syncStateManager, pageFetcher, batchUpserter) }

    describe("scan - full scan") {
        it("completes a full scan with single page") {
            val scanner = createScanner()
            val idleState = createState("MTO", SyncStatus.IDLE)
            coEvery { syncStateManager.getOrCreate("MTO") } returns idleState
            coEvery { pageFetcher.fetchPage(any(), eq(0), eq(50)) } returns createResponse(2)
            coEvery { batchUpserter.upsertBatch(any()) } returns 2

            val result = scanner.scan("MTO", ScanOptions(forceFullScan = true))

            result.status shouldBe ScanStatus.COMPLETED
            result.scanType shouldBe ScanType.FULL
            result.syncedIssues shouldBe 2
            coVerify { syncStateManager.markRunning("MTO") }
            coVerify { syncStateManager.markCompleted("MTO") }
        }

        it("handles multi-page scan") {
            val scanner = createScanner()
            val idleState = createState("MTO", SyncStatus.IDLE)
            coEvery { syncStateManager.getOrCreate("MTO") } returns idleState
            // First page returns total=100
            coEvery { pageFetcher.fetchPage(any(), eq(0), eq(50)) } returns createResponse(50, total = 100)
            coEvery { pageFetcher.fetchPage(any(), eq(50), eq(50)) } returns createResponse(50, total = 100)
            coEvery { batchUpserter.upsertBatch(any()) } returns 50

            val result = scanner.scan("MTO", ScanOptions(concurrency = 1, forceFullScan = true))

            result.status shouldBe ScanStatus.COMPLETED
            result.syncedIssues shouldBe 100
        }
    }

    describe("scan - incremental") {
        it("uses incremental scan when lastSyncAt exists") {
            val scanner = createScanner()
            val completedState = createState("MTO", SyncStatus.COMPLETED, lastSyncAt = Instant.parse("2026-05-01T10:00:00Z"))
            coEvery { syncStateManager.getOrCreate("MTO") } returns completedState
            coEvery { pageFetcher.fetchPage(any(), eq(0), eq(50)) } returns createResponse(5)
            coEvery { batchUpserter.upsertBatch(any()) } returns 5

            val result = scanner.scan("MTO")

            result.scanType shouldBe ScanType.INCREMENTAL
            result.status shouldBe ScanStatus.COMPLETED
        }
    }

    describe("scan - validation") {
        it("throws InvalidProjectKeyException for invalid key") {
            val scanner = createScanner()
            val idleState = createState("invalid", SyncStatus.IDLE)
            coEvery { syncStateManager.getOrCreate(any()) } returns idleState

            try {
                scanner.scan("invalid")
                throw AssertionError("Should have thrown")
            } catch (e: InvalidProjectKeyException) {
                e.message shouldBe "Invalid project key: 'invalid'. Must match [A-Z][A-Z0-9_]+"
            }
        }
    }

    describe("getProgress") {
        it("returns progress for running scan") {
            val scanner = createScanner()
            val state = createState("MTO", SyncStatus.RUNNING, totalIssues = 100, syncedIssues = 50)
            coEvery { syncStateManager.getOrCreate("MTO") } returns state

            val progress = scanner.getProgress("MTO")

            progress?.percentage shouldBe 50
            progress?.status shouldBe SyncStatus.RUNNING
        }

        it("returns null for idle project with no history") {
            val scanner = createScanner()
            val state = createState("NEW", SyncStatus.IDLE, totalIssues = 0)
            coEvery { syncStateManager.getOrCreate("NEW") } returns state

            scanner.getProgress("NEW") shouldBe null
        }
    }

    describe("cancelScan") {
        it("returns false when no scan is running") {
            val scanner = createScanner()
            scanner.cancelScan("MTO") shouldBe false
        }
    }
})

private fun createState(
    projectKey: String,
    status: SyncStatus,
    lastSyncAt: Instant? = null,
    totalIssues: Int = 0,
    syncedIssues: Int = 0,
    lastOffset: Int = 0
) = SyncState(
    projectKey = projectKey,
    lastSyncAt = lastSyncAt,
    lastOffset = lastOffset,
    totalIssues = totalIssues,
    syncedIssues = syncedIssues,
    status = status,
    errorMessage = null,
    updatedAt = Clock.System.now()
)

private fun createResponse(issueCount: Int, total: Int = issueCount): JiraSearchResponse {
    val issues = (1..issueCount).map { i ->
        JiraIssue(
            id = "$i", key = "MTO-$i", self = "",
            fields = buildJsonObject {
                put("summary", "Issue $i")
                put("status", buildJsonObject { put("name", "To Do") })
                put("issuetype", buildJsonObject { put("name", "Task") })
                put("priority", buildJsonObject { put("name", "Medium") })
                put("updated", "2026-05-01T10:00:00.000+0000")
            }
        )
    }
    return JiraSearchResponse(startAt = 0, maxResults = 50, total = total, issues = issues)
}
