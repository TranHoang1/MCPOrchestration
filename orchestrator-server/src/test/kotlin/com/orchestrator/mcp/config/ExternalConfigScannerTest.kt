package com.orchestrator.mcp.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

class ExternalConfigScannerTest : FunSpec({

    test("findExternalYaml returns null for empty dir") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            val result = ExternalConfigScanner
                .findExternalYaml(tempDir)
            result.shouldBeNull()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("findExternalYaml finds application.yml") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            File(tempDir, "application.yml").writeText(
                "orchestrator:\n  server:\n    port: 9090"
            )
            val result = ExternalConfigScanner
                .findExternalYaml(tempDir)
            result.shouldNotBeNull()
            result shouldBe
                "orchestrator:\n  server:\n    port: 9090"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("findExternalJson finds config.json") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            val content = """{"upstream_servers": []}"""
            File(tempDir, "config.json").writeText(content)
            val result = ExternalConfigScanner
                .findExternalJson(tempDir)
            result.shouldNotBeNull()
            result shouldBe content
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("findExternalJson finds application.json") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            val content = """{"upstream_servers": []}"""
            File(tempDir, "application.json")
                .writeText(content)
            val result = ExternalConfigScanner
                .findExternalJson(tempDir)
            result.shouldNotBeNull()
            result shouldBe content
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("config.json has priority over application.json") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            File(tempDir, "config.json")
                .writeText("config-json-content")
            File(tempDir, "application.json")
                .writeText("app-json-content")
            val result = ExternalConfigScanner
                .findExternalJson(tempDir)
            result shouldBe "config-json-content"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("listConfigFiles returns found files") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            File(tempDir, "application.yml").writeText("x")
            File(tempDir, "config.json").writeText("y")
            val files = ExternalConfigScanner
                .listConfigFiles(tempDir)
            files shouldContain "application.yml"
            files shouldContain "config.json"
        } finally {
            tempDir.deleteRecursively()
        }
    }

    test("findExternalJson returns null for empty dir") {
        val tempDir = createTempDirectory("scanner-test")
            .toFile()
        try {
            val result = ExternalConfigScanner
                .findExternalJson(tempDir)
            result.shouldBeNull()
        } finally {
            tempDir.deleteRecursively()
        }
    }
})
