package com.orchestrator.mcp.security.br

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class BrDlpServiceImplTest : FunSpec({

    val service = BrDlpServiceImpl()

    test("generateHeaders returns correct DLP headers") {
        val headers = service.generateHeaders()

        headers.cacheControl shouldBe "no-store, no-cache, must-revalidate"
        headers.pragma shouldBe "no-cache"
        headers.contentTypeOptions shouldBe "nosniff"
        headers.dlpFlag shouldBe "enforced"
    }

    test("generateHeaders toMap contains all required headers") {
        val map = service.generateHeaders().toMap()

        map["Cache-Control"] shouldBe "no-store, no-cache, must-revalidate"
        map["Pragma"] shouldBe "no-cache"
        map["X-Content-Type-Options"] shouldBe "nosniff"
        map["X-BR-DLP"] shouldBe "enforced"
    }

    test("sanitizeForLogging redacts BR content") {
        val sensitive = "Approval threshold is 50000 USD for Level A"
        val sanitized = service.sanitizeForLogging(sensitive)

        sanitized shouldContain "BR_CONTENT_REDACTED"
        sanitized shouldNotContain "50000"
        sanitized shouldNotContain "Approval"
    }

    test("sanitizeForLogging handles empty content") {
        val sanitized = service.sanitizeForLogging("")
        sanitized shouldBe "[EMPTY]"
    }

    test("sanitizeForLogging includes content length") {
        val content = "Some business rule content"
        val sanitized = service.sanitizeForLogging(content)
        sanitized shouldContain "length=${content.length}"
    }
})
