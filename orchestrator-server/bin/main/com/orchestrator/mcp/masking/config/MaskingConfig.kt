package com.orchestrator.mcp.masking.config

import com.orchestrator.mcp.kbstore.model.MappingType
import kotlinx.serialization.Serializable

/**
 * Configuration for PII Masking Engine.
 * Controls which strategies are active and their parameters.
 */
@Serializable
data class MaskingConfig(
    /** Which PII types are enabled for detection */
    val enabledStrategies: Set<MappingType> = MappingType.entries.toSet(),

    /** Characters to search before/after a match for context keywords */
    val contextWindow: Int = 50,

    /** Keywords that indicate a nearby number is a bank account */
    val contextKeywords: List<String> = listOf(
        "tài khoản", "STK", "account", "số TK", "bank account"
    ),

    /** Prefix indicators for Vietnamese name detection */
    val namePrefixes: List<String> = listOf(
        "Ông", "Bà", "Anh", "Chị", "KH",
        "Khách hàng", "Mr.", "Mrs.", "Ms."
    )
)
