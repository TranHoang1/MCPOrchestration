package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.SyncStatus
import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Array as SqlArray

/**
 * Unit tests for SyncStateManagerImpl state machine logic.
 * STC: TC-UT-01, TC-UT-02, TC-UT-12 through TC-UT-20
 */
class SyncStateManagerImplTest : FunSpec({

    val dataSource = mockk<HikariDataSource>()
    val connection = mockk<Connection>(relaxed = true)
    val statement = mockk<PreparedStatement>(relaxed = true)
    val resultSet = mockk<ResultSet>(relaxed = true)
    val sqlArray = mockk<SqlArray>(relaxed = true)
    val manager = SyncStateManagerImpl(dataSource)

    beforeEach {
        clearMocks(dataSource, connection, statement, resultSet, sqlArray)
        every { dataSource.connection } returns connection
        every { connection.prepareStatement(any()) } returns statement
        every { connection.createArrayOf(any(), any()) } returns sqlArray
        every { statement.executeQuery() } returns resultSet
    }

    // STC: TC-UT-01 — getOrCreate new project creates IDLE state
    test("getOrCreate - new project inserts IDLE state") {
        // First SELECT returns empty, then INSERT, then second SELECT returns row
        every { resultSet.next() } returnsMany listOf(false, true)
        every { statement.executeUpdate() } returns 1
        mockSyncStateRow(resultSet, "MTO", "IDLE")

        val result = manager.getOrCreate("MTO")
        result.status shouldBe SyncStatus.IDLE
        result.projectKey shouldBe "MTO"
    }

    // STC: TC-UT-02 — getOrCreate existing project returns current state
    test("getOrCreate - existing project returns current state") {
        every { resultSet.next() } returns true
        mockSyncStateRow(resultSet, "MTO", "RUNNING", lastOffset = 50, syncedIssues = 25)

        val result = manager.getOrCreate("MTO")
        result.status shouldBe SyncStatus.RUNNING
        result.lastOffset shouldBe 50
        result.syncedIssues shouldBe 25
    }

    // STC: TC-UT-12 — markRunning from IDLE (valid)
    test("markRunning - from IDLE succeeds") {
        every { statement.executeUpdate() } returns 1
        manager.markRunning("MTO")
    }

    // STC: TC-UT-13 — markRunning from PAUSED (valid)
    test("markRunning - from PAUSED succeeds") {
        every { statement.executeUpdate() } returns 1
        manager.markRunning("MTO")
    }

    // STC: TC-UT-14 — markRunning from FAILED (valid retry)
    test("markRunning - from FAILED succeeds (retry)") {
        every { statement.executeUpdate() } returns 1
        manager.markRunning("MTO")
    }

    // STC: TC-UT-15 — markRunning from COMPLETED (invalid)
    test("markRunning - from COMPLETED throws IllegalStateException") {
        // First call (transition) returns 0, second call (status query) returns result
        every { statement.executeUpdate() } returns 0
        every { resultSet.next() } returns true
        every { resultSet.getString("status") } returns "COMPLETED"

        val ex = shouldThrow<IllegalStateException> {
            manager.markRunning("MTO")
        }
        ex.message shouldContain "Cannot transition"
    }

    // STC: TC-UT-16 — markRunning from RUNNING (invalid)
    test("markRunning - from RUNNING throws IllegalStateException") {
        every { statement.executeUpdate() } returns 0
        every { resultSet.next() } returns true
        every { resultSet.getString("status") } returns "RUNNING"

        val ex = shouldThrow<IllegalStateException> {
            manager.markRunning("MTO")
        }
        ex.message shouldContain "RUNNING"
    }

    // STC: TC-UT-17 — markPaused from RUNNING (valid)
    test("markPaused - from RUNNING succeeds") {
        every { statement.executeUpdate() } returns 1
        manager.markPaused("MTO")
    }

    // STC: TC-UT-18 — markCompleted sets last_sync_at
    test("markCompleted - from RUNNING succeeds and sets last_sync_at") {
        every { statement.executeUpdate() } returns 1
        manager.markCompleted("MTO")
        verify { connection.prepareStatement(match { it.contains("last_sync_at") }) }
    }

    // STC: TC-UT-19 — markFailed stores error message
    test("markFailed - stores error message") {
        every { statement.executeUpdate() } returns 1
        manager.markFailed("MTO", "Connection timeout after 30s")
        verify { statement.setString(1, "Connection timeout after 30s") }
    }

    // STC: TC-UT-20 — updateProgress validates offset and synced
    test("updateProgress - negative offset throws IllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            manager.updateProgress("MTO", offset = -1, synced = 0)
        }
        ex.message shouldContain "Offset must be non-negative"
    }

    test("updateProgress - negative synced throws IllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            manager.updateProgress("MTO", offset = 50, synced = -1)
        }
        ex.message shouldContain "Synced count must be non-negative"
    }

    test("updateProgress - valid values succeeds when RUNNING") {
        every { statement.executeUpdate() } returns 1
        manager.updateProgress("MTO", offset = 50, synced = 25)
    }

    test("updateProgress - not RUNNING throws IllegalStateException") {
        every { statement.executeUpdate() } returns 0
        val ex = shouldThrow<IllegalStateException> {
            manager.updateProgress("MTO", offset = 50, synced = 25)
        }
        ex.message shouldContain "not RUNNING"
    }

    // Validation tests
    test("validateProjectKey - blank throws IllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            manager.getOrCreate("")
        }
        ex.message shouldContain "must not be blank"
    }

    test("validateProjectKey - too long throws IllegalArgumentException") {
        val ex = shouldThrow<IllegalArgumentException> {
            manager.getOrCreate("A".repeat(51))
        }
        ex.message shouldContain "exceeds 50 characters"
    }

    test("getStatus - returns null when project not found") {
        every { resultSet.next() } returns false
        manager.getStatus("UNKNOWN") shouldBe null
    }

    test("getStatus - returns current status") {
        every { resultSet.next() } returns true
        every { resultSet.getString("status") } returns "PAUSED"
        manager.getStatus("MTO") shouldBe SyncStatus.PAUSED
    }
})

private fun mockSyncStateRow(
    rs: ResultSet,
    projectKey: String,
    status: String,
    lastOffset: Int = 0,
    syncedIssues: Int = 0
) {
    every { rs.getString("project_key") } returns projectKey
    every { rs.getString("status") } returns status
    every { rs.getInt("last_offset") } returns lastOffset
    every { rs.getInt("total_issues") } returns 0
    every { rs.getInt("synced_issues") } returns syncedIssues
    every { rs.getString("error_message") } returns null
    every { rs.getTimestamp("last_sync_at") } returns null
    every { rs.getTimestamp("updated_at") } returns java.sql.Timestamp(System.currentTimeMillis())
}
