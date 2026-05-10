# Software Test Cases (STC)

## MCPOrchestration — MTO-37: KB Refinery — Feedback & Correction UI

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-37 |
| Title | KB Refinery — Feedback & Correction UI |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related STP | STP-v1-MTO-37.docx |
| Related FSD | FSD-v1-MTO-37.docx |

---

## Test Case Summary

| Category | ID Range | Count | Automation |
|----------|----------|-------|------------|
| Property-Based Tests | PBT-01 to PBT-03 | 3 | Automated (kotest-property) |
| Unit Tests | UT-01 to UT-15 | 15 | Automated (kotest + MockK) |
| Integration Tests | IT-01 to IT-07 | 7 | Automated (Testcontainers) |
| E2E API Tests | E2E-01 to E2E-05 | 5 | Automated (kotest) |

**Total: 30 test cases**

---

## 1. Property-Based Tests (PBT)

### PBT-01: Submit Always Creates PENDING Feedback

| Field | Value |
|-------|-------|
| **ID** | PBT-01 |
| **Requirement** | BR-01, UC-01 |
| **Property** | For any valid (non-empty) content and valid type, submit always returns feedback with status=PENDING |

**Generator:** Random non-empty String (1–2000 chars), random FeedbackType
**Iterations:** 1000
**Assertion:** `result.status == FeedbackStatus.PENDING && result.id > 0`

---

### PBT-02: Only PENDING Feedback Can Be Resolved

| Field | Value |
|-------|-------|
| **ID** | PBT-02 |
| **Requirement** | BR-03 |
| **Property** | For any feedback with status != PENDING, approve() and reject() throw InvalidParamsException |

**Generator:** Random FeedbackStatus in [APPROVED, REJECTED]
**Iterations:** 1000
**Assertion:** `shouldThrow<InvalidParamsException> { service.approve(id, reviewer) }`

---

### PBT-03: Resolution Rate Invariant

| Field | Value |
|-------|-------|
| **ID** | PBT-03 |
| **Requirement** | BR-09 |
| **Property** | Resolution rate is always between 0.0 and 100.0, and equals approved/(approved+rejected)×100 |

**Generator:** Random approved count (0–100), random rejected count (0–100)
**Iterations:** 1000
**Assertion:** `stats.resolutionRate in 0.0..100.0 && stats.resolutionRate == expected`

---

## 2. Unit Tests (UT)

### UT-01: Submit — Happy Path

| Field | Value |
|-------|-------|
| **ID** | UT-01 |
| **Requirement** | UC-01, Story #1 |
| **Preconditions** | Repository and AuditService mocked |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create Feedback(issueKey="MTO-35", type=CONTENT_ERROR, content="Typo in description") | Valid input |
| 2 | Mock repository.insert() → returns feedback with id=1 | Persisted |
| 3 | Call submit(feedback) | Returns Feedback(id=1, status=PENDING) |
| 4 | Verify auditService.log called with "FEEDBACK_SUBMITTED" | Audit logged |

---

### UT-02: Submit — With Suggested Correction

| Field | Value |
|-------|-------|
| **ID** | UT-02 |
| **Requirement** | UC-01, AF-01 |
| **Preconditions** | Repository mocked |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create Feedback with suggestedCorrection = "Fix: change X to Y" | Has correction |
| 2 | Call submit(feedback) | Returns feedback with correction stored |
| 3 | Verify suggestedCorrection persisted | Field saved |

---

### UT-03: Approve — Happy Path

| Field | Value |
|-------|-------|
| **ID** | UT-03 |
| **Requirement** | UC-02, Story #2 |
| **Preconditions** | Feedback exists with status=PENDING, no suggestedCorrection |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock repository.findById(1) → PENDING feedback | Found |
| 2 | Call approve(1, "admin-1") | Returns updated feedback |
| 3 | Verify status == APPROVED | Status changed |
| 4 | Verify reviewerId == "admin-1" | Reviewer set |
| 5 | Verify resolvedAt != null | Timestamp set |
| 6 | Verify auditService.log called with "FEEDBACK_APPROVED" | Audit logged |

---

### UT-04: Approve — With Correction Applied

| Field | Value |
|-------|-------|
| **ID** | UT-04 |
| **Requirement** | UC-02, BR-05 |
| **Preconditions** | Feedback has suggestedCorrection |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock feedback with suggestedCorrection = "New content" | Has correction |
| 2 | Call approve(1, "admin-1") | Approved |
| 3 | Verify kbEntryService.applyCorrection called | Correction applied |
| 4 | Verify correction content matches suggestedCorrection | Correct content |

---

### UT-05: Reject — Happy Path

| Field | Value |
|-------|-------|
| **ID** | UT-05 |
| **Requirement** | UC-03, Story #2 |
| **Preconditions** | Feedback exists with status=PENDING |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock repository.findById(1) → PENDING feedback | Found |
| 2 | Call reject(1, "admin-1", "Not a valid issue") | Returns updated |
| 3 | Verify status == REJECTED | Status changed |
| 4 | Verify rejectionReason == "Not a valid issue" | Reason stored |
| 5 | Verify auditService.log called with "FEEDBACK_REJECTED" | Audit logged |

---

### UT-06: Reject — Empty Reason

| Field | Value |
|-------|-------|
| **ID** | UT-06 |
| **Requirement** | BR-04 |
| **Preconditions** | Feedback exists with status=PENDING |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call reject(1, "admin-1", "") | Exception |
| 2 | Verify InvalidParamsException thrown | "Rejection reason required" |
| 3 | Verify feedback status unchanged | Still PENDING |

---

### UT-07: Query by Issue Key

| Field | Value |
|-------|-------|
| **ID** | UT-07 |
| **Requirement** | UC-04, Story #3 |
| **Preconditions** | 3 feedback records for MTO-35 |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock repository.findByIssueKey("MTO-35") → 3 records | Data ready |
| 2 | Call getByIssueKey("MTO-35") | Returns 3 records |
| 3 | Verify sorted by createdAt descending | Order correct |

---

### UT-08: Query by Status with Limit

| Field | Value |
|-------|-------|
| **ID** | UT-08 |
| **Requirement** | UC-04, BR-07 |
| **Preconditions** | 60 PENDING feedback records |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getByStatus(PENDING, limit=50) | Returns 50 |
| 2 | Call getByStatus(PENDING, limit=150) | Clamped to 100 |
| 3 | Verify max 100 returned | Limit enforced |

---

### UT-09: Get Stats — Correct Calculations

| Field | Value |
|-------|-------|
| **ID** | UT-09 |
| **Requirement** | UC-05, BR-09, BR-10, Story #4 |
| **Preconditions** | Mock: 10 PENDING, 30 APPROVED, 10 REJECTED |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock countByStatus → {PENDING:10, APPROVED:30, REJECTED:10} | Data ready |
| 2 | Mock avgResolutionTime → 24.5 hours | Avg set |
| 3 | Call getStats() | Returns FeedbackStats |
| 4 | Verify totalCount == 50 | Correct |
| 5 | Verify resolutionRate == 75.0 (30/(30+10)×100) | Formula correct |
| 6 | Verify avgResolutionTimeHours == 24.5 | Correct |

---

### UT-10: Submit — Empty Content Rejected

| Field | Value |
|-------|-------|
| **ID** | UT-10 |
| **Requirement** | BR-01 |
| **Preconditions** | None |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create Feedback with content = "" | Empty content |
| 2 | Call submit(feedback) | Throws InvalidParamsException |
| 3 | Create Feedback with content = "   " (whitespace only) | Blank content |
| 4 | Call submit(feedback) | Throws InvalidParamsException |

---

### UT-11: Submit — Invalid Feedback Type

| Field | Value |
|-------|-------|
| **ID** | UT-11 |
| **Requirement** | BR-02 |
| **Preconditions** | None |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Attempt to create Feedback with invalid type string | Compile-time or runtime error |
| 2 | Verify only 4 valid types accepted | Enum enforced |

---

### UT-12: Approve/Reject — Already Resolved

| Field | Value |
|-------|-------|
| **ID** | UT-12 |
| **Requirement** | BR-03 |
| **Preconditions** | Feedback with status=APPROVED |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock findById → feedback with status=APPROVED | Already resolved |
| 2 | Call approve(id, reviewer) | Throws InvalidParamsException |
| 3 | Call reject(id, reviewer, reason) | Throws InvalidParamsException |
| 4 | Verify message: "Feedback already resolved" | Correct message |

---

### UT-13: Reject — Reason Required Validation

| Field | Value |
|-------|-------|
| **ID** | UT-13 |
| **Requirement** | BR-04 |
| **Preconditions** | PENDING feedback exists |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call reject(1, "admin", "") | Throws InvalidParamsException |
| 2 | Call reject(1, "admin", "  ") | Throws InvalidParamsException |
| 3 | Call reject(1, "admin", "Valid reason") | Success |

---

### UT-14: Audit Logging on All Actions

| Field | Value |
|-------|-------|
| **ID** | UT-14 |
| **Requirement** | BR-06 |
| **Preconditions** | AuditService mocked |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call submit() | auditService.log called with "FEEDBACK_SUBMITTED" |
| 2 | Call approve() | auditService.log called with "FEEDBACK_APPROVED" |
| 3 | Call reject() | auditService.log called with "FEEDBACK_REJECTED" |
| 4 | Verify all 3 calls include userId, entityKey, details | Complete audit data |

---

### UT-15: Query Limit Clamping

| Field | Value |
|-------|-------|
| **ID** | UT-15 |
| **Requirement** | BR-07 |
| **Preconditions** | None |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getByStatus(PENDING, limit=0) | Clamped to 1 (or default) |
| 2 | Call getByStatus(PENDING, limit=50) | Uses 50 |
| 3 | Call getByStatus(PENDING, limit=200) | Clamped to 100 |

---

## 3. Integration Tests (IT)

### IT-01: Submit — Full DB Persistence

| Field | Value |
|-------|-------|
| **ID** | IT-01 |
| **Requirement** | UC-01 |
| **Preconditions** | PostgreSQL Testcontainer, kb_feedback table created |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start PostgreSQL Testcontainer | DB ready |
| 2 | Run migration to create kb_feedback table | Table exists |
| 3 | Call repository.insert(feedback) | Row inserted |
| 4 | Query DB: SELECT * FROM kb_feedback WHERE id = {id} | Row found |
| 5 | Verify all fields persisted correctly | Data matches |

---

### IT-02: Approve — Status Update + Correction Apply

| Field | Value |
|-------|-------|
| **ID** | IT-02 |
| **Requirement** | UC-02, BR-05 |
| **Preconditions** | PENDING feedback in DB with suggestedCorrection |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert PENDING feedback with correction | Row in DB |
| 2 | Call service.approve(id, "admin") | Success |
| 3 | Query DB: verify status = 'APPROVED' | Updated |
| 4 | Verify reviewer_id and resolved_at set | Fields populated |

---

### IT-03: Reject — Status Update with Reason

| Field | Value |
|-------|-------|
| **ID** | IT-03 |
| **Requirement** | UC-03 |
| **Preconditions** | PENDING feedback in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert PENDING feedback | Row in DB |
| 2 | Call service.reject(id, "admin", "Invalid report") | Success |
| 3 | Query DB: verify status = 'REJECTED', rejection_reason set | Updated |

---

### IT-04: Query — Filter by Status

| Field | Value |
|-------|-------|
| **ID** | IT-04 |
| **Requirement** | UC-04 |
| **Preconditions** | Mixed status feedback in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert: 3 PENDING, 2 APPROVED, 1 REJECTED | 6 rows |
| 2 | Call findByStatus(PENDING, 50) | Returns 3 |
| 3 | Call findByStatus(APPROVED, 50) | Returns 2 |
| 4 | Call findByIssueKey("MTO-35") | Returns matching |

---

### IT-05: Analytics — Aggregation Queries

| Field | Value |
|-------|-------|
| **ID** | IT-05 |
| **Requirement** | UC-05, BR-09, BR-10 |
| **Preconditions** | 20 feedback records with known timestamps |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 20 records: 5 PENDING, 10 APPROVED, 5 REJECTED | Data ready |
| 2 | Call countByStatus() | {PENDING:5, APPROVED:10, REJECTED:5} |
| 3 | Call countByType() | Correct breakdown |
| 4 | Call avgResolutionTime() | Correct average |

---

### IT-06: Audit Integration

| Field | Value |
|-------|-------|
| **ID** | IT-06 |
| **Requirement** | BR-06 |
| **Preconditions** | AuditService available (or mocked at integration boundary) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Submit feedback | Audit record created |
| 2 | Approve feedback | Audit record created |
| 3 | Verify audit entries have correct action, userId, entityKey | Data correct |

---

### IT-07: No Deletion — Feedback Persists

| Field | Value |
|-------|-------|
| **ID** | IT-07 |
| **Requirement** | BR-08 |
| **Preconditions** | Feedback in various states |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 5 feedback records | 5 rows |
| 2 | Approve 2, reject 1 | Status updated |
| 3 | Verify all 5 still in DB | No deletions |
| 4 | Verify no DELETE operations in repository interface | API enforces |

---

## 4. E2E API Tests

### E2E-01: Submit Feedback — Full Flow

| Field | Value |
|-------|-------|
| **ID** | E2E-01 |
| **Requirement** | UC-01, Story #1 |
| **Preconditions** | Service running with DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call submit with valid feedback | Returns created feedback |
| 2 | Verify id > 0 | Generated |
| 3 | Verify status == PENDING | Initial state |
| 4 | Call getByIssueKey | Feedback appears in list |

---

### E2E-02: Approve Workflow — Submit → Approve → Verify

| Field | Value |
|-------|-------|
| **ID** | E2E-02 |
| **Requirement** | UC-01 + UC-02, Story #1 + #2 |
| **Preconditions** | Service running |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Submit feedback with suggestedCorrection | Created (PENDING) |
| 2 | Call approve(id, "admin") | Status → APPROVED |
| 3 | Verify correction applied (if KB service available) | Content updated |
| 4 | Call getByIssueKey | Shows APPROVED status |

---

### E2E-03: Reject Workflow — Submit → Reject → Verify

| Field | Value |
|-------|-------|
| **ID** | E2E-03 |
| **Requirement** | UC-01 + UC-03, Story #1 + #2 |
| **Preconditions** | Service running |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Submit feedback | Created (PENDING) |
| 2 | Call reject(id, "admin", "Not reproducible") | Status → REJECTED |
| 3 | Verify rejectionReason stored | "Not reproducible" |
| 4 | Attempt approve same feedback | Throws exception (already resolved) |

---

### E2E-04: Query — Multiple Filters

| Field | Value |
|-------|-------|
| **ID** | E2E-04 |
| **Requirement** | UC-04, Story #3 |
| **Preconditions** | 10 feedback records submitted |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Submit 10 feedback (mixed types, some approved/rejected) | Data ready |
| 2 | Call getByIssueKey("MTO-35") | Returns matching |
| 3 | Call getByStatus(PENDING) | Returns only pending |
| 4 | Verify ordering (newest first) | Correct sort |

---

### E2E-05: Analytics — End-to-End Stats

| Field | Value |
|-------|-------|
| **ID** | E2E-05 |
| **Requirement** | UC-05, Story #4 |
| **Preconditions** | Multiple feedback in various states |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Submit 5 feedback | 5 PENDING |
| 2 | Approve 3 | 2 PENDING, 3 APPROVED |
| 3 | Reject 1 | 1 PENDING, 3 APPROVED, 1 REJECTED |
| 4 | Call getStats() | totalCount=5, resolutionRate=75.0 |
| 5 | Verify countByType breakdown | Correct per type |
