package com.orchestrator.mcp.attachment

import com.orchestrator.mcp.attachment.config.AttachmentProcessorConfig
import com.orchestrator.mcp.attachment.model.ProcessorStats
import com.orchestrator.mcp.client.embedding.EmbeddingService
import com.orchestrator.mcp.client.vectordb.VectorDbClient
import com.orchestrator.mcp.client.vectordb.model.VectorPoint
import com.orchestrator.mcp.sync.AttachmentQueueRepository
import com.orchestrator.mcp.sync.model.AttachmentQueueItem
import com.orchestrator.mcp.sync.model.AttachmentStatus
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background coroutine worker that polls the attachment queue,
 * downloads files, extracts text, and ingests into KB.
 */
class AttachmentProcessorImpl(
    private val queueRepository: AttachmentQueueRepository,
    private val downloader: AttachmentDownloader,
    private val textExtractor: TextExtractor,
    private val embeddingService: EmbeddingService,
    private val vectorDbClient: VectorDbClient,
    private val collectionName: String,
    private val config: AttachmentProcessorConfig
) : AttachmentProcessor {

    private val logger = LoggerFactory.getLogger(AttachmentProcessorImpl::class.java)
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    override suspend fun start() {
        if (!config.enabled) {
            logger.info("AttachmentProcessor disabled by config")
            return
        }
        if (running.getAndSet(true)) return

        logger.info("AttachmentProcessor starting (batch={}, concurrency={})",
            config.batchSize, config.maxConcurrentDownloads)

        job = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            runProcessingLoop()
        }
    }

    override suspend fun stop() {
        if (!running.getAndSet(false)) return
        job?.let { j ->
            j.cancel("Shutdown requested")
            withTimeoutOrNull(config.shutdownTimeoutMs) { j.join() }
        }
        logger.info("AttachmentProcessor stopped")
    }

    override fun isRunning(): Boolean = running.get()

    override suspend fun getStats(): ProcessorStats {
        return ProcessorStats(
            pending = queueRepository.countByStatus(AttachmentStatus.PENDING),
            processing = queueRepository.countByStatus(AttachmentStatus.DOWNLOADING),
            completed = queueRepository.countByStatus(AttachmentStatus.DONE),
            failed = queueRepository.countByStatus(AttachmentStatus.FAILED),
            skipped = 0 // No SKIPPED status in current enum
        )
    }

    private suspend fun runProcessingLoop() {
        var backoffMs = config.pollIntervalMs
        while (true) {
            val batch = queueRepository.pollPending(config.batchSize)
            if (batch.isEmpty()) {
                delay(backoffMs)
                backoffMs = minOf(backoffMs * 2, config.maxBackoffMs)
                continue
            }
            backoffMs = config.pollIntervalMs
            processBatch(batch)
        }
    }

    private suspend fun processBatch(batch: List<AttachmentQueueItem>) {
        for (item in batch) {
            processItem(item)
        }
    }

    private suspend fun processItem(item: AttachmentQueueItem) {
        try {
            queueRepository.updateStatus(item.id, AttachmentStatus.DOWNLOADING)

            if (!textExtractor.supports(item.mimeType ?: "")) {
                queueRepository.updateStatus(item.id, AttachmentStatus.FAILED, "Unsupported MIME type")
                return
            }

            if (item.sizeBytes != null && item.sizeBytes > config.maxFileSize) {
                queueRepository.updateStatus(item.id, AttachmentStatus.FAILED, "File too large")
                return
            }

            val bytes = downloader.download(item.downloadUrl)
            val text = textExtractor.extract(bytes, item.mimeType ?: "text/plain")

            if (text.isBlank()) {
                queueRepository.markDone(item.id)
                return
            }

            ingestToKB(item, text)
            queueRepository.markDone(item.id)
            logger.debug("Processed attachment: {} ({})", item.filename, item.ticketKey)
        } catch (e: Exception) {
            handleFailure(item, e)
        }
    }

    private suspend fun ingestToKB(item: AttachmentQueueItem, text: String) {
        val truncated = text.take(MAX_INGEST_LENGTH)
        val embedding = embeddingService.generateEmbedding(truncated)
        val pointId = UUID.nameUUIDFromBytes("attachment:${item.attachmentId}".toByteArray()).toString()
        val payload = mapOf(
            "title" to "${item.filename} (${item.ticketKey})",
            "type" to "jira_attachment",
            "issue_key" to item.ticketKey,
            "filename" to item.filename,
            "mime_type" to (item.mimeType ?: "unknown")
        )
        vectorDbClient.upsert(collectionName, listOf(VectorPoint(pointId, embedding, payload)))
    }

    private suspend fun handleFailure(item: AttachmentQueueItem, error: Exception) {
        logger.warn("Failed to process {}: {}", item.filename, error.message)
        if (item.retryCount >= config.maxRetries) {
            queueRepository.updateStatus(item.id, AttachmentStatus.FAILED, error.message)
        } else {
            queueRepository.incrementRetry(item.id, error.message ?: "Unknown error")
        }
    }

    companion object {
        private const val MAX_INGEST_LENGTH = 10_000
    }
}
