package com.orchestrator.mcp.ocr

import com.orchestrator.mcp.ocr.model.OcrConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Unit tests for OcrConfig defaults.
 * STC: UT-14.
 */
class OcrConfigTest : FunSpec({

    // UT-14: OcrConfig Default Values
    test("UT-14: default values are correct") {
        val config = OcrConfig()

        config.enabled shouldBe true
        config.serverName shouldBe "markitdown"
        config.toolName shouldBe "convert_to_markdown"
        config.timeoutSeconds shouldBe 30
        config.maxFileSizeMb shouldBe 20
        config.supportedFormats shouldContainExactly listOf(
            "image/png",
            "image/jpeg",
            "image/tiff"
        )
    }

    test("custom config overrides defaults") {
        val config = OcrConfig(
            enabled = false,
            serverName = "custom-server",
            toolName = "custom-tool",
            timeoutSeconds = 60,
            maxFileSizeMb = 50,
            supportedFormats = listOf("image/webp")
        )

        config.enabled shouldBe false
        config.serverName shouldBe "custom-server"
        config.toolName shouldBe "custom-tool"
        config.timeoutSeconds shouldBe 60
        config.maxFileSizeMb shouldBe 50
        config.supportedFormats shouldContainExactly listOf("image/webp")
    }
})
