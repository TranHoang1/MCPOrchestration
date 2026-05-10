package com.orchestrator.mcp.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests for --config CLI argument loading in
 * ConfigurationManagerImpl.
 * Covers: loadCliConfigServers(), merge priority,
 * relative path resolution, backward compatibility.
 *
 * Note: configPath is used by loadYamlConfig() first
 * (as YAML source), then by loadCliConfigServers()
 * (as mcpServers JSON). When testing CLI JSON loading,
 * we provide configContent for YAML to avoid configPath
 * being parsed as YAML.
 */
class CliConfigTest : FunSpec({

    val baseYaml = """
        orchestrator:
          embedding:
            api_key: test-key
          upstream_servers: []
    """.trimIndent()

    val yamlWithServer = """
        orchestrator:
          embedding:
            api_key: test-key
          upstream_servers:
            - name: yaml-server
              transport: stdio
              command: echo
    """.trimIndent()

    // STC: IT-020 — --config loads mcpServers format
    test("IT-020: --config loads mcpServers format") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            val configFile = File(tempDir, "mcp.json")
            configFile.writeText("""
            {
              "mcpServers": {
                "jira": {
                  "command": "npx",
                  "args": ["-y", "@mcp/jira"]
                }
              }
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                configContent = baseYaml,
                configPath = configFile.absolutePath,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 1
            config.orchestrator
                .upstreamServers[0].name shouldBe "jira"
            config.orchestrator
                .upstreamServers[0].transport shouldBe "stdio"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // STC: IT-021 — --config merges with YAML servers
    test("IT-021: --config servers merge with YAML") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            val configFile = File(tempDir, "cli.json")
            configFile.writeText("""
            {
              "mcpServers": {
                "cli-server": {
                  "url": "http://localhost:3001"
                }
              }
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                configContent = yamlWithServer,
                configPath = configFile.absolutePath,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 2
            val names = config.orchestrator
                .upstreamServers.map { it.name }
            names shouldBe listOf(
                "yaml-server", "cli-server"
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // STC: IT-022 — CLI overrides YAML server same name
    test("IT-022: CLI overrides YAML server same name") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            val configFile = File(tempDir, "cli.json")
            configFile.writeText("""
            {
              "mcpServers": {
                "yaml-server": {
                  "url": "http://localhost:9999"
                }
              }
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                configContent = yamlWithServer,
                configPath = configFile.absolutePath,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 1
            val server = config.orchestrator
                .upstreamServers[0]
            server.name shouldBe "yaml-server"
            server.transport shouldBe "http"
            server.url shouldBe "http://localhost:9999"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // STC: IT-023 — CLI > JSON > YAML priority
    test("IT-023: CLI has higher priority than JSON") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            File(tempDir, "config.json").writeText("""
            {
              "upstream_servers": [
                {
                  "name": "shared",
                  "transport": "stdio",
                  "command": "json-cmd"
                }
              ]
            }
            """.trimIndent())
            val cliFile = File(tempDir, "cli.json")
            cliFile.writeText("""
            {
              "mcpServers": {
                "shared": {
                  "url": "http://cli-wins:8080"
                }
              }
            }
            """.trimIndent())

            // Use external YAML + JSON scan + CLI
            File(tempDir, "application.yml")
                .writeText(baseYaml)
            val manager = ConfigurationManagerImpl(
                configPath = cliFile.absolutePath,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            val server = config.orchestrator
                .upstreamServers
                .find { it.name == "shared" }!!
            server.transport shouldBe "http"
            server.url shouldBe "http://cli-wins:8080"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("--config file not found continues gracefully") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            val manager = ConfigurationManagerImpl(
                configContent = baseYaml,
                configPath = "nonexistent.json",
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 0
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("--config with relative path resolves from workDir") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            val subDir = File(tempDir, "configs")
            subDir.mkdirs()
            File(subDir, "servers.json").writeText("""
            {
              "mcpServers": {
                "rel-server": {
                  "command": "echo"
                }
              }
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                configContent = baseYaml,
                configPath = "configs/servers.json",
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 1
            config.orchestrator
                .upstreamServers[0].name shouldBe "rel-server"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("no --config backward compatible") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            File(tempDir, "application.yml")
                .writeText(yamlWithServer)

            val manager = ConfigurationManagerImpl(
                configPath = null,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 1
            config.orchestrator
                .upstreamServers[0].name shouldBe "yaml-server"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("--config with absolute path works") {
        val tempDir = createTempDirectory("cli-test")
            .toFile()
        try {
            val configFile = File(tempDir, "abs.json")
            configFile.writeText("""
            {
              "mcpServers": {
                "abs-server": {
                  "command": "echo"
                }
              }
            }
            """.trimIndent())

            val manager = ConfigurationManagerImpl(
                configContent = baseYaml,
                configPath = configFile.absolutePath,
                workingDirectory = tempDir
            )
            val config = manager.getConfig()

            config.orchestrator
                .upstreamServers shouldHaveSize 1
            config.orchestrator
                .upstreamServers[0].name shouldBe "abs-server"
        } finally {
            tempDir.deleteRecursively()
        }
    }
})
