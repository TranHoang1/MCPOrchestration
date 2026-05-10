package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.UserProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.sql.DataSource

/** JDBC implementation of UserProjectRepository. */
class UserProjectRepositoryImpl(
    private val dataSource: DataSource
) : UserProjectRepository {

    override suspend fun assign(userId: UUID, projectKey: String, grantedBy: UUID): UserProject =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    INSERT INTO user_projects (user_id, project_key, granted_by)
                    VALUES (?, ?, ?)
                    RETURNING id, user_id, project_key, granted_by, granted_at
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setString(2, projectKey)
                    stmt.setObject(3, grantedBy)
                    val rs = stmt.executeQuery()
                    rs.next()
                    UserProject(
                        id = rs.getObject("id", UUID::class.java).toString(),
                        userId = rs.getObject("user_id", UUID::class.java).toString(),
                        projectKey = rs.getString("project_key"),
                        grantedBy = rs.getObject("granted_by", UUID::class.java).toString(),
                        grantedAt = rs.getString("granted_at")
                    )
                }
            }
        }

    override suspend fun findByUser(userId: UUID): List<UserProject> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT id, user_id, project_key, granted_by, granted_at FROM user_projects WHERE user_id = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, userId)
                    val rs = stmt.executeQuery()
                    buildList {
                        while (rs.next()) add(
                            UserProject(
                                id = rs.getObject("id", UUID::class.java).toString(),
                                userId = rs.getObject("user_id", UUID::class.java).toString(),
                                projectKey = rs.getString("project_key"),
                                grantedBy = rs.getObject("granted_by", UUID::class.java).toString(),
                                grantedAt = rs.getString("granted_at")
                            )
                        )
                    }
                }
            }
        }

    override suspend fun exists(userId: UUID, projectKey: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT 1 FROM user_projects WHERE user_id = ? AND project_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setString(2, projectKey)
                    stmt.executeQuery().next()
                }
            }
        }

    override suspend fun revoke(userId: UUID, projectKey: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM user_projects WHERE user_id = ? AND project_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setObject(1, userId)
                    stmt.setString(2, projectKey)
                    stmt.executeUpdate() > 0
                }
            }
        }
}
