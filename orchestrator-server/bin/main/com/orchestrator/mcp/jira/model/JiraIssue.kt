package com.orchestrator.mcp.jira.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Single Jira issue representation.
 * Uses JsonObject for dynamic fields to handle Jira's flexible schema.
 */
@Serializable
data class JiraIssue(
    val id: String,
    val key: String,
    val self: String,
    val fields: JsonObject,
    val changelog: JsonObject? = null
)
