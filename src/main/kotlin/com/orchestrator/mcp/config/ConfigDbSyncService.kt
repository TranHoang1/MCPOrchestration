package com.orchestrator.mcp.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

@Serializable
data class SyncResult(
    val totalServers: Int,
    val syncedServers: Int,
    val failedServers: Int,
    val errors: List<String> = emptyList()
)

interface ConfigDbSyncService {
    suspend fun sync(): SyncResult
    fun getSyncResult(): SyncResult?
}

class ConfigDbSyncServiceImpl(
    private val dataSource: HikariDataSource,
    private val configManager: ConfigurationManager
) : ConfigDbSyncService {
    private val log = LoggerFactory.getLogger(javaClass)
    private var lastResult: SyncResult? = null

    override fun getSyncResult(): SyncResult? = lastResult

    override suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        log.info("Starting Config-DB synchronization")
        val config = configManager.getConfig()
        val servers = config.orchestrator.upstreamServers
        var synced = 0
        var failed = 0
        val errors = mutableListOf<String>()

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                // 1. Mark all existing active servers as inactive (soft delete)
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate("UPDATE server_config SET is_active = false")
                }

                // 2. Upsert servers from config
                val sql = """
                    INSERT INTO server_config (
                        server_name, transport, command, args, env_keys, url, 
                        disabled, tool_filter, auto_approve, is_active, synced_at
                    ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, true, NOW())
                    ON CONFLICT (server_name) DO UPDATE SET
                        transport = EXCLUDED.transport,
                        command = EXCLUDED.command,
                        args = EXCLUDED.args,
                        env_keys = EXCLUDED.env_keys,
                        url = EXCLUDED.url,
                        disabled = EXCLUDED.disabled,
                        tool_filter = EXCLUDED.tool_filter,
                        auto_approve = EXCLUDED.auto_approve,
                        is_active = true,
                        synced_at = NOW();
                """.trimIndent()

                conn.prepareStatement(sql).use { pstmt ->
                    for (server in servers) {
                        try {
                            pstmt.setString(1, server.name)
                            pstmt.setString(2, server.transport)
                            pstmt.setString(3, server.command)
                            pstmt.setString(4, Json.encodeToString(server.args))
                            pstmt.setString(5, Json.encodeToString(server.env))
                            pstmt.setString(6, server.url)
                            pstmt.setBoolean(7, server.disabled)
                            pstmt.setString(8, server.toolFilter?.let { Json.encodeToString(it) })
                            pstmt.setString(9, Json.encodeToString(server.autoApprove))
                            pstmt.addBatch()
                            synced++
                        } catch (e: Exception) {
                            log.error("Failed to prepare sync for server ${server.name}", e)
                            errors.add("Server ${server.name}: ${e.message}")
                            failed++
                        }
                    }
                    pstmt.executeBatch()
                }
                conn.commit()
                log.info("Sync completed: $synced synced, $failed failed")
            } catch (e: Exception) {
                conn.rollback()
                log.error("Sync transaction failed", e)
                errors.add("Sync transaction failed: ${e.message}")
            }
        }

        val result = SyncResult(servers.size, synced, failed, errors)
        lastResult = result
        result
    }
}
