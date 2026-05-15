package com.orchestrator.mcp.kb.feature

import com.orchestrator.mcp.sync.pipeline.model.IndexEntry

/**
 * Data access abstraction for feature CRUD on sync.index_entries.
 * Operates on dimension_id = 'feature_grouping'.
 */
interface FeatureRepository {

    /** List all features for a project, ordered by source (manual first) then name. */
    suspend fun listByProject(projectKey: String): List<IndexEntry>

    /** Find a feature by its entry_key. */
    suspend fun findById(entryKey: String): IndexEntry?

    /** Check if a feature with the given name exists in the project. */
    suspend fun existsByName(projectKey: String, name: String): Boolean

    /** Find the feature containing a specific ticket key. */
    suspend fun findByTicketKey(projectKey: String, ticketKey: String): IndexEntry?

    /** Create (upsert) a feature entry. */
    suspend fun create(entry: IndexEntry)

    /** Update feature data and vector text by entry_key. */
    suspend fun update(entryKey: String, data: Map<String, String?>, vectorText: String?)

    /** Delete a feature by entry_key. Returns deleted entry or null. */
    suspend fun delete(entryKey: String): IndexEntry?
}
