package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class PhoneDetectionStrategyTest : FunSpec({

    val strategy = PhoneDetectionStrategy()

    test("detects VN mobile number") {
        val result = strategy.detect("SĐT: 0912345678")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "0912345678"
        result[0].mappingType shouldBe MappingType.PHONE
    }

    test("detects VN landline number") {
        val result = strategy.detect("gọi 0281234567")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "0281234567"
    }

    test("detects multiple phone numbers") {
        val text = "Liên hệ 0912345678 hoặc 0387654321"
        val result = strategy.detect(text)
        result shouldHaveSize 2
    }

    test("does not detect number not starting with 0") {
        val result = strategy.detect("mã: 1234567890")
        result.shouldBeEmpty()
    }

    test("does not detect 9-digit number starting with 0") {
        val result = strategy.detect("số 012345678")
        result.shouldBeEmpty()
    }

    test("priority is 2") {
        strategy.priority shouldBe 2
    }
})
