package com.orchestrator.mcp.linking.model

/**
 * Result of a linking operation.
 */
data class LinkingResult(
    val issueKey: String,
    val linksCreated: Int,
    val linksFound: Int,
    val links: List<EntityLink>
)
