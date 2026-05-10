package com.orchestrator.mcp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Unit tests for Application.kt CLI argument parsing.
 * Tests parseConfigArg() function.
 */
class ApplicationTest : FunSpec({

    // STC: UT-030 — parseConfigArg extracts path
    test("UT-030: parseConfigArg with --config returns path") {
        val args = arrayOf("--config", "mcp-servers.json")
        parseConfigArg(args) shouldBe "mcp-servers.json"
    }

    test("parseConfigArg with no args returns null") {
        val args = emptyArray<String>()
        parseConfigArg(args).shouldBeNull()
    }

    test("parseConfigArg without --config returns null") {
        val args = arrayOf("--port", "8080")
        parseConfigArg(args).shouldBeNull()
    }

    test("parseConfigArg with --config but no value returns null") {
        val args = arrayOf("--config")
        parseConfigArg(args).shouldBeNull()
    }

    test("parseConfigArg with --config as last arg returns null") {
        val args = arrayOf("--port", "8080", "--config")
        parseConfigArg(args).shouldBeNull()
    }

    test("parseConfigArg with absolute path") {
        val args = arrayOf(
            "--config", "/opt/mcp/servers.json"
        )
        parseConfigArg(args) shouldBe "/opt/mcp/servers.json"
    }

    test("parseConfigArg with relative path") {
        val args = arrayOf(
            "--config", "./config/mcp-servers.json"
        )
        parseConfigArg(args) shouldBe "./config/mcp-servers.json"
    }

    test("parseConfigArg with other args before") {
        val args = arrayOf(
            "--port", "9090",
            "--config", "servers.json"
        )
        parseConfigArg(args) shouldBe "servers.json"
    }

    test("parseConfigArg with other args after") {
        val args = arrayOf(
            "--config", "servers.json",
            "--verbose"
        )
        parseConfigArg(args) shouldBe "servers.json"
    }
})
