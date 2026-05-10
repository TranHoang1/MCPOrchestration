package com.orchestrator.mcp.kb.queue.model

/**
 * Queue priority levels for the Dual-Priority Queue system.
 * HIGH tasks preempt NORMAL tasks (BR-05, BR-06).
 */
enum class Priority {
    HIGH,
    NORMAL;

    companion object {
        fun fromString(value: String?): Priority =
            when (value?.lowercase()) {
                "high" -> HIGH
                else -> NORMAL
            }
    }
}
