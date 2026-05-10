package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.model.JiraTicketMetadata
import com.orchestrator.mcp.sync.TicketCacheRepository
import com.orchestrator.mcp.sync.TicketGraphRepository
import com.orchestrator.mcp.sync.model.TicketCache
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.datetime.Instant

class BatchUpserterTest : DescribeSpec({

    val repository = mockk<TicketCacheRepository>()
    val graphRepository = mockk<TicketGraphRepository>(relaxed = true)
    val upserter = BatchUpserterImpl(repository, graphRepository)

    beforeEach { clearMocks(repository) }

    describe("upsertBatch") {
        it("converts metadata to TicketCache and delegates to repository") {
            val metadata = listOf(
                createMetadata("MTO-1", "First ticket"),
                createMetadata("MTO-2", "Second ticket")
            )
            coEvery { repository.upsertBatch(any()) } returns 2

            val result = upserter.upsertBatch(metadata)

            result shouldBe 2
            coVerify(exactly = 1) { repository.upsertBatch(match { it.size == 2 }) }
        }

        it("returns 0 for empty list without calling repository") {
            val result = upserter.upsertBatch(emptyList())

            result shouldBe 0
            coVerify(exactly = 0) { repository.upsertBatch(any()) }
        }

        it("maps fields correctly to TicketCache") {
            val metadata = listOf(
                createMetadata(
                    key = "PROJ-42",
                    summary = "Important task",
                    status = "In Progress",
                    issueType = "Story",
                    priority = "High",
                    assignee = "Alice"
                )
            )
            val captured = slot<List<TicketCache>>()
            coEvery { repository.upsertBatch(capture(captured)) } returns 1

            upserter.upsertBatch(metadata)

            val cache = captured.captured.first()
            cache.ticketKey shouldBe "PROJ-42"
            cache.projectKey shouldBe "PROJ"
            cache.summary shouldBe "Important task"
            cache.status shouldBe "In Progress"
            cache.issueType shouldBe "Story"
            cache.priority shouldBe "High"
            cache.kbIngested shouldBe false
        }
    }
})

private fun createMetadata(
    key: String = "MTO-1",
    summary: String = "Test",
    status: String = "To Do",
    issueType: String = "Task",
    priority: String = "Medium",
    assignee: String? = null
) = JiraTicketMetadata(
    issueKey = key,
    projectKey = key.substringBefore('-'),
    summary = summary,
    status = status,
    issueType = issueType,
    priority = priority,
    assignee = assignee,
    parentKey = null,
    labels = emptyList(),
    links = emptyList(),
    updatedAt = Instant.parse("2026-05-01T10:00:00Z")
)
