package com.orchestrator.mcp.client.pool

import com.orchestrator.mcp.client.pool.model.PoolConfig
import com.orchestrator.mcp.client.pool.model.PoolException
import com.orchestrator.mcp.client.pool.model.PoolMetrics
import com.orchestrator.mcp.client.pool.model.ProcessState
import com.orchestrator.mcp.client.upstream.McpConnection
import com.orchestrator.mcp.client.upstream.McpFramingMode
import com.orchestrator.mcp.client.upstream.StdioMcpConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Single pool for one pool_key. Manages a set of PooledConnections.
 * Thread-safe via Mutex for state mutations.
 */
class ProcessPool(
    val poolKey: String,
    val serverName: String,
    private val config: PoolConfig
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val connections = CopyOnWriteArrayList<PooledConnection>()
    private val mutex = Mutex()

    /** Acquire an idle connection or spawn a new one. */
    suspend fun acquire(command: String, args: List<String>, env: Map<String, String>): PooledConnection {
        mutex.withLock {
            val idle = findIdleConnection()
            if (idle != null) {
                idle.markBusy()
                return idle
            }
            if (connections.size >= config.maxInstancesPerServer) {
                throw PoolException.PoolExhaustedException(poolKey, config.maxInstancesPerServer)
            }
        }
        return spawnAndAcquire(command, args, env)
    }

    /** Release a connection back to idle state. */
    fun release(connection: PooledConnection) {
        if (connection.isActive()) {
            connection.markIdle()
        } else {
            connections.remove(connection)
            logger.warn("Removed dead connection {} from pool {}", connection.id, poolKey)
        }
    }

    /** Get current pool metrics. */
    fun getMetrics(): PoolMetrics {
        val idle = connections.count { it.getState() == ProcessState.IDLE }
        val busy = connections.count { it.getState() == ProcessState.BUSY }
        val avgTime = calculateAvgResponseTime()
        val totalReqs = connections.sumOf { it.getRequestCount() }
        return PoolMetrics(
            poolKey = poolKey,
            serverName = serverName,
            totalConnections = connections.size,
            idleConnections = idle,
            busyConnections = busy,
            avgResponseTimeMs = avgTime,
            totalRequests = totalReqs,
            totalErrors = 0
        )
    }

    /** Get number of connections in this pool. */
    fun size(): Int = connections.size

    /** Get all connections (for scaling decisions). */
    fun getConnections(): List<PooledConnection> = connections.toList()

    /** Remove and close idle connections that exceed timeout. */
    suspend fun evictIdle() {
        val toEvict = connections.filter { isIdleExpired(it) }
        for (conn in toEvict) {
            if (connections.size <= 1) break
            connections.remove(conn)
            conn.close()
            logger.debug("Evicted idle connection {} from pool {}", conn.id, poolKey)
        }
    }

    /** Shutdown all connections in this pool. */
    suspend fun shutdown() {
        for (conn in connections) {
            try { conn.close() } catch (e: Exception) { logger.warn("Error closing {}", conn.id) }
        }
        connections.clear()
    }

    private fun findIdleConnection(): PooledConnection? {
        return connections.firstOrNull { it.getState() == ProcessState.IDLE && it.isActive() }
    }

    private suspend fun spawnAndAcquire(
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): PooledConnection {
        val mcpConn = spawnProcess(command, args, env)
        val pooled = PooledConnection(
            id = UUID.randomUUID().toString().take(8),
            poolKey = poolKey,
            serverName = serverName,
            delegate = mcpConn
        )
        pooled.transitionTo(ProcessState.IDLE)
        pooled.markBusy()
        connections.add(pooled)
        logger.info("Spawned new connection {} for pool {} (size={})", pooled.id, poolKey, connections.size)
        return pooled
    }

    private suspend fun spawnProcess(
        command: String,
        args: List<String>,
        env: Map<String, String>
    ): McpConnection {
        try {
            val conn = StdioMcpConnection(
                command = command,
                args = args,
                env = env,
                framingMode = McpFramingMode.NEWLINE_DELIMITED
            )
            conn.start()
            return conn
        } catch (e: Exception) {
            throw PoolException.ProcessSpawnFailedException(serverName, e.message ?: "Unknown")
        }
    }

    private fun isIdleExpired(conn: PooledConnection): Boolean {
        return conn.getState() == ProcessState.IDLE && conn.getIdleTimeMs() > config.idleTimeoutMs
    }

    private fun calculateAvgResponseTime(): Long {
        val active = connections.filter { it.getRequestCount() > 0 }
        if (active.isEmpty()) return 0L
        return active.sumOf { it.getAvgResponseTimeMs() } / active.size
    }
}
