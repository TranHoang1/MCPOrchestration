package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.masking.config.MaskingConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class NameDetectionStrategyTest : FunSpec({

    val config = MaskingConfig()
    val strategy = NameDetectionStrategy(config)

    test("detects name after 'KH' prefix") {
        val result = strategy.detect("KH Nguyễn Văn An đã thanh toán")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "Nguyễn Văn An"
        result[0].mappingType shouldBe MappingType.NAME
    }

    test("detects name after 'Bà' prefix with 4 words") {
        val result = strategy.detect("Bà Trần Thị Bích Ngọc")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "Trần Thị Bích Ngọc"
    }

    test("detects name after 'Ông' prefix with 2 words") {
        val result = strategy.detect("Ông Lê Minh gọi điện")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "Lê Minh"
    }

    test("does NOT detect name without prefix") {
        val result = strategy.detect("Nguyễn Văn An đã thanh toán")
        result.shouldBeEmpty()
    }

    test("does NOT detect single word after prefix") {
        val result = strategy.detect("Ông An gọi điện")
        result.shouldBeEmpty()
    }

    test("detects multiple names in text") {
        val text = "Ông Lê Minh và Bà Trần Thị Lan"
        val result = strategy.detect(text)
        result shouldHaveSize 2
    }

    test("priority is 5") {
        strategy.priority shouldBe 5
    }
})
