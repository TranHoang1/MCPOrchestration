package com.orchestrator.mcp.dashboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WebSocketHandlerTest : FunSpec({

    test("initial connection count is zero") {
        val bus = SyncEventBus()
        val handler = WebSocketHandler(bus)
        handler.connectionCount shouldBe 0
    }

    test("max connections constant is 50") {
        WebSocketHandler.MAX_CONNECTIONS shouldBe 50
    }
})
