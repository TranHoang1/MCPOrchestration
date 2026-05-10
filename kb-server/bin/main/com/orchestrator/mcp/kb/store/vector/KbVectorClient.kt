package com.orchestrator.mcp.kb.store.vector

/**
 * KB-specific vector operations interface.
 * Works with kb_entry_embeddings table, separate from tool_embeddings.
 */
interface KbVectorClient {

    /** Upsert an embedding for a KB entry */
    suspend fun upsert(entry: KbVectorEntry)

    /** Batch upsert embeddings */
    suspend fun upsertBatch(entries: List<KbVectorEntry>)

    /** Search by vector similarity, returns scored results */
    suspend fun search(
        vector: FloatArray,
        limit: Int,
        scoreThreshold: Float,
        projectKey: String? = null
    ): List<KbVectorSearchResult>

    /** Delete embedding by issue key */
    suspend fun deleteByIssueKey(issueKey: String)

    /** Check if vector DB is healthy */
    suspend fun isHealthy(): Boolean
}

/** Entry to store in vector DB */
data class KbVectorEntry(
    val issueKey: String,
    val projectKey: String,
    val contentHash: String?,
    val embedding: FloatArray,
    val searchText: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KbVectorEntry) return false
        return issueKey == other.issueKey
    }

    override fun hashCode(): Int = issueKey.hashCode()
}

/** Search result from vector similarity search */
data class KbVectorSearchResult(
    val issueKey: String,
    val projectKey: String,
    val score: Float
)
