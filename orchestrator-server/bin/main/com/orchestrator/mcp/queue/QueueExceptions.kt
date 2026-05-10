package com.orchestrator.mcp.queue

/**
 * Exception thrown when a database operation for queue state fails.
 */
class QueuePersistenceException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when task validation fails (null payload, empty task_type).
 */
class InvalidTaskException(
    message: String
) : IllegalArgumentException(message)

/**
 * Exception thrown when no TaskHandler is registered for a given task_type.
 */
class TaskHandlerNotFoundException(
    taskType: String
) : RuntimeException("No TaskHandler registered for task_type: $taskType")

/**
 * Exception thrown when task execution fails (wraps handler exceptions).
 */
class TaskExecutionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
