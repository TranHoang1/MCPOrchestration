package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.User
import com.orchestrator.mcp.usermanagement.model.UserFilter
import com.orchestrator.mcp.usermanagement.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

/**
 * JDBC implementation of UserRepository.
 */
class UserRepositoryImpl(
    private val dataSource: DataSource
) : UserRepository {

    override suspend fun create(
        email: String, encryptedToken: String, role: UserRole,
        displayName: String, createdBy: UUID?
    ): User = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO users (email, jira_token_encrypted, role, display_name, created_by)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, email, role, display_name, active, created_by, created_at, updated_at
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, email)
                stmt.setString(2, encryptedToken)
                stmt.setString(3, role.name)
                stmt.setString(4, displayName)
                stmt.setObject(5, createdBy)
                val rs = stmt.executeQuery()
                rs.next()
                mapRowToUser(rs)
            }
        }
    }

    override suspend fun findById(id: UUID): User? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT id, email, role, display_name, active, created_by, created_at, updated_at FROM users WHERE id = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRowToUser(rs) else null
            }
        }
    }

    override suspend fun findByEmail(email: String): User? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT id, email, role, display_name, active, created_by, created_at, updated_at FROM users WHERE email = ?"
            try {
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, email)
                    val rs = stmt.executeQuery()
                    if (rs.next()) mapRowToUser(rs) else null
                }
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(javaClass)
                    .error("findByEmail('{}') SQL error: {}", email, e.message)
                null
            }
        }
    }

    override suspend fun findAll(filter: UserFilter): List<User> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val conditions = mutableListOf<String>()
            val params = mutableListOf<Any>()
            filter.role?.let { conditions.add("role = ?"); params.add(it.name) }
            filter.active?.let { conditions.add("active = ?"); params.add(it) }
            val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
            val sql = "SELECT id, email, role, display_name, active, created_by, created_at, updated_at FROM users$where ORDER BY created_at DESC"
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                val rs = stmt.executeQuery()
                buildList { while (rs.next()) add(mapRowToUser(rs)) }
            }
        }
    }

    override suspend fun update(
        id: UUID, role: UserRole?, displayName: String?, encryptedToken: String?
    ): User? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sets = mutableListOf<String>()
            val params = mutableListOf<Any>()
            role?.let { sets.add("role = ?"); params.add(it.name) }
            displayName?.let { sets.add("display_name = ?"); params.add(it) }
            encryptedToken?.let { sets.add("jira_token_encrypted = ?"); params.add(it) }
            if (sets.isEmpty()) return@withContext findById(id)
            sets.add("updated_at = NOW()")
            params.add(id)
            val sql = "UPDATE users SET ${sets.joinToString(", ")} WHERE id = ? RETURNING id, email, role, display_name, active, created_by, created_at, updated_at"
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setObject(i + 1, p) }
                val rs = stmt.executeQuery()
                if (rs.next()) mapRowToUser(rs) else null
            }
        }
    }

    override suspend fun setActive(id: UUID, active: Boolean): User? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "UPDATE users SET active = ?, updated_at = NOW() WHERE id = ? RETURNING id, email, role, display_name, active, created_by, created_at, updated_at"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setBoolean(1, active)
                stmt.setObject(2, id)
                val rs = stmt.executeQuery()
                if (rs.next()) mapRowToUser(rs) else null
            }
        }
    }

    override suspend fun countByRole(role: UserRole, activeOnly: Boolean): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT COUNT(*) FROM users WHERE role = ?" + if (activeOnly) " AND active = true" else ""
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, role.name)
                val rs = stmt.executeQuery()
                rs.next(); rs.getInt(1)
            }
        }
    }

    override suspend fun getEncryptedToken(userId: UUID): String? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT jira_token_encrypted FROM users WHERE id = ? AND active = true"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.getString(1) else null
            }
        }
    }

    private fun mapRowToUser(rs: java.sql.ResultSet): User {
        val id = rs.getString("id") ?: ""
        val createdBy = rs.getString("created_by")
        return User(
            id = id,
            email = rs.getString("email") ?: "",
            role = UserRole.fromString(rs.getString("role") ?: "developer"),
            displayName = rs.getString("display_name") ?: "",
            active = rs.getBoolean("active"),
            createdBy = createdBy,
            createdAt = rs.getString("created_at") ?: "",
            updatedAt = rs.getString("updated_at") ?: ""
        )
    }

    override suspend fun countAll(): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM users")
                rs.next(); rs.getInt(1)
            }
        }
    }

    override suspend fun createWithPassword(
        email: String, displayName: String, role: String, passwordHash: String
    ): Unit = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO users (id, email, role, display_name, active, password_hash, auth_mode)
                VALUES (gen_random_uuid(), ?, ?, ?, true, ?, 'local')
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, email)
                stmt.setString(2, role)
                stmt.setString(3, displayName)
                stmt.setString(4, passwordHash)
                stmt.executeUpdate()
            }
        }
    }
}
