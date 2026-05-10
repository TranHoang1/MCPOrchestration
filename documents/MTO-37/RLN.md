# Release Notes (RLN)

## MCPOrchestration — MTO-37: KB Refinery — Feedback & Correction UI

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.5.0 |
| Release Date | 2026-05-09 |
| Jira Ticket | MTO-37 |
| Environment | DEV / SIT / UAT / PROD |
| Author | DevOps Agent |
| Status | Draft |

---

## 1. What's New

### 1.1 Feature Summary

The MCP Orchestrator now includes **Feedback & Correction** — a quality control system for KB entries. Users can flag inaccurate content with specific feedback types, suggest corrections, and administrators can review and apply changes through a structured approval workflow.

**Key benefits:**
- **Quality control:** Users can report issues with KB content (incorrect classification, missing PII, false positives, content errors)
- **Structured workflow:** Submit → Review → Approve/Reject → Apply
- **Analytics:** Track feedback volume, resolution rate, and average resolution time
- **Audit trail:** All actions logged via MTO-34 AuditService

### 1.2 User-Facing Changes

| # | Change | Description | Impact |
|---|--------|-------------|--------|
| 1 | Submit feedback API | Users can flag KB entry issues | High — enables quality feedback loop |
| 2 | Approval workflow | Admins review and approve/reject corrections | High — controlled KB updates |
| 3 | Feedback status query | Users can check their feedback status | Medium — transparency |
| 4 | Analytics API | PMs can view feedback metrics | Low — reporting |

---

## 2. Technical Changes

### 2.1 New Package

```
com.orchestrator.mcp.feedback/
├── FeedbackService.kt                (interface)
├── FeedbackServiceImpl.kt            (implementation ~75 lines)
├── model/
│   ├── Feedback.kt                   (data class ~20 lines)
│   ├── FeedbackStatus.kt             (enum: PENDING, APPROVED, REJECTED)
│   ├── FeedbackType.kt               (enum: 4 types)
│   ├── FeedbackConfig.kt             (configuration)
│   └── FeedbackStats.kt              (analytics data class)
├── repository/
│   ├── FeedbackRepository.kt         (interface)
│   └── FeedbackRepositoryImpl.kt     (JDBC ~90 lines)
└── di/
    └── FeedbackModule.kt             (Koin module)
```

### 2.2 Database Changes

| Type | Object | Description |
|------|--------|-------------|
| New Table | `kb_feedback` | Stores feedback records with approval workflow |
| New Index | `idx_feedback_issue` | Query by issue key |
| New Index | `idx_feedback_status` | Filter by status |
| New Index | `idx_feedback_user` | Query by submitter |

### 2.3 Configuration Changes

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `orchestrator.feedback.enabled` | Boolean | true | Master toggle |
| `orchestrator.feedback.max-content-length` | Int | 2000 | Max feedback content |
| `orchestrator.feedback.max-correction-length` | Int | 5000 | Max correction text |
| `orchestrator.feedback.query-limit-default` | Int | 50 | Default query limit |
| `orchestrator.feedback.query-limit-max` | Int | 100 | Max query limit |

### 2.4 DI Changes

| Interface | Implementation | Scope |
|-----------|---------------|-------|
| `FeedbackService` | `FeedbackServiceImpl` | Singleton |
| `FeedbackRepository` | `FeedbackRepositoryImpl` | Singleton |

### 2.5 Feedback Types

| Type | Description |
|------|-------------|
| INCORRECT_CLASSIFICATION | KB entry categorized wrongly |
| MISSING_PII | Sensitive data not properly flagged |
| FALSE_POSITIVE | Entry incorrectly flagged as issue |
| CONTENT_ERROR | Factual error in KB content |

---

## 3. Dependencies

### 3.1 Pre-requisite Releases

| Release | Version | Required Before |
|---------|---------|-----------------|
| MTO-10 (Base Orchestrator) | 1.0.0 | This release |
| MTO-26 (KB Entries) | 1.2.0 | This release |
| MTO-34 (Audit Service) | 1.x.x | This release |
| PostgreSQL 16+ | 16.x | This release |

### 3.2 External System Changes

| System | Change Required |
|--------|----------------|
| PostgreSQL | Run migration V4 (kb_feedback table) |
| MTO-34 AuditService | No changes (existing integration) |

---

## 4. Breaking Changes

None. Fully backward compatible.

- New feature only — no existing APIs modified
- Feedback feature can be disabled via `feedback.enabled: false`
- No existing tables modified

---

## 5. Known Limitations

| # | Limitation | Impact | Workaround |
|---|-----------|--------|------------|
| 1 | No notification on feedback resolution | Users must poll for status | Future: add webhook/email |
| 2 | No bulk approve/reject | Admin must process one-by-one | Future: batch API |
| 3 | No role-based access control | Any authenticated user can approve | Future: RBAC integration |
| 4 | No re-open flow for rejected feedback | User must submit new feedback | Submit new with reference |

---

## 6. Migration Notes

### 6.1 Data Migration

| Migration | Description | Automated | Time |
|-----------|-------------|-----------|------|
| V4__create_kb_feedback.sql | Create table + indexes | Yes (Flyway) | < 1 second |

### 6.2 No Data Migration Required

This is a new feature with no existing data to migrate.

---

## 7. Testing Summary

| Test Level | Total | Pass Rate |
|-----------|-------|-----------|
| Property-Based Tests | 3 | TBD |
| Unit Tests | 15 | TBD |
| Integration Tests | 7 | TBD |
| E2E API Tests | 5 | TBD |
| **Total** | **30** | TBD |

---

## 8. Deployment Instructions

See: [Deployment Guide (DPG.md)](DPG.md)

**Quick Reference:**
1. Run DB migration (V4)
2. Update application.yml
3. Deploy new JAR
4. Verify startup + smoke test

**Estimated deployment time:** ~5 minutes

---

## 9. Rollback Plan

**Quick rollback:** Set `feedback.enabled: false` and restart.
**Full rollback:** Restore previous JAR + drop kb_feedback table.
