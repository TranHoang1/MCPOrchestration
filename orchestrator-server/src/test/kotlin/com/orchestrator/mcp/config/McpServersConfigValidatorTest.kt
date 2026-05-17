package com.orchestrator.mcp.config

import com.orchestrator.mcp.core.config.UpstreamServerConfig
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Tests for MTO-113: mcp-servers.json validation on startup.
 */
class McpServersConfigValidatorTest : FunSpec({

    context("validateJsonSyntax") {

        test("valid JSON returns no errors") {
            val content = """{"mcpServers": {}}"""
            val errors = McpServersConfigValidator
                .validateJsonSyntax(content)
            errors.shouldBeEmpty()
        }

        test("empty content returns error") {
            val errors = McpServersConfigValidator
                .validateJsonSyntax("")
            errors shouldHaveSize 1
            errors[0] shouldContain "empty"
        }

        test("blank content returns error") {
            val errors = McpServersConfigValidator
                .validateJsonSyntax("   ")
            errors shouldHaveSize 1
            errors[0] shouldContain "empty"
        }

        test("missing comma in args array detected") {
            val content = """
            {
              "mcpServers": {
                "test": {
                  "command": "npx",
                  "args": ["-y" "@mcp/server"]
                }
              }
            }
            """.trimIndent()
            val errors = McpServersConfigValidator
                .validateJsonSyntax(content)
            errors shouldHaveSize 1
            errors[0] shouldContain "Invalid JSON syntax"
        }

        test("trailing comma detected") {
            val content = """
            {
              "mcpServers": {
                "test": {
                  "command": "npx",
                  "args": ["-y",],
                }
              }
            }
            """.trimIndent()
            val errors = McpServersConfigValidator
                .validateJsonSyntax(content)
            errors shouldHaveSize 1
            errors[0] shouldContain "Invalid JSON syntax"
        }

        test("unclosed brace detected") {
            val content = """{"mcpServers": {"""
            val errors = McpServersConfigValidator
                .validateJsonSyntax(content)
            errors shouldHaveSize 1
            errors[0] shouldContain "Invalid JSON syntax"
        }
    }

    context("validateServerConfigs") {

        test("valid stdio server returns no errors") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "test-server",
                    transport = "stdio",
                    command = "npx",
                    args = listOf("-y", "@mcp/server")
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors.shouldBeEmpty()
        }

        test("valid http server returns no errors") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "http-server",
                    transport = "http",
                    url = "http://localhost:3000"
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors.shouldBeEmpty()
        }

        test("blank name produces error") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "",
                    transport = "stdio",
                    command = "npx"
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors shouldHaveSize 1
            errors[0] shouldContain "name must not be blank"
        }

        test("stdio without command produces error") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "bad-server",
                    transport = "stdio",
                    command = null
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors shouldHaveSize 1
            errors[0] shouldContain "requires 'command'"
        }

        test("http without url produces error") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "bad-http",
                    transport = "http",
                    url = null
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors shouldHaveSize 1
            errors[0] shouldContain "requires 'url'"
        }

        test("blank env key produces error") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "env-server",
                    transport = "stdio",
                    command = "node",
                    env = mapOf("" to "value")
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors.shouldContain("[env-server] env contains blank key")
        }

        test("multiple errors collected") {
            val servers = listOf(
                UpstreamServerConfig(
                    name = "",
                    transport = "stdio",
                    command = null
                ),
                UpstreamServerConfig(
                    name = "ok-server",
                    transport = "http",
                    url = null
                )
            )
            val errors = McpServersConfigValidator
                .validateServerConfigs(servers)
            errors.size shouldBe 3
        }
    }

    context("validateOrThrow") {

        test("valid JSON does not throw") {
            val content = """{"mcpServers": {}}"""
            shouldNotThrow<ConfigValidationException> {
                McpServersConfigValidator
                    .validateOrThrow(content, "test.json")
            }
        }

        test("malformed JSON throws with path info") {
            val content = """{"broken: json"""
            val ex = shouldThrow<ConfigValidationException> {
                McpServersConfigValidator
                    .validateOrThrow(content, "mcp-servers.json")
            }
            ex.message!! shouldContain "mcp-servers.json"
            ex.message!! shouldContain "Invalid JSON syntax"
        }
    }

    context("integration with JsonConfigLoader") {

        test("malformed JSON fails fast in parseUpstreamServers") {
            val content = """{"args": ["-y" "missing-comma"]}"""
            shouldThrow<ConfigValidationException> {
                JsonConfigLoader.parseUpstreamServers(content)
            }
        }

        test("valid JSON parses successfully") {
            val content = """
            {
              "upstream_servers": [
                {
                  "name": "valid",
                  "transport": "stdio",
                  "command": "echo",
                  "args": ["-y", "test"]
                }
              ]
            }
            """.trimIndent()
            val servers = JsonConfigLoader
                .parseUpstreamServers(content)
            servers shouldHaveSize 1
            servers[0].name shouldBe "valid"
        }
    }
})
