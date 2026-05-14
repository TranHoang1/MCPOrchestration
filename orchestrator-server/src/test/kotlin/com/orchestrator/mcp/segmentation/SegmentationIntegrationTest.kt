package com.orchestrator.mcp.segmentation

import com.orchestrator.mcp.segmentation.config.SegmentationConfig
import com.orchestrator.mcp.segmentation.model.BrSensitivityLevel
import com.orchestrator.mcp.segmentation.model.SegmentationException
import com.orchestrator.mcp.segmentation.prompt.SegmentationAiService
import com.orchestrator.mcp.segmentation.prompt.SegmentationPromptBuilder
import com.orchestrator.mcp.segmentation.provider.ChatModelFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class SegmentationIntegrationTest : FunSpec({

    afterEach {
        try { stopKoin() } catch (_: Exception) {}
        clearAllMocks()
    }

        // IT-01: Full Segmentation Flow — Mixed Content
        test("IT-01: full segmentation flow with mixed content") {
            val aiService = object : SegmentationAiService {
                override fun classify(maskedText: String) = """{"publicContent":"Ticket MTO-100, Priority: High","technicalContent":"NPE at PaymentService:42","businessRules":"Lãi suất: 12.5%/năm","brSensitivityLevel":"LEVEL_1"}"""
            }

            val config = SegmentationConfig(provider = "openai", timeoutSeconds = 10)
            val service = ContentSegmentationServiceImpl(config, aiService)

            val result = service.segment("Ticket MTO-100 High. NPE at line 42. Rate = 12.5%")
            result.publicContent shouldContain "MTO-100"
            result.technicalContent shouldContain "NPE"
            result.businessRules shouldContain "12.5%"
            result.brSensitivityLevel shouldBe BrSensitivityLevel.LEVEL_1
            result.processingTimeMs shouldBeGreaterThanOrEqual 0
            result.provider shouldBe "openai"
        }

        // IT-02: Segmentation With No BR Content
        test("IT-02: handles no BR content correctly") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } returns """{"publicContent":"JIRA-200 Open","technicalContent":"SELECT * FROM users","businessRules":"","brSensitivityLevel":null}"""

            val config = SegmentationConfig(provider = "openai", timeoutSeconds = 10)
            val service = ContentSegmentationServiceImpl(config, mockAiService)

            val result = service.segment("JIRA-200 Open. SELECT * FROM users")
            result.businessRules shouldBe ""
            result.brSensitivityLevel shouldBe null
            result.degraded shouldBe false
        }

        // IT-03: BR Level 1 Classification End-to-End
        test("IT-03: classifies Level 1 BR end-to-end") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } returns """{"publicContent":"","technicalContent":"","businessRules":"Lãi suất cho vay tiêu dùng: 12.5%/năm, phí trả nợ trước hạn: 3%","brSensitivityLevel":"LEVEL_1"}"""

            val config = SegmentationConfig(provider = "openai", timeoutSeconds = 10)
            val service = ContentSegmentationServiceImpl(config, mockAiService)

            val result = service.segment("Lãi suất cho vay tiêu dùng: 12.5%/năm")
            result.brSensitivityLevel shouldBe BrSensitivityLevel.LEVEL_1
            result.businessRules shouldContain "12.5%"
        }

        // IT-04: Timeout Handling Integration
        test("IT-04: provider timeout exception is wrapped correctly") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } throws
                java.util.concurrent.TimeoutException("HTTP timeout")

            val config = SegmentationConfig(provider = "openai", timeoutSeconds = 1)
            val service = ContentSegmentationServiceImpl(config, mockAiService)

            shouldThrow<SegmentationException.ProviderUnavailableException> {
                withContext(Dispatchers.Default) { service.segment("text") }
            }
        }

        // IT-05: Provider Unavailable Integration
        test("IT-05: wraps connection error as ProviderUnavailableException") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } throws java.net.ConnectException("Connection refused")

            val config = SegmentationConfig(provider = "openai", timeoutSeconds = 10)
            val service = ContentSegmentationServiceImpl(config, mockAiService)

            val ex = shouldThrow<SegmentationException.ProviderUnavailableException> {
                service.segment("text")
            }
            ex.message shouldContain "openai"
        }

        // IT-07: BR Local-Only Full Flow
        test("IT-07: BR local-only full flow with two-phase processing") {
            // Use anonymous implementations to avoid MockK threading issues
            val primaryAi = object : SegmentationAiService {
                override fun classify(maskedText: String) = """{"publicContent":"meta","technicalContent":"code","businessRules":"rate = 5%","brSensitivityLevel":"LEVEL_1"}"""
            }
            val localAi = object : SegmentationAiService {
                override fun classify(maskedText: String) = """{"publicContent":"","technicalContent":"","businessRules":"local BR","brSensitivityLevel":"LEVEL_1"}"""
            }

            val config = SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 10)
            val service = ContentSegmentationServiceImpl(config, primaryAi, localAi)

            val result = service.segment("text with BR")
            result.provider shouldBe "openai+ollama"
            result.businessRules shouldBe "local BR"
        }

        // IT-08: Degraded Mode When Local Unavailable
        test("IT-08: graceful degradation when local LLM fails") {
            val primaryAi = mockk<SegmentationAiService>()
            val localAi = mockk<SegmentationAiService>()

            every { primaryAi.classify(any()) } returns """{"publicContent":"meta","technicalContent":"code","businessRules":"rate = 5%","brSensitivityLevel":"LEVEL_1"}"""
            every { localAi.classify(any()) } throws RuntimeException("Ollama down")

            val config = SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 10)
            val service = ContentSegmentationServiceImpl(config, primaryAi, localAi)

            val result = service.segment("text with BR")
            result.degraded shouldBe true
            result.businessRules shouldBe "rate = 5%"
        }

        // E2E-01: Full DI Container Segmentation
        test("E2E-01: full DI container resolves and segments") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } returns """{"publicContent":"test","technicalContent":"","businessRules":"","brSensitivityLevel":null}"""

            val koin = startKoin {
                modules(module {
                    single { SegmentationConfig(provider = "openai", timeoutSeconds = 5) }
                    single { ChatModelFactory() }
                    single { SegmentationPromptBuilder() }
                    single<ContentSegmentationService> {
                        ContentSegmentationServiceImpl(
                            config = get(),
                            aiService = mockAiService,
                            localAiService = null
                        )
                    }
                })
            }.koin

            val service = koin.get<ContentSegmentationService>()
            val result = withContext(Dispatchers.IO) { service.segment("test input") }
            result.publicContent shouldBe "test"
        }

        // E2E-02: Module Wiring Verification
        test("E2E-02: all DI bindings resolve without error") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } returns "{}"

            val koin = startKoin {
                modules(module {
                    single { SegmentationConfig(provider = "openai", apiKey = "test", timeoutSeconds = 5) }
                    single { ChatModelFactory() }
                    single { SegmentationPromptBuilder() }
                    single<ContentSegmentationService> {
                        ContentSegmentationServiceImpl(get(), mockAiService, null)
                    }
                })
            }.koin

            val config = koin.get<SegmentationConfig>()
            val factory = koin.get<ChatModelFactory>()
            val builder = koin.get<SegmentationPromptBuilder>()
            val service = koin.get<ContentSegmentationService>()

            config.provider shouldBe "openai"
            (factory != null) shouldBe true
            (builder != null) shouldBe true
            (service != null) shouldBe true
        }

        // E2E-04: Graceful Degradation E2E
        test("E2E-04: graceful degradation when local service is null") {
            val mockAiService = mockk<SegmentationAiService>()
            every { mockAiService.classify(any()) } returns """{"publicContent":"","technicalContent":"","businessRules":"rate = 5%","brSensitivityLevel":"LEVEL_1"}"""

            val koin = startKoin {
                modules(module {
                    single { SegmentationConfig(provider = "openai", brLocalOnly = true, timeoutSeconds = 5) }
                    single { ChatModelFactory() }
                    single { SegmentationPromptBuilder() }
                    single<ContentSegmentationService> {
                        ContentSegmentationServiceImpl(get(), mockAiService, null)
                    }
                })
            }.koin

            val service = koin.get<ContentSegmentationService>()
            val result = withContext(Dispatchers.IO) { service.segment("text with BR") }
            result.degraded shouldBe true
        }
})
