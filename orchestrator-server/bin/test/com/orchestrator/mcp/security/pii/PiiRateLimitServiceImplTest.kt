package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.security.pii.model.PiiAccessConfig
import com.orchestrator.mcp.security.pii.model.RateLimitResult
import com.orchestrator.mcp.security.pii.repository.PiiAccessAuditRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

class PiiRateLimitServiceImplTest : FunSpec({

    val auditRepository = mockk<PiiAccessAuditRepository>()
    val service = PiiRateLimitServiceImpl(auditRepository)
    val config = PiiAccessConfig(maxUnmaskPerWindow = 10)

    beforeEach { clearAllMocks() }

    test("UT-03: allows when under threshold") {
        coEvery { auditRepository.countSuccessfulUnmaskSince(any(), any()) } returns 5

        val result = service.check("user@test.com", config)

        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remaining shouldBe 5
    }

    test("UT-04: denies when at threshold") {
        val oldest = Clock.System.now() - 30.minutes
        coEvery { auditRepository.countSuccessfulUnmaskSince(any(), any()) } returns 10
        coEvery { auditRepository.findOldestSuccessfulInWindow(any(), any()) } returns oldest

        val result = service.check("user@test.com", config)

        result.shouldBeInstanceOf<RateLimitResult.Exceeded>()
        // Allow 1s tolerance for timing between Clock.System.now() calls
        (result.retryAfterSeconds in 1799L..1800L) shouldBe true
    }

    test("allows when count is zero") {
        coEvery { auditRepository.countSuccessfulUnmaskSince(any(), any()) } returns 0

        val result = service.check("user@test.com", config)

        result.shouldBeInstanceOf<RateLimitResult.Allowed>()
        result.remaining shouldBe 10
    }

    test("retryAfter is at least 1 second") {
        val oldest = Clock.System.now() - 59.minutes
        coEvery { auditRepository.countSuccessfulUnmaskSince(any(), any()) } returns 10
        coEvery { auditRepository.findOldestSuccessfulInWindow(any(), any()) } returns oldest

        val result = service.check("user@test.com", config)

        result.shouldBeInstanceOf<RateLimitResult.Exceeded>()
        (result.retryAfterSeconds >= 1L) shouldBe true
    }
})
