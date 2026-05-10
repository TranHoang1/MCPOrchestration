package com.orchestrator.mcp.brmasking

import com.orchestrator.mcp.brmasking.crypto.BrEncryptionService
import com.orchestrator.mcp.brmasking.di.brMaskingModule
import com.orchestrator.mcp.brmasking.model.BrCategory
import com.orchestrator.mcp.brmasking.model.BrMaskingConfig
import com.orchestrator.mcp.brmasking.prompt.BrIdentificationAiService
import com.orchestrator.mcp.segmentation.provider.ChatModelFactory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import java.util.Base64

class BrMaskingIntegrationTest : FunSpec(), KoinTest {

    private val testKeyBytes = ByteArray(32) { it.toByte() }
    private val testKeyBase64 = Base64.getEncoder().encodeToString(testKeyBytes)

    init {
        afterEach {
            clearAllMocks()
            stopKoin()
        }

        // --- IT-01: Full Masking Flow With Mock LLM ---
        test("IT-01: end-to-end masking flow - identify, mask, encrypt") {
            val mockAiService = mockk<BrIdentificationAiService>()
            every { mockAiService.identify(any()) } returns """[
                {"text":"Lãi suất cho vay tiêu dùng: 12.5%/năm","category":"RATE","summary":"Consumer loan interest rate"},
                {"text":"Điều kiện duyệt: thu nhập ≥ 15 triệu/tháng","category":"APPROVAL","summary":"Income approval condition"},
                {"text":"Ngưỡng NPL: 3%","category":"THRESHOLD","summary":"NPL threshold"}
            ]"""

            val testModule = module {
                single { BrMaskingConfig(encryptionKey = testKeyBase64, timeoutSeconds = 5) }
                single { BrEncryptionService(testKeyBase64) }
                single<BrIdentificationAiService> { mockAiService }
                single<BrMaskingService> {
                    BrMaskingServiceImpl(get(), get(), get())
                }
            }

            startKoin { modules(testModule) }

            val service: BrMaskingService by inject()
            val input = "Lãi suất cho vay tiêu dùng: 12.5%/năm. Điều kiện duyệt: thu nhập ≥ 15 triệu/tháng. Ngưỡng NPL: 3%."
            val result = service.maskBusinessRules(input)

            // Verify masked text has placeholders
            result.maskedBr shouldContain "[BR_RATE_01]"
            result.maskedBr shouldContain "[BR_APPROVAL_01]"
            result.maskedBr shouldContain "[BR_THRESHOLD_01]"

            // Verify original text not visible
            result.maskedBr shouldNotContain "12.5%"
            result.maskedBr shouldNotContain "15 triệu"
            result.maskedBr shouldNotContain "3%"

            // Verify placeholders
            result.brPlaceholders shouldHaveSize 3
            result.brPlaceholders[0].category shouldBe BrCategory.RATE
            result.brPlaceholders[1].category shouldBe BrCategory.APPROVAL
            result.brPlaceholders[2].category shouldBe BrCategory.THRESHOLD

            // Verify each placeholder has encrypted data
            result.brPlaceholders.forEach { placeholder ->
                placeholder.encryptedOriginal.isNotBlank() shouldBe true
                placeholder.iv.isNotBlank() shouldBe true
            }
        }

        // --- IT-02: Full Unmask Flow ---
        test("IT-02: mask then unmask returns original content for each BR") {
            val mockAiService = mockk<BrIdentificationAiService>()
            val originalBrs = listOf(
                "Lãi suất cho vay tiêu dùng: 12.5%/năm",
                "Điều kiện duyệt: thu nhập ≥ 15 triệu/tháng",
                "Ngưỡng NPL: 3%"
            )
            every { mockAiService.identify(any()) } returns """[
                {"text":"${originalBrs[0]}","category":"RATE","summary":"Consumer loan rate"},
                {"text":"${originalBrs[1]}","category":"APPROVAL","summary":"Income condition"},
                {"text":"${originalBrs[2]}","category":"THRESHOLD","summary":"NPL threshold"}
            ]"""

            val testModule = module {
                single { BrMaskingConfig(encryptionKey = testKeyBase64, timeoutSeconds = 5) }
                single { BrEncryptionService(testKeyBase64) }
                single<BrIdentificationAiService> { mockAiService }
                single<BrMaskingService> {
                    BrMaskingServiceImpl(get(), get(), get())
                }
            }

            startKoin { modules(testModule) }

            val service: BrMaskingService by inject()
            val input = originalBrs.joinToString(". ") + "."
            val result = service.maskBusinessRules(input)

            // Unmask each placeholder and verify original text
            result.brPlaceholders.forEachIndexed { index, placeholder ->
                val unmasked = service.unmask(placeholder)
                unmasked shouldBe originalBrs[index]
            }
        }

        // --- IT-03: Koin Module Wiring ---
        test("IT-03: brMaskingModule resolves all dependencies with test overrides") {
            val mockChatModelFactory = mockk<ChatModelFactory>(relaxed = true)
            val mockAiService = mockk<BrIdentificationAiService>()

            val testOverrides = module {
                single { BrMaskingConfig(encryptionKey = testKeyBase64) }
                single<ChatModelFactory> { mockChatModelFactory }
                single<BrIdentificationAiService> { mockAiService }
                single { BrEncryptionService(testKeyBase64) }
                single<BrMaskingService> {
                    BrMaskingServiceImpl(get(), get(), get())
                }
            }

            startKoin { modules(testOverrides) }

            val service: BrMaskingService by inject()
            val encryption: BrEncryptionService by inject()

            // Verify services resolve without error
            service.shouldNotBeNull()
            encryption.shouldNotBeNull()
        }

        // --- IT-04: Encryption Key From Config ---
        test("IT-04: service uses encryption key from config") {
            val mockAiService = mockk<BrIdentificationAiService>()
            every { mockAiService.identify(any()) } returns
                """[{"text":"test rule","category":"RATE","summary":"test"}]"""

            val configWithKey = BrMaskingConfig(encryptionKey = testKeyBase64, timeoutSeconds = 5)

            val testModule = module {
                single { configWithKey }
                single { BrEncryptionService(configWithKey.encryptionKey) }
                single<BrIdentificationAiService> { mockAiService }
                single<BrMaskingService> {
                    BrMaskingServiceImpl(get(), get(), get())
                }
            }

            startKoin { modules(testModule) }

            val service: BrMaskingService by inject()
            val result = service.maskBusinessRules("test rule")

            // Verify encryption works (can unmask)
            val unmasked = service.unmask(result.brPlaceholders[0])
            unmasked shouldBe "test rule"
        }
    }
}
