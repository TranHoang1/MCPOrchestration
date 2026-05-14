package com.orchestrator.mcp.usermanagement.service

import com.orchestrator.mcp.usermanagement.model.ApprovalLogEntry
import com.orchestrator.mcp.usermanagement.repository.ApprovalLogRepository
import com.orchestrator.mcp.usermanagement.repository.UserRepository
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Background job that syncs approval decisions to Jira as comments.
 * Runs periodically, picks up entries with jira_synced=false, posts comments.
 */
class ApprovalJiraSyncJob(
    private val approvalLogRepo: ApprovalLogRepository,
    private val jiraCommentPoster: JiraCommentPoster,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    /** Start the periodic sync job. Safe to call multiple times. */
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { runLoop() }
        logger.info("ApprovalJiraSyncJob started (interval=60s)")
    }

    /** Stop the sync job gracefully. */
    fun stop() {
        job?.cancel()
        logger.info("ApprovalJiraSyncJob stopped")
    }

    private suspend fun runLoop() {
        while (coroutineContext.isActive) {
            try {
                syncPendingEntries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Sync loop error (will retry): {}", e.message)
            }
            delay(60_000L)
        }
    }

    private suspend fun syncPendingEntries() {
        val entries = approvalLogRepo.findPendingSyncEntries()
        if (entries.isEmpty()) return
        logger.debug("Found {} pending sync entries", entries.size)

        for (entry in entries) {
            syncEntry(entry)
        }
    }

    private suspend fun syncEntry(entry: ApprovalLogEntry) {
        try {
            val comment = formatComment(entry)
            jiraCommentPoster.postComment(entry.ticketKey, comment)
            approvalLogRepo.updateJiraSynced(UUID.fromString(entry.id), true)
        } catch (e: Exception) {
            logger.warn("Failed to sync entry {} to Jira: {}", entry.id, e.message)
        }
    }

    private suspend fun formatComment(entry: ApprovalLogEntry): String {
        val userName = resolveUserName(entry.userId)
        val decision = entry.decision.name
        val comment = entry.comment ?: "No comment"
        return "\uD83D\uDCCB Document Approval: ${entry.documentType.name} v${entry.documentVersion}\n" +
            "Decision: $decision\n" +
            "By: $userName\n" +
            "Comment: $comment\n" +
            "Date: ${entry.createdAt}"
    }

    private suspend fun resolveUserName(userId: String): String {
        return try {
            val user = userRepository.findById(UUID.fromString(userId))
            user?.displayName ?: userId
        } catch (e: Exception) {
            userId
        }
    }
}
