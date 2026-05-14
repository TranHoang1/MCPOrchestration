package com.orchestrator.mcp.sync.pipeline.unit

import com.orchestrator.mcp.sync.pipeline.SyncTestFixtures
import com.orchestrator.mcp.sync.pipeline.dimension.builtin.TicketMetadataDimension
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

// STC: UT-004, UT-005 — TicketMetadataDimension extraction
class TicketMetadataDimensionTest : FunSpec({

    val dimension = TicketMetadataDimension()
    val config = SyncTestFixtures.dimensionConfig(id = "ticket_metadata")

    test("full ticket produces exactly 1 IndexEntry") {
        val ticket = SyncTestFixtures.crawledTicket()
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 1
    }

    test("entry contains all expected data fields") {
        val ticket = SyncTestFixtures.crawledTicket()
        val entry = dimension.extract(ticket, config).first()

        entry.data["summary"] shouldBe ticket.summary
        entry.data["issue_type"] shouldBe "Story"
        entry.data["status"] shouldBe "In Progress"
        entry.data["priority"] shouldBe "High"
        entry.data["assignee_id"] shouldBe "assignee-1"
        entry.data["reporter_id"] shouldBe "reporter-1"
        entry.data["labels"] shouldBe "backend,api"
        entry.data["components"] shouldBe "sync-pipeline"
    }

    test("sourceRef path format is correct") {
        val ticket = SyncTestFixtures.crawledTicket(key = "PROJ-42", projectKey = "PROJ")
        val entry = dimension.extract(ticket, config).first()
        entry.sourceRef.path shouldBe "jira:PROJ/PROJ-42"
        entry.sourceRef.type shouldBe "jira_ticket"
    }

    test("entryKey format is ticketKey:metadata") {
        val ticket = SyncTestFixtures.crawledTicket(key = "ABC-99")
        val entry = dimension.extract(ticket, config).first()
        entry.entryKey shouldBe "ABC-99:metadata"
    }

    test("vectorText contains issue type, key, and summary") {
        val ticket = SyncTestFixtures.crawledTicket(key = "TEST-1")
        val entry = dimension.extract(ticket, config).first()
        entry.vectorText shouldNotBe null
        entry.vectorText!! shouldContain "[Story]"
        entry.vectorText!! shouldContain "TEST-1"
        entry.vectorText!! shouldContain ticket.summary
    }

    test("minimal ticket with null optional fields still works") {
        val ticket = SyncTestFixtures.crawledTicket().copy(
            priority = null,
            assignee = null,
            reporter = null,
            parentKey = null,
            epicKey = null,
            labels = emptyList(),
            components = emptyList(),
            fixVersions = emptyList(),
            storyPoints = null,
            sprint = null,
            resolvedAt = null
        )
        val entries = dimension.extract(ticket, config)
        entries.size shouldBe 1
        entries.first().data["priority"] shouldBe null
        entries.first().data["assignee_id"] shouldBe null
        entries.first().data["labels"] shouldBe null
    }

    test("sourceRef contentHash matches ticket contentHash") {
        val ticket = SyncTestFixtures.crawledTicket()
        val entry = dimension.extract(ticket, config).first()
        entry.sourceRef.contentHash shouldBe "abc123hash"
    }
})
