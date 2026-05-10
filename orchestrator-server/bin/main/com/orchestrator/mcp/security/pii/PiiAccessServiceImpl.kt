package com.orchestrator.mcp.security.pii

import com.orchestrator.mcp.kbstore.repository.PiiMappingRepository
import com.orchestrator.mcp.security.model.KbRole
import com.orchestrator.mcp.security.pii.model.*
import com.orchestrator.mcp.security.pii.repository.PiiAccessAuditRepository
import org.slf4j.LoggerFactory

/**
 * Orchestrates PII unmask with access control pipeline:
 * session validation → role check → rate limit → audit → decrypt.
 * Fail-closed: if audit write fails, unmask is denied.
 */
class PiiAccessServiceImpl(
    private val sessionService: PiiSessionService,
    private val rateLimitService: PiiRateLimitService,
    private val auditRepository: PiiAccessAuditRepository,
    private val piiMappingRepository: PiiMappingRepository,
    private val config: PiiAccessConfig
) : PiiAccessService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun createSession(userId: String, role: KbRole): PiiSession {
        return sessionService.create(userId, role)
    }

    override suspend fun unmask(
        sessionToken: String,
        issueKey: String,
        placeholder: String,
        ipAddress: String?
    ): UnmaskResult {
        val session = validateSession(sessionToken) ?: return sessionExpired()
        if (session.revoked) return sessionRevoked()
        if (session.role != KbRole.BA_ADMIN) return permissionDenied(session, issueKey, placeholder, ipAddress)

        val rateLimitResult = rateLimitService.check(session.userId, config)
        if (rateLimitResult is RateLimitResult.Exceeded) {
            logFailure(session, issueKey, placeholder, "RATE_LIMIT_EXCEEDED", ipAddress)
            return UnmaskResult.RateLimited(rateLimitResult.retryAfterSeconds, rateLimitResult.windowResetAt)
        }

        val originalValue = findPiiValue(issueKey, placeholder)
            ?: return notFound(session, issueKey, placeholder, ipAddress)

        if (!logSuccess(session, issueKey, placeholder, ipAddress)) {
            return auditFailure()
        }

        val remaining = (rateLimitResult as RateLimitResult.Allowed).remaining - 1
        return UnmaskResult.Success(originalValue, remaining)
    }

    override suspend fun revokeSession(sessionToken: String): Boolean {
        return sessionService.revoke(sessionToken)
    }

    override suspend fun getRemainingQuota(userId: String): Int {
        val result = rateLimitService.check(userId, config)
        return when (result) {
            is RateLimitResult.Allowed -> result.remaining
            is RateLimitResult.Exceeded -> 0
        }
    }

    private suspend fun validateSession(token: String): PiiSession? {
        return sessionService.validate(token)
    }

    private suspend fun findPiiValue(issueKey: String, placeholder: String): String? {
        val mappings = piiMappingRepository.findByIssueKey(issueKey)
        return mappings.find { it.placeholder == placeholder }?.originalValue
    }

    private suspend fun logSuccess(
        session: PiiSession, issueKey: String, placeholder: String, ip: String?
    ): Boolean {
        val entry = PiiAuditEntry(
            userId = session.userId, issueKey = issueKey,
            placeholder = placeholder, success = true, ipAddress = ip
        )
        return auditRepository.insert(entry)
    }

    private suspend fun logFailure(
        session: PiiSession, issueKey: String, placeholder: String, reason: String, ip: String?
    ) {
        val entry = PiiAuditEntry(
            userId = session.userId, issueKey = issueKey,
            placeholder = placeholder, success = false, failureReason = reason, ipAddress = ip
        )
        auditRepository.insert(entry)
    }

    private suspend fun permissionDenied(
        session: PiiSession, issueKey: String, placeholder: String, ip: String?
    ): UnmaskResult.Denied {
        logFailure(session, issueKey, placeholder, "INSUFFICIENT_PERMISSION", ip)
        return UnmaskResult.Denied(DenialReason.INSUFFICIENT_PERMISSION, "Admin role required")
    }

    private suspend fun notFound(
        session: PiiSession, issueKey: String, placeholder: String, ip: String?
    ): UnmaskResult.Denied {
        logFailure(session, issueKey, placeholder, "NOT_FOUND", ip)
        return UnmaskResult.Denied(DenialReason.NOT_FOUND, "Placeholder not found")
    }

    private fun sessionExpired() = UnmaskResult.Denied(DenialReason.SESSION_EXPIRED, "Session expired")
    private fun sessionRevoked() = UnmaskResult.Denied(DenialReason.SESSION_REVOKED, "Session revoked")
    private fun auditFailure() = UnmaskResult.Denied(DenialReason.AUDIT_FAILURE, "Audit write failed")
}
