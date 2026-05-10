# Software Test Cases (STC)

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
| Related STP | STP-v1-MTO-36.docx |
| Related FSD | FSD-v1-MTO-36.docx |

---

## Test Case Summary

| Category | ID Range | Count | Automation |
|----------|----------|-------|------------|
| Property-Based Tests | PBT-01 to PBT-03 | 3 | Automated (kotest-property) |
| Unit Tests | UT-01 to UT-10 | 10 | Automated (kotest + MockK) |
| Integration Tests | IT-01 to IT-03 | 3 | Automated (Testcontainers) |
| E2E API Tests | E2E-01 to E2E-05 | 5 | Automated (kotest) |

**Total: 21 test cases**

---

## 1. Property-Based Tests (PBT)

### PBT-01: BFS Never Visits a Node Twice

| Field | Value |
|-------|-------|
| **ID** | PBT-01 |
| **Requirement** | BR-09, UC-01 |
| **Property** | For any graph and any center node, getNetwork returns nodes with unique IDs (no duplicates) |

**Generator:** Random graph with 5–50 nodes, 10–100 edges (including cycles)
**Iterations:** 1000
**Assertion:** `result.nodes.map { it.id }.distinct().size == result.nodes.size`

---

### PBT-02: Edge Weights Within Valid Range

| Field | Value |
|-------|-------|
| **ID** | PBT-02 |
| **Requirement** | BR-04, UC-04 |
| **Property** | All edge weights in output are between 0.0 and 1.0 inclusive |

**Generator:** Random entity_links with scores in [0.0, 1.0]
**Iterations:** 1000
**Assertion:** `result.edges.all { it.weight in 0.0..1.0 }`

---

### PBT-03: Hop Limit Respected

| Field | Value |
|-------|-------|
| **ID** | PBT-03 |
| **Requirement** | BR-07 |
| **Property** | For any graph with known shortest paths, no node in result has shortest path > hops from center |

**Generator:** Linear chain graph (A→B→C→D→E), random hops in [0, 5]
**Iterations:** 500
**Assertion:** All returned nodes are within `hops` edges of center

---

## 2. Unit Tests (UT)

### UT-01: getNetwork — Simple 2-Hop Traversal

| Field | Value |
|-------|-------|
| **ID** | UT-01 |
| **Requirement** | UC-01, Story #1 |
| **Preconditions** | Mock repository: A→B(0.9), B→C(0.85), C→D(0.8) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getNetwork("A", hops=2) | Graph returned |
| 2 | Verify nodes = [A, B, C] | 2-hop neighborhood |
| 3 | Verify D is NOT included | Beyond 2 hops |
| 4 | Verify edges = [A→B, B→C] | Correct edges |
| 5 | Verify metadata.centerNode = "A" | Center set |

---

### UT-02: getNetwork — Handles Cycles

| Field | Value |
|-------|-------|
| **ID** | UT-02 |
| **Requirement** | BR-09 |
| **Preconditions** | Mock: A→B(0.9), B→C(0.85), C→A(0.8) (cycle) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getNetwork("A", hops=3) | No infinite loop |
| 2 | Verify nodes = [A, B, C] | Each visited once |
| 3 | Verify edges include all 3 | All edges present |
| 4 | Verify no duplicate nodes | Unique IDs |

---

### UT-03: getFullNetwork — All Nodes Returned

| Field | Value |
|-------|-------|
| **ID** | UT-03 |
| **Requirement** | UC-02 |
| **Preconditions** | Mock: 5 nodes, 8 edges |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork(projectKey=null) | Full graph |
| 2 | Verify nodes.size == 5 | All nodes |
| 3 | Verify edges.size == 8 | All edges |
| 4 | Verify metadata.centerNode == null | No center for full graph |

---

### UT-04: getFullNetwork — Project Filter

| Field | Value |
|-------|-------|
| **ID** | UT-04 |
| **Requirement** | UC-02, UC-03, Story #3 |
| **Preconditions** | Mock: MTO-35, MTO-36, COLLEX-1, COLLEX-2 with cross-links |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork(projectKey="MTO") | Filtered graph |
| 2 | Verify only MTO-* nodes returned | COLLEX nodes excluded |
| 3 | Verify edges only between MTO nodes | Cross-project edges removed |

---

### UT-05: Filter — Minimum Weight

| Field | Value |
|-------|-------|
| **ID** | UT-05 |
| **Requirement** | UC-03, BR-02 |
| **Preconditions** | Mock: edges with weights [0.5, 0.7, 0.8, 0.95] |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork(minWeight=0.75) | Filtered |
| 2 | Verify only edges with weight ≥ 0.75 returned | [0.8, 0.95] |
| 3 | Verify orphan nodes removed | Nodes with no remaining edges excluded |

---

### UT-06: Filter — Orphan Node Removal

| Field | Value |
|-------|-------|
| **ID** | UT-06 |
| **Requirement** | BR-03 |
| **Preconditions** | After weight filter, node X has no edges |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Build graph with node X connected only by low-weight edge (0.5) | X has one edge |
| 2 | Apply minWeight=0.75 filter | X's edge removed |
| 3 | Verify X not in result nodes | Orphan removed |

---

### UT-07: Edge Weight Mapping

| Field | Value |
|-------|-------|
| **ID** | UT-07 |
| **Requirement** | UC-04, BR-04, Story #4 |
| **Preconditions** | Entity link with score 0.87 |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock link: MTO-35→MTO-36, score=0.87 | Link exists |
| 2 | Call getNetwork("MTO-35", hops=1) | Graph returned |
| 3 | Verify edge weight == 0.87 | Score mapped correctly |
| 4 | Verify edge type == "semantic" | Type correct |

---

### UT-08: Max Hops Clamping

| Field | Value |
|-------|-------|
| **ID** | UT-08 |
| **Requirement** | BR-07 |
| **Preconditions** | Long chain: A→B→C→D→E→F→G→H (8 hops) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getNetwork("A", hops=10) | Clamped to 5 |
| 2 | Verify max depth of returned nodes = 5 | F included, G/H excluded |
| 3 | Verify WARN logged about clamping | Log present |

---

### UT-09: Node Truncation at 1000

| Field | Value |
|-------|-------|
| **ID** | UT-09 |
| **Requirement** | BR-08 |
| **Preconditions** | Mock repository returns 2000 unique nodes |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork() with large dataset | Truncated |
| 2 | Verify nodes.size <= 1000 | Limit enforced |
| 3 | Verify metadata.truncated == true | Flag set |

---

### UT-10: Center Node Always Included

| Field | Value |
|-------|-------|
| **ID** | UT-10 |
| **Requirement** | BR-10 |
| **Preconditions** | Center node has no links |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock: no links for "MTO-99" | Isolated node |
| 2 | Call getNetwork("MTO-99", hops=2) | Graph returned |
| 3 | Verify nodes = [MTO-99] | Center included |
| 4 | Verify edges = [] | No edges |
| 5 | Verify metadata.totalNodes == 1 | Correct count |

---

## 3. Integration Tests (IT)

### IT-01: Full Network from Real DB

| Field | Value |
|-------|-------|
| **ID** | IT-01 |
| **Requirement** | UC-02 |
| **Preconditions** | PostgreSQL Testcontainer with entity_links populated (20 links) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start PostgreSQL Testcontainer | DB ready |
| 2 | Insert 20 entity_links rows | Data populated |
| 3 | Call getFullNetwork() via service | Graph built from real DB |
| 4 | Verify node count matches unique keys in DB | Correct |
| 5 | Verify edge count matches link count | Correct |

---

### IT-02: BFS Traversal with Real DB

| Field | Value |
|-------|-------|
| **ID** | IT-02 |
| **Requirement** | UC-01 |
| **Preconditions** | Known graph topology in DB: A→B→C→D (chain) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert chain: A→B(0.9), B→C(0.85), C→D(0.8) | Chain in DB |
| 2 | Call getNetwork("A", hops=2) | Returns A, B, C |
| 3 | Verify D not included | Beyond 2 hops |
| 4 | Call getNetwork("A", hops=3) | Returns A, B, C, D |

---

### IT-03: Project Filter with Real DB

| Field | Value |
|-------|-------|
| **ID** | IT-03 |
| **Requirement** | UC-03, BR-01 |
| **Preconditions** | Mixed project links in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert: MTO-1→MTO-2, MTO-1→COL-1, COL-1→COL-2 | Mixed data |
| 2 | Call getFullNetwork(projectKey="MTO") | Filtered |
| 3 | Verify only MTO-1, MTO-2 in nodes | Project filter works |

---

## 4. E2E API Tests

### E2E-01: Get Network — Full Flow

| Field | Value |
|-------|-------|
| **ID** | E2E-01 |
| **Requirement** | UC-01, Story #1, Story #2 |
| **Preconditions** | Pre-populated entity_links (10 nodes, 15 edges) |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getNetwork("MTO-35", hops=2) | Graph returned |
| 2 | Verify JSON structure has nodes, edges, metadata | D3.js format |
| 3 | Verify all nodes have id, label, type fields | Schema valid |
| 4 | Verify all edges have source, target, weight, type | Schema valid |
| 5 | Verify center node in result | MTO-35 present |

---

### E2E-02: Get Full Network — All Data

| Field | Value |
|-------|-------|
| **ID** | E2E-02 |
| **Requirement** | UC-02, Story #1 |
| **Preconditions** | 50 nodes, 100 edges in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork() | Full graph |
| 2 | Verify all 50 nodes present | Complete |
| 3 | Verify all 100 edges present | Complete |
| 4 | Verify metadata.truncated == false | Not truncated |

---

### E2E-03: Filter by Project and Weight

| Field | Value |
|-------|-------|
| **ID** | E2E-03 |
| **Requirement** | UC-03, Story #3 |
| **Preconditions** | Mixed project data with varied weights |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork(projectKey="MTO", minWeight=0.8) | Filtered |
| 2 | Verify only MTO-* nodes | Project filter |
| 3 | Verify all edges weight ≥ 0.8 | Weight filter |
| 4 | Verify no orphan nodes | Cleanup applied |

---

### E2E-04: Performance — getNetwork Under 200ms

| Field | Value |
|-------|-------|
| **ID** | E2E-04 |
| **Requirement** | NFR Performance |
| **Preconditions** | 500 nodes, 1500 edges in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getNetwork("CENTER", hops=2) | Result returned |
| 2 | Measure execution time | < 200ms |
| 3 | Repeat 10 times, calculate p95 | p95 < 200ms |

---

### E2E-05: Performance — getFullNetwork Under 500ms

| Field | Value |
|-------|-------|
| **ID** | E2E-05 |
| **Requirement** | NFR Performance |
| **Preconditions** | 1000 nodes, 3000 edges in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call getFullNetwork() | Result returned |
| 2 | Measure execution time | < 500ms |
| 3 | Verify nodes.size == 1000 | All returned |
