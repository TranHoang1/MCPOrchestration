# Software Test Plan (STP)

## MCPOrchestration — MTO-21: Web Dashboard – Sync Status & Monitoring

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-21 |
| Title | Web Dashboard – Sync Status & Monitoring |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-21.docx |
| Related FSD | FSD-v1-MTO-21.docx |
| Related TDD | TDD-v1-MTO-21.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STP |

---

## 1. Introduction

### 1.1 Purpose

Test plan for the Web Dashboard — REST API endpoints, WebSocket real-time updates, and HTML UI for monitoring Jira sync operations.

### 1.2 Test Objectives

- Verify REST API endpoints (GET/POST) return correct responses
- Validate WebSocket event streaming (progress, error, completed, heartbeat)
- Ensure HTML dashboard renders correctly and updates in real-time
- Confirm responsive design across breakpoints
- Verify error handling and edge cases

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Tools | Automation |
|-------|-------|-------|------------|
| PBT | DTO serialization, status enum mapping | Kotest Property | 100% |
| UT | SyncRoutes, WebSocketHandler, SyncDashboardService | Kotest + MockK | 100% |
| IT | Ktor routes with real DB, WebSocket connections | Ktor testApplication + Testcontainers | 100% |
| E2E-API | Full REST + WebSocket lifecycle | Ktor client + Testcontainers | 100% |
| E2E-UI | Dashboard rendering, responsive layout | Playwright | 100% |
| SIT | Visual inspection, cross-browser | Manual | 0% |

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement ID | Description | Test Case IDs | Coverage |
|----------------|-------------|---------------|----------|
| UC-01 | GET /sync/status | TC-001, TC-002 | ✅ |
| UC-02 | GET /sync/status/{key} | TC-003, TC-004 | ✅ |
| UC-03 | POST /sync/start | TC-005, TC-006, TC-007 | ✅ |
| UC-04 | POST /sync/stop | TC-008, TC-009 | ✅ |
| UC-05 | WS /sync/live | TC-010, TC-011, TC-012, TC-013 | ✅ |
| UC-06 | HTML Dashboard | TC-014, TC-015, TC-016, TC-017 | ✅ |
| BR-01 | Empty array if no projects | TC-002 | ✅ |
| BR-02 | Progress calculation | TC-001 | ✅ |
| BR-03 | Status enum values | TC-001 | ✅ |
| BR-04 | Max 50 WebSocket connections | TC-012 | ✅ |
| BR-05 | Heartbeat every 30s | TC-011 | ✅ |
| BR-06 | Progress throttled 5s | TC-010 | ✅ |
| BR-07 | Graceful close on shutdown | TC-013 | ✅ |

---

## 4. Test Summary

| Level | Test Cases | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 2 | 2 | 0 |
| UT | 9 | 9 | 0 |
| IT | 5 | 5 | 0 |
| E2E-API | 4 | 4 | 0 |
| E2E-UI | 4 | 4 | 0 |
| SIT | 2 | 0 | 2 |
| **Total** | **26** | **24** | **2** |

---

## 5. Risk & Mitigation

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | WebSocket flaky in CI | Medium | Use deterministic event timing |
| 2 | Playwright browser download slow | Low | Cache browser binaries |
| 3 | Responsive breakpoint edge cases | Low | Test exact breakpoint values |
