package com.orchestrator.mcp.kb.queue.handler

import com.orchestrator.mcp.kb.queue.TaskHandler
import com.orchestrator.mcp.kb.queue.model.QueueTask
import com.orchestrator.mcp.sync.pipeline.SyncOrchestrator
import com.orchestrator.mcp.sync.pipeline.model.SyncOptions
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Handles "sync" tasks from the queue.
 * Delegates to SyncOrchestrator for unified multi-dimensional indexing.
 */
class SyncTaskHandler(
    private val syncOrchestrator: SyncOrchestrator
) : TaskHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun taskType(): String = "sync"

    override suspend fun handle(task: QueueTask) {
        val projectKey = task.payload["project_key"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("project_key required in sync task payload")
        val fullSync = task.payload["full_sync"]?.jsonPrimitive?.content?.toBoolean() ?: false

        logger.info("Sync task started for project={} fullSync={}", projectKey, fullSync)

        try {
            val result = syncOrchestrator.sync(projectKey, SyncOptions(fullSync = fullSync))
            logger.info(
                "Sync completed for project={}: {} tickets processed, {} entries created",
                projectKey, result.processedTickets, result.entriesCreated.values.sum()
            )
        } catch (e: Exception) {
            logger.error("Sync failed for project={}: {}", projectKey, e.message, e)
            throw e
        }
    }
}
