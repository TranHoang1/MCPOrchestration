package com.orchestrator.mcp.masking

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.config.MaskingConfig
import com.orchestrator.mcp.masking.strategy.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class PiiMaskingEngineImplTest : FunSpec({

    val config = MaskingConfig()
    val strategies = listOf(
        EmailDetectionStrategy(),
        PhoneDetectionStrategy(),
        BankAccountDetectionStrategy(config),
        IdCardDetectionStrategy(),
        NameDetectionStrategy(config)
    )
    val engine = PiiMaskingEngineImpl(strategies, config)

    test("returns unchanged text for blank input") {
        val result = engine.mask("")
        result.maskedText shouldBe ""
        result.mappings.shouldBeEmpty()
    }

    test("returns unchanged text when no PII found") {
        val result = engine.mask("Hello world, no PII here")
        result.maskedText shouldBe "Hello world, no PII here"
        result.mappings.shouldBeEmpty()
    }

    test("masks email address") {
        val result = engine.mask("Email: user@example.com")
        result.maskedText shouldBe "Email: [PII_EMAIL_01]"
        result.mappings shouldHaveSize 1
        result.mappings[0].placeholder shouldBe "[PII_EMAIL_01]"
        result.mappings[0].originalValue shouldBe "user@example.com"
        result.mappings[0].mappingType shouldBe MappingType.EMAIL
    }

    test("masks phone number") {
        val result = engine.mask("SĐT: 0912345678")
        result.maskedText shouldBe "SĐT: [PII_PHONE_01]"
        result.mappings shouldHaveSize 1
        result.mappings[0].mappingType shouldBe MappingType.PHONE
    }

    test("masks bank account with context") {
        val result = engine.mask("STK 1234567890123")
        result.maskedText shouldBe "STK [PII_ACCOUNT_01]"
        result.mappings shouldHaveSize 1
        result.mappings[0].mappingType shouldBe MappingType.BANK_ACCOUNT
    }

    test("masks ID card number") {
        val result = engine.mask("CMND 123456789")
        result.maskedText shouldBe "CMND [PII_ID_01]"
        result.mappings shouldHaveSize 1
        result.mappings[0].mappingType shouldBe MappingType.ID_CARD
    }

    test("masks Vietnamese name with prefix") {
        val result = engine.mask("KH Nguyễn Văn An đã thanh toán")
        result.maskedText shouldContain "[PII_NAME_01]"
        result.maskedText shouldNotContain "Nguyễn Văn An"
        result.mappings shouldHaveSize 1
        result.mappings[0].mappingType shouldBe MappingType.NAME
    }

    test("masks multiple PII types in same text") {
        val text = "KH Nguyễn Văn An, SĐT 0912345678, email an@test.com"
        val result = engine.mask(text)
        result.maskedText shouldNotContain "Nguyễn Văn An"
        result.maskedText shouldNotContain "0912345678"
        result.maskedText shouldNotContain "an@test.com"
        result.mappings.size shouldBe 3
    }

    test("phone takes priority over ID card for 10-digit starting with 0") {
        val result = engine.mask("số 0123456789")
        result.mappings shouldHaveSize 1
        result.mappings[0].mappingType shouldBe MappingType.PHONE
    }

    test("respects disabled strategies in config") {
        val restrictedConfig = MaskingConfig(
            enabledStrategies = setOf(MappingType.EMAIL)
        )
        val restrictedEngine = PiiMaskingEngineImpl(strategies, restrictedConfig)
        val result = restrictedEngine.mask("SĐT 0912345678 email a@b.com")
        result.maskedText shouldContain "0912345678"
        result.maskedText shouldContain "[PII_EMAIL_01]"
    }

    test("sequential numbering for same PII type") {
        val text = "Call 0912345678 or 0387654321"
        val result = engine.mask(text)
        result.maskedText shouldContain "[PII_PHONE_01]"
        result.maskedText shouldContain "[PII_PHONE_02]"
    }
})
