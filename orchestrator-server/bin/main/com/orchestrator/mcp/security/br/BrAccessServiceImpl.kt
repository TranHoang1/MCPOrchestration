package com.orchestrator.mcp.security.br

import com.orchestrator.mcp.security.br.model.*
import com.orchestrator.mcp.security.br.repository.BrAccessAuditRepository
import com.orchestrator.mcp.security.model.KbRole
import org.slf4j.LoggerFactory

/**
 * Orchestrates the BR access pipeline:
 * session → sensitivity check → rate limit → decrypt → DLP → audit.
 */
class BrAccessServiceImpl(
    private val sessionService: BrSessionService,
    private val rateLimitService: BrRateLimitService,
    private val kmsService: BrKeyManagementService,
    private val dlpService: BrDlpService,
    private val auditRepository: BrAccessAuditRepository,
    private val config: BrAccessConfig
) : BrAccessService {

    private val logger = LoggerFactory.getLogger(BrAccessServiceImpl::class.java)

    override suspend fun createSession(userId: String, role: KbRole): BrSession =
        sessionService.create(userId, role)

    override suspend fun viewBusinessRules(
        sessionToken: String,
        issueKey: String,
        ipAddress: String?
    ): BrAccessResult {
        val session = validateSession(sessionToken) ?: return sessionDenied()
        if (session.revoked) return denied(BrDenialReason.SESSION_REVOKED, "Session revoked")

        // Simulate BR lookup — in real impl, query kb_entries
        val brEntry = lookupBrEntry(issueKey)
            ?: return denied(BrDenialReason.NOT_FOUND, "BR not found for issue")

        val level = BrSensitivityLevel.fromInt(brEntry.sensitivityLevel)
        if (!hasAccess(session.role, level)) {
            logDenied(session.userId, issueKey, level, "INSUFFICIENT_PERMISSION", ipAddress)
            return denied(BrDenialReason.INSUFFICIENT_PERMISSION, "Insufficient permissions")
        }

        val rateResult = rateLimitService.check(session.userId, level, config)
        if (rateResult is BrRateLimitResult.Exceeded) {
            logDenied(session.userId, issueKey, level, "RATE_LIMIT_EXCEEDED", ipAddress)
            return BrAccessResult.RateLimited(rateResult.retryAfterSeconds, level)
        }

        val decrypted = kmsService.decrypt(brEntry.encryptedContent, brEntry.keyId)
            ?: return denied(BrDenialReason.DECRYPTION_ERROR, "Decryption failed")

        auditRepository.logAccess(session.userId, issueKey, level, true, ipAddress)
        logger.info("BR accessed: issue={}, level={}, user={}", issueKey, level, session.userId)

        val remaining = (rateResult as BrRateLimitResult.Allowed).remaining
        return BrAccessResult.Success(decrypted, level, dlpService.generateHeaders(), remaining - 1)
    }

    override suspend fun revokeSession(sessionToken: String): Boolean =
        sessionService.revoke(sessionToken)

    override suspend fun getRemainingQuota(userId: String, level: BrSensitivityLevel): Int {
        val result = rateLimitService.check(userId, level, config)
        return when (result) {
            is BrRateLimitResult.Allowed -> result.remaining
            is BrRateLimitResult.Exceeded -> 0
        }
    }

    private suspend fun validateSession(token: String): BrSession? =
        sessionService.validate(token)

    private fun sessionDenied(): BrAccessResult.Denied =
        BrAccessResult.Denied(BrDenialReason.SESSION_EXPIRED, "Session expired or invalid")

    private fun denied(reason: BrDenialReason, message: String): BrAccessResult.Denied =
        BrAccessResult.Denied(reason, message)

    private fun hasAccess(role: KbRole, level: BrSensitivityLevel): Boolean =
        when (level) {
            BrSensitivityLevel.HIGH -> role == KbRole.BA_ADMIN
            BrSensitivityLevel.MEDIUM -> role == KbRole.BA_ADMIN
            BrSensitivityLevel.LOW -> role in listOf(KbRole.BA_ADMIN, KbRole.DEVELOPER)
        }

    private suspend fun logDenied(
        userId: String, issueKey: String, level: BrSensitivityLevel,
        reason: String, ipAddress: String?
    ) {
        auditRepository.logAccess(userId, issueKey, level, false, ipAddress, reason)
    }

    /**
     * Simulated BR entry lookup. In production, this queries kb_entries table.
     * For now, returns null to indicate "not found" — real impl injected via repository.
     */
    private fun lookupBrEntry(issueKey: String): BrEntryData? = null
}

/** Internal data holder for BR entry from database. */
data class BrEntryData(
    val issueKey: String,
    val encryptedContent: String,
    val keyId: String,
    val sensitivityLevel: Int
)
