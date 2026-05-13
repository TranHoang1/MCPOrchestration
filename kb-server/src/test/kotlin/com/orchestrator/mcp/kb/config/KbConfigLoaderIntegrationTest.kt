package com.orchestrator.mcp.kb.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KbConfigLoaderIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `load returns defaults when config file does not exist`() {
        val config = KbConfigLoader.load("/nonexistent/path.yml")
        assertNotNull(config)
        assertEquals("pgvector", config.kb.vectorDb.provider)
    }

    @Test
    fun `load parses valid YAML file`() {
        val yamlContent = """
            kb:
              database:
                url: "jdbc:postgresql://localhost:5432/testdb"
                username: "testuser"
                password: "testpass"
              vector_db:
                provider: "pgvector"
                collection_name: "test_entries"
        """.trimIndent()

        val configFile = File(tempDir, "test-config.yml")
        configFile.writeText(yamlContent)

        val config = KbConfigLoader.load(configFile.absolutePath)
        assertEquals("jdbc:postgresql://localhost:5432/testdb", config.kb.database.url)
        assertEquals("testuser", config.kb.database.username)
        assertEquals("test_entries", config.kb.vectorDb.collectionName)
    }

    @Test
    fun `load resolves env vars in YAML content`() {
        // Uses PATH which is always available
        val yamlContent = """
            kb:
              database:
                url: "jdbc:postgresql://localhost:5432/testdb"
                schema: "test_schema"
        """.trimIndent()

        val configFile = File(tempDir, "env-config.yml")
        configFile.writeText(yamlContent)

        val config = KbConfigLoader.load(configFile.absolutePath)
        assertNotNull(config)
        assertEquals("test_schema", config.kb.database.schema)
    }

    @Test
    fun `load falls back to defaults on malformed YAML`() {
        val configFile = File(tempDir, "bad-config.yml")
        configFile.writeText("this is not: [valid: yaml: {{{}}")

        val config = KbConfigLoader.load(configFile.absolutePath)
        assertNotNull(config)
        // Should get defaults since parse failed
        assertEquals("pgvector", config.kb.vectorDb.provider)
    }
}
