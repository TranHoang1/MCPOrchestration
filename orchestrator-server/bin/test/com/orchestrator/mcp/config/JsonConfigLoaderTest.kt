package com.orchestrator.mcp.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class JsonConfigLoaderTest : FunSpec({

    test("parse root-level upstream_servers") {
        val json = """
        {
          "upstream_servers": [
            {
              "name": "jira-server",
              "transport": "stdio",
              "command": "npx",
              "args": ["-y", "@mcp/server-jira"],
              "env": { "JIRA_URL": "https://jira.test" }
            }
          ]
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 1
        servers[0].name shouldBe "jira-server"
        servers[0].transport shouldBe "stdio"
        servers[0].command shouldBe "npx"
        servers[0].args shouldBe listOf("-y", "@mcp/server-jira")
        servers[0].env["JIRA_URL"] shouldBe "https://jira.test"
    }

    test("parse nested orchestrator.upstream_servers") {
        val json = """
        {
          "orchestrator": {
            "upstream_servers": [
              {
                "name": "git-server",
                "transport": "http",
                "url": "http://localhost:3001/mcp"
              }
            ]
          }
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 1
        servers[0].name shouldBe "git-server"
        servers[0].transport shouldBe "http"
        servers[0].url shouldBe "http://localhost:3001/mcp"
    }

    test("parse multiple servers") {
        val json = """
        {
          "upstream_servers": [
            {
              "name": "server-a",
              "transport": "stdio",
              "command": "echo"
            },
            {
              "name": "server-b",
              "transport": "http",
              "url": "http://localhost:9999"
            }
          ]
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 2
        servers[0].name shouldBe "server-a"
        servers[1].name shouldBe "server-b"
    }

    test("empty upstream_servers returns empty list") {
        val json = """{ "upstream_servers": [] }"""
        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 0
    }

    test("no upstream_servers key returns empty list") {
        val json = """{ "other_key": "value" }"""
        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 0
    }

    test("invalid JSON returns empty list") {
        val json = "not valid json {{"
        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 0
    }

    test("defaults applied for missing fields") {
        val json = """
        {
          "upstream_servers": [
            { "name": "minimal-server" }
          ]
        }
        """.trimIndent()

        val servers = JsonConfigLoader
            .parseUpstreamServers(json)
        servers shouldHaveSize 1
        servers[0].name shouldBe "minimal-server"
        servers[0].transport shouldBe "stdio"
        servers[0].command shouldBe null
        servers[0].args shouldBe emptyList()
        servers[0].env shouldBe emptyMap()
        servers[0].url shouldBe null
    }
})
