package com.orchestrator.mcp.usermanagement.repository

import com.orchestrator.mcp.usermanagement.model.DocumentType
import com.orchestrator.mcp.usermanagement.model.RolePermission
import com.orchestrator.mcp.usermanagement.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

/** JDBC implementation of RolePermissionRepository. */
class RolePermissionRepositoryImpl(
    private val dataSource: DataSource
) : RolePermissionRepository {

    override suspend fun findAll(): List<RolePermission> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT role, document_type, can_view, can_approve FROM role_permissions ORDER BY role, document_type"
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                buildList {
                    while (rs.next()) add(mapRow(rs))
                }
            }
        }
    }

    override suspend fun findByRole(role: UserRole): List<RolePermission> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = "SELECT role, document_type, can_view, can_approve FROM role_permissions WHERE role = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, role.name)
                val rs = stmt.executeQuery()
                buildList { while (rs.next()) add(mapRow(rs)) }
            }
        }
    }

    override suspend fun find(role: UserRole, docType: DocumentType): RolePermission? =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT role, document_type, can_view, can_approve FROM role_permissions WHERE role = ? AND document_type = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, role.name)
                    stmt.setString(2, docType.name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }

    override suspend fun upsert(role: UserRole, docType: DocumentType, canView: Boolean, canApprove: Boolean): Unit =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    INSERT INTO role_permissions (role, document_type, can_view, can_approve)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (role, document_type) DO UPDATE SET can_view = ?, can_approve = ?
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, role.name)
                    stmt.setString(2, docType.name)
                    stmt.setBoolean(3, canView)
                    stmt.setBoolean(4, canApprove)
                    stmt.setBoolean(5, canView)
                    stmt.setBoolean(6, canApprove)
                    stmt.executeUpdate()
                }
            }
        }

    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM role_permissions").use { stmt ->
                val rs = stmt.executeQuery(); rs.next(); rs.getInt(1)
            }
        }
    }

    override suspend fun seedDefaults() = withContext(Dispatchers.IO) {
        val defaults = buildDefaultMatrix()
        defaults.forEach { upsert(it.role, it.documentType, it.canView, it.canApprove) }
    }

    private fun buildDefaultMatrix(): List<RolePermission> = buildList {
        for (docType in DocumentType.entries) {
            add(RolePermission(UserRole.DEVELOPER, docType, canView = true, canApprove = false))
            add(RolePermission(UserRole.LEADER, docType, canView = true, canApprove = true))
            add(RolePermission(UserRole.SYSTEM_OWNER, docType, canView = true, canApprove = true))
        }
        // BA: approve BRD, FSD, UG
        add(RolePermission(UserRole.BA, DocumentType.BRD, canView = true, canApprove = true))
        add(RolePermission(UserRole.BA, DocumentType.FSD, canView = true, canApprove = true))
        add(RolePermission(UserRole.BA, DocumentType.UG, canView = true, canApprove = true))
        add(RolePermission(UserRole.BA, DocumentType.TDD, canView = true, canApprove = false))
        add(RolePermission(UserRole.BA, DocumentType.STP_STC, canView = true, canApprove = false))
        add(RolePermission(UserRole.BA, DocumentType.DPG, canView = true, canApprove = false))
        // Architect: approve FSD, TDD
        add(RolePermission(UserRole.ARCHITECT, DocumentType.FSD, canView = true, canApprove = true))
        add(RolePermission(UserRole.ARCHITECT, DocumentType.TDD, canView = true, canApprove = true))
        add(RolePermission(UserRole.ARCHITECT, DocumentType.BRD, canView = true, canApprove = false))
        add(RolePermission(UserRole.ARCHITECT, DocumentType.STP_STC, canView = true, canApprove = false))
        add(RolePermission(UserRole.ARCHITECT, DocumentType.DPG, canView = true, canApprove = false))
        add(RolePermission(UserRole.ARCHITECT, DocumentType.UG, canView = true, canApprove = false))
        // QA: approve STP_STC
        add(RolePermission(UserRole.QA, DocumentType.STP_STC, canView = true, canApprove = true))
        add(RolePermission(UserRole.QA, DocumentType.BRD, canView = true, canApprove = false))
        add(RolePermission(UserRole.QA, DocumentType.FSD, canView = true, canApprove = false))
        add(RolePermission(UserRole.QA, DocumentType.TDD, canView = true, canApprove = false))
        add(RolePermission(UserRole.QA, DocumentType.DPG, canView = true, canApprove = false))
        add(RolePermission(UserRole.QA, DocumentType.UG, canView = true, canApprove = false))
        // DevOps: approve DPG
        add(RolePermission(UserRole.DEVOPS, DocumentType.DPG, canView = true, canApprove = true))
        add(RolePermission(UserRole.DEVOPS, DocumentType.BRD, canView = true, canApprove = false))
        add(RolePermission(UserRole.DEVOPS, DocumentType.FSD, canView = true, canApprove = false))
        add(RolePermission(UserRole.DEVOPS, DocumentType.TDD, canView = true, canApprove = false))
        add(RolePermission(UserRole.DEVOPS, DocumentType.STP_STC, canView = true, canApprove = false))
        add(RolePermission(UserRole.DEVOPS, DocumentType.UG, canView = true, canApprove = false))
    }

    private fun mapRow(rs: java.sql.ResultSet): RolePermission = RolePermission(
        role = UserRole.fromString(rs.getString("role")),
        documentType = DocumentType.fromString(rs.getString("document_type")),
        canView = rs.getBoolean("can_view"),
        canApprove = rs.getBoolean("can_approve")
    )
}
