package com.orchestrator.mcp.promotion

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

/**
 * Represents a tool that has been promoted to the top-level tools/list.
 * Promoted tools are directly callable without going through find_tools first.
 */
data class PromotedTool(
    val name: String,
    val upstreamServer: String,
    val originalSchema: JsonObject,
    val compactSchema: JsonObject,
    val compactDescription: String,
    val promotedAt: Instant,
    var lastUsedAt: Instant,
    var callCount: Int = 0,
    var status: PromotionStatus = PromotionStatus.ACTIVE
)

enum class PromotionStatus { ACTIVE, DEMOTED, FAILED }
