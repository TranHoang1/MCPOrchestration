# Software Test Plan (STP)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-22 |
| Title | 3D Graph Visualization – Force-Directed Graph Views |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-22.docx |
| Related FSD | FSD-v1-MTO-22.docx |
| Related TDD | TDD-v1-MTO-22.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STP |

---

## 1. Introduction

### 1.1 Purpose

Test plan for the 3D Graph Visualization — REST API serving graph data and WebGL frontend viewer with 7 view modes.

### 1.2 Test Objectives

- Verify REST API endpoints return correct graph data
- Validate 7 view modes produce correct color/size/group transformations
- Ensure BFS subgraph traversal works correctly
- Confirm frontend renders 3D graph with interactions (click, hover, filter, search)
- Verify performance targets (500 nodes < 500ms, >30 FPS)

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Tools | Automation |
|-------|-------|-------|------------|
| PBT | Color/size/group determination, BFS traversal | Kotest Property | 100% |
| UT | GraphService, GraphRoutes, view mode transformations | Kotest + MockK | 100% |
| IT | API with real DB data, graph queries | Ktor testApplication + Testcontainers | 100% |
| E2E-API | Full graph API lifecycle | Ktor client + Testcontainers | 100% |
| E2E-UI | 3D rendering, interactions, responsive | Playwright | 80% |
| SIT | Visual quality, performance, cross-browser | Manual | 0% |

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement ID | Description | Test Case IDs | Coverage |
|----------------|-------------|---------------|----------|
| UC-01 | Full project graph API | TC-001, TC-002, TC-003 | ✅ |
| UC-02 | Subgraph API | TC-004, TC-005, TC-006 | ✅ |
| BR-01 | Node color by view | TC-007, TC-008 | ✅ |
| BR-02 | Node size logic | TC-009 | ✅ |
| BR-03 | Edge width by type | TC-010 | ✅ |
| BR-04 | Edge color by type | TC-010 | ✅ |
| BR-05 | BFS traversal | TC-004, TC-PBT-001 | ✅ |
| BR-06 | Center node styling | TC-005 | ✅ |
| BR-07 | Include all edges between nodes | TC-006 | ✅ |
| View modes | 7 view modes | TC-007, TC-008, TC-011 | ✅ |
| Interactions | Click, hover, filter, search | TC-012, TC-013, TC-014, TC-015 | ✅ |
| NFR | Performance targets | TC-016, TC-017 | ✅ |

---

## 4. Test Summary

| Level | Test Cases | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 3 | 3 | 0 |
| UT | 8 | 8 | 0 |
| IT | 4 | 4 | 0 |
| E2E-API | 4 | 4 | 0 |
| E2E-UI | 6 | 5 | 1 |
| SIT | 2 | 0 | 2 |
| **Total** | **27** | **24** | **3** |

---

## 5. Risk & Mitigation

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | WebGL not available in CI | High | Use headless Chrome with GPU emulation |
| 2 | 3d-force-graph CDN unavailable | Medium | Bundle locally for tests |
| 3 | Performance varies by hardware | Medium | Set generous thresholds, test on reference hardware |
| 4 | Large graph (1000+ nodes) rendering | High | Test with max dataset, verify FPS |
