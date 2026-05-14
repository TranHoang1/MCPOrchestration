package com.orchestrator.mcp.sync.pipeline.storage

import com.orchestrator.mcp.sync.pipeline.model.IndexEntry

/**
 * Interface for vector embedding and storage operations.
 * Batches entries for efficient embedding generation and upsert.
 */
interface VectorIndexWriter {

    /** Queue an entry for batch vector indexing. */
    suspend fun queue(entry: IndexEntry)

    /** Flush all queued entries (generate embeddings + upsert to vector DB). */
    suspend fun flush()

    /** Get count of entries pending vector indexing. */
    suspend fun pendingCount(): Int
}
