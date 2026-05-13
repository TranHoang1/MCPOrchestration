package com.orchestrator.mcp.auth

import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.sql.DataSource

/**
 * PostgreSQL implementation of BridgeTokenRepository.
 * Uses bridge_tokens table created by migration V4.
 */
class BridgeTokenRepositoryImpl(
    private val dataSource: DataSource
) : BridgeTokenRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun save(
        id: String,
        userId: String,
        tokenHash: String,
        expiresAt: String
    ) {
        val sql = """
            INSERT INTO bridge_tokens (id, user_id, token_hash, expires_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, userId)
                stmt.setString(3, tokenHash)
                stmt.setString(4, expiresAt)
                stmt.executeUpdate()
            }
        }
        logger.debug("Saved bridge token: id={}, userId={}", id, userId)
    }

    override suspend fun isValid(tokenHash: String): Boolean {
        val sql = """
            SELECT 1 FROM bridge_tokens
            WHERE token_hash = ? AND revoked = false
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, tokenHash)
                stmt.executeQuery().next()
            }
        }
    }

    override suspend fun revokeAllExcept(userId: String, keepId: String) {
        val sql = """
            UPDATE bridge_tokens SET revoked = true
            WHERE user_id = ? AND id != ? AND revoked = false
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.setString(2, keepId)
                val count = stmt.executeUpdate()
                if (count > 0) {
                    logger.info("Revoked {} previous bridge tokens for user={}", count, userId)
                }
            }
        }
    }

    override suspend fun revoke(tokenId: String) {
        val sql = "UPDATE bridge_tokens SET revoked = true WHERE id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, tokenId)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun countActive(userId: String): Int {
        val sql = """
            SELECT COUNT(*) FROM bridge_tokens
            WHERE user_id = ? AND revoked = false
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }
}
