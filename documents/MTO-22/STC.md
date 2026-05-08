# Software Test Cases (STC)

## MCPOrchestration — MTO-22: 3D Graph Visualization – Force-Directed Graph Views

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-22 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-22.docx |

---

## 1. PBT — Property-Based Tests

### TC-PBT-001: BFS traversal returns nodes within depth limit

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-05 |
| Property | For any graph and center node, BFS(center, depth) returns only nodes reachable within depth hops |
| Generator | Random graph (10-100 nodes, 20-200 edges) + random center + Arb.int(1..5) |
| Iterations | 200 |

### TC-PBT-002: View mode color assignment is deterministic

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-01 |
| Property | For same ticket and view mode, determineColor() always returns same color |
| Generator | Arb.enum<ViewMode>() × Custom ticket Arb |
| Iterations | 500 |

### TC-PBT-003: Node size always within bounds (3-15)

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-02 |
| Property | For any ticket and view mode, determineSize() ∈ [3, 15] |
| Generator | Arb.int(0..100) (story points) × Arb.enum<ViewMode>() |
| Iterations | 500 |

---

## 2. UT — Unit Tests

### TC-001: GET /sync/graph/{projectKey} — returns full graph

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01 |
| Precondition | Mock repository returns 20 nodes, 30 edges |
| Expected | 200 OK, response has 20 nodes, 30 edges, metadata.totalNodes=20 |

### TC-002: GET /sync/graph/{projectKey} — empty project

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01 |
| Precondition | No tickets for project |
| Expected | 200 OK, nodes=[], edges=[], totalNodes=0 |

### TC-003: GET /sync/graph/{projectKey}?view=complexity — correct colors

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-01 |
| Input | view=complexity, tickets with varying dependency depths |
| Expected | Nodes colored: depth 0=#4CAF50, depth 3=#FF9800, depth 5+=#F44336 |

### TC-004: GET /sync/graph/{key}/{issueKey} — BFS subgraph

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-02, BR-05 |
| Input | center=MTO-14, depth=2 |
| Expected | Only nodes within 2 hops of MTO-14 returned |

### TC-005: Subgraph — center node has enhanced styling

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-06 |
| Input | center=MTO-14 |
| Expected | MTO-14 node has size * 1.5 compared to same type |

### TC-006: Subgraph — includes all edges between included nodes

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-07 |
| Precondition | Nodes A, B, C in subgraph; edges A→B, B→C, A→C exist |
| Expected | All 3 edges included in response |

### TC-007: View mode hierarchy — colors by issue type

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-01 |
| Input | view=hierarchy, Epic + Story + Task + Bug |
| Expected | Epic=#9C27B0, Story=#4CAF50, Task=#2196F3, Bug=#F44336 |

### TC-008: View mode team — colors by assignee

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-01 |
| Input | view=team, 3 different assignees |
| Expected | Each assignee gets unique HSL color |

### TC-009: Node sizing — complexity view uses story points

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-02 |
| Input | view=complexity, storyPoints=0, 5, 13 |
| Expected | Sizes: min(3), 7.5, max(15) — clamped to [3, 15] |

### TC-010: Edge styling — width and color by relationship type

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-03, BR-04 |
| Input | parent, blocks, relates-to edges |
| Expected | parent: width=2, color=#999; blocks: width=3, color=#f44336; relates-to: width=1, color=#2196f3 |

---

## 3. IT — Integration Tests

### TC-IT-001: Graph API with real PostgreSQL data

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Testcontainers PostgreSQL with 50 tickets + 80 edges |
| Steps | 1. GET /sync/graph/MTO 2. Verify response matches DB |
| Expected | All 50 nodes, 80 edges returned with correct metadata |

### TC-IT-002: Subgraph query with real graph data

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | Known graph structure in DB |
| Steps | 1. GET /sync/graph/MTO/MTO-14?depth=1 |
| Expected | Only MTO-14 and its direct neighbors returned |

### TC-IT-003: Performance — 500 nodes response time

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Precondition | 500 tickets + 800 edges in DB |
| Steps | 1. GET /sync/graph/MTO 2. Measure response time |
| Expected | Response < 500ms |

### TC-IT-004: Static graph viewer HTML served

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Steps | 1. GET /sync/graph-viewer 2. Verify HTML |
| Expected | 200 OK, HTML contains 3d-force-graph script reference |

---

## 4. E2E-API — End-to-End Tests

### TC-E2E-001: Full graph with all 7 view modes

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | For each view mode: GET /sync/graph/MTO?view={mode} |
| Expected | Each returns valid response with view-specific colors/sizes |

### TC-E2E-002: Subgraph with depth and view combination

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. GET /sync/graph/MTO/MTO-14?depth=3&view=dependency |
| Expected | Subgraph with dependency-colored nodes within 3 hops |

### TC-E2E-003: 404 for non-existent issue in subgraph

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Steps | 1. GET /sync/graph/MTO/MTO-999 |
| Expected | 404, `{ "error": "Issue not found: MTO-999" }` |

### TC-E2E-004: Large graph truncation (>1000 nodes)

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Precondition | Project with 1500 tickets |
| Steps | 1. GET /sync/graph/LARGE |
| Expected | Response has ≤ 1000 nodes + truncation warning in metadata |

---

## 5. E2E-UI — Browser Tests

### TC-UI-001: Graph viewer loads and renders 3D graph

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Tool | Playwright |
| Steps | 1. Navigate to /sync/graph-viewer?project=MTO 2. Wait for render |
| Expected | Canvas element visible, nodes rendered (check WebGL context) |

### TC-UI-002: Click node shows details panel

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Click on a node 2. Verify details panel |
| Expected | Panel shows id, label, type, status, priority, assignee |

### TC-UI-003: View mode selector changes colors

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Select "team" view 2. Verify API called with view=team 3. Verify re-render |
| Expected | Graph re-renders with team-based colors |

### TC-UI-004: Search highlights matching node

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Type "MTO-14" in search 2. Verify camera animation |
| Expected | Camera centers on MTO-14, node highlighted |

### TC-UI-005: Filter by issue type

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Select filter: type=Bug 2. Verify only Bug nodes visible |
| Expected | Non-Bug nodes hidden, Bug nodes remain |

### TC-UI-006: Responsive — minimum viewport 768px

| Attribute | Value |
|-----------|-------|
| Level | E2E-UI |
| Steps | 1. Set viewport 768px 2. Verify graph still renders |
| Expected | Graph visible, controls accessible, no overflow |

---

## 6. SIT — System Integration Tests (Manual)

### TC-SIT-001: Visual quality assessment — 3D rendering

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Load graph with 100+ nodes 2. Rotate/zoom 3. Assess visual quality |
| Expected | Smooth rotation, readable labels, no z-fighting |

### TC-SIT-002: Performance — 500 nodes at >30 FPS

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Load 500-node graph 2. Measure FPS during interaction |
| Expected | Consistent >30 FPS during rotation/zoom |
