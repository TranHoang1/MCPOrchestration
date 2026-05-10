package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.*
import com.orchestrator.mcp.security.br.repository.BrAccessAuditRepository
import com.orchestrator.mcp.security.model.KbRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BrAccessServiceImplTest : FunSpec({

    val sessionService = mockk<BrSessionService>()
    val rateLimitService = mockk<BrRateLimitService>()
    val kmsService = mockk<BrKeyManagementService>()
    val dlpService = mockk<BrDlpService>()
    val auditRepository = mockk<BrAccessAuditRepository>(relaxed = true)
    val config = BrAccessConfig(sessionTimeout = 30.minutes, rateLimitWindow = 1.hours)

    val service = BrAccessServiceImpl(
        sessionService = sessionService,
        rateLimitService = rateLimitService,
        kmsService = kmsService,
        dlpService = dlpService,
        auditRepository = auditRepository,
        config = config
    )

    val validSession = BrSession(
        token = "valid-token",
        userId = "admin@test.com",
        role = KbRole.BA_ADMIN,
        createdAt = Clock.System.now(),
        expiresAt = Clock.System.now() + 30.minutes,
        revoked = false
    )

    test("expired session returns SESSION_EXPIRED") {
        coEvery { sessionService.validate("expired-token") } returns null

        val result = service.viewBusinessRules("expired-token", "PROJ-1")

        result.shouldBeInstanceOf<BrAccessResult.Denied>()
        result.reason shouldBe BrDenialReason.SESSION_EXPIRED
    }

    test("revoked session returns SESSION_REVOKED") {
        val revokedSession = validSession.copy(revoked = true)
        coEvery { sessionService.validate("revoked-token") } returns revokedSession

        val result = service.viewBusinessRules("revoked-token", "PROJ-1")

        result.shouldBeInstanceOf<BrAccessResult.Denied>()
        result.reason shouldBe BrDenialReason.SESSION_REVOKED
    }

    test("BR not found returns NOT_FOUND") {
        coEvery { sessionService.validate("valid-token") } returns validSession

        // lookupBrEntry returns null by default (no real DB)
        val result = service.viewBusinessRules("valid-token", "NONEXISTENT")

        result.shouldBeInstanceOf<BrAccessResult.Denied>()
        result.reason shouldBe BrDenialReason.NOT_FOUND
    }

    test("createSession delegates to sessionService") {
        coEvery { sessionService.create("user@test.com", KbRole.BA_ADMIN) } returns validSession

        val session = service.createSession("user@test.com", KbRole.BA_ADMIN)

        session.userId shouldBe "admin@test.com"
        session.role shouldBe KbRole.BA_ADMIN
    }

    test("revokeSession delegates to sessionService") {
        coEvery { sessionService.revoke("valid-token") } returns true

        val result = service.revokeSession("valid-token")
        result shouldBe true
    }

    test("getRemainingQuota returns remaining when allowed") {
        coEvery {
            rateLimitService.check("user@test.com", BrSensitivityLevel.HIGH, config)
        } returns BrRateLimitResult.Allowed(remaining = 3)

        val quota = service.getRemainingQuota("user@test.com", BrSensitivityLevel.HIGH)
        quota shouldBe 3
    }

    test("getRemainingQuota returns 0 when exceeded") {
        coEvery {
            rateLimitService.check("user@test.com", BrSensitivityLevel.HIGH, config)
        } returns BrRateLimitResult.Exceeded(3600L, BrSensitivityLevel.HIGH)

        val quota = service.getRemainingQuota("user@test.com", BrSensitivityLevel.HIGH)
        quota shouldBe 0
    }
})
