package com.orchestrator.mcp.routing

import com.orchestrator.mcp.core.config.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class RoutingTableServiceImplTest : FunSpec({

    fun createConfig(
        enabled: Boolean = true,
        defaultLocation: String = "remote",
        localServers: List<LocalServerRouting> = emptyList()
    ): OrchestratorConfig {
        return OrchestratorConfig(
            orchestrator = OrchestratorSettings(
                routing = RoutingConfig(
                    enabled = enabled,
                    defaultLocation = defaultLocation,
                    localServers = localServers
                )
            )
        )
    }

    test("getRoutingTable returns table with configured tools") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file", "write_file")),
                LocalServerRouting("db-server", listOf("execute_sql"))
            )
        )
        val service = RoutingTableServiceImpl(config)
        val table = service.getRoutingTable()

        table.tools shouldHaveSize 3
        table.tools shouldContainKey "read_file"
        table.tools shouldContainKey "write_file"
        table.tools shouldContainKey "execute_sql"
        table.defaultLocation shouldBe "remote"
    }

    test("getRoutingTable sets correct location and server for each tool") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)
        val table = service.getRoutingTable()

        val route = table.tools["read_file"]
        route.shouldNotBeNull()
        route.location shouldBe "local"
        route.server shouldBe "fs-server"
    }

    test("getRoutingTable includes fallback when configured") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("embed-server", listOf("embed"), fallback = "remote")
            )
        )
        val service = RoutingTableServiceImpl(config)
        val table = service.getRoutingTable()

        val route = table.tools["embed"]
        route.shouldNotBeNull()
        route.fallback shouldBe "remote"
    }

    test("getRoutingTable returns empty tools when no local servers configured") {
        val config = createConfig(localServers = emptyList())
        val service = RoutingTableServiceImpl(config)
        val table = service.getRoutingTable()

        table.tools shouldHaveSize 0
        table.defaultLocation shouldBe "remote"
    }

    test("version contains hash suffix") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)
        val table = service.getRoutingTable()

        table.version shouldStartWith "1.0.0-"
        table.version.length shouldBe 14 // "1.0.0-" + 8 hex chars
    }

    test("updatedAt is ISO-8601 format") {
        val config = createConfig()
        val service = RoutingTableServiceImpl(config)
        val table = service.getRoutingTable()

        table.updatedAt shouldContain "T"
        table.updatedAt shouldContain "Z"
    }

    test("resolve returns ToolRoute for configured tool") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)

        val route = service.resolve("read_file")
        route.shouldNotBeNull()
        route.location shouldBe "local"
        route.server shouldBe "fs-server"
    }

    test("resolve returns null for unconfigured tool") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)

        service.resolve("unknown_tool").shouldBeNull()
    }

    test("getETag returns quoted string") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)
        val etag = service.getETag()

        etag shouldStartWith "\""
        etag shouldContain "\""
    }

    test("invalidate clears cache and rebuilds on next access") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)

        val table1 = service.getRoutingTable()
        service.invalidate()
        val table2 = service.getRoutingTable()

        // Both should have same tools (config unchanged)
        table1.tools.keys shouldBe table2.tools.keys
    }

    test("caching returns same instance on repeated calls") {
        val config = createConfig(
            localServers = listOf(
                LocalServerRouting("fs-server", listOf("read_file"))
            )
        )
        val service = RoutingTableServiceImpl(config)

        val table1 = service.getRoutingTable()
        val table2 = service.getRoutingTable()

        // Same cached instance
        (table1 === table2) shouldBe true
    }
})
