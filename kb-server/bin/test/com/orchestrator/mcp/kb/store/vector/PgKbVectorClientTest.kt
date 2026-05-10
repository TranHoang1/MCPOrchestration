package com.orchestrator.mcp.kb.store.vector

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Unit tests for PgKbVectorClient.
 * Tests SQL generation and parameter binding logic.
 */
class PgKbVectorClientTest : DescribeSpec({

    describe("PgKbVectorClient") {

        describe("deleteByIssueKey") {
            it("should execute DELETE with correct issue_key parameter") {
                val mockDs = mockk<HikariDataSource>()
                val mockConn = mockk<Connection>(relaxed = true)
                val mockStmt = mockk<PreparedStatement>(relaxed = true)

                coEvery { mockDs.connection } returns mockConn
                coEvery { mockConn.prepareStatement(any()) } returns mockStmt
                coEvery { mockStmt.executeUpdate() } returns 1

                val client = PgKbVectorClient(mockDs)
                client.deleteByIssueKey("MTO-25")

                val sqlSlot = slot<String>()
                coVerify { mockConn.prepareStatement(capture(sqlSlot)) }
                coVerify { mockStmt.setString(1, "MTO-25") }
                coVerify { mockStmt.executeUpdate() }
                coVerify { mockStmt.close() }
                coVerify { mockConn.close() }
            }
        }

        describe("isHealthy") {
            it("should return true when DB responds") {
                val mockDs = mockk<HikariDataSource>()
                val mockConn = mockk<Connection>(relaxed = true)
                val mockStmt = mockk<java.sql.Statement>(relaxed = true)
                val mockRs = mockk<ResultSet>(relaxed = true)

                coEvery { mockDs.connection } returns mockConn
                coEvery { mockConn.createStatement() } returns mockStmt
                coEvery { mockStmt.executeQuery("SELECT 1") } returns mockRs
                coEvery { mockRs.next() } returns true

                val client = PgKbVectorClient(mockDs)
                val result = client.isHealthy()

                result shouldBe true
            }

            it("should return false when DB throws") {
                val mockDs = mockk<HikariDataSource>()
                coEvery { mockDs.connection } throws RuntimeException("Connection refused")

                val client = PgKbVectorClient(mockDs)
                val result = client.isHealthy()

                result shouldBe false
            }
        }
    }
})
