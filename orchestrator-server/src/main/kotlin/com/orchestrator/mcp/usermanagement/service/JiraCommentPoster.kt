package com.orchestrator.mcp.usermanagement.service

/**
 * Interface for posting comments to Jira issues.
 * Used by ApprovalJiraSyncJob to sync approval decisions.
 */
interface JiraCommentPoster {
    suspend fun postComment(ticketKey: String, body: String)
}
