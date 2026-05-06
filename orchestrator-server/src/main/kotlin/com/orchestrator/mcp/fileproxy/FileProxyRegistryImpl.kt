package com.orchestrator.mcp.fileproxy

import com.orchestrator.mcp.fileproxy.model.FileProxyEntry
import com.orchestrator.mcp.fileproxy.model.FileProxyStatus
import com.orchestrator.mcp.fileproxy.model.ProxyDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL implementation of FileProxyRegistry using HikariCP.
 */
class FileProxyRegistryImpl(
    private val dataSource: DataSource
) : FileProxyRegistry {

    private val logger = LoggerFactory.getLogger(FileProxyRegistryImpl::class.java)

    override suspend fun createEntry(entry: FileProxyEntry): FileProxyEntry {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(INSERT_SQL).use { stmt ->
                    stmt.setObject(1, entry.fileId)
                    stmt.setObject(2, entry.sessionId)
                    stmt.setString(3, entry.filePath)
                    stmt.setString(4, entry.fileName)
                    stmt.setObject(5, entry.fileSize)
                    stmt.setString(6, entry.realToolName)
                    stmt.setString(7, entry.upstreamServer)
                    stmt.setString(8, entry.direction.name)
                    stmt.setString(9, entry.status.name)
                    stmt.setTimestamp(10, Timestamp.from(entry.createdAt.toJavaInstant()))
                    stmt.executeUpdate()
                }
            }
            logger.debug("[FileProxy] Registry entry created: file_id={}", entry.fileId)
            entry
        }
    }

    override suspend fun updateStatus(
        fileId: UUID,
        status: FileProxyStatus,
        processedAt: Instant?
    ) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(UPDATE_STATUS_SQL).use { stmt ->
                    stmt.setString(1, status.name)
                    val ts = processedAt ?: Clock.System.now()
                    stmt.setTimestamp(2, Timestamp.from(ts.toJavaInstant()))
                    stmt.setObject(3, fileId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun deleteEntry(fileId: UUID) {
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_BY_ID_SQL).use { stmt ->
                    stmt.setObject(1, fileId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun findByFileId(fileId: UUID): FileProxyEntry? {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SELECT_BY_ID_SQL).use { stmt ->
                    stmt.setObject(1, fileId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) mapRow(rs) else null
                    }
                }
            }
        }
    }

    override suspend fun findBySessionId(sessionId: UUID): List<FileProxyEntry> {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SELECT_BY_SESSION_SQL).use { stmt ->
                    stmt.setObject(1, sessionId)
                    stmt.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(mapRow(rs)) }
                    }
                }
            }
        }
    }

    override suspend fun deleteBySessionId(sessionId: UUID): Int {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_BY_SESSION_SQL).use { stmt ->
                    stmt.setObject(1, sessionId)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun deleteExpiredEntries(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(DELETE_EXPIRED_SQL).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(olderThan.toJavaInstant()))
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun findOrphanEntries(currentSessionId: UUID): List<FileProxyEntry> {
        return withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SELECT_ORPHANS_SQL).use { stmt ->
                    stmt.setObject(1, currentSessionId)
                    stmt.executeQuery().use { rs ->
                        buildList { while (rs.next()) add(mapRow(rs)) }
                    }
                }
            }
        }
    }

    private fun mapRow(rs: ResultSet): FileProxyEntry {
        return FileProxyEntry(
            fileId = rs.getObject("file_id", UUID::class.java),
            sessionId = rs.getObject("session_id", UUID::class.java),
            filePath = rs.getString("file_path"),
            fileName = rs.getString("file_name"),
            fileSize = rs.getObject("file_size") as? Long,
            realToolName = rs.getString("real_tool_name"),
            upstreamServer = rs.getString("upstream_server"),
            direction = ProxyDirection.valueOf(rs.getString("direction")),
            status = FileProxyStatus.valueOf(rs.getString("status")),
            createdAt = Instant.fromEpochMilliseconds(
                rs.getTimestamp("created_at").time
            ),
            processedAt = rs.getTimestamp("processed_at")?.let {
                Instant.fromEpochMilliseconds(it.time)
            }
        )
    }

    companion object {
        private const val INSERT_SQL = """
            INSERT INTO file_proxy_registry 
            (file_id, session_id, file_path, file_name, file_size, 
             real_tool_name, upstream_server, direction, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
        private const val UPDATE_STATUS_SQL = """
            UPDATE file_proxy_registry SET status=?, processed_at=? WHERE file_id=?
        """
        private const val DELETE_BY_ID_SQL = "DELETE FROM file_proxy_registry WHERE file_id=?"
        private const val SELECT_BY_ID_SQL = "SELECT * FROM file_proxy_registry WHERE file_id=?"
        private const val SELECT_BY_SESSION_SQL = "SELECT * FROM file_proxy_registry WHERE session_id=?"
        private const val DELETE_BY_SESSION_SQL = "DELETE FROM file_proxy_registry WHERE session_id != ?"
        private const val DELETE_EXPIRED_SQL = "DELETE FROM file_proxy_registry WHERE created_at < ?"
        private const val SELECT_ORPHANS_SQL = "SELECT * FROM file_proxy_registry WHERE session_id != ?"
    }
}
