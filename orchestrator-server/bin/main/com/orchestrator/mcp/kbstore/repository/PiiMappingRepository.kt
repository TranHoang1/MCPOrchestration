package com.orchestrator.mcp.kbstore.repository

import com.orchestrator.mcp.kbstore.model.PiiMapping

/**
 * Repository for PII mapping CRUD operations.
 * All operations are suspend (coroutine-friendly).
 * Encryption of original_value is handled internally by the implementation.
 */
interface PiiMappingRepository {

    suspend fun insertBatch(mappings: List<PiiMapping>): Int

    suspend fun findByIssueKey(issueKey: String): List<PiiMapping>

    suspend fun deleteByIssueKey(issueKey: String): Int

    /**
     * Replace all PII mappings for an issue_key (delete + insert in transaction).
     */
    suspend fun replaceForIssueKey(issueKey: String, mappings: List<PiiMapping>): Int
}
