package com.orchestrator.mcp.scanner

import com.orchestrator.mcp.scanner.model.ScanType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

/**
 * Constructs JQL queries for project scanning.
 * Supports full, incremental, and resumed scan types.
 */
class JqlBuilder(private val syncBufferMinutes: Long = 1) {

    fun build(projectKey: String, scanType: ScanType, lastSyncTime: Instant?): String {
        return when (scanType) {
            ScanType.FULL, ScanType.RESUMED ->
                buildFullJql(projectKey)
            ScanType.INCREMENTAL ->
                buildIncrementalJql(projectKey, requireNotNull(lastSyncTime))
        }
    }

    private fun buildFullJql(projectKey: String): String =
        """project = "$projectKey" ORDER BY updated DESC"""

    private fun buildIncrementalJql(projectKey: String, lastSyncTime: Instant): String {
        val buffered = lastSyncTime.minus(syncBufferMinutes.minutes)
        val formatted = formatForJira(buffered)
        return """project = "$projectKey" AND updated > "$formatted" ORDER BY updated DESC"""
    }

    private fun formatForJira(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.UTC)
        return "%04d-%02d-%02d %02d:%02d".format(
            local.year, local.monthNumber, local.dayOfMonth,
            local.hour, local.minute
        )
    }
}
