package com.orchestrator.mcp.dashboard

import com.orchestrator.mcp.scanner.ProjectScanner
import com.orchestrator.mcp.scanner.model.ScanOptions
import com.orchestrator.mcp.scanner.model.ScanResult
import com.orchestrator.mcp.scanner.model.ScanStatus
import com.orchestrator.mcp.scanner.model.ScanType
import com.orchestrator.mcp.sync.SyncStateManager
import com.orchestrator.mcp.sync.model.SyncState
import com.orchestrator.mcp.sync.model.SyncStatus as SyncStateStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

class SyncDashboardServiceTest : FunSpec({

    val syncStateManager = mockk<SyncStateManager>()
    val projectScanner = mockk<ProjectScanner>()
    val service = SyncDashboardService(syncStateManager, projectScanner)

    test("getProjectStatus returns correct percentage") {
        coEvery { syncStateManager.getOrCreate("MTO") } returns SyncState(
            projectKey = "MTO",
            lastSyncAt = null,
            lastOffset = 0,
            totalIssues = 200,
            syncedIssues = 100,
            status = SyncStateStatus.RUNNING,
            errorMessage = null,
            updatedAt = Clock.System.now()
        )

        val result = service.getProjectStatus("MTO")
        result!!.percentage shouldBe 50
        result.status shouldBe "RUNNING"
    }

    test("getProjectStatus returns 0 percentage when no issues") {
        coEvery { syncStateManager.getOrCreate("EMPTY") } returns SyncState(
            projectKey = "EMPTY",
            lastSyncAt = null,
            lastOffset = 0,
            totalIssues = 0,
            syncedIssues = 0,
            status = SyncStateStatus.IDLE,
            errorMessage = null,
            updatedAt = Clock.System.now()
        )

        val result = service.getProjectStatus("EMPTY")
        result!!.percentage shouldBe 0
    }

    test("startSync returns success on valid project") {
        coEvery { projectScanner.scan("MTO", any()) } returns ScanResult(
            totalIssues = 100,
            syncedIssues = 100,
            skippedIssues = 0,
            duration = 10.seconds,
            scanType = ScanType.FULL,
            status = ScanStatus.COMPLETED
        )

        val result = service.startSync("MTO", false)
        result.success shouldBe true
    }

    test("stopSync returns failure when no scan running") {
        coEvery { projectScanner.cancelScan("MTO") } returns false

        val result = service.stopSync("MTO")
        result.success shouldBe false
    }
})
