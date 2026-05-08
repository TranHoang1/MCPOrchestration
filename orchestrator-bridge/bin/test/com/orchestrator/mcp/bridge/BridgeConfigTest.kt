package com.orchestrator.mcp.bridge

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BridgeConfigTest : DescribeSpec({

    describe("BridgeConfig.load") {

        it("should use --url flag when provided") {
            val config = BridgeConfig.load(arrayOf("--url", "http://remote:9090"))
            config.orchestratorUrl shouldBe "http://remote:9090"
        }

        it("should default to localhost:8080 when no url provided") {
            val config = BridgeConfig.load(arrayOf())
            config.orchestratorUrl shouldBe "http://localhost:8080"
        }

        it("should disable reconnect with --no-reconnect flag") {
            val config = BridgeConfig.load(arrayOf("--no-reconnect"))
            config.reconnectEnabled shouldBe false
        }

        it("should enable reconnect by default") {
            val config = BridgeConfig.load(arrayOf())
            config.reconnectEnabled shouldBe true
        }

        it("should parse --timeout flag") {
            val config = BridgeConfig.load(arrayOf("--timeout", "60000"))
            config.requestTimeoutMs shouldBe 60_000
        }

        it("should default timeout to 30000ms") {
            val config = BridgeConfig.load(arrayOf())
            config.requestTimeoutMs shouldBe 30_000
        }

        it("should disable local stream write with --no-local-write") {
            val config = BridgeConfig.load(arrayOf("--no-local-write"))
            config.enableLocalStreamWrite shouldBe false
        }

        it("should have max reconnect delay of 15s") {
            val config = BridgeConfig.load(arrayOf())
            config.maxReconnectDelayMs shouldBe 15_000
        }
    }
})
