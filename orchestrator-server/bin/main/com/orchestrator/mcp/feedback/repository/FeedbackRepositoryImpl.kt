package com.orchestrator.mcp.feedback.repository

import com.orchestrator.mcp.feedback.model.Feedback
import com.orchestrator.mcp.feedback.model.FeedbackStatus
import com.orchestrator.mcp.feedback.model.FeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * JDBC implementation of feedback repository.
 */
class FeedbackRepositoryImpl(
    private val dataSource: DataSource
) : FeedbackRepository {

    private val logger = LoggerFactory.getLogger(FeedbackRepositoryImpl::class.java)

    override suspend fun save(feedback: Feedback): Feedback = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO kb_feedback (issue_key, user_id, feedback_type, content, suggested_correction, status)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id, created_at
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, feedback.issueKey)
                stmt.setString(2, feedback.userId)
                stmt.setString(3, feedback.type.name)
                stmt.setString(4, feedback.content)
                stmt.setString(5, feedback.suggestedCorrection)
                stmt.setString(6, feedback.status.name)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    feedback.copy(id = rs.getLong("id"), createdAt = Instant.parse(rs.getString("created_at")))
                } else feedback
            }
        }
    }

    override suspend fun update(feedback: Feedback): Unit = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE kb_feedback SET status = ?, reviewer_id = ?, rejection_reason = ?, resolved_at = ?::timestamptz
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, feedback.status.name)
                stmt.setString(2, feedback.reviewerId)
                stmt.setString(3, feedback.rejectionReason)
                stmt.setString(4, feedback.resolvedAt?.toString())
                stmt.setLong(5, feedback.id)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findById(id: Long): Feedback? = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM kb_feedback WHERE id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRow(rs) else null
            }
        }
    }

    override suspend fun findByIssueKey(issueKey: String): List<Feedback> =
        withContext(Dispatchers.IO) {
            val sql = "SELECT * FROM kb_feedback WHERE issue_key = ? ORDER BY created_at DESC"
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, issueKey)
                    val rs = stmt.executeQuery()
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }

    override suspend fun findByStatus(status: FeedbackStatus, limit: Int): List<Feedback> =
        withContext(Dispatchers.IO) {
            val sql = "SELECT * FROM kb_feedback WHERE status = ? ORDER BY created_at DESC LIMIT ?"
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, status.name)
                    stmt.setInt(2, limit)
                    val rs = stmt.executeQuery()
                    buildList { while (rs.next()) add(mapRow(rs)) }
                }
            }
        }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM kb_feedback"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    override suspend fun countByStatus(status: FeedbackStatus): Long = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM kb_feedback WHERE status = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, status.name)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): Feedback = Feedback(
        id = rs.getLong("id"),
        issueKey = rs.getString("issue_key"),
        userId = rs.getString("user_id"),
        type = FeedbackType.valueOf(rs.getString("feedback_type")),
        content = rs.getString("content"),
        suggestedCorrection = rs.getString("suggested_correction"),
        status = FeedbackStatus.valueOf(rs.getString("status")),
        reviewerId = rs.getString("reviewer_id"),
        rejectionReason = rs.getString("rejection_reason"),
        createdAt = Instant.parse(rs.getString("created_at")),
        resolvedAt = rs.getString("resolved_at")?.let { Instant.parse(it) }
    )
}
