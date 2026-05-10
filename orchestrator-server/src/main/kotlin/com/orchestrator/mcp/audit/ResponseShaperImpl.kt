package com.orchestrator.mcp.audit

import com.orchestrator.mcp.security.model.KbRole

/**
 * Implements role-based response shaping rules:
 * - BA_ADMIN: sees everything
 * - DEVELOPER: BR masked, PII masked, own audit only
 * - LOW_PRIVILEGE: BR hidden, PII hidden, no audit access
 */
class ResponseShaperImpl : ResponseShaper {

    override fun shape(role: KbRole, fieldName: String, value: String?): String? =
        when (fieldName) {
            "business_rules" -> shapeBr(role, value)
            "pii_original" -> shapePii(role, value)
            "audit_logs" -> shapeAudit(role, value)
            "content" -> shapeContent(role, value)
            else -> value
        }

    override fun shapeMap(role: KbRole, data: Map<String, Any?>): Map<String, Any?> =
        data.mapValues { (key, value) ->
            when (value) {
                is String -> shape(role, key, value)
                else -> value
            }
        }.filterValues { shouldInclude(role, it) }

    private fun shapeBr(role: KbRole, value: String?): String? =
        when (role) {
            KbRole.BA_ADMIN -> value
            KbRole.DEVELOPER -> "[BR_MASKED]"
            KbRole.LOW_PRIVILEGE -> null
        }

    private fun shapePii(role: KbRole, value: String?): String? =
        when (role) {
            KbRole.BA_ADMIN -> value
            KbRole.DEVELOPER -> "[PII_MASKED]"
            KbRole.LOW_PRIVILEGE -> null
        }

    private fun shapeAudit(role: KbRole, value: String?): String? =
        when (role) {
            KbRole.BA_ADMIN -> value
            else -> null
        }

    private fun shapeContent(role: KbRole, value: String?): String? =
        when (role) {
            KbRole.LOW_PRIVILEGE -> value?.take(200)
            else -> value
        }

    private fun shouldInclude(role: KbRole, value: Any?): Boolean =
        value != null || role == KbRole.BA_ADMIN
}
