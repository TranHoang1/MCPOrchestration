package com.orchestrator.mcp.kb.queue.handler

import com.orchestrator.mcp.kb.queue.TaskHandler
import com.orchestrator.mcp.kb.queue.model.QueueTask
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Handles "sync" tasks from the queue.
 * Fetches tickets from Jira and ingests them into KB.
 * Phase 3: basic implementation — full Jira crawling in Phase 4.
 */
class SyncTaskHandler : TaskHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun taskType(): String = "sync"

    override suspend fun handle(task: QueueTask) {
        val projectKey = task.payload["project_key"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("project_key required in sync task payload")
        val fullSync = task.payload["full_sync"]?.jsonPrimitive?.content?.toBoolean() ?: false

        logger.info("Sync task started for project={} fullSync={}", projectKey, fullSync)

        // Phase 3: Log sync intent. Full Jira API crawling in Phase 4.
        // This handler will be expanded to:
        // 1. Call Jira REST API to list tickets
        // 2. For each ticket, create an "ingest" sub-task
        // 3. Track progress in sync_status table

        logger.info("Sync task completed for project={} (Phase 3 stub)", projectKey)
    }
}
