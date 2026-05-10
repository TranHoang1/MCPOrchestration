# Software Test Plan (STP)

## MCPOrchestration — MTO-36: KB Refinery — Feature Network Mapping

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-36 |
| Title | KB Refinery — Feature Network Mapping |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-36.docx |
| Related FSD | FSD-v1-MTO-36.docx |
| Related TDD | TDD-v1-MTO-36.docx |

---

## 1. Introduction

### 1.1 Purpose

This test plan defines the testing strategy for the **Feature Network Mapping** service (MTO-36). The service builds relationship graphs from semantic entity links (MTO-35) and provides D3.js-ready JSON output for frontend visualization.

### 1.2 Test Objectives

- Verify BFS traversal correctly builds N-hop neighborhood graphs
- Validate full network graph construction with filtering
- Ensure edge weights correctly reflect similarity scores
- Verify graph truncation at configured limits (500/1000 nodes)
- Confirm D3.js-compatible JSON output format
- Validate performance targets: getNetwork < 200ms, getFullNetwork < 500ms

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-36/BRD.md |
| FSD | documents/MTO-36/FSD.md |
| TDD | documents/MTO-36/TDD.md |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Automation | Tools |
|-------|-------|------------|-------|
| PBT | BFS traversal properties, graph invariants | Automated | kotest-property |
| UT | NetworkServiceImpl logic, filtering, truncation | Automated | kotest + MockK |
| IT | Full flow with real PostgreSQL (entity_links data) | Automated | kotest + Testcontainers |
| E2E-API | End-to-end graph queries with pre-populated data | Automated | kotest |

### 2.2 Test Approach

**Risk-Based Prioritization:**
- **Critical:** BFS correctness (cycles, depth limiting, visited tracking)
- **High:** Graph truncation (prevent OOM on large graphs)
- **Medium:** Filtering accuracy (project, weight)
- **Low:** JSON output format compliance

---

## 3. Requirements Traceability Matrix (RTM)

| Requirement | Source | Test Cases | Priority |
|-------------|--------|------------|----------|
| UC-01: N-Hop Network | FSD §3.1 | UT-01, UT-02, PBT-01, E2E-01 | High |
| UC-02: Full Network | FSD §3.2 | UT-03, UT-04, IT-01, E2E-02 | High |
| UC-03: Filter Network | FSD §3.3 | UT-05, UT-06, E2E-03 | Medium |
| UC-04: Edge Weights | FSD §3.4 | UT-07, PBT-02 | Medium |
| BR-07: Max hops = 5 | FSD §4 | UT-08, PBT-03 | High |
| BR-08: Max nodes = 1000 | FSD §4 | UT-09 | High |
| BR-09: No cycles in BFS | FSD §4 | PBT-01 | High |
| BR-10: Center node always included | FSD §4 | UT-10 | Medium |
| NFR: getNetwork < 200ms | FSD §8 | E2E-04 | High |
| NFR: getFullNetwork < 500ms | FSD §8 | E2E-05 | Medium |
| Story #1: Network graph | BRD §2 | E2E-01, E2E-02 | High |
| Story #2: Graph data API | BRD §2 | UT-07, E2E-01 | High |
| Story #3: Filter by project | BRD §2 | UT-05, E2E-03 | Medium |
| Story #4: Edge weights | BRD §2 | UT-07, PBT-02 | Medium |

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

| Dataset | Description | Size |
|---------|-------------|------|
| Small graph | 10 nodes, 15 edges (known topology) | 10 nodes |
| Medium graph | 100 nodes, 300 edges | 100 nodes |
| Large graph | 2000 nodes, 5000 edges (truncation test) | 2000 nodes |
| Disconnected graph | 3 separate clusters | 30 nodes |

---

## 5. Pass/Fail Criteria

| Criteria | Threshold |
|----------|-----------|
| UT pass rate | 100% |
| IT pass rate | 100% |
| E2E pass rate | 100% |
| PBT pass rate | 100% (1000 iterations) |
| Performance targets | ≥ 95% within target |

---

## 6. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Large graph causes OOM | Test failure | Enforce truncation limits in tests |
| BFS infinite loop on cyclic data | Hang | Visited set prevents revisiting; PBT verifies |
| Slow DB queries on large datasets | Performance test failure | Use indexed queries, limit test data size |
