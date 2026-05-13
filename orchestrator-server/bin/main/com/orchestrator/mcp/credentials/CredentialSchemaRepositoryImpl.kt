package com.orchestrator.mcp.credentials

import com.orchestrator.mcp.credentials.model.CredentialSchemaField
import com.orchestrator.mcp.credentials.model.SchemaListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL implementation of CredentialSchemaRepository.
 * Uses credential_schemas table for field definitions.
 */
class CredentialSchemaRepositoryImpl(
    private val dataSource: DataSource
) : CredentialSchemaRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun listSchemas(): List<SchemaListItem> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val sql = """
                SELECT server_name, COUNT(*) as field_count,
                       MAX(updated_at) as updated_at
                FROM credential_schemas
                GROUP BY server_name ORDER BY server_name
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                buildList {
                    while (rs.next()) {
                        add(buildListItem(rs))
                    }
                }
            }
        }
    }

    override suspend fun getByServerName(serverName: String): List<CredentialSchemaField> =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = """
                    SELECT id, field_key, field_label, field_type, field_required,
                           field_description, field_placeholder, display_order
                    FROM credential_schemas
                    WHERE server_name = ? ORDER BY display_order
                """.trimIndent()
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, serverName)
                    val rs = stmt.executeQuery()
                    buildList { while (rs.next()) add(mapField(rs)) }
                }
            }
        }

    override suspend fun saveSchema(serverName: String, fields: List<CredentialSchemaField>) =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                conn.autoCommit = false
                try {
                    deleteAllForServer(conn, serverName)
                    insertFields(conn, serverName, fields)
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                }
            }
        }

    override suspend fun deleteField(serverName: String, fieldKey: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "DELETE FROM credential_schemas WHERE server_name = ? AND field_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, serverName)
                    stmt.setString(2, fieldKey)
                    stmt.executeUpdate() > 0
                }
            }
        }

    override suspend fun countUsersWithData(serverName: String): Int =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT COUNT(DISTINCT user_id) FROM user_credentials WHERE server_name = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, serverName)
                    val rs = stmt.executeQuery()
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }

    override suspend fun fieldExists(serverName: String, fieldKey: String): Boolean =
        withContext(Dispatchers.IO) {
            dataSource.connection.use { conn ->
                val sql = "SELECT 1 FROM credential_schemas WHERE server_name = ? AND field_key = ?"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, serverName)
                    stmt.setString(2, fieldKey)
                    stmt.executeQuery().next()
                }
            }
        }

    private fun deleteAllForServer(conn: java.sql.Connection, serverName: String) {
        val sql = "DELETE FROM credential_schemas WHERE server_name = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, serverName)
            stmt.executeUpdate()
        }
    }

    private fun insertFields(
        conn: java.sql.Connection,
        serverName: String,
        fields: List<CredentialSchemaField>
    ) {
        val sql = """
            INSERT INTO credential_schemas 
            (id, server_name, field_key, field_label, field_type, field_required,
             field_description, field_placeholder, display_order)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            for (field in fields) {
                setInsertParams(stmt, serverName, field)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun setInsertParams(
        stmt: java.sql.PreparedStatement,
        serverName: String,
        field: CredentialSchemaField
    ) {
        stmt.setString(1, UUID.randomUUID().toString())
        stmt.setString(2, serverName)
        stmt.setString(3, field.field_key)
        stmt.setString(4, field.field_label)
        stmt.setString(5, field.field_type)
        stmt.setBoolean(6, field.field_required)
        stmt.setString(7, field.field_description)
        stmt.setString(8, field.field_placeholder)
        stmt.setInt(9, field.display_order)
    }

    private fun buildListItem(rs: java.sql.ResultSet): SchemaListItem {
        val serverName = rs.getString("server_name")
        return SchemaListItem(
            server_name = serverName,
            field_count = rs.getInt("field_count"),
            users_configured = 0,
            updated_at = rs.getString("updated_at")
        )
    }

    private fun mapField(rs: java.sql.ResultSet): CredentialSchemaField {
        return CredentialSchemaField(
            id = rs.getString("id"),
            field_key = rs.getString("field_key"),
            field_label = rs.getString("field_label"),
            field_type = rs.getString("field_type"),
            field_required = rs.getBoolean("field_required"),
            field_description = rs.getString("field_description"),
            field_placeholder = rs.getString("field_placeholder"),
            display_order = rs.getInt("display_order")
        )
    }
}
