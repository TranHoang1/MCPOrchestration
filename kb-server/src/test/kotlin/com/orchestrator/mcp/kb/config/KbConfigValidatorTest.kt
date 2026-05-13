package com.orchestrator.mcp.kb.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class KbConfigValidatorTest {

    @Test
    fun `validate passes with valid config`() {
        val config = KbConfig(
            kb = KbSettings(
                database = KbDatabaseConfig(
                    url = "jdbc:postgresql://localhost:5432/testdb",
                    username = "app_user",
                    password = "secure_pass"
                ),
                security = KbSecurityConfig(
                    encryptionKey = "uniqueKey123456789=",
                    brEncryptionKey = "anotherKey987654="
                )
            )
        )
        val result = KbConfigValidator.validate(config)
        assertEquals(config, result)
    }

    @Test
    fun `validate throws when database url is empty`() {
        val config = KbConfig(
            kb = KbSettings(
                database = KbDatabaseConfig(url = "")
            )
        )
        assertThrows(IllegalStateException::class.java) {
            KbConfigValidator.validate(config)
        }
    }

    @Test
    fun `validate throws when database url is blank`() {
        val config = KbConfig(
            kb = KbSettings(
                database = KbDatabaseConfig(url = "   ")
            )
        )
        assertThrows(IllegalStateException::class.java) {
            KbConfigValidator.validate(config)
        }
    }

    @Test
    fun `validate passes with default credentials but logs warning`() {
        val config = KbConfig(
            kb = KbSettings(
                database = KbDatabaseConfig(
                    url = "jdbc:postgresql://localhost/db",
                    username = "postgres",
                    password = "postgres"
                )
            )
        )
        // Should not throw — just logs warning
        val result = KbConfigValidator.validate(config)
        assertEquals(config, result)
    }

    @Test
    fun `validate passes with known default encryption key but logs warning`() {
        val config = KbConfig(
            kb = KbSettings(
                database = KbDatabaseConfig(
                    url = "jdbc:postgresql://localhost/db"
                ),
                security = KbSecurityConfig(
                    encryptionKey = "sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w="
                )
            )
        )
        // Should not throw — just logs warning
        val result = KbConfigValidator.validate(config)
        assertEquals(config, result)
    }
}
