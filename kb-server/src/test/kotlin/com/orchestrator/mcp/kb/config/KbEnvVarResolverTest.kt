package com.orchestrator.mcp.kb.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class KbEnvVarResolverTest {

    @Test
    fun `resolve returns content unchanged when no patterns present`() {
        val content = "database:\n  url: jdbc:postgresql://localhost/db"
        val result = KbEnvVarResolver.resolve(content)
        assertEquals(content, result)
    }

    @Test
    fun `resolve keeps literal pattern when env var is not set`() {
        val content = "password: \${NONEXISTENT_VAR_XYZ_12345}"
        val result = KbEnvVarResolver.resolve(content)
        assertEquals(content, result)
    }

    @Test
    fun `resolve substitutes known env var`() {
        // PATH is always set on all OS
        val content = "value: \${PATH}"
        val result = KbEnvVarResolver.resolve(content)
        val expected = "value: ${System.getenv("PATH")}"
        assertEquals(expected, result)
    }

    @Test
    fun `resolve handles multiple patterns in same content`() {
        val content = "a: \${PATH}\nb: \${NONEXISTENT_VAR_ABC}"
        val result = KbEnvVarResolver.resolve(content)
        assertTrue(result.contains(System.getenv("PATH")!!))
        assertTrue(result.contains("\${NONEXISTENT_VAR_ABC}"))
    }

    @Test
    fun `resolve ignores patterns with lowercase letters`() {
        val content = "value: \${lowercase_var}"
        val result = KbEnvVarResolver.resolve(content)
        assertEquals(content, result)
    }

    @Test
    fun `resolve handles empty content`() {
        val result = KbEnvVarResolver.resolve("")
        assertEquals("", result)
    }
}
