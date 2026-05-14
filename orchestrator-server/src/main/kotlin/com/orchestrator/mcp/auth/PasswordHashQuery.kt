package com.orchestrator.mcp.auth

import com.orchestrator.mcp.usermanagement.repository.UserRepository
import javax.sql.DataSource
import org.koin.java.KoinJavaComponent
import org.slf4j.LoggerFactory

/**
 * Queries password_hash from users table.
 * Separate from UserRepository since password_hash is auth-specific.
 */
object PasswordHashQuery {

    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun getHash(userRepository: UserRepository, userId: String): String? {
        val dataSource = KoinJavaComponent.getKoin().get<DataSource>()
        return queryHash(dataSource, userId)
    }

    private fun queryHash(dataSource: DataSource, userId: String): String? {
        val sql = "SELECT password_hash FROM users WHERE id = ?::uuid"
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getString("password_hash") else null
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to query password_hash for userId={}: {}", userId, e.message)
            null
        }
    }
}
