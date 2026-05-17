package com.orchestrator.mcp.client.pool.model

/**
 * Sealed exception hierarchy for Process Pool operations.
 * Each exception maps to a specific HTTP status code and error code.
 */
sealed class PoolException(
    message: String,
    val errorCode: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    /** Waited acquireTimeoutMs, no instance became available. HTTP 503. */
    class AcquireTimeoutException(
        poolKey: PoolKey,
        timeoutMs: Long
    ) : PoolException(
        "Acquire timeout for pool '${poolKey.value}' after ${timeoutMs}ms",
        "POOL_ACQUIRE_TIMEOUT"
    )

    /** At max total instances, cannot spawn more. HTTP 503. */
    class ExhaustedException(
        poolKey: PoolKey,
        maxInstances: Int
    ) : PoolException(
        "Pool '${poolKey.value}' exhausted: max $maxInstances instances reached",
        "POOL_EXHAUSTED"
    )

    /** Pool is draining/shutting down, not accepting new requests. HTTP 503. */
    class ShuttingDownException : PoolException(
        "Pool manager is shutting down, not accepting new requests",
        "POOL_SHUTTING_DOWN"
    )

    /** Acquired instance failed during use. HTTP 500. */
    class InstanceDeadException(
        instanceId: String,
        cause: Throwable? = null
    ) : PoolException(
        "Pool instance '$instanceId' died during use",
        "POOL_INSTANCE_DEAD",
        cause
    )

    /** Failed to spawn a new process. HTTP 500. */
    class SpawnFailedException(
        serverName: String,
        cause: Throwable? = null
    ) : PoolException(
        "Failed to spawn process for server '$serverName': ${cause?.message ?: "Unknown"}",
        "SPAWN_FAILED",
        cause
    )

    /** Invalid state transition attempted. Internal error. */
    class InvalidStateException(
        currentState: InstanceState,
        targetState: InstanceState
    ) : PoolException(
        "Invalid state transition: $currentState → $targetState",
        "INVALID_STATE_TRANSITION"
    )
}
