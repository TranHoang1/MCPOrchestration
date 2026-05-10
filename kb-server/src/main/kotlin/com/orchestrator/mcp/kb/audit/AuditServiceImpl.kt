package com.orchestrator.mcp.kb.audit

import com.orchestrator.mcp.kb.audit.model.AuditEvent
import com.orchestrator.mcp.kb.audit.repository.AuditEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Async audit service implementation.
 * Uses coroutine scope for fire-and-forget writes.
 * Never fails the calling operation — errors are logged only.
 */
class AuditServiceImpl(
    private val repository: AuditEventRepository,
    private val scope: CoroutineScope
) : AuditService {

    private val logger = LoggerFactory.getLogger(AuditServiceImpl::class.java)

    override fun log(event: AuditEvent) {
        scope.launch {
            try {
                repository.save(event)
            } catch (e: Exception) {
                logger.error("Audit write failed for event={}: {}", event.eventType, e.message)
            }
        }
    }

    override suspend fun logSuspend(event: AuditEvent) {
        try {
            repository.save(event)
        } catch (e: Exception) {
            logger.error("Audit write failed for event={}: {}", event.eventType, e.message)
        }
    }
}
