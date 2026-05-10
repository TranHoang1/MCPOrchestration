package com.orchestrator.mcp.queue.model

/**
 * Lifecycle states for a queue task.
 */
enum class TaskStatus(val value: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    FAILED("Failed");

    companion object {
        fun fromValue(value: String): TaskStatus =
            entries.first { it.value.equals(value, ignoreCase = true) }
    }
}
