package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class IdCardDetectionStrategyTest : FunSpec({

    val strategy = IdCardDetectionStrategy()

    test("detects 9-digit CMND number") {
        val result = strategy.detect("CMND 123456789")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "123456789"
        result[0].mappingType shouldBe MappingType.ID_CARD
    }

    test("detects 12-digit CCCD number") {
        val result = strategy.detect("CCCD 012345678901")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "012345678901"
    }

    test("detects multiple ID numbers") {
        val text = "CMND 123456789 và CCCD 012345678901"
        val result = strategy.detect(text)
        result shouldHaveSize 2
    }

    test("does not detect 8-digit number") {
        val result = strategy.detect("số 12345678")
        result.shouldBeEmpty()
    }

    test("does not detect 10-digit number") {
        val result = strategy.detect("số 1234567890")
        result.shouldBeEmpty()
    }

    test("priority is 4") {
        strategy.priority shouldBe 4
    }
})
