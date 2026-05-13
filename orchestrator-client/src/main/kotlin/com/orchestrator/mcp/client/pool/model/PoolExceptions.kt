package com.orchestrator.mcp.client.pool.model

/**
 * Sealed exception hierarchy for Process Pool operations.
 */
sealed class PoolException(
    message: String,
    val errorCode: String
) : RuntimeException(message) {

    /** Pool has reached max capacity and cannot spawn more processes. */
    class PoolExhaustedException(poolKey: String, maxSize: Int) :
        PoolException(
            "Pool '$poolKey' exhausted: max $maxSize instances reached",
            "POOL_EXHAUSTED"
        )

    /** Failed to spawn a new process for the pool. */
    class ProcessSpawnFailedException(serverName: String, cause: String) :
        PoolException(
            "Failed to spawn process for server '$serverName': $cause",
            "SPAWN_FAILED"
        )

    /** Connection is not in a valid state for the requested operation. */
    class InvalidStateTransitionException(
        currentState: ProcessState,
        targetState: ProcessState
    ) : PoolException(
        "Invalid state transition: $currentState → $targetState",
        "INVALID_STATE_TRANSITION"
    )
}
