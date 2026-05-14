package com.orchestrator.mcp.sync.pipeline.storage

import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import com.orchestrator.mcp.sync.pipeline.config.SyncPipelineConfig
import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Vector index writer that batches entries for efficient embedding + upsert.
 * Gracefully degrades if embedding service or vector DB is unavailable.
 */
class VectorIndexWriterImpl(
    private val embeddingService: EmbeddingService,
    private val vectorClient: VectorDbClient,
    private val config: SyncPipelineConfig
) : VectorIndexWriter {

    private val logger = LoggerFactory.getLogger(VectorIndexWriterImpl::class.java)
    private val buffer = mutableListOf<IndexEntry>()
    private val mutex = Mutex()

    override suspend fun queue(entry: IndexEntry) {
        if (!config.vector.enabled) return
        mutex.withLock {
            buffer.add(entry)
            if (buffer.size >= config.embedding.batchSize) {
                flushInternal()
            }
        }
    }

    override suspend fun flush() {
        if (!config.vector.enabled) return
        mutex.withLock { flushInternal() }
    }

    override suspend fun pendingCount(): Int = mutex.withLock { buffer.size }

    private suspend fun flushInternal() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()

        try {
            val texts = batch.map { it.vectorText!! }
            val embeddings = embeddingService.generateEmbeddings(texts)
            val points = buildVectorPoints(batch, embeddings)
            vectorClient.upsert(config.vector.collection, points)
            logger.debug("Flushed {} vector entries", batch.size)
        } catch (e: Exception) {
            logger.error("Vector flush failed for {} entries: {}", batch.size, e.message)
            // Graceful degradation — entries remain unindexed
        }
    }

    private fun buildVectorPoints(
        entries: List<IndexEntry>,
        embeddings: List<FloatArray>
    ): List<VectorPoint> {
        return entries.zip(embeddings).map { (entry, embedding) ->
            VectorPoint(
                id = entry.id,
                vector = embedding,
                payload = mapOf(
                    "dimension_id" to entry.dimensionId,
                    "project_key" to entry.projectKey,
                    "ticket_key" to (entry.ticketKey ?: ""),
                    "source_path" to entry.sourceRef.path,
                    "text_preview" to (entry.vectorText?.take(200) ?: "")
                )
            )
        }
    }
}
