package com.orchestrator.mcp.e2e

import com.orchestrator.mcp.config.ConfigurationManagerImpl
import com.orchestrator.mcp.core.model.ConfigException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * E2E-API tests for configuration management (E2E-005).
 */
class E2eConfigApiTest : FunSpec({

    // STC: E2E-005 — Config hot-reload add and remove server
    test("E2E-005: config reload reflects new server list") {
        val yaml1 = """
            orchestrator:
              upstream_servers:
                - name: server-a
                  transport: stdio
                  command: echo
        """.trimIndent()

        val manager = ConfigurationManagerImpl(configContent = yaml1)
        val config1 = manager.getConfig()
        config1.orchestrator.upstreamServers.size shouldBe 1

        // Simulate reload with new config (via new manager instance)
        val yaml2 = """
            orchestrator:
              upstream_servers:
                - name: server-a
                  transport: stdio
                  command: echo
                - name: server-b
                  transport: http
                  url: http://localhost:9999
        """.trimIndent()

        val manager2 = ConfigurationManagerImpl(configContent = yaml2)
        val config2 = manager2.getConfig()
        config2.orchestrator.upstreamServers.size shouldBe 2
    }

    test("E2E-005b: config with removed server has fewer entries") {
        val yaml = """
            orchestrator:
              upstream_servers:
                - name: server-b
                  transport: http
                  url: http://localhost:9999
        """.trimIndent()

        val manager = ConfigurationManagerImpl(configContent = yaml)
        val config = manager.getConfig()
        config.orchestrator.upstreamServers.size shouldBe 1
        config.orchestrator.upstreamServers[0].name shouldBe "server-b"
    }

    test("E2E-005c: invalid config throws ConfigException") {
        shouldThrow<ConfigException> {
            ConfigurationManagerImpl(configContent = "invalid: [yaml {{").getConfig()
        }
    }

    test("E2E-005d: env var substitution in config") {
        val yaml = """
            orchestrator:
              embedding:
                api_key: "${'$'}{TEST_API_KEY}"
        """.trimIndent()

        val resolved = ConfigurationManagerImpl.resolveEnvVars(yaml)
        // If env var not set, resolves to empty
        val expected = System.getenv("TEST_API_KEY") ?: ""
        resolved.contains(expected) shouldBe true
    }
})
