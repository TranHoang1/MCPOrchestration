package com.orchestrator.mcp.sync

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Executes DDL migration for Jira sync tables.
 * Follows existing DatabaseInitializer pattern — idempotent via IF NOT EXISTS.
 */
class JiraSyncDatabaseInitializer(private val dataSource: HikariDataSource) {

    private val log = LoggerFactory.getLogger(JiraSyncDatabaseInitializer::class.java)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        log.info("Initializing Jira sync database schema...")
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(CREATE_SYNC_STATE)
                    stmt.execute(CREATE_TICKET_CACHE)
                    stmt.execute(CREATE_TICKET_GRAPH)
                    stmt.execute(CREATE_ATTACHMENT_QUEUE)
                    stmt.execute(CREATE_INDEXES)
                }
                conn.commit()
                log.info("Jira sync database schema initialized successfully.")
            } catch (e: Exception) {
                conn.rollback()
                log.error("Failed to initialize Jira sync schema", e)
                throw e
            }
        }
    }

    companion object {
        private val CREATE_SYNC_STATE = """
            CREATE TABLE IF NOT EXISTS jira_sync_state (
                project_key VARCHAR(50) PRIMARY KEY,
                last_sync_at TIMESTAMPTZ,
                last_offset INTEGER NOT NULL DEFAULT 0,
                total_issues INTEGER NOT NULL DEFAULT 0,
                synced_issues INTEGER NOT NULL DEFAULT 0,
                status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
                error_message TEXT,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                CONSTRAINT chk_sync_status CHECK (status IN ('IDLE','RUNNING','PAUSED','COMPLETED','FAILED')),
                CONSTRAINT chk_offset_non_negative CHECK (last_offset >= 0),
                CONSTRAINT chk_total_non_negative CHECK (total_issues >= 0),
                CONSTRAINT chk_synced_non_negative CHECK (synced_issues >= 0)
            );
        """.trimIndent()

        private val CREATE_TICKET_CACHE = """
            CREATE TABLE IF NOT EXISTS jira_ticket_cache (
                ticket_key VARCHAR(50) PRIMARY KEY,
                project_key VARCHAR(50) NOT NULL,
                summary TEXT NOT NULL,
                issue_type VARCHAR(50) NOT NULL,
                status VARCHAR(50) NOT NULL,
                priority VARCHAR(20),
                parent_key VARCHAR(50),
                epic_key VARCHAR(50),
                labels JSONB,
                updated_at_jira TIMESTAMPTZ NOT NULL,
                synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                content_hash VARCHAR(64) NOT NULL,
                kb_ingested BOOLEAN NOT NULL DEFAULT FALSE
            );
        """.trimIndent()

        private val CREATE_TICKET_GRAPH = """
            CREATE TABLE IF NOT EXISTS jira_ticket_graph (
                source_key VARCHAR(50) NOT NULL,
                target_key VARCHAR(50) NOT NULL,
                link_type VARCHAR(100) NOT NULL,
                category VARCHAR(20) NOT NULL,
                PRIMARY KEY (source_key, target_key, link_type),
                CONSTRAINT chk_graph_category CHECK (category IN ('INWARD','OUTWARD','SUBTASK','EPIC'))
            );
        """.trimIndent()

        private val CREATE_ATTACHMENT_QUEUE = """
            CREATE TABLE IF NOT EXISTS jira_attachment_queue (
                id SERIAL PRIMARY KEY,
                ticket_key VARCHAR(50) NOT NULL,
                attachment_id VARCHAR(50) NOT NULL,
                filename VARCHAR(500) NOT NULL,
                mime_type VARCHAR(100),
                size_bytes BIGINT,
                download_url TEXT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                retry_count INTEGER NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                processed_at TIMESTAMPTZ,
                CONSTRAINT chk_attachment_status CHECK (status IN ('PENDING','DOWNLOADING','PROCESSING','DONE','FAILED')),
                CONSTRAINT chk_retry_non_negative CHECK (retry_count >= 0),
                CONSTRAINT uq_ticket_attachment UNIQUE (ticket_key, attachment_id)
            );
        """.trimIndent()

        private val CREATE_INDEXES = """
            CREATE INDEX IF NOT EXISTS idx_ticket_cache_project ON jira_ticket_cache (project_key);
            CREATE INDEX IF NOT EXISTS idx_ticket_cache_updated ON jira_ticket_cache (updated_at_jira);
            CREATE INDEX IF NOT EXISTS idx_ticket_cache_not_ingested ON jira_ticket_cache (kb_ingested) WHERE kb_ingested = FALSE;
            CREATE INDEX IF NOT EXISTS idx_ticket_graph_source ON jira_ticket_graph (source_key);
            CREATE INDEX IF NOT EXISTS idx_ticket_graph_target ON jira_ticket_graph (target_key);
            CREATE INDEX IF NOT EXISTS idx_attachment_queue_status ON jira_attachment_queue (status);
            CREATE INDEX IF NOT EXISTS idx_attachment_queue_ticket ON jira_attachment_queue (ticket_key);
            CREATE INDEX IF NOT EXISTS idx_attachment_queue_pending ON jira_attachment_queue (status, created_at) WHERE status = 'PENDING';
        """.trimIndent()
    }
}
