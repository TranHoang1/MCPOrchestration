package com.orchestrator.mcp.audit

import com.orchestrator.mcp.audit.model.AuditEvent
import com.orchestrator.mcp.audit.model.AuditEventType
import com.orchestrator.mcp.audit.repository.AuditEventRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest

class AuditServiceImplTest : FunSpec({

    lateinit var repository: AuditEventRepository
    lateinit var service: AuditServiceImpl

    beforeEach {
        repository = mockk(relaxed = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        service = AuditServiceImpl(repository, scope)
    }

    val testEvent = AuditEvent(
        eventType = AuditEventType.VIEW_BR,
        userId = "admin@test.com",
        issueKey = "PROJ-1",
        action = "View business rules",
        success = true
    )

    test("log fires and forgets — calls repository save") {
        service.log(testEvent)
        Thread.sleep(50)
        coVerify(atLeast = 1) { repository.save(any()) }
    }

    test("logSuspend calls repository save directly") {
        runTest {
            service.logSuspend(testEvent)
            coVerify(atLeast = 1) { repository.save(any()) }
        }
    }

    test("log does not throw when repository fails") {
        coEvery { repository.save(any()) } throws RuntimeException("DB down")
        // Should not throw
        service.log(testEvent)
        Thread.sleep(50)
        coVerify(atLeast = 1) { repository.save(any()) }
    }

    test("logSuspend does not throw when repository fails") {
        coEvery { repository.save(any()) } throws RuntimeException("DB down")
        runTest {
            service.logSuspend(testEvent)
        }
    }

    test("log preserves all event fields") {
        val slot = slot<AuditEvent>()
        coEvery { repository.save(capture(slot)) } just Runs

        service.log(testEvent)
        Thread.sleep(50)

        slot.captured.eventType shouldBe AuditEventType.VIEW_BR
        slot.captured.userId shouldBe "admin@test.com"
        slot.captured.issueKey shouldBe "PROJ-1"
        slot.captured.success shouldBe true
    }
})
