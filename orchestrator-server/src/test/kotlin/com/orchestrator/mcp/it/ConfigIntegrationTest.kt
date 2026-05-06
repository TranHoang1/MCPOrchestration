package com.orchestrator.mcp.it

import com.orchestrator.mcp.config.ConfigurationManagerImpl
import com.orchestrator.mcp.core.model.ConfigException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * IT tests for Configuration Management.
 * Real: ConfigurationManagerImpl with real file I/O.
 * No mocks — tests actual YAML parsing, env var
 * substitution, and config reload via temp files.
 */
class ConfigIntegrationTest : FunSpec({

    // STC: IT-016 — hot-reload adds new server config
    test("IT-016: load config then reload with new server") {
        val dir = createTempDirectory("cfg-it016").toFile()
        try {
            val file = File(dir, "application.yml")
            file.writeText(buildYaml(servers = 1))

            val mgr = ConfigurationManagerImpl(
                configPath = file.absolutePath
            )
            mgr.getConfig().orchestrator
                .upstreamServers.size shouldBe 1

            // Modify file — add second server
            file.writeText(buildYaml(servers = 2))
            val reloaded = mgr.reload()
            reloaded.orchestrator
                .upstreamServers.size shouldBe 2
        } finally {
            dir.deleteRecursively()
        }
    }

    // STC: IT-017 — hot-reload server removed
    test("IT-017: reload with fewer servers") {
        val dir = createTempDirectory("cfg-it017").toFile()
        try {
            val file = File(dir, "application.yml")
            file.writeText(buildYaml(servers = 2))

            val mgr = ConfigurationManagerImpl(
                configPath = file.absolutePath
            )
            mgr.getConfig().orchestrator
                .upstreamServers.size shouldBe 2

            // Remove one server
            file.writeText(buildYaml(servers = 1))
            val reloaded = mgr.reload()
            reloaded.orchestrator
                .upstreamServers.size shouldBe 1
        } finally {
            dir.deleteRecursively()
        }
    }

    // STC: IT-018 — invalid config rejected, previous retained
    test("IT-018: invalid config keeps previous config") {
        val dir = createTempDirectory("cfg-it018").toFile()
        try {
            val file = File(dir, "application.yml")
            file.writeText(buildYaml(servers = 1))

            val mgr = ConfigurationManagerImpl(
                configPath = file.absolutePath
            )
            val original = mgr.getConfig()
            original.orchestrator
                .upstreamServers.size shouldBe 1

            // Write invalid YAML
            file.writeText("orchestrator: [invalid yaml {{")
            val afterReload = mgr.reload()
            // Previous config retained
            afterReload.orchestrator
                .upstreamServers.size shouldBe 1
        } finally {
            dir.deleteRecursively()
        }
    }

    // STC: IT-019 — environment variable substitution
    test("IT-019: env var substitution works") {
        val content =
            "orchestrator:\n  embedding:\n    api_key: \${OPENAI_API_KEY}"
        val resolved =
            ConfigurationManagerImpl.resolveEnvVars(content)

        val envValue = System.getenv("OPENAI_API_KEY") ?: ""
        resolved shouldContain "api_key: $envValue"
    }

    test("IT-018b: invalid YAML throws ConfigException") {
        val invalid = "orchestrator: [invalid yaml {{"

        shouldThrow<ConfigException> {
            ConfigurationManagerImpl(
                configContent = invalid
            ).getConfig()
        }
    }
})

/**
 * Build a minimal valid YAML config with N servers.
 */
private fun buildYaml(servers: Int): String {
    val sb = StringBuilder()
    sb.appendLine("orchestrator:")
    sb.appendLine("  upstream_servers:")
    for (i in 1..servers) {
        sb.appendLine("    - name: server-$i")
        sb.appendLine("      transport: stdio")
        sb.appendLine("      command: echo")
    }
    return sb.toString()
}
