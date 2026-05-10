package com.orchestrator.mcp.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests for JSON + YAML config merge behavior.
 */
class JsonConfigMergeTest : FunSpec({

    val baseYaml = """
        orchestrator:
          server:
            port: 8080
            transport: stdio
          discovery:
            top_k: 5
            similarity_threshold: 0.7
            max_query_length: 2000
            fallback_to_keyword: true
          execution:
            timeout_seconds: 30
            validate_arguments: true
            max_retries: 1
          embedding:
            provider: openai
            model: text-embedding-3-small
            api_key: test-key
            dimensions: 768
          vector_db:
            provider: qdrant
            host: localhost
            port: 6333
            collection_name: mcp_tools
          health:
            check_interval_seconds: 30
            auto_reconnect: true
            max_reconnect_attempts: 5
          upstream_servers: []
    """.trimIndent()

    test("JSON servers merged into YAML config") {
        val tempDir = createTempDirectory("merge-test")
            .toFile()
        try {
            File(tempDir, "application.yml")
                .writeText(baseYaml)
            File(tempDir, "config.json").writeText("""
            {
              "upstream_servers": [
                {
                  "name": "jira-server",
                  "transport": "stdio",
                  "command": "npx",
                  "args": ["-y", "@mcp/server-jira"]
                }
              ]
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator.discovery.topK shouldBe 5
            config.orchestrator.upstreamServers shouldHaveSize 1
            config.orchestrator.upstreamServers[0]
                .name shouldBe "jira-server"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("JSON servers appended to YAML servers") {
        val yamlWithServer = """
            orchestrator:
              embedding:
                api_key: test-key
              upstream_servers:
                - name: yaml-server
                  transport: stdio
                  command: echo
        """.trimIndent()

        val tempDir = createTempDirectory("merge-test")
            .toFile()
        try {
            File(tempDir, "application.yml")
                .writeText(yamlWithServer)
            File(tempDir, "config.json").writeText("""
            {
              "upstream_servers": [
                {
                  "name": "json-server",
                  "transport": "http",
                  "url": "http://localhost:3001"
                }
              ]
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator.upstreamServers shouldHaveSize 2
            config.orchestrator.upstreamServers[0]
                .name shouldBe "yaml-server"
            config.orchestrator.upstreamServers[1]
                .name shouldBe "json-server"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("JSON overrides YAML server with same name") {
        val yamlWithServer = """
            orchestrator:
              embedding:
                api_key: test-key
              upstream_servers:
                - name: shared-server
                  transport: stdio
                  command: old-command
        """.trimIndent()

        val tempDir = createTempDirectory("merge-test")
            .toFile()
        try {
            File(tempDir, "application.yml")
                .writeText(yamlWithServer)
            File(tempDir, "config.json").writeText("""
            {
              "upstream_servers": [
                {
                  "name": "shared-server",
                  "transport": "http",
                  "url": "http://localhost:9999"
                }
              ]
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator.upstreamServers shouldHaveSize 1
            val server = config.orchestrator
                .upstreamServers[0]
            server.name shouldBe "shared-server"
            server.transport shouldBe "http"
            server.url shouldBe "http://localhost:9999"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("no JSON file — YAML only") {
        val tempDir = createTempDirectory("merge-test")
            .toFile()
        try {
            File(tempDir, "application.yml")
                .writeText(baseYaml)

            val manager = ConfigurationManagerImpl(
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator.discovery.topK shouldBe 5
            config.orchestrator.upstreamServers shouldHaveSize 0
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("configContent bypasses external file scan") {
        val tempDir = createTempDirectory("merge-test")
            .toFile()
        try {
            File(tempDir, "config.json").writeText("""
            {
              "upstream_servers": [
                { "name": "should-not-load" }
              ]
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                configContent = baseYaml,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            // configContent skips external scan
            config.orchestrator.upstreamServers shouldHaveSize 0
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("external YAML overrides classpath YAML") {
        val externalYaml = """
            orchestrator:
              discovery:
                top_k: 15
              embedding:
                api_key: external-key
        """.trimIndent()

        val tempDir = createTempDirectory("merge-test")
            .toFile()
        try {
            File(tempDir, "application.yml")
                .writeText(externalYaml)

            val manager = ConfigurationManagerImpl(
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator.discovery.topK shouldBe 15
        } finally {
            tempDir.deleteRecursively()
        }
    }
})
