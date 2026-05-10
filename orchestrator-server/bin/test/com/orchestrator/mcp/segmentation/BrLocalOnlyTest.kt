package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.config.SegmentationConfig
import com.orchestrator.mcp.segmentation.prompt.SegmentationAiService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrLocalOnlyTest : FunSpec({

    afterEach { clearAllMocks() }

    fun brResponse(br: String, level: String) =
        """{"publicContent":"meta","technicalContent":"code","businessRules":"$br","brSensitivityLevel":"$level"}"""

    fun localBrResponse() =
        """{"publicContent":"","technicalContent":"","businessRules":"local processed BR","brSensitivityLevel":"LEVEL_1"}"""

    // UT-14: BR Local-Only — Re-process Via Ollama
    test("UT-14: re-processes BR via local Ollama when brLocalOnly=true") {
        val config = SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 10)

        val primaryAi = object : SegmentationAiService {
            override fun classify(maskedText: String) = brResponse("rate = 5%", "LEVEL_1")
        }
        val localAi = object : SegmentationAiService {
            override fun classify(maskedText: String) = localBrResponse()
        }

        val service = ContentSegmentationServiceImpl(config, primaryAi, localAi)

        val result = service.segment("text with BR content")
        result.businessRules shouldBe "local processed BR"
        result.provider shouldBe "openai+ollama"
        result.degraded shouldBe false
    }

    // UT-15: BR Local-Only — Skip When Already Ollama
    test("UT-15: skips re-processing when provider is already ollama") {
        val config = SegmentationConfig(provider = "ollama", brLocalOnly = true, timeoutSeconds = 10)
        val primaryAi = mockk<SegmentationAiService>()
        val localAi = mockk<SegmentationAiService>()

        every { primaryAi.classify(any()) } returns brResponse("rate = 5%", "LEVEL_1")

        val service = ContentSegmentationServiceImpl(config, primaryAi, localAi)

        val result = withContext(Dispatchers.IO) {
            service.segment("text with BR")
        }
        result.provider shouldBe "ollama"
        verify(exactly = 0) { localAi.classify(any()) }
    }

    // UT-16: BR Local-Only — Degraded When Ollama Unavailable
    test("UT-16: degrades gracefully when local Ollama unavailable") {
        val config = SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 10)
        val primaryAi = mockk<SegmentationAiService>()
        val localAi = mockk<SegmentationAiService>()

        every { primaryAi.classify(any()) } returns brResponse("rate = 5%", "LEVEL_1")
        every { localAi.classify(any()) } throws RuntimeException("Ollama unavailable")

        val service = ContentSegmentationServiceImpl(config, primaryAi, localAi)

        val result = withContext(Dispatchers.IO) {
            service.segment("text with BR")
        }
        result.degraded shouldBe true
        result.businessRules shouldBe "rate = 5%"
    }

    // BR Local-Only — No re-processing when BR is empty
    test("skips local re-processing when businessRules is empty") {
        val config = SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 10)
        val primaryAi = mockk<SegmentationAiService>()
        val localAi = mockk<SegmentationAiService>()

        every { primaryAi.classify(any()) } returns """{"publicContent":"meta","technicalContent":"code","businessRules":"","brSensitivityLevel":null}"""

        val service = ContentSegmentationServiceImpl(config, primaryAi, localAi)

        val result = withContext(Dispatchers.IO) {
            service.segment("no BR text")
        }
        result.degraded shouldBe false
        verify(exactly = 0) { localAi.classify(any()) }
    }

    // BR Local-Only — Degraded when localAiService is null
    test("degrades when localAiService is null and brLocalOnly=true") {
        val config = SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 10)
        val primaryAi = mockk<SegmentationAiService>()
        every { primaryAi.classify(any()) } returns brResponse("rate = 5%", "LEVEL_1")

        val service = ContentSegmentationServiceImpl(config, primaryAi, null)

        val result = withContext(Dispatchers.IO) {
            service.segment("text with BR")
        }
        result.degraded shouldBe true
    }
})
