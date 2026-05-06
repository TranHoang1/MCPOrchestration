package com.orchestrator.mcp.fileproxy

import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Database migration for file_proxy_registry table.
 * Executed on startup using existing pattern of manual SQL via HikariCP.
 */
class FileProxyMigration(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(FileProxyMigration::class.java)

    fun migrate() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(CREATE_TABLE_SQL)
                stmt.execute(INDEX_SESSION_SQL)
                stmt.execute(INDEX_STATUS_SQL)
                stmt.execute(INDEX_CREATED_SQL)
            }
        }
        logger.info("[FileProxy] Database migration completed: file_proxy_registry")
    }

    companion object {
        private const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS file_proxy_registry (
                file_id UUID PRIMARY KEY,
                session_id UUID NOT NULL,
                file_path VARCHAR(500) NOT NULL,
                file_name VARCHAR(255),
                file_size BIGINT,
                real_tool_name VARCHAR(255),
                upstream_server VARCHAR(255),
                direction VARCHAR(10) NOT NULL DEFAULT 'INPUT',
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                processed_at TIMESTAMP,
                CONSTRAINT chk_direction CHECK (direction IN ('INPUT', 'OUTPUT')),
                CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'EXPIRED'))
            )
        """
        private const val INDEX_SESSION_SQL =
            "CREATE INDEX IF NOT EXISTS idx_file_proxy_session ON file_proxy_registry(session_id)"
        private const val INDEX_STATUS_SQL =
            "CREATE INDEX IF NOT EXISTS idx_file_proxy_status ON file_proxy_registry(status)"
        private const val INDEX_CREATED_SQL =
            "CREATE INDEX IF NOT EXISTS idx_file_proxy_created ON file_proxy_registry(created_at)"
    }
}
