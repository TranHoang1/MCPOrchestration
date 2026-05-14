package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.model.SyncProgress
import com.orchestrator.mcp.sync.pipeline.model.SyncStatus
import com.orchestrator.mcp.sync.pipeline.state.SyncStateTracker
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// STC: UT-017, UT-018 — SyncStateTracker state transitions
class SyncStateTrackerTest : FunSpec({

    // In-memory implementation for testing state machine logic
    class InMemorySyncStateTracker : SyncStateTracker {
        private val states = mutableMapOf<String, SyncStatus>()
        private val errors = mutableMapOf<String, String>()
        private val progress = mutableMapOf<String, Pair<Int, Int>>()

        fun getStatus(projectKey: String) = states[projectKey] ?: SyncStatus.IDLE

        override suspend fun markRunning(projectKey: String) {
            val current = states[projectKey]
            if (current == SyncStatus.RUNNING) {
                error("Sync already running for $projectKey")
            }
            states[projectKey] = SyncStatus.RUNNING
        }

        override suspend fun markCompleted(projectKey: String) {
            states[projectKey] = SyncStatus.COMPLETED
        }

        override suspend fun markFailed(projectKey: String, errorMessage: String) {
            states[projectKey] = SyncStatus.FAILED
            errors[projectKey] = errorMessage
        }

        override suspend fun markCancelled(projectKey: String) {
            states[projectKey] = SyncStatus.CANCELLED
        }

        override suspend fun updateProgress(
            projectKey: String, syncedIssues: Int, totalIssues: Int
        ) {
            progress[projectKey] = syncedIssues to totalIssues
        }

        override suspend fun getLastSyncAt(projectKey: String): Instant? = null
        override suspend fun getProgress(projectKey: String): SyncProgress? {
            val (synced, total) = progress[projectKey] ?: return null
            return SyncProgress(
                projectKey = projectKey,
                status = states[projectKey] ?: SyncStatus.IDLE,
                totalIssues = total,
                syncedIssues = synced,
                currentOffset = synced,
                updatedAt = Clock.System.now(),
                errorMessage = errors[projectKey]
            )
        }

        override suspend fun isRunning(projectKey: String): Boolean {
            return states[projectKey] == SyncStatus.RUNNING
        }
    }

    test("IDLE -> markRunning -> RUNNING") {
        val tracker = InMemorySyncStateTracker()
        tracker.getStatus("PROJ") shouldBe SyncStatus.IDLE
        tracker.markRunning("PROJ")
        tracker.getStatus("PROJ") shouldBe SyncStatus.RUNNING
    }

    test("already RUNNING -> markRunning throws") {
        val tracker = InMemorySyncStateTracker()
        tracker.markRunning("PROJ")
        shouldThrow<IllegalStateException> {
            tracker.markRunning("PROJ")
        }
    }

    test("RUNNING -> markCompleted -> COMPLETED") {
        val tracker = InMemorySyncStateTracker()
        tracker.markRunning("PROJ")
        tracker.markCompleted("PROJ")
        tracker.getStatus("PROJ") shouldBe SyncStatus.COMPLETED
    }

    test("RUNNING -> markFailed -> FAILED with error message") {
        val tracker = InMemorySyncStateTracker()
        tracker.markRunning("PROJ")
        tracker.updateProgress("PROJ", 3, 10)
        tracker.markFailed("PROJ", "Connection timeout")
        tracker.getStatus("PROJ") shouldBe SyncStatus.FAILED
        val progress = tracker.getProgress("PROJ")
        progress?.status shouldBe SyncStatus.FAILED
        progress?.errorMessage shouldBe "Connection timeout"
    }

    test("RUNNING -> markCancelled -> CANCELLED") {
        val tracker = InMemorySyncStateTracker()
        tracker.markRunning("PROJ")
        tracker.markCancelled("PROJ")
        tracker.getStatus("PROJ") shouldBe SyncStatus.CANCELLED
    }

    test("updateProgress tracks synced and total issues") {
        val tracker = InMemorySyncStateTracker()
        tracker.markRunning("PROJ")
        tracker.updateProgress("PROJ", 5, 100)
        val progress = tracker.getProgress("PROJ")
        progress?.syncedIssues shouldBe 5
        progress?.totalIssues shouldBe 100
    }

    test("isRunning returns true only when RUNNING") {
        val tracker = InMemorySyncStateTracker()
        tracker.isRunning("PROJ") shouldBe false
        tracker.markRunning("PROJ")
        tracker.isRunning("PROJ") shouldBe true
        tracker.markCompleted("PROJ")
        tracker.isRunning("PROJ") shouldBe false
    }
})
