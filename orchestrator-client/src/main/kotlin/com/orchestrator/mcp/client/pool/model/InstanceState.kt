package com.orchestrator.mcp.client.pool.model

/**
 * State machine for individual pooled process instances.
 *
 * Lifecycle:
 * WARMING → IDLE → ACTIVE → IDLE (or DEAD)
 * WARMING → DEAD (init failure)
 * IDLE → DRAINING → [killed]
 * ACTIVE → DRAINING → [killed]
 */
enum class InstanceState {
    /** Being created/initialized — not yet ready for use. */
    WARMING,

    /** Ready for use — available in the pool. */
    IDLE,

    /** Currently handling a request. */
    ACTIVE,

    /** Finishing current request, will be killed after. */
    DRAINING,

    /** Failed health check or crashed — pending removal. */
    DEAD;

    /** Valid transitions from this state. */
    fun canTransitionTo(target: InstanceState): Boolean = when (this) {
        WARMING -> target == IDLE || target == DEAD
        IDLE -> target == ACTIVE || target == DRAINING || target == DEAD
        ACTIVE -> target == IDLE || target == DRAINING || target == DEAD
        DRAINING -> target == DEAD
        DEAD -> false
    }
}
