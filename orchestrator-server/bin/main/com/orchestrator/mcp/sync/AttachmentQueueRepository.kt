package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.AttachmentQueueItem
import com.orchestrator.mcp.sync.model.AttachmentStatus

/**
 * Repository for attachment processing queue operations.
 * Implements persistent FIFO queue with status lifecycle.
 */
interface AttachmentQueueRepository {

    suspend fun enqueue(item: AttachmentQueueItem)

    suspend fun enqueueBatch(items: List<AttachmentQueueItem>): Int

    suspend fun pollPending(limit: Int): List<AttachmentQueueItem>

    suspend fun updateStatus(id: Int, status: AttachmentStatus, error: String? = null)

    suspend fun markDone(id: Int)

    suspend fun incrementRetry(id: Int, error: String)

    suspend fun findByTicket(ticketKey: String): List<AttachmentQueueItem>

    suspend fun countByStatus(status: AttachmentStatus): Int
}
