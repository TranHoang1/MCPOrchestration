package com.orchestrator.mcp.dashboard

import com.orchestrator.mcp.dashboard.model.ProgressEvent
import com.orchestrator.mcp.dashboard.model.CompletedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class SyncEventBusTest : FunSpec({

    test("emit event is received by collector") {
        runTest {
            val bus = SyncEventBus()
            val event = ProgressEvent(
                projectKey = "MTO",
                syncedIssues = 50,
                totalIssues = 100,
                percentage = 50
            )

            val received = launch {
                val collected = bus.events.first()
                collected.type shouldBe "progress"
                collected.projectKey shouldBe "MTO"
            }

            // Wait for collector coroutine to subscribe
            bus.awaitSubscribers(1)
            bus.emit(event)
            received.join()
        }
    }

    test("completed event has correct type") {
        val event = CompletedEvent(
            projectKey = "TEST",
            totalSynced = 200,
            durationMs = 5000
        )
        event.type shouldBe "completed"
        event.projectKey shouldBe "TEST"
    }
})
