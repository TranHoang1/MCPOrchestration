package com.orchestrator.mcp.security

import com.orchestrator.mcp.security.model.KbRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

/**
 * Wraps HikariCP connections with RLS role context.
 * Sets PostgreSQL role via SET LOCAL before executing queries,
 * ensuring role is transaction-scoped and auto-resets on commit/rollback.
 */
class RlsConnectionWrapper(
    private val dataSource: DataSource
) {
    private val logger = LoggerFactory.getLogger(RlsConnectionWrapper::class.java)

    /**
     * Execute a database operation with the specified RLS role.
     * Role is set via SET LOCAL — scoped to this transaction only.
     *
     * @param role The KB role to activate
     * @param block The database operation to execute
     * @return The result of the operation
     */
    suspend fun <T> executeWithRole(
        role: KbRole,
        block: suspend (Connection) -> T
    ): T = withContext(Dispatchers.IO) {
        val connection = dataSource.connection
        try {
            connection.autoCommit = false
            setLocalRole(connection, role)
            val result = block(connection)
            connection.commit()
            logger.debug("Transaction committed with role '{}'", role.pgRoleName)
            result
        } catch (e: Exception) {
            logger.error("Transaction failed with role '{}': {}", role.pgRoleName, e.message)
            rollbackSafely(connection)
            throw e
        } finally {
            closeSafely(connection)
        }
    }

    private fun setLocalRole(connection: Connection, role: KbRole) {
        connection.createStatement().use { stmt ->
            stmt.execute("SET LOCAL ROLE '${role.pgRoleName}'")
        }
        logger.debug("Set LOCAL ROLE to '{}'", role.pgRoleName)
    }

    private fun rollbackSafely(connection: Connection) {
        runCatching { connection.rollback() }
            .onFailure { logger.warn("Rollback failed: {}", it.message) }
    }

    private fun closeSafely(connection: Connection) {
        runCatching {
            connection.autoCommit = true
            connection.close()
        }.onFailure { logger.warn("Connection close failed: {}", it.message) }
    }
}
