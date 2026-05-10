# Software Test Cases (STC)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-35 |
| Title | KB Refinery — Semantic Entity Linking |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related STP | STP-v1-MTO-35.docx |
| Related FSD | FSD-v1-MTO-35.docx |

---

## Test Case Summary

| Category | ID Range | Count | Automation |
|----------|----------|-------|------------|
| Property-Based Tests | PBT-01 to PBT-04 | 4 | Automated (kotest-property) |
| Unit Tests | UT-01 to UT-18 | 18 | Automated (kotest + MockK) |
| Integration Tests | IT-01 to IT-06 | 6 | Automated (Testcontainers PostgreSQL) |
| E2E API Tests | E2E-01 to E2E-06 | 6 | Automated (kotest) |

**Total: 34 test cases**

---

## 1. Property-Based Tests (PBT)

### PBT-01: Similarity Threshold Filtering

| Field | Value |
|-------|-------|
| **ID** | PBT-01 |
| **Requirement** | BR-01, UC-05 |
| **Property** | For any threshold t ∈ [0.0, 1.0] and any list of scores, filtering by t returns only scores ≥ t |

**Generator:** Random Double in [0.0, 1.0] for threshold; List<Double> in [0.0, 1.0] for scores
**Iterations:** 1000
**Assertion:** `filteredResults.all { it.similarityScore >= threshold }`

---

### PBT-02: Self-Link Prevention

| Field | Value |
|-------|-------|
| **ID** | PBT-02 |
| **Requirement** | BR-04 |
| **Property** | For any issueKey, linkEntry never creates a link where source == target |

**Generator:** Random String matching `[A-Z]{3}-\d{1,4}` for issueKey
**Iterations:** 1000
**Assertion:** `result.links.none { it.sourceIssueKey == it.targetIssueKey }`

---

### PBT-03: Batch Chunking Correctness

| Field | Value |
|-------|-------|
| **ID** | PBT-03 |
| **Requirement** | BR-08 |
| **Property** | For any list of N entries, batchLink processes all entries regardless of chunk boundaries |

**Generator:** List of 1–200 entries with random keys and content
**Iterations:** 100
**Assertion:** `results.size == entries.size && results.all { it.success }`

---

### PBT-04: Score Ordering Invariant

| Field | Value |
|-------|-------|
| **ID** | PBT-04 |
| **Requirement** | UC-01 |
| **Property** | findSimilar always returns results sorted by similarity score descending |

**Generator:** Random embedding vectors, random topK in [1, 50]
**Iterations:** 500
**Assertion:** `results == results.sortedByDescending { it.similarityScore }`

---

## 2. Unit Tests (UT)

### UT-01: findSimilar — Returns Ranked Results

| Field | Value |
|-------|-------|
| **ID** | UT-01 |
| **Requirement** | UC-01, Story #2 |
| **Preconditions** | VectorDbClient mocked to return 5 results with known scores |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock vectorDbClient.getVector(issueKey) → returns embedding | Embedding retrieved |
| 2 | Mock vectorDbClient.search() → returns 5 results [0.95, 0.88, 0.82, 0.76, 0.60] | Search executed |
| 3 | Call findSimilar("MTO-1", topK=10) with threshold=0.75 | Returns 4 results (0.60 filtered out) |
| 4 | Verify order is descending by score | [0.95, 0.88, 0.82, 0.76] |

---

### UT-02: findSimilar — Issue Key Not in Vector DB

| Field | Value |
|-------|-------|
| **ID** | UT-02 |
| **Requirement** | UC-01, EF-01 |
| **Preconditions** | VectorDbClient.getVector returns null |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock vectorDbClient.getVector("UNKNOWN-1") → null | Not found |
| 2 | Call findSimilar("UNKNOWN-1") | Returns empty list |
| 3 | Verify no search was performed | vectorDbClient.search not called |

---

### UT-03: linkEntry — Happy Path

| Field | Value |
|-------|-------|
| **ID** | UT-03 |
| **Requirement** | UC-02, Story #4 |
| **Preconditions** | EmbeddingService and VectorDbClient mocked |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock embeddingService.embed(content) → FloatArray(768) | Embedding generated |
| 2 | Mock vectorDbClient.upsert() → success | Vector stored |
| 3 | Mock vectorDbClient.search() → 3 results above threshold | Similar found |
| 4 | Mock entityLinkRepository.saveBatch() → 3 | Links saved |
| 5 | Call linkEntry("MTO-35", "some content") | Returns LinkingResult(success=true, linksCreated=3) |

---

### UT-04: linkEntry — Embedding Service Failure

| Field | Value |
|-------|-------|
| **ID** | UT-04 |
| **Requirement** | UC-02, EF-01 |
| **Preconditions** | EmbeddingService throws EmbeddingServiceException |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock embeddingService.embed() → throws EmbeddingServiceException | Exception thrown |
| 2 | Call linkEntry("MTO-35", "content") | Returns LinkingResult(success=false, error="...") |
| 3 | Verify no vector DB or DB operations performed | No upsert, no save called |

---

### UT-05: batchLink — Processes in Chunks of 50

| Field | Value |
|-------|-------|
| **ID** | UT-05 |
| **Requirement** | UC-03, BR-08 |
| **Preconditions** | 120 entries provided |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 120 (issueKey, content) pairs | Input prepared |
| 2 | Mock all services to succeed | Services ready |
| 3 | Call batchLink(entries) | Returns 120 LinkingResults |
| 4 | Verify embeddingService.embed called 120 times | All entries processed |
| 5 | Verify processing happened in 3 chunks (50+50+20) | Chunking correct |

---

### UT-06: batchLink — Partial Failure

| Field | Value |
|-------|-------|
| **ID** | UT-06 |
| **Requirement** | UC-03, AF-01 |
| **Preconditions** | 5 entries, 2nd entry fails embedding |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock embed to fail on 2nd entry | Partial failure setup |
| 2 | Call batchLink(5 entries) | Returns 5 results |
| 3 | Verify results[1].success == false | Failed entry reported |
| 4 | Verify other 4 results are success | Remaining processed |

---

### UT-07: getLinks — Returns Bidirectional Links

| Field | Value |
|-------|-------|
| **ID** | UT-07 |
| **Requirement** | UC-04, BR-03 |
| **Preconditions** | Repository has links where MTO-35 is both source and target |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock repository with links: MTO-35→MTO-36 (0.9), MTO-34→MTO-35 (0.85) | Links exist |
| 2 | Call getLinks("MTO-35") | Returns both links |
| 3 | Verify sorted by score descending | [0.9, 0.85] |

---

### UT-08: Configuration — Threshold Validation

| Field | Value |
|-------|-------|
| **ID** | UT-08 |
| **Requirement** | UC-05, BR-01 |
| **Preconditions** | LinkingConfig with various threshold values |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create LinkingConfig(similarityThreshold = 0.75) | Valid config |
| 2 | Create LinkingConfig(similarityThreshold = 1.5) | Validation error |
| 3 | Create LinkingConfig(similarityThreshold = -0.1) | Validation error |
| 4 | Create LinkingConfig(similarityThreshold = 0.0) | Valid (edge case) |
| 5 | Create LinkingConfig(similarityThreshold = 1.0) | Valid (edge case) |

---

### UT-09: Threshold Boundary — Exact Match

| Field | Value |
|-------|-------|
| **ID** | UT-09 |
| **Requirement** | BR-01 |
| **Preconditions** | Threshold = 0.75, result score = 0.75 exactly |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set threshold = 0.75 | Config set |
| 2 | Mock search result with score = 0.75 | Exact boundary |
| 3 | Call findSimilar() | Result IS included (>= threshold) |

---

### UT-10: Default Configuration Values

| Field | Value |
|-------|-------|
| **ID** | UT-10 |
| **Requirement** | BR-02, BR-07 |
| **Preconditions** | Default LinkingConfig |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create LinkingConfig() with defaults | Config created |
| 2 | Verify similarityThreshold == 0.75 | Default threshold |
| 3 | Verify topK == 10 | Default topK |

---

### UT-11: Bidirectional Link Query

| Field | Value |
|-------|-------|
| **ID** | UT-11 |
| **Requirement** | BR-03 |
| **Preconditions** | Link saved as A→B |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Save link: source=MTO-35, target=MTO-36, score=0.9 | Link persisted |
| 2 | Call getLinks("MTO-35") | Returns link (as source) |
| 3 | Call getLinks("MTO-36") | Returns same link (as target) |

---

### UT-12: Self-Link Prevention

| Field | Value |
|-------|-------|
| **ID** | UT-12 |
| **Requirement** | BR-04 |
| **Preconditions** | VectorDB search returns self as top result |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock search to return [MTO-35(1.0), MTO-36(0.9)] | Self included |
| 2 | Call linkEntry("MTO-35", content) | Self filtered out |
| 3 | Verify only MTO-36 link created | No self-link |

---

### UT-13: Link Type Defaults to SEMANTIC

| Field | Value |
|-------|-------|
| **ID** | UT-13 |
| **Requirement** | BR-06 |
| **Preconditions** | Standard linkEntry call |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call linkEntry("MTO-35", content) | Links created |
| 2 | Verify all links have linkType == LinkType.SEMANTIC | Type correct |

---

### UT-14: Default TopK = 10

| Field | Value |
|-------|-------|
| **ID** | UT-14 |
| **Requirement** | BR-07 |
| **Preconditions** | VectorDB has 20 similar entries |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call findSimilar("MTO-35") without topK parameter | Uses default |
| 2 | Verify vectorDbClient.search called with topK=11 (10+1 for self) | Default applied |

---

### UT-15: Batch Chunk Size Enforcement

| Field | Value |
|-------|-------|
| **ID** | UT-15 |
| **Requirement** | BR-08 |
| **Preconditions** | 75 entries provided |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call batchLink(75 entries) | Processes in 2 chunks |
| 2 | Verify first chunk = 50 entries | Chunk 1 correct |
| 3 | Verify second chunk = 25 entries | Chunk 2 correct |

---

### UT-16: Embedding Dimension Validation

| Field | Value |
|-------|-------|
| **ID** | UT-16 |
| **Requirement** | BR-10 |
| **Preconditions** | Config expects 768 dimensions |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock embeddingService.embed() → FloatArray(768) | Correct dimension |
| 2 | Call linkEntry() | Success |
| 3 | Mock embeddingService.embed() → FloatArray(512) | Wrong dimension |
| 4 | Call linkEntry() | Error or dimension mismatch handled |

---

### UT-17: VectorDB Unavailable — Graceful Degradation

| Field | Value |
|-------|-------|
| **ID** | UT-17 |
| **Requirement** | NFR Availability, EF-02 |
| **Preconditions** | VectorDbClient throws VectorDbUnavailableException |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock vectorDbClient.search() → throws VectorDbUnavailableException | Service down |
| 2 | Call findSimilar("MTO-35") | Throws VectorDbUnavailableException |
| 3 | Verify exception propagated cleanly (no crash, no data corruption) | Clean failure |

---

### UT-18: Content Truncation for Long Text

| Field | Value |
|-------|-------|
| **ID** | UT-18 |
| **Requirement** | Error Handling (FSD §11) |
| **Preconditions** | Content > 10,000 characters |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create content with 15,000 characters | Long content |
| 2 | Call linkEntry("MTO-35", longContent) | Truncated to 10,000 |
| 3 | Verify embeddingService.embed called with truncated content | Truncation applied |

---

## 3. Integration Tests (IT)

### IT-01: linkEntry — Full DB Persistence

| Field | Value |
|-------|-------|
| **ID** | IT-01 |
| **Requirement** | UC-02, BR-09 |
| **Preconditions** | PostgreSQL Testcontainer running, entity_links table created |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start PostgreSQL Testcontainer | DB available |
| 2 | Run Flyway migration to create entity_links table | Table exists |
| 3 | Call repository.saveBatch(3 links) | 3 rows inserted |
| 4 | Query DB directly: SELECT COUNT(*) FROM entity_links | Count = 3 |
| 5 | Call repository.findByIssueKey("MTO-35") | Returns correct links |

---

### IT-02: batchLink — Large Batch DB Insert

| Field | Value |
|-------|-------|
| **ID** | IT-02 |
| **Requirement** | UC-03, BR-08 |
| **Preconditions** | PostgreSQL Testcontainer, 100 entries to link |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Prepare 100 entries with mock embeddings | Data ready |
| 2 | Call saveBatch with 500 links (100 entries × ~5 links each) | All inserted |
| 3 | Verify no duplicate constraint violations | UNIQUE handled |
| 4 | Query total count | Matches expected |

---

### IT-03: getLinks — Bidirectional Query from DB

| Field | Value |
|-------|-------|
| **ID** | IT-03 |
| **Requirement** | UC-04, BR-03 |
| **Preconditions** | Links in DB: A→B, C→A |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert link: source=MTO-35, target=MTO-36, score=0.9 | Inserted |
| 2 | Insert link: source=MTO-34, target=MTO-35, score=0.85 | Inserted |
| 3 | Call findByIssueKey("MTO-35") | Returns both links |
| 4 | Verify results include both directions | Bidirectional works |

---

### IT-04: Unique Constraint — Duplicate Prevention

| Field | Value |
|-------|-------|
| **ID** | IT-04 |
| **Requirement** | BR-05 |
| **Preconditions** | PostgreSQL Testcontainer |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert link: MTO-35→MTO-36, score=0.9 | Success |
| 2 | Insert same link: MTO-35→MTO-36, score=0.85 | Constraint violation |
| 3 | Verify original link unchanged | score still 0.9 |

---

### IT-05: Delete Links by Issue Key

| Field | Value |
|-------|-------|
| **ID** | IT-05 |
| **Requirement** | UC-04 |
| **Preconditions** | Multiple links for MTO-35 in DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 5 links for MTO-35 | 5 rows |
| 2 | Call deleteByIssueKey("MTO-35") | Returns 5 (deleted count) |
| 3 | Call findByIssueKey("MTO-35") | Returns empty list |

---

### IT-06: Persistence Across Connections

| Field | Value |
|-------|-------|
| **ID** | IT-06 |
| **Requirement** | BR-09 |
| **Preconditions** | PostgreSQL Testcontainer |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert links using connection pool A | Links saved |
| 2 | Close connection pool A | Pool closed |
| 3 | Create new connection pool B to same DB | New pool |
| 4 | Query links using pool B | Same links returned |

---

## 4. E2E API Tests

### E2E-01: Find Similar — Full Flow

| Field | Value |
|-------|-------|
| **ID** | E2E-01 |
| **Requirement** | UC-01, Story #2 |
| **Preconditions** | 10 KB entries pre-indexed in vector DB with known embeddings |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Pre-populate vector DB with 10 entries | Entries indexed |
| 2 | Pre-populate entity_links table | Links exist |
| 3 | Call findSimilar("MTO-35", topK=5) | Returns ≤5 results |
| 4 | Verify all results have score ≥ threshold | Threshold applied |
| 5 | Verify results sorted descending | Order correct |
| 6 | Verify latency < 100ms | Performance met |

---

### E2E-02: Link Entry — Ingest to Link Creation

| Field | Value |
|-------|-------|
| **ID** | E2E-02 |
| **Requirement** | UC-02, Story #1, Story #4 |
| **Preconditions** | 5 existing entries in vector DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call linkEntry("MTO-99", "content about MCP orchestration") | Processing starts |
| 2 | Verify embedding generated (768 dimensions) | Embedding created |
| 3 | Verify vector upserted to collection | Vector stored |
| 4 | Verify similar entries found | Search executed |
| 5 | Verify links persisted in entity_links table | Links in DB |
| 6 | Call getLinks("MTO-99") | Returns created links |

---

### E2E-03: Batch Link — Multiple Entries

| Field | Value |
|-------|-------|
| **ID** | E2E-03 |
| **Requirement** | UC-03, Story #4 |
| **Preconditions** | Empty vector DB and entity_links table |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Prepare 20 entries with varied content | Data ready |
| 2 | Call batchLink(20 entries) | Returns 20 results |
| 3 | Verify all results success=true | All processed |
| 4 | Verify vector DB has 20 entries | All indexed |
| 5 | Verify entity_links table has links | Cross-links created |
| 6 | Verify no self-links exist | BR-04 enforced |

---

### E2E-04: Get Links — Query Persisted Links

| Field | Value |
|-------|-------|
| **ID** | E2E-04 |
| **Requirement** | UC-04, Story #1 |
| **Preconditions** | Links created via linkEntry |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call linkEntry for 3 related entries | Links created |
| 2 | Call getLinks("MTO-35") | Returns links |
| 3 | Verify links include both directions | Bidirectional |
| 4 | Verify scores match original similarity | Scores preserved |

---

### E2E-05: Performance — findSimilar Under 100ms

| Field | Value |
|-------|-------|
| **ID** | E2E-05 |
| **Requirement** | NFR Performance |
| **Preconditions** | 1000 entries in vector DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Pre-populate vector DB with 1000 entries | Large dataset |
| 2 | Call findSimilar("MTO-500", topK=10) | Results returned |
| 3 | Measure execution time | < 100ms |
| 4 | Repeat 10 times, calculate p95 | p95 < 100ms |

---

### E2E-06: Performance — linkEntry Under 500ms

| Field | Value |
|-------|-------|
| **ID** | E2E-06 |
| **Requirement** | NFR Performance |
| **Preconditions** | 100 entries in vector DB |

**Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call linkEntry("NEW-1", "test content") | Completes |
| 2 | Measure total time (embed + upsert + search + persist) | < 500ms |
| 3 | Repeat 5 times, calculate average | avg < 500ms |
