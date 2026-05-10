package com.orchestrator.mcp.bridge

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HealthCheckManagerTest : DescribeSpec({

    describe("HealthCheckManager") {

        it("should not start when interval is 0 (disabled)") {
            val client = mockk<HttpStreamableClient>()
            val reconnection = mockk<ReconnectionManager>()
            val config = HealthCheckConfig(pingIntervalMs = 0)

            val manager = HealthCheckManager(config, client, reconnection)
            val scope = TestScope()
            manager.start(scope)

            // No coroutine launched
            coVerify(exactly = 0) { client.sendRequest(any(), any()) }
            scope.cancel()
        }

        it("should send ping after interval") {
            runTest {
                val client = mockk<HttpStreamableClient>()
                val reconnection = mockk<ReconnectionManager>()
                val config = HealthCheckConfig(
                    pingIntervalMs = 1000,
                    pingTimeoutMs = 500
                )

                coEvery { client.sendRequest("ping", null) } returns mockk<kotlinx.serialization.json.JsonObject>()

                val manager = HealthCheckManager(config, client, reconnection)
                manager.start(this)

                advanceTimeBy(1100)

                coVerify(atLeast = 1) { client.sendRequest("ping", null) }
                manager.stop()
            }
        }

        it("should trigger reconnect on ping failure") {
            runTest {
                val client = mockk<HttpStreamableClient>()
                val reconnection = mockk<ReconnectionManager>()
                val config = HealthCheckConfig(
                    pingIntervalMs = 1000,
                    pingTimeoutMs = 500,
                    failureThreshold = 1
                )

                coEvery { client.sendRequest("ping", null) } throws RuntimeException("timeout")
                every { reconnection.state } returns BridgeState.DISCONNECTED
                every { reconnection.state = any() } just Runs
                every { client.resetSession() } just Runs
                coEvery { reconnection.reconnectLoop() } just Runs

                val manager = HealthCheckManager(config, client, reconnection)
                manager.start(this)

                advanceTimeBy(1100)

                verify { reconnection.state = BridgeState.DISCONNECTED }
                manager.stop()
            }
        }

        it("should reset failures on successful ping") {
            runTest {
                val client = mockk<HttpStreamableClient>()
                val reconnection = mockk<ReconnectionManager>()
                val config = HealthCheckConfig(
                    pingIntervalMs = 500,
                    pingTimeoutMs = 200,
                    failureThreshold = 3
                )

                // First ping fails, second succeeds
                coEvery { client.sendRequest("ping", null) } throws RuntimeException("fail") andThen mockk<kotlinx.serialization.json.JsonObject>()

                val manager = HealthCheckManager(config, client, reconnection)
                manager.start(this)

                advanceTimeBy(600)  // first ping (fail)
                advanceTimeBy(600)  // second ping (success)

                // Should not have triggered reconnect (threshold=3)
                coVerify(exactly = 0) { reconnection.reconnectLoop() }
                manager.stop()
            }
        }
    }
})
