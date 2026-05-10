package com.orchestrator.mcp.kb.queue

/**
 * Exception thrown when a task cannot be persisted to the database
 * before being enqueued to the channel (BR-01, BR-02).
 */
class QueuePersistenceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
