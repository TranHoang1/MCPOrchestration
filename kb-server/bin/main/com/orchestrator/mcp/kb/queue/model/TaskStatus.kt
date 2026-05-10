package com.orchestrator.mcp.kb.queue.model

/**
 * Lifecycle states for a queue task.
 * See TDD §7.5 Task State Diagram.
 */
enum class TaskStatus {
    Pending,
    Processing,
    Completed,
    Failed;

    fun toDbValue(): String = name
    companion object {
        fun fromDbValue(value: String): TaskStatus =
            entries.first { it.name.equals(value, ignoreCase = true) }
    }
}
