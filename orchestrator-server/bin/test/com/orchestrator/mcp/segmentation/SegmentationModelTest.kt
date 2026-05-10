package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.model.BrSensitivityLevel
import com.orchestrator.mcp.segmentation.model.SegmentationException
import com.orchestrator.mcp.segmentation.model.SegmentationResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class SegmentationModelTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // BrSensitivityLevel tests
    test("BrSensitivityLevel LEVEL_1 has correct label and description") {
        BrSensitivityLevel.LEVEL_1.label shouldBe "Confidential"
        BrSensitivityLevel.LEVEL_1.description shouldContain "rates"
    }

    test("BrSensitivityLevel LEVEL_2 has correct label and description") {
        BrSensitivityLevel.LEVEL_2.label shouldBe "Internal"
        BrSensitivityLevel.LEVEL_2.description shouldContain "thresholds"
    }

    test("BrSensitivityLevel LEVEL_3 has correct label and description") {
        BrSensitivityLevel.LEVEL_3.label shouldBe "Restricted"
        BrSensitivityLevel.LEVEL_3.description shouldContain "SLAs"
    }

    test("BrSensitivityLevel has exactly 3 values") {
        BrSensitivityLevel.entries.size shouldBe 3
    }

    // SegmentationResult serialization
    test("SegmentationResult serializes to JSON correctly") {
        val result = SegmentationResult(
            publicContent = "ticket info",
            technicalContent = "stack trace",
            businessRules = "rate = 5%",
            brSensitivityLevel = BrSensitivityLevel.LEVEL_1,
            processingTimeMs = 150,
            provider = "openai",
            degraded = false
        )
        val jsonStr = json.encodeToString(SegmentationResult.serializer(), result)
        jsonStr shouldContain "\"publicContent\":\"ticket info\""
        jsonStr shouldContain "\"brSensitivityLevel\":\"LEVEL_1\""
        jsonStr shouldContain "\"provider\":\"openai\""
    }

    test("SegmentationResult deserializes from JSON correctly") {
        val jsonStr = """{"publicContent":"a","technicalContent":"b","businessRules":"c","brSensitivityLevel":"LEVEL_2","processingTimeMs":100,"provider":"ollama","degraded":true}"""
        val result = json.decodeFromString<SegmentationResult>(jsonStr)
        result.publicContent shouldBe "a"
        result.technicalContent shouldBe "b"
        result.businessRules shouldBe "c"
        result.brSensitivityLevel shouldBe BrSensitivityLevel.LEVEL_2
        result.processingTimeMs shouldBe 100
        result.provider shouldBe "ollama"
        result.degraded shouldBe true
    }

    test("SegmentationResult handles null brSensitivityLevel") {
        val jsonStr = """{"publicContent":"a","technicalContent":"b","businessRules":"","brSensitivityLevel":null}"""
        val result = json.decodeFromString<SegmentationResult>(jsonStr)
        result.brSensitivityLevel shouldBe null
        result.processingTimeMs shouldBe 0
        result.provider shouldBe ""
        result.degraded shouldBe false
    }

    // SegmentationException tests
    test("InvalidInputException has correct message") {
        val ex = SegmentationException.InvalidInputException("test msg")
        ex.message shouldBe "test msg"
    }

    test("LlmTimeoutException includes timeout duration") {
        val ex = SegmentationException.LlmTimeoutException(5000)
        ex.message shouldContain "5000ms"
    }

    test("InvalidLlmResponseException truncates long response") {
        val longResponse = "x".repeat(200)
        val ex = SegmentationException.InvalidLlmResponseException(longResponse, null)
        ex.message shouldContain "x".repeat(100)
        (ex.message!!.length < 200) shouldBe true
    }

    test("ProviderUnavailableException includes provider name") {
        val cause = RuntimeException("connection refused")
        val ex = SegmentationException.ProviderUnavailableException("openai", cause)
        ex.message shouldContain "openai"
        ex.cause shouldBe cause
    }
})
