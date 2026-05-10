package com.orchestrator.mcp.kbstore.repository

import com.orchestrator.mcp.kbstore.model.KbEntry
import kotlinx.datetime.Instant

/**
 * Repository for KB entry CRUD operations.
 * All operations are suspend (coroutine-friendly).
 * Encryption of business_rules is handled internally by the implementation.
 */
interface KbEntryRepository {

    suspend fun upsert(entry: KbEntry)

    suspend fun upsertBatch(entries: List<KbEntry>): Int

    suspend fun findByIssueKey(issueKey: String): KbEntry?

    suspend fun findByProjectKey(projectKey: String): List<KbEntry>

    suspend fun findByContentHash(projectKey: String, hash: String): KbEntry?

    suspend fun updateLastSyncedAt(issueKey: String, syncedAt: Instant)

    suspend fun delete(issueKey: String)
}
