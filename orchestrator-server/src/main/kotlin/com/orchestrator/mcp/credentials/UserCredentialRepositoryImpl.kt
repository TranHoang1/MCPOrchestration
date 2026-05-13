package com.orchestrator.mcp.credentials

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL implementation of UserCredentialRepository.
 * Stores encrypted credential blobs in user_credentials table.
 */
class UserCredentialRepositoryImpl(
    private val dataSource: DataSource
) : UserCredentialRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun getEncrypted(userId: String, serverName: String): String? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT credentials_encrypted FROM user_credentials WHERE user_id = ? AND server_name = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, serverName)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getString("credentials_encrypted") else null
                }
            }
        }

    override suspend fun save(userId: String, serverName: String, encryptedJson: String) =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    INSERT INTO user_credentials (id, user_id, server_name, credentials_encrypted, updated_at)
                    VALUES (?, ?, ?, ?, to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))
                    ON CONFLICT (user_id, server_name)
                    DO UPDATE SET credentials_encrypted = EXCLUDED.credentials_encrypted,
                                  updated_at = EXCLUDED.updated_at
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, UUID.randomUUID().toString())
                    stmt.setString(2, userId)
                    stmt.setString(3, serverName)
                    stmt.setString(4, encryptedJson)
                    stmt.executeUpdate()
                }
            }
            logger.debug("Saved credentials for user={} server={}", userId, serverName)
        }

    override suspend fun delete(userId: String, serverName: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM user_credentials WHERE user_id = ? AND server_name = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, serverName)
                    stmt.executeUpdate() > 0
                }
            }
        }

    override suspend fun listServerNames(userId: String): List<String> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT server_name FROM user_credentials WHERE user_id = ? ORDER BY server_name"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    val rs = stmt.executeQuery()
                    buildList { while (rs.next()) add(rs.getString("server_name")) }
                }
            }
        }

    override suspend fun exists(userId: String, serverName: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT 1 FROM user_credentials WHERE user_id = ? AND server_name = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.setString(2, serverName)
                    stmt.executeQuery().next()
                }
            }
        }
}
