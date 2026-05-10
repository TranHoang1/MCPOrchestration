package com.orchestrator.mcp.sync

import com.orchestrator.mcp.sync.model.AttachmentQueueItem
import com.orchestrator.mcp.sync.model.AttachmentStatus
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.sql.ResultSet

/**
 * JDBC implementation of AttachmentQueueRepository.
 * Implements persistent FIFO queue with status lifecycle and retry support.
 */
class AttachmentQueueRepositoryImpl(
    private val dataSource: HikariDataSource
) : AttachmentQueueRepository {

    private val log = LoggerFactory.getLogger(AttachmentQueueRepositoryImpl::class.java)

    override suspend fun enqueue(item: AttachmentQueueItem) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(ENQUEUE_SQL).use { stmt ->
                setEnqueueParams(stmt, item)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun enqueueBatch(items: List<AttachmentQueueItem>): Int =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext 0
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    val count = conn.prepareStatement(ENQUEUE_SQL).use { stmt ->
                        items.forEach { item ->
                            setEnqueueParams(stmt, item)
                            stmt.addBatch()
                        }
                        stmt.executeBatch().sum()
                    }
                    conn.commit()
                    log.debug("Batch enqueue completed: {} attachments", count)
                    count
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }

    override suspend fun pollPending(limit: Int): List<AttachmentQueueItem> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT * FROM jira_attachment_queue 
                    WHERE status = 'PENDING' 
                    ORDER BY created_at ASC 
                    LIMIT ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, limit)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun updateStatus(id: Int, status: AttachmentStatus, error: String?) =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    UPDATE jira_attachment_queue 
                    SET status = ?, error_message = ?, processed_at = NOW()
                    WHERE id = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, status.name)
                    stmt.setString(2, error)
                    stmt.setInt(3, id)
                    stmt.executeUpdate()
                }
            }
            Unit
        }

    override suspend fun markDone(id: Int) = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = """
                UPDATE jira_attachment_queue 
                SET status = 'DONE', processed_at = NOW(), error_message = NULL
                WHERE id = ?
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, id)
                stmt.executeUpdate()
            }
        }
        Unit
    }

    override suspend fun incrementRetry(id: Int, error: String) =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    UPDATE jira_attachment_queue 
                    SET retry_count = retry_count + 1, error_message = ?, 
                        status = 'PENDING'
                    WHERE id = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, error)
                    stmt.setInt(2, id)
                    stmt.executeUpdate()
                }
            }
            Unit
        }

    override suspend fun findByTicket(ticketKey: String): List<AttachmentQueueItem> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT * FROM jira_attachment_queue WHERE ticket_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, ticketKey)
                    mapResults(stmt.executeQuery())
                }
            }
        }

    override suspend fun countByStatus(status: AttachmentStatus): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT COUNT(*) FROM jira_attachment_queue WHERE status = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, status.name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }

    private fun setEnqueueParams(stmt: java.sql.PreparedStatement, item: AttachmentQueueItem) {
        stmt.setString(1, item.ticketKey)
        stmt.setString(2, item.attachmentId)
        stmt.setString(3, item.filename)
        stmt.setString(4, item.mimeType)
        if (item.sizeBytes != null) stmt.setLong(5, item.sizeBytes) else stmt.setNull(5, java.sql.Types.BIGINT)
        stmt.setString(6, item.downloadUrl)
    }

    private fun mapResults(rs: ResultSet): List<AttachmentQueueItem> {
        val results = mutableListOf<AttachmentQueueItem>()
        while (rs.next()) results.add(mapRow(rs))
        return results
    }

    private fun mapRow(rs: ResultSet): AttachmentQueueItem {
        val processedTs = rs.getTimestamp("processed_at")
        return AttachmentQueueItem(
            id = rs.getInt("id"),
            ticketKey = rs.getString("ticket_key"),
            attachmentId = rs.getString("attachment_id"),
            filename = rs.getString("filename"),
            mimeType = rs.getString("mime_type"),
            sizeBytes = rs.getObject("size_bytes") as? Long,
            downloadUrl = rs.getString("download_url"),
            status = AttachmentStatus.valueOf(rs.getString("status")),
            retryCount = rs.getInt("retry_count"),
            errorMessage = rs.getString("error_message"),
            createdAt = Instant.fromEpochMilliseconds(rs.getTimestamp("created_at").time),
            processedAt = processedTs?.let { Instant.fromEpochMilliseconds(it.time) }
        )
    }

    companion object {
        private val ENQUEUE_SQL = """
            INSERT INTO jira_attachment_queue 
                (ticket_key, attachment_id, filename, mime_type, size_bytes, download_url)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (ticket_key, attachment_id) DO NOTHING
        """.trimIndent()
    }
}
