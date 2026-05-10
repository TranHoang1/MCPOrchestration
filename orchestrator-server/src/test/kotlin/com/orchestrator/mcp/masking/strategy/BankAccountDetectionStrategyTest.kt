package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.config.MaskingConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class BankAccountDetectionStrategyTest : FunSpec({

    val config = MaskingConfig()
    val strategy = BankAccountDetectionStrategy(config)

    test("detects bank account with STK keyword") {
        val result = strategy.detect("STK 1234567890123")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "1234567890123"
        result[0].mappingType shouldBe MappingType.BANK_ACCOUNT
    }

    test("detects bank account with 'tài khoản' keyword") {
        val result = strategy.detect("tài khoản số 9876543210")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "9876543210"
    }

    test("detects bank account with 'account' keyword") {
        val result = strategy.detect("bank account: 12345678901234")
        result shouldHaveSize 1
    }

    test("does NOT detect number without context keyword") {
        val result = strategy.detect("mã giao dịch 1234567890123")
        result.shouldBeEmpty()
    }

    test("detects with keyword within context window") {
        val padding = " ".repeat(40)
        val text = "STK${padding}1234567890"
        val result = strategy.detect(text)
        result shouldHaveSize 1
    }

    test("does NOT detect with keyword outside context window") {
        val padding = " ".repeat(100)
        val text = "STK${padding}1234567890"
        val result = strategy.detect(text)
        result.shouldBeEmpty()
    }

    test("priority is 3") {
        strategy.priority shouldBe 3
    }
})
