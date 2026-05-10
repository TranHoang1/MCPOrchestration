# Software Test Plan (STP)

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
| Related BRD | BRD-v1-MTO-37.docx |
| Related FSD | FSD-v1-MTO-37.docx |
| Related TDD | TDD-v1-MTO-37.docx |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy for the **Feedback & Correction** feature (MTO-37). The feature provides API endpoints for submitting feedback on KB entries, an approval workflow, and feedback analytics.

### 1.2 Test Objectives

- Verify all 5 use cases (UC-01 through UC-05) from FSD
- Validate all 10 business rules (BR-01 through BR-10)
- Ensure approval workflow state transitions are correct (PENDING → APPROVED/REJECTED)
- Verify audit logging for all feedback actions
- Validate analytics calculations (resolution rate, avg time)
- Confirm error handling for invalid inputs and edge cases

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-37/BRD.md |
| FSD | documents/MTO-37/FSD.md |
| TDD | documents/MTO-37/TDD.md |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | State transition properties, analytics invariants | Automated | kotest-property |
| UT | FeedbackServiceImpl logic, validation, status transitions | Automated | kotest + MockK |
| IT | Full flow with real PostgreSQL (Testcontainers) | Automated | kotest + Testcontainers |
| E2E-API | Complete workflow: submit → approve/reject → query → stats | Automated | kotest |

### 2.2 Test Approach

**Risk-Based Prioritization:**
- **Critical:** State transitions (only PENDING can be resolved)
- **High:** Data integrity (feedback never lost, audit always logged)
- **Medium:** Analytics accuracy (correct calculations)
- **Low:** Query pagination and limits

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement | Source | Test Cases | Priority |
|-------------|--------|------------|----------|
| UC-01: Submit Feedback | FSD §3.1 | UT-01, UT-02, IT-01, E2E-01 | High |
| UC-02: Approve Correction | FSD §3.2 | UT-03, UT-04, IT-02, E2E-02 | High |
| UC-03: Reject Feedback | FSD §3.3 | UT-05, UT-06, IT-03, E2E-03 | High |
| UC-04: Query Feedback | FSD §3.4 | UT-07, UT-08, IT-04, E2E-04 | Medium |
| UC-05: Analytics | FSD §3.5 | UT-09, IT-05, E2E-05 | Medium |
| BR-01: Non-empty content | FSD §4 | UT-10, PBT-01 | High |
| BR-02: Valid feedback type | FSD §4 | UT-11 | High |
| BR-03: Only PENDING resolvable | FSD §4 | UT-12, PBT-02 | High |
| BR-04: Rejection reason required | FSD §4 | UT-13 | High |
| BR-05: Auto-apply correction | FSD §4 | UT-04, IT-02 | High |
| BR-06: Audit logging | FSD §4 | UT-14, IT-06 | High |
| BR-07: Query limit max 100 | FSD §4 | UT-15 | Low |
| BR-08: No deletion | FSD §4 | IT-07 | Medium |
| BR-09: Resolution rate formula | FSD §4 | UT-09, PBT-03 | Medium |
| BR-10: Avg resolution time | FSD §4 | UT-09 | Medium |
| Story #1: Submit feedback | BRD §2 | E2E-01 | High |
| Story #2: Approve/reject | BRD §2 | E2E-02, E2E-03 | High |
| Story #3: View status | BRD §2 | E2E-04 | Medium |
| Story #4: Analytics | BRD §2 | E2E-05 | Medium |

---

## 4. Test Environment

### 4.1 Infrastructure

| Component | Specification |
|-----------|--------------|
| JDK | 21 |
| Kotlin | 2.3.20 |
| Test Framework | Kotest 5.9.1 |
| Mocking | MockK 1.14.2 |
| Containers | Testcontainers 1.21.1 (PostgreSQL 16) |

### 4.2 Test Data

| Dataset | Description |
|---------|-------------|
| Valid feedback | All 4 types, with/without suggestedCorrection |
| Invalid feedback | Empty content, invalid type, missing fields |
| Workflow data | Feedback in various states for transition testing |
| Analytics data | 50 feedback records with mixed statuses for stats |

---

## 5. Pass/Fail Criteria

| Criteria | Threshold |
|----------|-----------|
| UT pass rate | 100% |
| IT pass rate | 100% |
| E2E pass rate | 100% |
| PBT pass rate | 100% (1000 iterations) |
| Audit coverage | 100% of actions logged |

---

## 6. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Concurrent approve/reject on same feedback | Data race | DB-level status check in UPDATE WHERE |
| Audit service failure blocks feedback | User cannot submit | Make audit non-blocking (fire-and-forget) |
| Large analytics queries slow | Timeout | Add indexes on status, type columns |
