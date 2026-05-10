package com.orchestrator.mcp.kbstore

import com.orchestrator.mcp.kbstore.encryption.EncryptionService
import com.orchestrator.mcp.kbstore.model.BrSensitivityLevel
import com.orchestrator.mcp.kbstore.model.KbEntry
import com.orchestrator.mcp.kbstore.repository.KbEntryRepositoryImpl
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.sql.*
import java.util.UUID

class KbEntryRepositoryImplTest : FunSpec({

    val dataSource = mockk<HikariDataSource>()
    val encryptionService = mockk<EncryptionService>()
    val connection = mockk<Connection>(relaxed = true)
    val statement = mockk<PreparedStatement>(relaxed = true)
    val resultSet = mockk<ResultSet>()

    lateinit var repository: KbEntryRepositoryImpl

    beforeEach {
        clearAllMocks()
        repository = KbEntryRepositoryImpl(dataSource, encryptionService)
        every { dataSource.connection } returns connection
        every { connection.prepareStatement(any()) } returns statement
    }

    fun testEntry() = KbEntry(
        issueKey = "MTO-100",
        projectKey = "MTO",
        publicContent = "Public info",
        technicalContent = "Stack trace",
        businessRules = "Rate = 12.5%",
        maskedFull = "[MASKED]",
        brSensitivityLevel = BrSensitivityLevel.CONFIDENTIAL,
        contentHash = "abc123"
    )

    test("UT-11: upsert encrypts business_rules before persist") {
        val entry = testEntry()
        val encrypted = byteArrayOf(1, 2, 3, 4)
        every { encryptionService.encrypt("Rate = 12.5%") } returns encrypted
        every { statement.executeUpdate() } returns 1

        repository.upsert(entry)

        verify { encryptionService.encrypt("Rate = 12.5%") }
        verify { statement.setBytes(6, encrypted) }
    }

    test("UT-12: upsert sets null for business_rules when null") {
        val entry = testEntry().copy(businessRules = null)
        every { statement.executeUpdate() } returns 1

        repository.upsert(entry)

        verify(exactly = 0) { encryptionService.encrypt(any()) }
        verify { statement.setNull(6, Types.BINARY) }
    }

    test("UT-13: findByIssueKey decrypts business_rules") {
        val encrypted = byteArrayOf(10, 20, 30)
        every { resultSet.next() } returns true
        every { resultSet.getObject("id", UUID::class.java) } returns UUID.randomUUID()
        every { resultSet.getString("issue_key") } returns "MTO-100"
        every { resultSet.getString("project_key") } returns "MTO"
        every { resultSet.getString("public_content") } returns "Public"
        every { resultSet.getString("technical_content") } returns "Tech"
        every { resultSet.getBytes("business_rules") } returns encrypted
        every { resultSet.getString("masked_full") } returns "[MASKED]"
        every { resultSet.getInt("br_sensitivity_level") } returns 1
        every { resultSet.getString("content_hash") } returns "hash"
        every { resultSet.getTimestamp("created_at") } returns Timestamp(System.currentTimeMillis())
        every { resultSet.getTimestamp("updated_at") } returns Timestamp(System.currentTimeMillis())
        every { resultSet.getTimestamp("last_synced_at") } returns null
        every { statement.executeQuery() } returns resultSet
        every { encryptionService.decrypt(encrypted) } returns "Decrypted BR"

        val result = repository.findByIssueKey("MTO-100")

        result shouldNotBe null
        result!!.businessRules shouldBe "Decrypted BR"
        verify { encryptionService.decrypt(encrypted) }
    }

    test("UT-14: findByIssueKey returns null when not found") {
        every { resultSet.next() } returns false
        every { statement.executeQuery() } returns resultSet

        val result = repository.findByIssueKey("UNKNOWN-1")

        result shouldBe null
    }

    test("UT-15: upsertBatch uses transaction with rollback on error") {
        val entries = listOf(testEntry(), testEntry().copy(issueKey = "MTO-101"))
        every { encryptionService.encrypt(any()) } returns byteArrayOf(1, 2, 3)
        every { connection.autoCommit = any() } just Runs
        every { statement.addBatch() } just Runs
        every { statement.executeBatch() } throws SQLException("DB error")
        every { connection.rollback() } just Runs

        try {
            repository.upsertBatch(entries)
        } catch (_: Exception) {}

        verify { connection.rollback() }
    }

    test("UT-16: upsertBatch commits on success") {
        val entries = listOf(testEntry())
        every { encryptionService.encrypt(any()) } returns byteArrayOf(1, 2, 3)
        every { connection.autoCommit = any() } just Runs
        every { statement.addBatch() } just Runs
        every { statement.executeBatch() } returns intArrayOf(1)
        every { connection.commit() } just Runs

        val count = repository.upsertBatch(entries)

        count shouldBe 1
        verify { connection.commit() }
    }

    test("UT-17: upsertBatch returns 0 for empty list") {
        val count = repository.upsertBatch(emptyList())
        count shouldBe 0
    }
})
