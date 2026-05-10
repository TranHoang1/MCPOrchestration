package com.orchestrator.mcp.security.model

/**
 * PostgreSQL roles for KB Row-Level Security.
 * Each enum value maps to a NOLOGIN role created by V8 migration.
 * Used with SET LOCAL ROLE to activate RLS policies per transaction.
 */
enum class KbRole(val pgRoleName: String) {
    DEVELOPER("kb_developer"),
    BA_ADMIN("kb_admin"),
    LOW_PRIVILEGE("kb_viewer");

    companion object {
        fun fromString(value: String): KbRole =
            entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown KB role: $value")
    }
}
