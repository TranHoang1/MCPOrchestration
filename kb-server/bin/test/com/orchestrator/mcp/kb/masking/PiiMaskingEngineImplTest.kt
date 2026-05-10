package com.orchestrator.mcp.kb.masking

import com.orchestrator.mcp.kb.config.KbMaskingConfig
import com.orchestrator.mcp.kb.masking.model.PiiType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Unit tests for PiiMaskingEngineImpl.
 * Verifies detection and masking of email, phone, and other PII types.
 */
class PiiMaskingEngineImplTest : DescribeSpec({

    val config = KbMaskingConfig(
        strategies = listOf("email", "phone", "bank_account", "id_card"),
        placeholderFormat = "[{TYPE}_{INDEX}]"
    )
    val engine = PiiMaskingEngineImpl(config)

    describe("PII Masking Engine") {

        it("should mask email addresses") {
            val content = "Contact john.doe@example.com for details"
            val result = engine.mask(content)

            result.maskedContent shouldContain "[EMAIL_1]"
            result.maskedContent shouldNotContain "john.doe@example.com"
            result.mappings shouldHaveSize 1
            result.mappings[0].piiType shouldBe PiiType.EMAIL
            result.mappings[0].originalValue shouldBe "john.doe@example.com"
        }

        it("should mask phone numbers") {
            val content = "Call me at 0912345678 or +84987654321"
            val result = engine.mask(content)

            result.maskedContent shouldContain "[PHONE_1]"
            result.maskedContent shouldNotContain "0912345678"
            result.mappings.filter { it.piiType == PiiType.PHONE }
                .shouldHaveSize(2)
        }

        it("should mask multiple PII types in same content") {
            val content = "Email: test@mail.com, Phone: 0901234567"
            val result = engine.mask(content)

            result.maskedContent shouldContain "[EMAIL_1]"
            result.maskedContent shouldContain "[PHONE_1]"
            result.mappings shouldHaveSize 2
        }

        it("should return original content when no PII found") {
            val content = "This is a clean text with no PII"
            val result = engine.mask(content)

            result.maskedContent shouldBe content
            result.mappings shouldHaveSize 0
        }

        it("should use configured placeholder format") {
            val customConfig = KbMaskingConfig(
                strategies = listOf("email"),
                placeholderFormat = "<<{TYPE}_{INDEX}>>"
            )
            val customEngine = PiiMaskingEngineImpl(customConfig)

            val result = customEngine.mask("user@test.com is here")
            result.maskedContent shouldContain "<<EMAIL_1>>"
        }

        it("should handle multiple emails with incrementing index") {
            val content = "a@b.com and c@d.com and e@f.com"
            val result = engine.mask(content)

            result.maskedContent shouldContain "[EMAIL_1]"
            result.maskedContent shouldContain "[EMAIL_2]"
            result.maskedContent shouldContain "[EMAIL_3]"
            result.mappings shouldHaveSize 3
        }
    }
})
