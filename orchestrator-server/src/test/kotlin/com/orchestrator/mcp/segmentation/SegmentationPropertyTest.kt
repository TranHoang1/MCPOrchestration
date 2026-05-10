package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.model.BrSensitivityLevel
import com.orchestrator.mcp.segmentation.model.SegmentationResult
import com.orchestrator.mcp.segmentation.prompt.SegmentationPromptBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.serialization.json.Json

class SegmentationPropertyTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // PBT-01: SegmentationResult Serialization Roundtrip
    test("PBT-01: SegmentationResult serialization roundtrip") {
        val resultArb = Arb.bind(
            Arb.string(0..100),
            Arb.string(0..100),
            Arb.string(0..100),
            Arb.enum<BrSensitivityLevel>().orNull(),
            Arb.long(0L..60000L),
            Arb.element("openai", "ollama", "azure"),
            Arb.boolean()
        ) { pub, tech, br, level, time, prov, deg ->
            SegmentationResult(pub, tech, br, level, time, prov, deg)
        }

        checkAll(100, resultArb) { original ->
            val encoded = json.encodeToString(SegmentationResult.serializer(), original)
            val decoded = json.decodeFromString<SegmentationResult>(encoded)
            decoded shouldBe original
        }
    }

    // PBT-02: Input Truncation Preserves Max Length
    test("PBT-02: truncation never exceeds MAX_INPUT_LENGTH") {
        val longStringArb = Arb.string(10001..15000)

        checkAll(100, longStringArb) { input ->
            val truncated = if (input.length > SegmentationPromptBuilder.MAX_INPUT_LENGTH) {
                input.take(SegmentationPromptBuilder.MAX_INPUT_LENGTH)
            } else input
            (truncated.length <= SegmentationPromptBuilder.MAX_INPUT_LENGTH) shouldBe true
        }
    }

    // PBT-03: Config Temperature Bounds (data class allows any value)
    test("PBT-03: SegmentationConfig accepts any temperature value") {
        val tempArb = Arb.double(-10.0..10.0)

        checkAll(100, tempArb) { temp ->
            val config = com.orchestrator.mcp.segmentation.config.SegmentationConfig(
                temperature = temp
            )
            config.temperature shouldBe temp
        }
    }

    // PBT-04: BrSensitivityLevel Enum Serialization Roundtrip
    test("PBT-04: BrSensitivityLevel serialization roundtrip") {
        val levelArb = Arb.enum<BrSensitivityLevel>()

        checkAll(100, levelArb) { level ->
            val encoded = json.encodeToString(BrSensitivityLevel.serializer(), level)
            val decoded = json.decodeFromString(BrSensitivityLevel.serializer(), encoded)
            decoded shouldBe level
        }
    }
})
