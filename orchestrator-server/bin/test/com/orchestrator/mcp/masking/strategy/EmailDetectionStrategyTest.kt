package com.orchestrator.mcp.masking.strategy

import com.orchestrator.mcp.kbstore.model.MappingType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class EmailDetectionStrategyTest : FunSpec({

    val strategy = EmailDetectionStrategy()

    test("detects standard email address") {
        val result = strategy.detect("contact: user@example.com for info")
        result shouldHaveSize 1
        result[0].originalValue shouldBe "user@example.com"
        result[0].mappingType shouldBe MappingType.EMAIL
    }

    test("detects multiple emails in text") {
        val text = "Send to alice@test.com and bob@company.org"
        val result = strategy.detect(text)
        result shouldHaveSize 2
        result[0].originalValue shouldBe "alice@test.com"
        result[1].originalValue shouldBe "bob@company.org"
    }

    test("detects email with special characters") {
        val text = "Email: user.name+tag@sub.domain.co.uk"
        val result = strategy.detect(text)
        result shouldHaveSize 1
        result[0].originalValue shouldBe "user.name+tag@sub.domain.co.uk"
    }

    test("does not detect invalid email without domain") {
        val result = strategy.detect("not an email: user@")
        result.shouldBeEmpty()
    }

    test("returns correct positions") {
        val text = "prefix user@test.com suffix"
        val result = strategy.detect(text)
        result shouldHaveSize 1
        result[0].startIndex shouldBe 7
        result[0].endIndex shouldBe 20
    }

    test("priority is 1") {
        strategy.priority shouldBe 1
    }
})
