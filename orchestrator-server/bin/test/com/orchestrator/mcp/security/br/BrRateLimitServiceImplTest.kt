package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.BrAccessConfig
import com.orchestrator.mcp.security.br.model.BrRateLimitResult
import com.orchestrator.mcp.security.br.model.BrSensitivityLevel
import com.orchestrator.mcp.security.br.repository.BrAccessAuditRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.time.Duration.Companion.hours

class BrRateLimitServiceImplTest : FunSpec({

    val auditRepository = mockk<BrAccessAuditRepository>()
    val service = BrRateLimitServiceImpl(auditRepository)
    val config = BrAccessConfig(rateLimitWindow = 1.hours)

    test("allows when count is under threshold for HIGH level") {
        coEvery { auditRepository.countSuccessfulAccessSince(any(), any(), any()) } returns 3

        val result = service.check("user@test.com", BrSensitivityLevel.HIGH, config)

        result.shouldBeInstanceOf<BrRateLimitResult.Allowed>()
        result.remaining shouldBe 2 // HIGH max=5, used=3
    }

    test("allows when count is under threshold for LOW level") {
        coEvery { auditRepository.countSuccessfulAccessSince(any(), any(), any()) } returns 20

        val result = service.check("user@test.com", BrSensitivityLevel.LOW, config)

        result.shouldBeInstanceOf<BrRateLimitResult.Allowed>()
        result.remaining shouldBe 10 // LOW max=30, used=20
    }

    test("denies when count equals threshold for HIGH level") {
        coEvery { auditRepository.countSuccessfulAccessSince(any(), any(), any()) } returns 5

        val result = service.check("user@test.com", BrSensitivityLevel.HIGH, config)

        result.shouldBeInstanceOf<BrRateLimitResult.Exceeded>()
        result.sensitivityLevel shouldBe BrSensitivityLevel.HIGH
        result.retryAfterSeconds shouldBe 3600L
    }

    test("denies when count exceeds threshold for MEDIUM level") {
        coEvery { auditRepository.countSuccessfulAccessSince(any(), any(), any()) } returns 16

        val result = service.check("user@test.com", BrSensitivityLevel.MEDIUM, config)

        result.shouldBeInstanceOf<BrRateLimitResult.Exceeded>()
        result.sensitivityLevel shouldBe BrSensitivityLevel.MEDIUM
    }

    test("allows when count is zero") {
        coEvery { auditRepository.countSuccessfulAccessSince(any(), any(), any()) } returns 0

        val result = service.check("user@test.com", BrSensitivityLevel.HIGH, config)

        result.shouldBeInstanceOf<BrRateLimitResult.Allowed>()
        result.remaining shouldBe 5
    }
})
