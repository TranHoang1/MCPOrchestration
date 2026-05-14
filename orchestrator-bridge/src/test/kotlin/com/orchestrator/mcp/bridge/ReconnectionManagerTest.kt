package com.orchestrator.mcp.bridge

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.test.runTest

class ReconnectionManagerTest : DescribeSpec({

    describe("ReconnectionManager") {

        it("should transition to CONNECTED on successful init") {
            runTest {
                val client = mockk<HttpStreamableClient>()
                coEvery { client.initialize(any(), any()) } returns true
                every { client.isConnected } returns true

                val config = BridgeConfig(
                    orchestratorUrls = listOf("http://localhost:8080"),
                    baseReconnectDelayMs = 10,
                    maxReconnectDelayMs = 100
                )
                val manager = ReconnectionManager(config, client)

                val result = manager.connectWithRetry()
                result shouldBe true
                manager.state shouldBe BridgeState.CONNECTED
            }
        }

        it("should transition to DISCONNECTED after all URLs fail") {
            runTest {
                val client = mockk<HttpStreamableClient>()
                coEvery { client.initialize(any(), any()) } returns false
                every { client.isConnected } returns false

                val config = BridgeConfig(
                    orchestratorUrls = listOf("http://localhost:8080"),
                    baseReconnectDelayMs = 1,
                    maxReconnectDelayMs = 10
                )
                val manager = ReconnectionManager(config, client)

                val result = manager.connectWithRetry()
                result shouldBe false
                manager.state shouldBe BridgeState.DISCONNECTED
            }
        }

        it("should start in DISCONNECTED state") {
            val client = mockk<HttpStreamableClient>()
            val config = BridgeConfig(orchestratorUrls = listOf("http://localhost:8080"))
            val manager = ReconnectionManager(config, client)
            manager.state shouldBe BridgeState.DISCONNECTED
        }

        it("should try multiple URLs on failover") {
            runTest {
                val client = mockk<HttpStreamableClient>()
                coEvery { client.initialize("http://primary:8080", any()) } returns false
                coEvery { client.initialize("http://backup:8080", any()) } returns true
                every { client.isConnected } returns true

                val config = BridgeConfig(
                    orchestratorUrls = listOf("http://primary:8080", "http://backup:8080"),
                    baseReconnectDelayMs = 1,
                    maxReconnectDelayMs = 10
                )
                val manager = ReconnectionManager(config, client)

                val result = manager.connectWithRetry()
                result shouldBe true
                manager.state shouldBe BridgeState.CONNECTED
            }
        }
    }
})
