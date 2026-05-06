package com.orchestrator.mcp.fileproxy

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Unit tests for FileProxyConfig serialization and defaults.
 */
class FileProxyConfigTest : DescribeSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    describe("FileProxyConfig defaults") {
        it("has correct default values") {
            val config = FileProxyConfig()
            config.enabled shouldBe true
            config.maxSizeMb shouldBe 50
            config.tempDirectory shouldBe "/tmp/mcp-file-proxy"
            config.ttlMinutes shouldBe 60
            config.cleanupIntervalMinutes shouldBe 15
            config.shutdownTimeoutSeconds shouldBe 30
            config.inputProxyEnabled shouldBe true
            config.outputProxyEnabled shouldBe true
            config.runtimeDetectionEnabled shouldBe true
            config.servers shouldBe emptyMap()
        }

        it("serializes and deserializes correctly") {
            val config = FileProxyConfig(
                enabled = false,
                maxSizeMb = 100,
                servers = mapOf("pdf-tools" to ServerFileProxyConfig(maxSizeMb = 200))
            )

            val serialized = json.encodeToString(FileProxyConfig.serializer(), config)
            val deserialized = json.decodeFromString(FileProxyConfig.serializer(), serialized)

            deserialized.enabled shouldBe false
            deserialized.maxSizeMb shouldBe 100
            deserialized.servers["pdf-tools"]?.maxSizeMb shouldBe 200
        }
    }

    describe("ServerFileProxyConfig") {
        it("allows null maxSizeMb for inheriting global default") {
            val serverConfig = ServerFileProxyConfig()
            serverConfig.maxSizeMb shouldBe null
        }

        it("overrides global max size when specified") {
            val serverConfig = ServerFileProxyConfig(maxSizeMb = 200)
            serverConfig.maxSizeMb shouldBe 200
        }
    }
})
