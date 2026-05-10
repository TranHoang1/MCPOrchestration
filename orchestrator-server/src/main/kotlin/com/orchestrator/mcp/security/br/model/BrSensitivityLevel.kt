package com.orchestrator.mcp.security.br.model

/**
 * Sensitivity classification for Business Rules content.
 * Higher sensitivity = stricter access control + lower rate limit.
 */
enum class BrSensitivityLevel(val level: Int, val maxPerHour: Int) {
    HIGH(1, 5),
    MEDIUM(2, 15),
    LOW(3, 30);

    companion object {
        fun fromInt(value: Int): BrSensitivityLevel =
            entries.find { it.level == value }
                ?: throw IllegalArgumentException("Unknown sensitivity level: $value")
    }
}
