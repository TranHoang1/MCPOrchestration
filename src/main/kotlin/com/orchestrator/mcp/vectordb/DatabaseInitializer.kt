package com.orchestrator.mcp.vectordb

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

class DatabaseInitializer(private val dataSource: HikariDataSource) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        log.info("Initializing database schema...")
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { stmt ->
                    // 1. server_config table
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS server_config (
                            server_name VARCHAR(255) PRIMARY KEY,
                            transport VARCHAR(50) NOT NULL,
                            command TEXT,
                            args JSONB,
                            env_keys JSONB,
                            url TEXT,
                            disabled BOOLEAN DEFAULT FALSE,
                            tool_filter JSONB,
                            auto_approve JSONB,
                            is_active BOOLEAN DEFAULT TRUE,
                            synced_at TIMESTAMPTZ DEFAULT NOW()
                        );
                    """.trimIndent())

                    // 2. tool_toggle_state table
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS tool_toggle_state (
                            id SERIAL PRIMARY KEY,
                            session_id VARCHAR(255) NOT NULL,
                            tool_name VARCHAR(255),
                            server_name VARCHAR(255),
                            enabled BOOLEAN NOT NULL,
                            updated_at TIMESTAMPTZ DEFAULT NOW()
                        );
                    """.trimIndent())
                    
                    // 3. Partial indexes for tool_toggle_state (matching ON CONFLICT logic in ToolManagementServiceImpl)
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_session_tool ON tool_toggle_state (session_id, tool_name) WHERE tool_name IS NOT NULL;")
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_uq_session_server ON tool_toggle_state (session_id, server_name) WHERE server_name IS NOT NULL;")
                    
                    log.info("Database schema initialized successfully.")
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                log.error("Failed to initialize database schema", e)
                throw e
            }
        }
    }
}
