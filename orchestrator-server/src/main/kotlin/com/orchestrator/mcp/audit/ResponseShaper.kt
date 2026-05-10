package com.orchestrator.mcp.audit

import com.orchestrator.mcp.security.model.KbRole

/**
 * Role-based response shaping.
 * Filters/masks fields in API responses based on caller's role.
 */
interface ResponseShaper {

    /** Shape a single field value based on role and field name. */
    fun shape(role: KbRole, fieldName: String, value: String?): String?

    /** Shape an entire response map based on role. */
    fun shapeMap(role: KbRole, data: Map<String, Any?>): Map<String, Any?>
}
