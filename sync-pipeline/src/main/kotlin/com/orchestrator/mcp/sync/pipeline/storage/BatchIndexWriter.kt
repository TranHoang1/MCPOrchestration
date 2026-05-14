package com.orchestrator.mcp.sync.pipeline.storage

import com.orchestrator.mcp.sync.pipeline.model.IndexEntry
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Buffered index writer that flushes every N entries.
 * Wraps an underlying IndexWriter for batch efficiency.
 */
class BatchIndexWriter(
    private val delegate: IndexWriter,
    private val bufferSize: Int = 100
) : IndexWriter by delegate {

    private val logger = LoggerFactory.getLogger(BatchIndexWriter::class.java)
    private val buffer = mutableListOf<IndexEntry>()
    private val mutex = Mutex()

    /** Add entries to buffer, flush when buffer is full. */
    override suspend fun writeBatch(entries: List<IndexEntry>) {
        mutex.withLock {
            buffer.addAll(entries)
            if (buffer.size >= bufferSize) {
                flushInternal()
            }
        }
    }

    /** Force flush all buffered entries. */
    suspend fun flush() {
        mutex.withLock { flushInternal() }
    }

    /** Get current buffer size for monitoring. */
    suspend fun pendingCount(): Int = mutex.withLock { buffer.size }

    private suspend fun flushInternal() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList()
        buffer.clear()
        try {
            delegate.writeBatch(batch)
            logger.debug("Flushed {} entries to storage", batch.size)
        } catch (e: Exception) {
            logger.error("Batch flush failed for {} entries: {}", batch.size, e.message)
            throw e
        }
    }
}
