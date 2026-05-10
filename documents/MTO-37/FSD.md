# Functional Specification Document (FSD)

## MCPOrchestration — MTO-37: KB Refinery — Feedback & Correction UI

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-37 |
| Title | KB Refinery — Feedback & Correction UI |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-37.docx |
| Related TDD | TDD-v1-MTO-37.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA + TA Agent | Initial FSD — full functional specification |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the complete functional design for the **Feedback & Correction** feature. It provides API endpoints for users to submit feedback on KB entries, an approval workflow for corrections (Submit → Review → Approve/Reject → Apply), and feedback analytics.

### 1.2 Scope

**In Scope:**
- Submit feedback on KB entries (multiple feedback types)
- Approval workflow: PENDING → APPROVED / REJECTED
- Apply approved corrections to KB entries
- Query feedback by issue key, status, user
- Feedback analytics (count by type, resolution rate, average resolution time)
- Audit trail integration (MTO-34 AuditService)

**Out of Scope:**
- Frontend UI implementation (API-only in this ticket)
- Email/notification on feedback submission
- Bulk feedback operations
- Feedback on non-KB content (e.g., code, documents)
- User authentication (assumes authenticated context)

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| Feedback | A user-submitted report about KB entry quality |
| Correction | A suggested change to KB entry content |
| Approval Workflow | Process: Submit → Review → Approve/Reject → Apply |
| Resolution | Final state of feedback (APPROVED or REJECTED) |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD — MTO-37 | documents/MTO-37/BRD.md |
| TDD — MTO-37 | documents/MTO-37/TDD.md |
| MTO-26 (KB Entries) | Dependency — KB entry schema |
| MTO-34 (Audit Service) | Dependency — audit logging |

---

## 2. System Overview

### 2.1 System Context

The Feedback & Correction service provides a quality control layer for the KB. Users can flag inaccurate content, suggest corrections, and administrators can review and apply changes. All actions are audit-logged via MTO-34.

**Actors:**
- **User:** Submits feedback, views feedback status
- **Admin/BA:** Reviews feedback, approves/rejects corrections
- **System:** Applies approved corrections to KB entries

### 2.2 Integration Points

| System | Direction | Protocol | Purpose |
|--------|-----------|----------|---------|
| KB Entry Store (MTO-26) | Outbound | Internal API | Apply corrections to entries |
| AuditService (MTO-34) | Outbound | Internal API | Log all feedback actions |
| PostgreSQL | Outbound | JDBC | Persist feedback records |

---

## 3. Functional Requirements

### 3.1 Feature: Submit Feedback

**Source:** [Implements: Story #1]

#### 3.1.1 Use Case

**Use Case ID:** UC-01
**Actor:** User
**Preconditions:**
- User is authenticated
- KB entry exists for the specified issue key

**Postconditions:**
- Feedback record created with status = PENDING
- Audit log entry created

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Calls `submit(feedback)` with issue key, type, content |
| 2 | | FeedbackService | Validate input (non-empty content, valid type) |
| 3 | | FeedbackRepository | INSERT feedback with status=PENDING |
| 4 | | AuditService | Log: "Feedback submitted by {userId} for {issueKey}" |
| 5 | | FeedbackService | Return created Feedback with generated ID |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | User includes suggestedCorrection | Store correction text alongside feedback |
| AF-02 | Multiple feedback for same issue key | All stored independently (no dedup) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Empty content | Throw InvalidParamsException("Content cannot be empty") |
| EF-02 | Invalid feedback type | Throw InvalidParamsException("Invalid feedback type") |
| EF-03 | Issue key not found in KB | Log WARN, still create feedback (issue may exist in Jira) |
| EF-04 | Database failure | Throw ServerUnavailableException |

---

### 3.2 Feature: Approve Correction

**Source:** [Implements: Story #2]

#### 3.2.1 Use Case

**Use Case ID:** UC-02
**Actor:** Admin / BA
**Preconditions:**
- Feedback exists with status = PENDING
- Reviewer has admin/BA role

**Postconditions:**
- Feedback status = APPROVED
- If suggestedCorrection exists, KB entry updated
- Audit log entry created

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin | | Calls `approve(feedbackId, reviewerId)` |
| 2 | | FeedbackService | Validate feedback exists and status == PENDING |
| 3 | | FeedbackRepository | UPDATE status=APPROVED, reviewerId, resolvedAt |
| 4 | | FeedbackService | If suggestedCorrection != null: apply to KB entry |
| 5 | | AuditService | Log: "Feedback {id} approved by {reviewerId}" |
| 6 | | FeedbackService | Return updated Feedback |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | No suggestedCorrection | Approve without applying changes (acknowledgment only) |
| AF-02 | KB entry no longer exists | Approve feedback but skip KB update, log WARN |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Feedback not found | Return null |
| EF-02 | Feedback already resolved (not PENDING) | Throw InvalidParamsException("Feedback already resolved") |
| EF-03 | KB update fails | Rollback approval, throw ServerUnavailableException |

---

### 3.3 Feature: Reject Feedback

**Source:** [Implements: Story #2]

#### 3.3.1 Use Case

**Use Case ID:** UC-03
**Actor:** Admin / BA
**Preconditions:**
- Feedback exists with status = PENDING
- Reviewer provides rejection reason

**Postconditions:**
- Feedback status = REJECTED with reason
- Audit log entry created

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin | | Calls `reject(feedbackId, reviewerId, reason)` |
| 2 | | FeedbackService | Validate feedback exists and status == PENDING |
| 3 | | FeedbackRepository | UPDATE status=REJECTED, reviewerId, rejectionReason, resolvedAt |
| 4 | | AuditService | Log: "Feedback {id} rejected by {reviewerId}: {reason}" |
| 5 | | FeedbackService | Return updated Feedback |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | Empty rejection reason | Throw InvalidParamsException("Rejection reason required") |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Feedback not found | Return null |
| EF-02 | Feedback already resolved | Throw InvalidParamsException("Feedback already resolved") |

---

### 3.4 Feature: Query Feedback

**Source:** [Implements: Story #3]

#### 3.4.1 Use Case

**Use Case ID:** UC-04
**Actor:** User / Admin
**Preconditions:**
- Feedback records exist in database

**Postconditions:**
- Matching feedback records returned

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Calls `getByIssueKey(issueKey)` or `getByStatus(status, limit)` |
| 2 | | FeedbackRepository | Query with filter criteria |
| 3 | | FeedbackService | Return list sorted by createdAt descending |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | No matching feedback | Return empty list |
| AF-02 | Limit exceeds max (100) | Clamp to 100 |

---

### 3.5 Feature: Feedback Analytics

**Source:** [Implements: Story #4]

#### 3.5.1 Use Case

**Use Case ID:** UC-05
**Actor:** PM
**Preconditions:**
- Feedback records exist

**Postconditions:**
- Analytics summary returned

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | PM | | Calls `getStats()` |
| 2 | | FeedbackRepository | Aggregate: count by type, count by status |
| 3 | | FeedbackRepository | Calculate: resolution rate, avg resolution time |
| 4 | | FeedbackService | Return FeedbackStats |

---

## 4. Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Feedback content cannot be empty | Story #1 |
| BR-02 | Feedback type must be one of: INCORRECT_CLASSIFICATION, MISSING_PII, FALSE_POSITIVE, CONTENT_ERROR | Story #1 |
| BR-03 | Only PENDING feedback can be approved or rejected | Story #2 |
| BR-04 | Rejection requires a non-empty reason | Story #2 |
| BR-05 | Approved corrections with suggestedCorrection are auto-applied to KB | Story #2 |
| BR-06 | All feedback actions are audit-logged (MTO-34) | Design |
| BR-07 | Query limit max = 100 (default 50) | Design |
| BR-08 | Feedback is never deleted (soft state via status) | Design |
| BR-09 | Resolution rate = approved / (approved + rejected) × 100% | Story #4 |
| BR-10 | Average resolution time = avg(resolvedAt - createdAt) for resolved feedback | Story #4 |

---

## 5. Data Specifications

### 5.1 Input Data

#### 5.1.1 Submit Feedback Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| issueKey | String | Yes | Non-empty, `[A-Z]+-\d+` | Target KB entry |
| userId | String | Yes | Non-empty, max 100 chars | Submitter ID |
| type | FeedbackType | Yes | Valid enum value | Feedback category |
| content | String | Yes | Non-empty, max 2000 chars | Feedback description |
| suggestedCorrection | String? | No | Max 5000 chars | Proposed fix |

#### 5.1.2 Approve/Reject Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| feedbackId | Long | Yes | > 0 | Feedback record ID |
| reviewerId | String | Yes | Non-empty | Reviewer ID |
| reason | String | Yes (reject only) | Non-empty, max 1000 chars | Rejection reason |

### 5.2 Output Data

#### 5.2.1 Feedback

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Auto-generated ID |
| issueKey | String | Target KB entry key |
| userId | String | Submitter |
| type | FeedbackType | INCORRECT_CLASSIFICATION / MISSING_PII / FALSE_POSITIVE / CONTENT_ERROR |
| content | String | Feedback description |
| suggestedCorrection | String? | Proposed fix (optional) |
| status | FeedbackStatus | PENDING / APPROVED / REJECTED |
| reviewerId | String? | Who reviewed |
| rejectionReason | String? | Why rejected |
| createdAt | Instant | Submission time |
| resolvedAt | Instant? | Resolution time |

#### 5.2.2 FeedbackStats

| Field | Type | Description |
|-------|------|-------------|
| totalCount | Long | Total feedback records |
| pendingCount | Long | Awaiting review |
| approvedCount | Long | Approved |
| rejectedCount | Long | Rejected |
| countByType | Map<FeedbackType, Long> | Breakdown by type |
| resolutionRate | Double | approved / (approved + rejected) % |
| avgResolutionTimeHours | Double | Average hours to resolve |

---

## 6. API Contracts

### 6.1 FeedbackService Interface

```kotlin
interface FeedbackService {
    /**
     * Submit new feedback for a KB entry.
     * @param feedback Feedback data (id will be auto-generated)
     * @return Created feedback with generated ID
     * @throws InvalidParamsException if content empty or type invalid
     */
    suspend fun submit(feedback: Feedback): Feedback

    /**
     * Approve pending feedback. Applies correction if suggestedCorrection exists.
     * @param feedbackId ID of feedback to approve
     * @param reviewerId ID of the reviewer
     * @return Updated feedback, or null if not found
     * @throws InvalidParamsException if feedback not in PENDING status
     */
    suspend fun approve(feedbackId: Long, reviewerId: String): Feedback?

    /**
     * Reject pending feedback with reason.
     * @param feedbackId ID of feedback to reject
     * @param reviewerId ID of the reviewer
     * @param reason Rejection reason (required, non-empty)
     * @return Updated feedback, or null if not found
     * @throws InvalidParamsException if feedback not PENDING or reason empty
     */
    suspend fun reject(feedbackId: Long, reviewerId: String, reason: String): Feedback?

    /**
     * Get all feedback for a specific KB entry.
     * @param issueKey Target issue key
     * @return List sorted by createdAt descending
     */
    suspend fun getByIssueKey(issueKey: String): List<Feedback>

    /**
     * Get feedback filtered by status.
     * @param status Filter status
     * @param limit Max results (default 50, max 100)
     * @return List sorted by createdAt descending
     */
    suspend fun getByStatus(status: FeedbackStatus, limit: Int = 50): List<Feedback>

    /**
     * Get feedback analytics summary.
     * @return Aggregated statistics
     */
    suspend fun getStats(): FeedbackStats
}
```

### 6.2 FeedbackRepository Interface

```kotlin
interface FeedbackRepository {
    suspend fun insert(feedback: Feedback): Feedback
    suspend fun updateStatus(id: Long, status: FeedbackStatus, reviewerId: String?, reason: String?, resolvedAt: Instant?)
    suspend fun findById(id: Long): Feedback?
    suspend fun findByIssueKey(issueKey: String): List<Feedback>
    suspend fun findByStatus(status: FeedbackStatus, limit: Int): List<Feedback>
    suspend fun countByStatus(): Map<FeedbackStatus, Long>
    suspend fun countByType(): Map<FeedbackType, Long>
    suspend fun avgResolutionTime(): Double
}
```

### 6.3 MCP Tool Exposure

```json
{
  "name": "submit_kb_feedback",
  "description": "Submit feedback on a KB entry (flag inaccurate content, suggest corrections)",
  "inputSchema": {
    "type": "object",
    "properties": {
      "issue_key": { "type": "string", "description": "KB entry issue key" },
      "feedback_type": { "type": "string", "enum": ["INCORRECT_CLASSIFICATION", "MISSING_PII", "FALSE_POSITIVE", "CONTENT_ERROR"] },
      "content": { "type": "string", "description": "Feedback description" },
      "suggested_correction": { "type": "string", "description": "Proposed fix (optional)" }
    },
    "required": ["issue_key", "feedback_type", "content"]
  }
}
```

---

## 7. Processing Logic

### 7.1 Submit Feedback Pseudocode

```
suspend fun submit(feedback: Feedback): Feedback {
    // Validate
    require(feedback.content.isNotBlank()) { "Content cannot be empty" }
    require(feedback.type in FeedbackType.values()) { "Invalid feedback type" }

    // Persist
    val created = feedbackRepository.insert(feedback.copy(
        status = FeedbackStatus.PENDING,
        createdAt = Clock.System.now()
    ))

    // Audit
    auditService.log(
        action = "FEEDBACK_SUBMITTED",
        userId = feedback.userId,
        entityKey = feedback.issueKey,
        details = "Type: ${feedback.type}, ID: ${created.id}"
    )

    return created
}
```

### 7.2 Approve Pseudocode

```
suspend fun approve(feedbackId: Long, reviewerId: String): Feedback? {
    val feedback = feedbackRepository.findById(feedbackId) ?: return null

    require(feedback.status == FeedbackStatus.PENDING) { "Feedback already resolved" }

    val now = Clock.System.now()

    // Update status
    feedbackRepository.updateStatus(
        id = feedbackId,
        status = FeedbackStatus.APPROVED,
        reviewerId = reviewerId,
        reason = null,
        resolvedAt = now
    )

    // Apply correction if provided
    if (feedback.suggestedCorrection != null) {
        kbEntryService.applyCorrection(
            issueKey = feedback.issueKey,
            correction = feedback.suggestedCorrection
        )
    }

    // Audit
    auditService.log(
        action = "FEEDBACK_APPROVED",
        userId = reviewerId,
        entityKey = feedback.issueKey,
        details = "Feedback ID: $feedbackId"
    )

    return feedback.copy(status = FeedbackStatus.APPROVED, reviewerId = reviewerId, resolvedAt = now)
}
```

### 7.3 Reject Pseudocode

```
suspend fun reject(feedbackId: Long, reviewerId: String, reason: String): Feedback? {
    require(reason.isNotBlank()) { "Rejection reason required" }

    val feedback = feedbackRepository.findById(feedbackId) ?: return null
    require(feedback.status == FeedbackStatus.PENDING) { "Feedback already resolved" }

    val now = Clock.System.now()

    feedbackRepository.updateStatus(
        id = feedbackId,
        status = FeedbackStatus.REJECTED,
        reviewerId = reviewerId,
        reason = reason,
        resolvedAt = now
    )

    auditService.log(
        action = "FEEDBACK_REJECTED",
        userId = reviewerId,
        entityKey = feedback.issueKey,
        details = "Feedback ID: $feedbackId, Reason: $reason"
    )

    return feedback.copy(status = FeedbackStatus.REJECTED, reviewerId = reviewerId, rejectionReason = reason, resolvedAt = now)
}
```

### 7.4 Get Stats Pseudocode

```
suspend fun getStats(): FeedbackStats {
    val byStatus = feedbackRepository.countByStatus()
    val byType = feedbackRepository.countByType()
    val avgTime = feedbackRepository.avgResolutionTime()

    val approved = byStatus[FeedbackStatus.APPROVED] ?: 0
    val rejected = byStatus[FeedbackStatus.REJECTED] ?: 0
    val total = byStatus.values.sum()
    val resolved = approved + rejected

    return FeedbackStats(
        totalCount = total,
        pendingCount = byStatus[FeedbackStatus.PENDING] ?: 0,
        approvedCount = approved,
        rejectedCount = rejected,
        countByType = byType,
        resolutionRate = if (resolved > 0) (approved.toDouble() / resolved) * 100 else 0.0,
        avgResolutionTimeHours = avgTime
    )
}
```

---

## 8. State Diagram

### 8.1 Feedback Lifecycle

```
[User Submits]
      │
      ▼
┌──────────┐
│ PENDING  │ ← Initial state
└────┬─────┘
     │
     ├──── Admin approves ────┐
     │                        ▼
     │               ┌──────────────┐
     │               │  APPROVED    │ → Apply correction (if exists)
     │               └──────────────┘
     │
     └──── Admin rejects ────┐
                              ▼
                     ┌──────────────┐
                     │  REJECTED    │ → Reason stored
                     └──────────────┘
```

### 8.2 State Transitions

| From | To | Trigger | Actor | Conditions |
|------|----|---------|-------|------------|
| — | PENDING | submit() | User | Valid input |
| PENDING | APPROVED | approve() | Admin/BA | Feedback exists, is PENDING |
| PENDING | REJECTED | reject() | Admin/BA | Feedback exists, is PENDING, reason provided |

---

## 9. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | submit() latency | < 100ms |
| Performance | approve/reject latency | < 200ms |
| Performance | getStats() latency | < 300ms |
| Scalability | Total feedback records | 100,000+ |
| Availability | Service availability | 99.9% |
| Auditability | All actions logged | 100% coverage |
| Data Retention | Feedback never deleted | Permanent |

---

## 10. Error Handling

| Error Condition | Response | Recovery |
|-----------------|----------|----------|
| Empty content on submit | Throw InvalidParamsException | Caller validates |
| Invalid feedback type | Throw InvalidParamsException | Caller validates |
| Feedback not found (approve/reject) | Return null | Caller handles |
| Feedback already resolved | Throw InvalidParamsException | Caller checks status |
| Empty rejection reason | Throw InvalidParamsException | Caller validates |
| Database failure | Throw ServerUnavailableException | Connection pool retry |
| Audit service failure | Log ERROR, continue (non-blocking) | Audit is best-effort |
| KB update failure on approve | Rollback approval, throw error | Retry or manual fix |

---

## 11. Open Issues

| # | Issue | Status | Decision Needed By |
|---|-------|--------|-------------------|
| 1 | Should feedback submitter be notified on resolution? | Deferred | PM |
| 2 | Should there be a "re-open" flow for rejected feedback? | Open | PM |
| 3 | Role-based access control for approve/reject? | Open — currently any authenticated user | SA |
