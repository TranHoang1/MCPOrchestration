package com.orchestrator.mcp.jira.model

/**
 * Result of downloading an attachment binary.
 */
data class DownloadResult(
    val content: ByteArray,
    val contentType: String,
    val contentLength: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadResult) return false
        return content.contentEquals(other.content) &&
            contentType == other.contentType &&
            contentLength == other.contentLength
    }

    override fun hashCode(): Int {
        var result = content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + contentLength.hashCode()
        return result
    }
}
