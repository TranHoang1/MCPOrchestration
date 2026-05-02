package com.orchestrator.mcp.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Tests for JsonConfigLoader.parseMcpServersFormat().
 * Validates parsing of MCP setting format JSON.
 */
class McpServersFormatTest : FunSpec({

    // STC: UT-031 — parse stdio server from mcpServers
    test("UT-031: parse stdio server from mcpServers") {
        val json = """
        {
          "mcpServers": {
            "jira-server": {
              "command": "npx",
              "args": ["-y", "@mcp/server-jira"],
              "env": {
                "JIRA_URL": "https://jira.test",
                "JIRA_TOKEN": "abc123"
              }
            }
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 1
        servers[0].name shouldBe "jira-server"
        servers[0].transport shouldBe "stdio"
        servers[0].command shouldBe "npx"
        servers[0].args shouldBe listOf("-y", "@mcp/server-jira")
        servers[0].env["JIRA_URL"] shouldBe "https://jira.test"
        servers[0].env["JIRA_TOKEN"] shouldBe "abc123"
        servers[0].url shouldBe null
    }

    // STC: UT-032 — parse http server from mcpServers
    test("UT-032: parse http server from mcpServers") {
        val json = """
        {
          "mcpServers": {
            "http-server": {
              "url": "http://localhost:3001/mcp"
            }
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 1
        servers[0].name shouldBe "http-server"
        servers[0].transport shouldBe "http"
        servers[0].url shouldBe "http://localhost:3001/mcp"
        servers[0].command shouldBe null
    }

    test("parse multiple servers from mcpServers") {
        val json = """
        {
          "mcpServers": {
            "jira": {
              "command": "npx",
              "args": ["-y", "@mcp/jira"]
            },
            "git": {
              "url": "http://localhost:3002"
            },
            "db": {
              "command": "python",
              "args": ["db-mcp-server.py"]
            }
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 3

        val jira = servers.find { it.name == "jira" }!!
        jira.transport shouldBe "stdio"
        jira.command shouldBe "npx"

        val git = servers.find { it.name == "git" }!!
        git.transport shouldBe "http"
        git.url shouldBe "http://localhost:3002"

        val db = servers.find { it.name == "db" }!!
        db.transport shouldBe "stdio"
        db.command shouldBe "python"
    }

    test("empty mcpServers returns empty list") {
        val json = """{ "mcpServers": {} }"""
        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 0
    }

    test("no mcpServers key returns empty list") {
        val json = """{ "other_key": "value" }"""
        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 0
    }

    test("invalid JSON returns empty list") {
        val json = "not valid json {{"
        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 0
    }

    test("server with no command and no url defaults stdio") {
        val json = """
        {
          "mcpServers": {
            "minimal": {}
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 1
        servers[0].name shouldBe "minimal"
        servers[0].transport shouldBe "stdio"
        servers[0].command shouldBe null
        servers[0].url shouldBe null
        servers[0].args shouldBe emptyList()
        servers[0].env shouldBe emptyMap()
    }

    test("server key becomes server name") {
        val json = """
        {
          "mcpServers": {
            "my-custom-name": {
              "command": "echo"
            }
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers[0].name shouldBe "my-custom-name"
    }

    test("env vars with dollar-brace syntax resolved") {
        val json = """
        {
          "mcpServers": {
            "test-server": {
              "command": "echo",
              "env": {
                "API_KEY": "literal-value",
                "HOME_DIR": "fixed-path"
              }
            }
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseMcpServersFormat(json)
        servers shouldHaveSize 1
        servers[0].env["API_KEY"] shouldBe "literal-value"
        servers[0].env["HOME_DIR"] shouldBe "fixed-path"
    }
})
