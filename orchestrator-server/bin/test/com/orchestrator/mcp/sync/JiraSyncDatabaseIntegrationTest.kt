package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.*
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.Clock
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Integration tests for Jira sync database operations using Testcontainers.
 * STC: TC-IT-01 through TC-IT-22
 *
 * Requires Docker to be running. Tests are skipped if Docker is unavailable.
 */
@EnabledIf(DockerAvailableCondition::class)
class JiraSyncDatabaseIntegrationTest : FunSpec({

    val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    lateinit var dataSource: HikariDataSource
    lateinit var manager: SyncStateManagerImpl
    lateinit var ticketRepo: TicketCacheRepositoryImpl
    lateinit var graphRepo: TicketGraphRepositoryImpl
    lateinit var attachmentRepo: AttachmentQueueRepositoryImpl

    beforeSpec {
        postgres.start()
        dataSource = HikariDataSource().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            maximumPoolSize = 5
        }
        JiraSyncDatabaseInitializer(dataSource).initialize()
        manager = SyncStateManagerImpl(dataSource)
        ticketRepo = TicketCacheRepositoryImpl(dataSource)
        graphRepo = TicketGraphRepositoryImpl(dataSource)
        attachmentRepo = AttachmentQueueRepositoryImpl(dataSource)
    }

    afterSpec {
        dataSource.close()
        postgres.stop()
    }

    beforeEach {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM jira_attachment_queue")
                stmt.execute("DELETE FROM jira_ticket_graph")
                stmt.execute("DELETE FROM jira_ticket_cache")
                stmt.execute("DELETE FROM jira_sync_state")
            }
        }
    }

    // STC: TC-IT-19 — Migration creates all 4 tables
    test("migration creates all 4 tables") {
        val tables = dataSource.connection.use { conn ->
            val rs = conn.createStatement().executeQuery(
                """SELECT table_name FROM information_schema.tables 
                   WHERE table_schema = 'public' 
                   AND table_name LIKE 'jira_%'"""
            )
            val names = mutableListOf<String>()
            while (rs.next()) names.add(rs.getString("table_name"))
            names
        }
        tables.sorted() shouldBe listOf(
            "jira_attachment_queue",
            "jira_sync_state",
            "jira_ticket_cache",
            "jira_ticket_graph"
        )
    }

    // STC: TC-IT-20 — Migration is idempotent
    test("migration is idempotent - second run does not throw") {
        manager.getOrCreate("IDEM")
        JiraSyncDatabaseInitializer(dataSource).initialize()
        manager.getStatus("IDEM") shouldBe SyncStatus.IDLE
    }

    // STC: TC-IT-01 — getOrCreate inserts new record
    test("sync state - getOrCreate inserts new record") {
        val state = manager.getOrCreate("MTO")
        state.projectKey shouldBe "MTO"
        state.status shouldBe SyncStatus.IDLE
        state.lastOffset shouldBe 0

        val state2 = manager.getOrCreate("MTO")
        state2.status shouldBe SyncStatus.IDLE
    }

    // STC: TC-IT-02 — Full lifecycle
    test("sync state - full lifecycle IDLE -> RUNNING -> COMPLETED") {
        manager.getOrCreate("PROJ")
        manager.markRunning("PROJ")
        manager.getStatus("PROJ") shouldBe SyncStatus.RUNNING

        manager.updateProgress("PROJ", 50, 25)
        val state = manager.getOrCreate("PROJ")
        state.lastOffset shouldBe 50
        state.syncedIssues shouldBe 25

        manager.markCompleted("PROJ")
        manager.getStatus("PROJ") shouldBe SyncStatus.COMPLETED
    }

    // STC: TC-IT-03 — Optimistic locking
    test("sync state - concurrent markRunning only one succeeds") {
        manager.getOrCreate("RACE")

        val results = (1..2).map {
            async {
                try {
                    manager.markRunning("RACE")
                    true
                } catch (_: IllegalStateException) {
                    false
                }
            }
        }.awaitAll()

        results.count { it } shouldBe 1
        results.count { !it } shouldBe 1
        manager.getStatus("RACE") shouldBe SyncStatus.RUNNING
    }

    // STC: TC-IT-04 — Ticket cache single UPSERT
    test("ticket cache - single upsert creates new ticket") {
        val ticket = createTestTicket("MTO-15")
        ticketRepo.upsert(ticket)

        val found = ticketRepo.findByProject("MTO")
        found shouldHaveSize 1
        found[0].ticketKey shouldBe "MTO-15"
        found[0].kbIngested shouldBe false
    }

    // STC: TC-IT-05 — UPSERT updates existing
    test("ticket cache - upsert updates existing ticket") {
        val ticket = createTestTicket("MTO-15", summary = "Original")
        ticketRepo.upsert(ticket)

        val updated = ticket.copy(summary = "Updated", contentHash = "b".repeat(64))
        ticketRepo.upsert(updated)

        val found = ticketRepo.findByProject("MTO")
        found shouldHaveSize 1
        found[0].summary shouldBe "Updated"
    }

    // STC: TC-IT-06 — Batch UPSERT 100 tickets
    test("ticket cache - batch upsert 100 tickets") {
        val tickets = (1..100).map { createTestTicket("MTO-$it") }
        val count = ticketRepo.upsertBatch(tickets)
        count shouldBe 100

        val found = ticketRepo.findByProject("MTO")
        found shouldHaveSize 100
    }

    // STC: TC-IT-07 — findNotIngested
    test("ticket cache - findNotIngested returns only unprocessed") {
        (1..5).forEach { ticketRepo.upsert(createTestTicket("MTO-$it")) }
        ticketRepo.markIngested("MTO-1")
        ticketRepo.markIngested("MTO-2")

        val notIngested = ticketRepo.findNotIngested("MTO")
        notIngested shouldHaveSize 3
    }

    // STC: TC-IT-08 — Graph insert new relationship
    test("ticket graph - insert new relationship") {
        val rel = TicketRelation("MTO-15", "MTO-14", "blocks", RelationCategory.OUTWARD)
        graphRepo.insertRelation(rel)

        val outgoing = graphRepo.findOutgoing("MTO-15")
        outgoing shouldHaveSize 1
        outgoing[0].targetKey shouldBe "MTO-14"
    }

    // STC: TC-IT-09 — Duplicate edge ignored
    test("ticket graph - duplicate edge ignored") {
        val rel = TicketRelation("MTO-15", "MTO-14", "blocks", RelationCategory.OUTWARD)
        graphRepo.insertRelation(rel)
        graphRepo.insertRelation(rel)

        val outgoing = graphRepo.findOutgoing("MTO-15")
        outgoing shouldHaveSize 1
    }

    // STC: TC-IT-10 — findOutgoing and findIncoming
    test("ticket graph - findOutgoing and findIncoming") {
        graphRepo.insertRelation(TicketRelation("MTO-15", "MTO-14", "blocks", RelationCategory.OUTWARD))
        graphRepo.insertRelation(TicketRelation("MTO-15", "MTO-16", "relates", RelationCategory.OUTWARD))
        graphRepo.insertRelation(TicketRelation("MTO-17", "MTO-15", "depends", RelationCategory.INWARD))

        graphRepo.findOutgoing("MTO-15") shouldHaveSize 2
        graphRepo.findIncoming("MTO-15") shouldHaveSize 1
    }

    // STC: TC-IT-11 — Attachment enqueue and poll
    test("attachment queue - enqueue and poll") {
        attachmentRepo.enqueue(createTestAttachment("MTO-15", "att-001"))
        attachmentRepo.enqueue(createTestAttachment("MTO-15", "att-002"))

        val pending = attachmentRepo.pollPending(5)
        pending shouldHaveSize 2
        pending[0].status shouldBe AttachmentStatus.PENDING
        pending[0].retryCount shouldBe 0
    }

    // STC: TC-IT-12 — Duplicate prevention
    test("attachment queue - duplicate prevention") {
        val item = createTestAttachment("MTO-15", "att-001")
        attachmentRepo.enqueue(item)
        attachmentRepo.enqueue(item)

        val found = attachmentRepo.findByTicket("MTO-15")
        found shouldHaveSize 1
    }

    // STC: TC-IT-13 — markDone sets processed_at
    test("attachment queue - markDone sets processed_at") {
        attachmentRepo.enqueue(createTestAttachment("MTO-15", "att-001"))
        val items = attachmentRepo.pollPending(1)
        val id = items[0].id

        attachmentRepo.markDone(id)

        val found = attachmentRepo.findByTicket("MTO-15")
        found[0].status shouldBe AttachmentStatus.DONE
        found[0].processedAt shouldNotBe null
    }

    // STC: TC-IT-14 — incrementRetry
    test("attachment queue - incrementRetry updates count and error") {
        attachmentRepo.enqueue(createTestAttachment("MTO-15", "att-001"))
        val items = attachmentRepo.pollPending(1)
        val id = items[0].id

        attachmentRepo.incrementRetry(id, "Timeout")
        val after1 = attachmentRepo.findByTicket("MTO-15")[0]
        after1.retryCount shouldBe 1
        after1.errorMessage shouldBe "Timeout"

        attachmentRepo.incrementRetry(id, "Server 500")
        val after2 = attachmentRepo.findByTicket("MTO-15")[0]
        after2.retryCount shouldBe 2
        after2.errorMessage shouldBe "Server 500"
    }

    // STC: TC-IT-15 — Full state machine in real DB
    test("sync state - full state machine lifecycle") {
        manager.getOrCreate("TEST")
        manager.markRunning("TEST")
        manager.markPaused("TEST")
        manager.markRunning("TEST")
        manager.markFailed("TEST", "error")
        manager.markRunning("TEST")
        manager.markCompleted("TEST")
        manager.getStatus("TEST") shouldBe SyncStatus.COMPLETED
    }

    // STC: TC-IT-16 — Invalid transitions in real DB
    test("sync state - invalid transitions from COMPLETED") {
        manager.getOrCreate("TEST")
        manager.markRunning("TEST")
        manager.markCompleted("TEST")

        shouldThrow<IllegalStateException> { manager.markRunning("TEST") }
        shouldThrow<IllegalStateException> { manager.markPaused("TEST") }
        shouldThrow<IllegalStateException> { manager.markFailed("TEST", "x") }

        manager.getStatus("TEST") shouldBe SyncStatus.COMPLETED
    }

    // STC: TC-IT-17 — updateProgress atomic update
    test("sync state - updateProgress atomic update") {
        manager.getOrCreate("MTO")
        manager.markRunning("MTO")

        manager.updateProgress("MTO", offset = 100, synced = 50)
        val state = manager.getOrCreate("MTO")
        state.lastOffset shouldBe 100
        state.syncedIssues shouldBe 50
    }

    // STC: TC-IT-18 — Concurrent updateProgress
    test("sync state - concurrent updateProgress no corruption") {
        manager.getOrCreate("MTO")
        manager.markRunning("MTO")

        (1..10).map { i ->
            async { manager.updateProgress("MTO", offset = i * 10, synced = i * 5) }
        }.awaitAll()

        val state = manager.getOrCreate("MTO")
        state.lastOffset shouldBeGreaterThan 0
        state.syncedIssues shouldBeGreaterThan 0
    }
})

private fun createTestTicket(key: String, summary: String = "Test ticket $key"): TicketCache {
    return TicketCache(
        ticketKey = key,
        projectKey = key.substringBefore("-"),
        summary = summary,
        issueType = "Story",
        status = "Open",
        priority = "Medium",
        parentKey = null,
        epicKey = null,
        labels = listOf("test"),
        createdAt = Clock.System.now(),
        updatedAtJira = Clock.System.now(),
        contentHash = "a".repeat(64)
    )
}

private fun createTestAttachment(ticketKey: String, attachmentId: String): AttachmentQueueItem {
    return AttachmentQueueItem(
        ticketKey = ticketKey,
        attachmentId = attachmentId,
        filename = "test-file.pdf",
        mimeType = "application/pdf",
        sizeBytes = 1024L,
        downloadUrl = "https://jira.example.com/attachments/$attachmentId"
    )
}
