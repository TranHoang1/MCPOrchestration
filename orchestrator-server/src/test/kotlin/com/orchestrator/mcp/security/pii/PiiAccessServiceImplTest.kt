package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.kbstore.model.MappingType
import com.orchestrator.mcp.kbstore.model.PiiMapping
import com.orchestrator.mcp.kbstore.repository.PiiMappingRepository
import com.orchestrator.mcp.security.model.KbRole
import com.orchestrator.mcp.security.pii.model.*
import com.orchestrator.mcp.security.pii.repository.PiiAccessAuditRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class PiiAccessServiceImplTest : FunSpec({

    val sessionService = mockk<PiiSessionService>()
    val rateLimitService = mockk<PiiRateLimitService>()
    val auditRepository = mockk<PiiAccessAuditRepository>()
    val piiMappingRepository = mockk<PiiMappingRepository>()
    val config = PiiAccessConfig()

    val service = PiiAccessServiceImpl(
        sessionService = sessionService,
        rateLimitService = rateLimitService,
        auditRepository = auditRepository,
        piiMappingRepository = piiMappingRepository,
        config = config
    )

    val adminSession = PiiSession(
        token = "test-token",
        userId = "admin@test.com",
        role = KbRole.BA_ADMIN,
        createdAt = Clock.System.now(),
        expiresAt = Clock.System.now() + config.sessionTimeout
    )

    val devSession = adminSession.copy(role = KbRole.DEVELOPER)

    val testMapping = PiiMapping(
        id = UUID.randomUUID(),
        issueKey = "TEST-001",
        placeholder = "{{PII_EMAIL_001}}",
        originalValue = "john@example.com",
        mappingType = MappingType.EMAIL,
        createdAt = Clock.System.now()
    )

    beforeEach { clearAllMocks() }

    test("UT-05: admin role unmask succeeds") {
        coEvery { sessionService.validate("test-token") } returns adminSession
        coEvery { rateLimitService.check(any(), any()) } returns RateLimitResult.Allowed(9)
        coEvery { piiMappingRepository.findByIssueKey("TEST-001") } returns listOf(testMapping)
        coEvery { auditRepository.insert(any()) } returns true

        val result = service.unmask("test-token", "TEST-001", "{{PII_EMAIL_001}}")

        result.shouldBeInstanceOf<UnmaskResult.Success>()
        result.originalValue shouldBe "john@example.com"
        result.remainingQuota shouldBe 8
    }

    test("UT-06: developer role denied") {
        coEvery { sessionService.validate("test-token") } returns devSession
        coEvery { auditRepository.insert(any()) } returns true

        val result = service.unmask("test-token", "TEST-001", "{{PII_EMAIL_001}}")

        result.shouldBeInstanceOf<UnmaskResult.Denied>()
        result.reason shouldBe DenialReason.INSUFFICIENT_PERMISSION
    }

    test("UT-08: expired session returns denied") {
        coEvery { sessionService.validate("expired-token") } returns null

        val result = service.unmask("expired-token", "TEST-001", "{{PII_EMAIL_001}}")

        result.shouldBeInstanceOf<UnmaskResult.Denied>()
        result.reason shouldBe DenialReason.SESSION_EXPIRED
    }

    test("UT-09: audit write failure denies unmask (fail-closed)") {
        coEvery { sessionService.validate("test-token") } returns adminSession
        coEvery { rateLimitService.check(any(), any()) } returns RateLimitResult.Allowed(9)
        coEvery { piiMappingRepository.findByIssueKey("TEST-001") } returns listOf(testMapping)
        coEvery { auditRepository.insert(any()) } returns false

        val result = service.unmask("test-token", "TEST-001", "{{PII_EMAIL_001}}")

        result.shouldBeInstanceOf<UnmaskResult.Denied>()
        result.reason shouldBe DenialReason.AUDIT_FAILURE
    }

    test("UT-10: revoked session denied") {
        val revokedSession = adminSession.copy(revoked = true)
        coEvery { sessionService.validate("test-token") } returns revokedSession

        val result = service.unmask("test-token", "TEST-001", "{{PII_EMAIL_001}}")

        result.shouldBeInstanceOf<UnmaskResult.Denied>()
        result.reason shouldBe DenialReason.SESSION_REVOKED
    }

    test("UT-04: rate limit exceeded returns RateLimited") {
        val resetAt = Clock.System.now() + config.windowDuration
        coEvery { sessionService.validate("test-token") } returns adminSession
        coEvery { rateLimitService.check(any(), any()) } returns RateLimitResult.Exceeded(1800, resetAt)
        coEvery { auditRepository.insert(any()) } returns true

        val result = service.unmask("test-token", "TEST-001", "{{PII_EMAIL_001}}")

        result.shouldBeInstanceOf<UnmaskResult.RateLimited>()
        result.retryAfterSeconds shouldBe 1800
    }

    test("placeholder not found returns Denied") {
        coEvery { sessionService.validate("test-token") } returns adminSession
        coEvery { rateLimitService.check(any(), any()) } returns RateLimitResult.Allowed(9)
        coEvery { piiMappingRepository.findByIssueKey("TEST-001") } returns emptyList()
        coEvery { auditRepository.insert(any()) } returns true

        val result = service.unmask("test-token", "TEST-001", "{{PII_UNKNOWN}}")

        result.shouldBeInstanceOf<UnmaskResult.Denied>()
        result.reason shouldBe DenialReason.NOT_FOUND
    }
})
