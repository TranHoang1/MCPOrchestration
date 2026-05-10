package com.orchestrator.mcp.security

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Executes DDL migration for RLS roles, policies, and security barrier views.
 * Follows existing DatabaseInitializer pattern — idempotent via IF NOT EXISTS / DO blocks.
 */
class RlsDatabaseInitializer(private val dataSource: HikariDataSource) {

    private val logger = LoggerFactory.getLogger(RlsDatabaseInitializer::class.java)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        logger.info("Initializing RLS database schema...")
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(CREATE_ROLES)
                    stmt.execute(ENABLE_RLS_KB_ENTRIES)
                    stmt.execute(CREATE_KB_POLICIES)
                    stmt.execute(CREATE_SECURITY_VIEWS)
                    stmt.execute(ENABLE_RLS_PII_MAPPING)
                }
                conn.commit()
                logger.info("RLS database schema initialized successfully.")
            } catch (e: Exception) {
                conn.rollback()
                logger.error("Failed to initialize RLS schema", e)
                throw e
            }
        }
    }
}
