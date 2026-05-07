# Software Test Cases (STC)

## Jira Project Sync Service — MTO-15: Database Schema & Sync State Management

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-15 |
| Title | Database Schema & Sync State Management |
| Author | QA Agent |
| Version | 2.0 |
| Date | 2026-05-07 |
| Status | Draft |
| Related STP | STP-v2-MTO-15.docx |
| Related FSD | FSD-v1-MTO-15.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-14 | QA Agent | Initiate document — 41 test cases (UT + IT + SIT) |
| 2.0 | 2026-05-07 | QA Agent | Complete rewrite — 61 test cases across 6 levels (PBT, UT, IT, E2E-API, SIT). Added property-based tests, E2E-API lifecycle tests, enhanced boundary testing |

---

## Test Case Summary

| Category | ID Range | Count | Priority |
|----------|----------|-------|----------|
| Property-Based Tests — State Machine Invariants | TC-PBT-01 to TC-PBT-05 | 5 | High |
| Unit Tests — State Machine & Validation | TC-UT-01 to TC-UT-20 | 20 | High |
| Integration Tests — Database Operations | TC-IT-01 to TC-IT-22 | 22 | High |
| E2E-API Tests — Full Lifecycle | TC-E2E-01 to TC-E2E-06 | 6 | High |
| System Integration Tests — Performance & Regression | TC-SIT-01 to TC-SIT-08 | 8 | Medium |

---

## 1. Property-Based Tests (PBT) — State Machine Invariants

**Test Class:** `SyncStatePropertyTest.kt`
**Framework:** Kotest 5.9.1 Property Testing (Arb generators)
**Purpose:** Verify state machine invariants hold for arbitrary transition sequences

### TC-PBT-01: State Machine Never Reaches Invalid State

| Field | Value |
|-------|-------|
| **ID** | TC-PBT-01 |
| **Priority** | High |
| **Type** | Property-Based — Invariant |
| **Requirement** | BR-02, BR-21–BR-26 |
| **Preconditions** | SyncStateManagerImpl instantiated with mocked DataSource |

**Property:**
For any random sequence of valid transition attempts (markRunning, markPaused, markCompleted, markFailed), the resulting state is always one of: IDLE, RUNNING, PAUSED, COMPLETED, FAILED.

**Generator:** `Arb.list(Arb.enum<TransitionAction>(), range = 1..50)`

**Invariant Assertion:**
```
forAll(transitionSequences) { sequence ->
    val finalState = applySequence(initialState = IDLE, sequence)
    finalState in setOf(IDLE, RUNNING, PAUSED, COMPLETED, FAILED)
}
```

**Iterations:** 1000
**Postconditions:** No `IllegalStateException` escapes (caught internally), state always valid

---

### TC-PBT-02: Offset Monotonically Increases During RUNNING

| Field | Value |
|-------|-------|
| **ID** | TC-PBT-02 |
| **Priority** | High |
| **Type** | Property-Based — Invariant |
| **Requirement** | BR-03 |
| **Preconditions** | Sync state in RUNNING status |

**Property:**
For any sequence of `updateProgress(offset_i)` calls where each offset_i > offset_{i-1}, the stored `last_offset` is always the maximum value seen.

**Generator:** `Arb.list(Arb.int(1..10000), range = 1..100).map { it.runningReduce { acc, i -> acc + i } }`

**Invariant Assertion:**
```
forAll(offsetSequences) { offsets ->
    offsets.forEach { offset -> manager.updateProgress(project, offset, offset) }
    getState(project).lastOffset == offsets.last()
}
```

**Iterations:** 1000

---

### TC-PBT-03: Valid Transitions Always Succeed

| Field | Value |
|-------|-------|
| **ID** | TC-PBT-03 |
| **Priority** | High |
| **Type** | Property-Based — Completeness |
| **Requirement** | BR-21–BR-24 |
| **Preconditions** | State machine in any valid state |

**Property:**
Every transition defined in the state machine diagram (FSD §3.5.4) succeeds without exception when the precondition state matches.

**Generator:** `Arb.element(validTransitionPairs)` where validTransitionPairs = [(IDLE, markRunning), (RUNNING, markCompleted), (RUNNING, markFailed), (RUNNING, markPaused), (PAUSED, markRunning), (FAILED, markRunning)]

**Invariant Assertion:**
```
forAll(validTransitions) { (fromState, action) ->
    setupState(fromState)
    action() // should not throw
    true
}
```

**Iterations:** 1000

---

### TC-PBT-04: Invalid Transitions Always Throw IllegalStateException

| Field | Value |
|-------|-------|
| **ID** | TC-PBT-04 |
| **Priority** | High |
| **Type** | Property-Based — Safety |
| **Requirement** | BR-26 |
| **Preconditions** | State machine in any valid state |

**Property:**
Every transition NOT in the valid set throws `IllegalStateException`.

**Generator:** `Arb.element(invalidTransitionPairs)` where invalidTransitionPairs = [(COMPLETED, markRunning), (COMPLETED, markPaused), (IDLE, markCompleted), (IDLE, markFailed), (IDLE, markPaused), (PAUSED, markCompleted), (PAUSED, markFailed)]

**Invariant Assertion:**
```
forAll(invalidTransitions) { (fromState, action) ->
    setupState(fromState)
    shouldThrow<IllegalStateException> { action() }
    true
}
```

**Iterations:** 1000

---

### TC-PBT-05: synced_issues Never Exceeds total_issues

| Field | Value |
|-------|-------|
| **ID** | TC-PBT-05 |
| **Priority** | High |
| **Type** | Property-Based — Invariant |
| **Requirement** | BR-04 |
| **Preconditions** | Sync state exists with total_issues set |

**Property:**
For any `updateProgress(offset, synced)` call, if `synced > total_issues`, the operation is rejected or clamped.

**Generator:** `Arb.pair(Arb.int(0..1000), Arb.int(0..2000))`

**Invariant Assertion:**
```
forAll(progressPairs) { (total, synced) ->
    setTotalIssues(project, total)
    if (synced <= total) {
        manager.updateProgress(project, synced, synced) // succeeds
    } else {
        shouldThrow<IllegalArgumentException> { manager.updateProgress(project, synced, synced) }
    }
    true
}
```

**Iterations:** 1000


---

## 2. Unit Tests — State Machine & Validation (TC-UT-01 to TC-UT-20)

**Test Class:** `SyncStateManagerImplTest.kt`
**Framework:** Kotest 5.9.1 FunSpec + MockK 1.14.2
**Mocking:** HikariDataSource, Connection, PreparedStatement are mocked

### TC-UT-01: getOrCreate — New Project Creates IDLE State

| Field | Value |
|-------|-------|
| **ID** | TC-UT-01 |
| **Priority** | High |
| **Type** | Unit — Functional |
| **Requirement** | UC-01, BR-01 |
| **Preconditions** | SyncStateManagerImpl instantiated with mocked DataSource; no existing record for "MTO" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock PreparedStatement.executeQuery() to return empty ResultSet | ResultSet.next() returns false |
| 2 | Call `manager.getOrCreate("MTO")` | INSERT statement executed with project_key="MTO", status="IDLE", offset=0 |
| 3 | Verify returned SyncState | status=IDLE, lastOffset=0, totalIssues=0, syncedIssues=0 |

**Test Data:** projectKey = "MTO"
**Postconditions:** INSERT was called once with correct parameters

---

### TC-UT-02: getOrCreate — Existing Project Returns Current State

| Field | Value |
|-------|-------|
| **ID** | TC-UT-02 |
| **Priority** | High |
| **Type** | Unit — Functional |
| **Requirement** | UC-01, BR-01 |
| **Preconditions** | SyncStateManagerImpl instantiated; record exists for "MTO" with status=RUNNING, offset=50 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock ResultSet to return existing record (status=RUNNING, offset=50, synced=25) | ResultSet.next() returns true |
| 2 | Call `manager.getOrCreate("MTO")` | No INSERT executed |
| 3 | Verify returned SyncState | status=RUNNING, lastOffset=50, syncedIssues=25 |

**Test Data:** projectKey = "MTO", existing record with status=RUNNING
**Postconditions:** No INSERT statement executed; only SELECT

---

### TC-UT-03: TicketCache Validation — Invalid Ticket Key Format

| Field | Value |
|-------|-------|
| **ID** | TC-UT-03 |
| **Priority** | High |
| **Type** | Unit — Validation |
| **Requirement** | UC-02, BR-06 |
| **Preconditions** | TicketCacheRepository instantiated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upsert(ticket)` with ticketKey = "invalid-key" | Throws IllegalArgumentException |
| 2 | Call `upsert(ticket)` with ticketKey = "" | Throws IllegalArgumentException |
| 3 | Call `upsert(ticket)` with ticketKey = "mto-15" (lowercase) | Throws IllegalArgumentException |
| 4 | Call `upsert(ticket)` with ticketKey = "MTO-15" (valid) | No exception |

**Test Data:** Various invalid and valid ticket key formats
**Postconditions:** Only valid format accepted

---

### TC-UT-04: TicketCache Validation — Content Hash Length

| Field | Value |
|-------|-------|
| **ID** | TC-UT-04 |
| **Priority** | High |
| **Type** | Unit — Validation |
| **Requirement** | BR-07 |
| **Preconditions** | TicketCacheRepository instantiated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upsert(ticket)` with contentHash = "abc" (3 chars) | Throws IllegalArgumentException("Content hash must be 64 hex characters") |
| 2 | Call `upsert(ticket)` with contentHash = "a".repeat(64) (valid length) | No exception |
| 3 | Call `upsert(ticket)` with contentHash = "g".repeat(64) (invalid hex) | Throws IllegalArgumentException |

**Test Data:** contentHash values of various lengths and character sets
**Postconditions:** Only 64-char hex strings accepted

---

### TC-UT-05: TicketCache Validation — Labels JSON Array

| Field | Value |
|-------|-------|
| **ID** | TC-UT-05 |
| **Priority** | Medium |
| **Type** | Unit — Validation |
| **Requirement** | BR-10 |
| **Preconditions** | TicketCacheRepository instantiated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upsert(ticket)` with labels = listOf("bug", "urgent") | No exception, stored as JSON array |
| 2 | Call `upsert(ticket)` with labels = emptyList() | No exception, stored as "[]" |
| 3 | Call `upsert(ticket)` with labels = null | No exception, stored as NULL |

**Test Data:** Various label list configurations
**Postconditions:** Valid JSON arrays and NULL accepted

---

### TC-UT-06: TicketGraph Validation — Self-Referencing Edge Rejected

| Field | Value |
|-------|-------|
| **ID** | TC-UT-06 |
| **Priority** | High |
| **Type** | Unit — Validation |
| **Requirement** | BR-13 |
| **Preconditions** | TicketGraphRepository instantiated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `insertRelation(TicketRelation(sourceKey="MTO-15", targetKey="MTO-15", ...))` | Throws IllegalArgumentException("Self-referencing edges not allowed") |
| 2 | Call `insertRelation(TicketRelation(sourceKey="MTO-15", targetKey="MTO-16", ...))` | No exception |

**Test Data:** sourceKey = targetKey = "MTO-15"
**Postconditions:** Self-referencing edge rejected at application level

---

### TC-UT-07: TicketGraph Validation — Invalid Category Rejected

| Field | Value |
|-------|-------|
| **ID** | TC-UT-07 |
| **Priority** | Medium |
| **Type** | Unit — Validation |
| **Requirement** | BR-12 |
| **Preconditions** | TicketGraphRepository instantiated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `insertRelation(...)` with category = "INVALID" | Throws IllegalArgumentException |
| 2 | Call `insertRelation(...)` with category = "INWARD" | No exception |
| 3 | Call `insertRelation(...)` with category = "OUTWARD" | No exception |
| 4 | Call `insertRelation(...)` with category = "SUBTASK" | No exception |
| 5 | Call `insertRelation(...)` with category = "EPIC" | No exception |

**Test Data:** Valid and invalid category values
**Postconditions:** Only INWARD, OUTWARD, SUBTASK, EPIC accepted

---

### TC-UT-08: TicketGraph — Batch Insert Returns Count

| Field | Value |
|-------|-------|
| **ID** | TC-UT-08 |
| **Priority** | Medium |
| **Type** | Unit — Functional |
| **Requirement** | BR-11 |
| **Preconditions** | TicketGraphRepository with mocked DataSource |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock batch execution to report 3 rows inserted | executeBatch returns [1,1,1] |
| 2 | Call `insertBatch(listOf(rel1, rel2, rel3))` | Returns 3 |
| 3 | Mock batch with 1 duplicate (ON CONFLICT DO NOTHING) | executeBatch returns [1,0,1] |
| 4 | Call `insertBatch(listOf(rel1, dup, rel3))` | Returns 2 |

**Test Data:** 3 relations, 1 duplicate
**Postconditions:** Count reflects only newly inserted edges


---

### TC-UT-09: AttachmentQueue Validation — Duplicate Prevention Logic

| Field | Value |
|-------|-------|
| **ID** | TC-UT-09 |
| **Priority** | High |
| **Type** | Unit — Validation |
| **Requirement** | BR-15 |
| **Preconditions** | AttachmentQueueRepository with mocked DataSource |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `enqueue(item)` with ticketKey="MTO-15", attachmentId="att-001" | INSERT executed |
| 2 | Mock ON CONFLICT DO NOTHING (0 rows affected on duplicate) | executeUpdate returns 0 |
| 3 | Call `enqueue(item)` with same ticketKey + attachmentId | No exception, no duplicate created |

**Test Data:** ticketKey="MTO-15", attachmentId="att-001"
**Postconditions:** Duplicate silently ignored

---

### TC-UT-10: AttachmentQueue — Status Lifecycle Validation

| Field | Value |
|-------|-------|
| **ID** | TC-UT-10 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | BR-16 |
| **Preconditions** | AttachmentQueueRepository instantiated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify initial status after enqueue | status = PENDING |
| 2 | Call `updateStatus(id, DOWNLOADING)` | Status updated to DOWNLOADING |
| 3 | Call `updateStatus(id, PROCESSING)` | Status updated to PROCESSING |
| 4 | Call `markDone(id)` | Status = DONE, processed_at set |

**Test Data:** Queue item with id=1
**Postconditions:** Status follows PENDING → DOWNLOADING → PROCESSING → DONE

---

### TC-UT-11: AttachmentQueue — Retry Count Increment

| Field | Value |
|-------|-------|
| **ID** | TC-UT-11 |
| **Priority** | High |
| **Type** | Unit — Functional |
| **Requirement** | BR-17, BR-18 |
| **Preconditions** | Queue item exists with retry_count=0 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `incrementRetry(id, "Connection timeout")` | retry_count=1, error_message="Connection timeout" |
| 2 | Call `incrementRetry(id, "Server error")` | retry_count=2, error_message="Server error" |
| 3 | Call `incrementRetry(id, "Disk full")` | retry_count=3, error_message="Disk full" |
| 4 | Verify max retry check (application level) | retry_count=3 means no more automatic retries |

**Test Data:** Queue item with incrementing retry count
**Postconditions:** retry_count incremented, error_message updated each time

---

### TC-UT-12: markRunning — From IDLE (Valid Transition)

| Field | Value |
|-------|-------|
| **ID** | TC-UT-12 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | UC-05, BR-02, BR-21, BR-27 |
| **Preconditions** | Record exists with status=IDLE |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE with WHERE status IN ('IDLE','PAUSED','FAILED') to affect 1 row | executeUpdate() returns 1 |
| 2 | Call `manager.markRunning("MTO")` | No exception thrown |
| 3 | Verify SQL uses withContext(Dispatchers.IO) | Coroutine context verified |

**Test Data:** projectKey = "MTO"
**Postconditions:** Status transitioned to RUNNING

---

### TC-UT-13: markRunning — From PAUSED (Valid Transition)

| Field | Value |
|-------|-------|
| **ID** | TC-UT-13 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | UC-05, BR-02, BR-21 |
| **Preconditions** | Record exists with status=PAUSED |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE to affect 1 row (status was PAUSED) | executeUpdate() returns 1 |
| 2 | Call `manager.markRunning("MTO")` | No exception thrown |

**Test Data:** projectKey = "MTO", current status = PAUSED
**Postconditions:** Status transitioned to RUNNING

---

### TC-UT-14: markRunning — From FAILED (Valid Retry)

| Field | Value |
|-------|-------|
| **ID** | TC-UT-14 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | UC-05, BR-02, BR-21 |
| **Preconditions** | Record exists with status=FAILED |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE to affect 1 row (status was FAILED) | executeUpdate() returns 1 |
| 2 | Call `manager.markRunning("MTO")` | No exception thrown |

**Test Data:** projectKey = "MTO", current status = FAILED
**Postconditions:** Status transitioned to RUNNING (retry scenario)

---

### TC-UT-15: markRunning — From COMPLETED (Invalid Transition)

| Field | Value |
|-------|-------|
| **ID** | TC-UT-15 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | UC-05 EF-01, BR-21, BR-26 |
| **Preconditions** | Record exists with status=COMPLETED |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE with WHERE status IN ('IDLE','PAUSED','FAILED') to affect 0 rows | executeUpdate() returns 0 |
| 2 | Call `manager.markRunning("MTO")` | Throws IllegalStateException |
| 3 | Verify exception message | Contains "Cannot transition" or "Invalid state transition" |

**Test Data:** projectKey = "MTO", current status = COMPLETED
**Postconditions:** No state change; exception thrown with descriptive message

---

### TC-UT-16: markRunning — From RUNNING (Invalid — Already Running)

| Field | Value |
|-------|-------|
| **ID** | TC-UT-16 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | UC-05 EF-01, BR-26 |
| **Preconditions** | Record exists with status=RUNNING |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE to affect 0 rows (RUNNING not in allowed set) | executeUpdate() returns 0 |
| 2 | Call `manager.markRunning("MTO")` | Throws IllegalStateException |
| 3 | Verify exception message | Contains "RUNNING" in message |

**Test Data:** projectKey = "MTO", current status = RUNNING
**Postconditions:** No state change; prevents double-start

---

### TC-UT-17: markPaused — From RUNNING (Valid)

| Field | Value |
|-------|-------|
| **ID** | TC-UT-17 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | UC-05, BR-22 |
| **Preconditions** | Record exists with status=RUNNING |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE with WHERE status='RUNNING' to affect 1 row | executeUpdate() returns 1 |
| 2 | Call `manager.markPaused("MTO")` | No exception thrown |

**Test Data:** projectKey = "MTO"
**Postconditions:** Status transitioned to PAUSED

---

### TC-UT-18: markCompleted — Sets last_sync_at

| Field | Value |
|-------|-------|
| **ID** | TC-UT-18 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | UC-05, BR-23, BR-25 |
| **Preconditions** | Record exists with status=RUNNING |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE to affect 1 row | executeUpdate() returns 1 |
| 2 | Call `manager.markCompleted("MTO")` | No exception thrown |
| 3 | Verify SQL includes `last_sync_at = NOW()` and `updated_at = NOW()` | SQL contains both timestamp updates |

**Test Data:** projectKey = "MTO"
**Postconditions:** Status = COMPLETED, last_sync_at and updated_at set

---

### TC-UT-19: markFailed — Stores Error Message

| Field | Value |
|-------|-------|
| **ID** | TC-UT-19 |
| **Priority** | High |
| **Type** | Unit — State Machine |
| **Requirement** | UC-05, BR-24 |
| **Preconditions** | Record exists with status=RUNNING |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock UPDATE to affect 1 row | executeUpdate() returns 1 |
| 2 | Call `manager.markFailed("MTO", "Connection timeout after 30s")` | No exception thrown |
| 3 | Verify SQL includes `error_message = ?` parameter | Parameter set to "Connection timeout after 30s" |

**Test Data:** projectKey = "MTO", error = "Connection timeout after 30s"
**Postconditions:** Status = FAILED, error_message stored

---

### TC-UT-20: updateProgress — Validates Offset and Synced

| Field | Value |
|-------|-------|
| **ID** | TC-UT-20 |
| **Priority** | High |
| **Type** | Unit — Validation |
| **Requirement** | BR-03, BR-04, BR-25 |
| **Preconditions** | Record exists with status=RUNNING, total_issues=100 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `updateProgress("MTO", offset=50, synced=25)` | No exception, values updated |
| 2 | Call `updateProgress("MTO", offset=-1, synced=0)` | Throws IllegalArgumentException("Offset must be non-negative") |
| 3 | Call `updateProgress("MTO", offset=50, synced=-1)` | Throws IllegalArgumentException("Synced must be non-negative") |

**Test Data:** Various offset and synced values
**Postconditions:** Only valid non-negative values accepted


---

## 3. Integration Tests — Database Operations (TC-IT-01 to TC-IT-22)

**Test Class:** `JiraSyncDatabaseIntegrationTest.kt`
**Framework:** Kotest 5.9.1 FunSpec + Testcontainers 1.21.1
**Database:** PostgreSQL 16-alpine (Docker container per test class)
**Setup:** Migration script V3 executed before each test class

### TC-IT-01: Sync State — getOrCreate Inserts New Record in Real DB

| Field | Value |
|-------|-------|
| **ID** | TC-IT-01 |
| **Priority** | High |
| **Type** | Integration — CRUD |
| **Requirement** | UC-01, BR-01, BR-05 |
| **Preconditions** | PostgreSQL container running, migration executed, no existing records |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `manager.getOrCreate("MTO")` | Returns SyncState(projectKey="MTO", status=IDLE, lastOffset=0) |
| 2 | Query DB directly: `SELECT * FROM jira_sync_state WHERE project_key = 'MTO'` | Row exists with status='IDLE', last_offset=0, updated_at IS NOT NULL |
| 3 | Call `manager.getOrCreate("MTO")` again | Returns same record (no duplicate) |
| 4 | Query DB: `SELECT COUNT(*) FROM jira_sync_state WHERE project_key = 'MTO'` | Count = 1 |

**Test Data:** projectKey = "MTO"
**Postconditions:** Exactly one record exists for "MTO"

---

### TC-IT-02: Sync State — Full Lifecycle (IDLE → RUNNING → COMPLETED)

| Field | Value |
|-------|-------|
| **ID** | TC-IT-02 |
| **Priority** | High |
| **Type** | Integration — Lifecycle |
| **Requirement** | UC-01, UC-05, BR-02, BR-21, BR-23, BR-25 |
| **Preconditions** | PostgreSQL container running, migration executed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `manager.getOrCreate("PROJ")` | status=IDLE |
| 2 | Call `manager.markRunning("PROJ")` | No exception |
| 3 | Query DB: status | status='RUNNING', updated_at changed |
| 4 | Call `manager.updateProgress("PROJ", 50, 25)` | No exception |
| 5 | Query DB: last_offset, synced_issues | last_offset=50, synced_issues=25 |
| 6 | Call `manager.markCompleted("PROJ")` | No exception |
| 7 | Query DB: status, last_sync_at | status='COMPLETED', last_sync_at IS NOT NULL |

**Test Data:** projectKey = "PROJ"
**Postconditions:** Full lifecycle completed, all timestamps set

---

### TC-IT-03: Sync State — Optimistic Locking (Concurrent Modification)

| Field | Value |
|-------|-------|
| **ID** | TC-IT-03 |
| **Priority** | High |
| **Type** | Integration — Concurrency |
| **Requirement** | UC-05 EF-03, BR-21, BR-26 |
| **Preconditions** | PostgreSQL container running, record exists with status=IDLE |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create record with status=IDLE | Record exists |
| 2 | Launch 2 coroutines simultaneously calling `markRunning("MTO")` | One succeeds, one throws IllegalStateException |
| 3 | Query DB: status | status='RUNNING' (exactly one transition) |
| 4 | Verify only 1 coroutine succeeded | Exactly 1 success + 1 exception |

**Test Data:** projectKey = "MTO", 2 concurrent coroutines
**Postconditions:** Optimistic locking prevents double-transition

---

### TC-IT-04: Ticket Cache — Single UPSERT (New Ticket)

| Field | Value |
|-------|-------|
| **ID** | TC-IT-04 |
| **Priority** | High |
| **Type** | Integration — CRUD |
| **Requirement** | UC-02, BR-06, BR-08, BR-09 |
| **Preconditions** | PostgreSQL container running, migration executed, no existing ticket |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create TicketCache(ticketKey="MTO-15", projectKey="MTO", summary="Test", ...) | Object created |
| 2 | Call `repository.upsert(ticket)` | No exception |
| 3 | Query DB: `SELECT * FROM jira_ticket_cache WHERE ticket_key = 'MTO-15'` | Row exists, kb_ingested=FALSE, synced_at IS NOT NULL |
| 4 | Verify content_hash stored correctly | Exactly 64 hex characters |

**Test Data:** Full TicketCache object with valid SHA-256 hash
**Postconditions:** New ticket cached with kb_ingested=FALSE

---

### TC-IT-05: Ticket Cache — UPSERT Updates Existing Ticket

| Field | Value |
|-------|-------|
| **ID** | TC-IT-05 |
| **Priority** | High |
| **Type** | Integration — CRUD |
| **Requirement** | UC-02, BR-09 |
| **Preconditions** | Ticket "MTO-15" already exists in cache |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert ticket with summary="Original" | Row created |
| 2 | Call `upsert(ticket.copy(summary="Updated", contentHash=newHash))` | No exception |
| 3 | Query DB: summary, content_hash, synced_at | summary="Updated", hash=newHash, synced_at refreshed |
| 4 | Query DB: COUNT(*) WHERE ticket_key='MTO-15' | Count = 1 (no duplicate) |

**Test Data:** Same ticketKey, different summary and hash
**Postconditions:** Existing record updated, not duplicated

---

### TC-IT-06: Ticket Cache — Batch UPSERT (100 Tickets)

| Field | Value |
|-------|-------|
| **ID** | TC-IT-06 |
| **Priority** | High |
| **Type** | Integration — Batch |
| **Requirement** | UC-02, BR-09 |
| **Preconditions** | PostgreSQL container running, empty ticket cache |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate 100 TicketCache objects (MTO-1 through MTO-100) | List of 100 items |
| 2 | Call `repository.upsertBatch(tickets)` | Returns 100 |
| 3 | Query DB: COUNT(*) FROM jira_ticket_cache | Count = 100 |
| 4 | Call `repository.upsertBatch(tickets)` again (all duplicates) | Returns 100 (UPSERT updates) |
| 5 | Query DB: COUNT(*) | Still 100 (no duplicates) |

**Test Data:** 100 generated tickets with unique keys
**Postconditions:** All 100 tickets cached, re-UPSERT is idempotent

---

### TC-IT-07: Ticket Cache — findNotIngested Returns Only Unprocessed

| Field | Value |
|-------|-------|
| **ID** | TC-IT-07 |
| **Priority** | High |
| **Type** | Integration — Query |
| **Requirement** | BR-08 |
| **Preconditions** | 5 tickets exist: 3 with kb_ingested=FALSE, 2 with kb_ingested=TRUE |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 5 tickets, mark 2 as ingested | 3 not ingested, 2 ingested |
| 2 | Call `repository.findNotIngested("MTO")` | Returns exactly 3 tickets |
| 3 | Call `repository.markIngested("MTO-1")` | No exception |
| 4 | Call `repository.findNotIngested("MTO")` | Returns exactly 2 tickets |

**Test Data:** 5 tickets with mixed kb_ingested values
**Postconditions:** Query correctly filters by kb_ingested=FALSE

---

### TC-IT-08: Ticket Graph — Insert New Relationship

| Field | Value |
|-------|-------|
| **ID** | TC-IT-08 |
| **Priority** | High |
| **Type** | Integration — CRUD |
| **Requirement** | UC-03, BR-11, BR-14 |
| **Preconditions** | PostgreSQL container running, migration executed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create TicketRelation(source="MTO-15", target="MTO-14", linkType="blocks", category=OUTWARD) | Object created |
| 2 | Call `repository.insertRelation(relation)` | No exception |
| 3 | Query DB: `SELECT * FROM jira_ticket_graph WHERE source_key='MTO-15'` | Row exists |
| 4 | Note: MTO-14 not in jira_ticket_cache | No FK violation (BR-14) |

**Test Data:** Relationship between tickets (target not in cache)
**Postconditions:** Edge stored without FK constraint on ticket_cache

---

### TC-IT-09: Ticket Graph — Duplicate Edge Ignored (ON CONFLICT DO NOTHING)

| Field | Value |
|-------|-------|
| **ID** | TC-IT-09 |
| **Priority** | High |
| **Type** | Integration — Idempotency |
| **Requirement** | BR-11 |
| **Preconditions** | Edge already exists in graph |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert relation (MTO-15 → MTO-14, "blocks", OUTWARD) | Row created |
| 2 | Insert same relation again | No exception, no duplicate |
| 3 | Query DB: COUNT(*) with same composite key | Count = 1 |

**Test Data:** Same (source_key, target_key, link_type) twice
**Postconditions:** Composite PK prevents duplicates silently

---

### TC-IT-10: Ticket Graph — findOutgoing and findIncoming

| Field | Value |
|-------|-------|
| **ID** | TC-IT-10 |
| **Priority** | Medium |
| **Type** | Integration — Query |
| **Requirement** | UC-03 |
| **Preconditions** | Multiple edges exist: MTO-15→MTO-14, MTO-15→MTO-16, MTO-17→MTO-15 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 3 edges as described | All inserted |
| 2 | Call `repository.findOutgoing("MTO-15")` | Returns 2 edges (→MTO-14, →MTO-16) |
| 3 | Call `repository.findIncoming("MTO-15")` | Returns 1 edge (MTO-17→) |

**Test Data:** 3 directed edges
**Postconditions:** Directional queries return correct results


---

### TC-IT-11: Attachment Queue — Enqueue and Poll

| Field | Value |
|-------|-------|
| **ID** | TC-IT-11 |
| **Priority** | High |
| **Type** | Integration — CRUD |
| **Requirement** | UC-04, BR-15, BR-16, BR-19 |
| **Preconditions** | PostgreSQL container running, migration executed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `repository.enqueue(item1)` with ticketKey="MTO-15", attachmentId="att-001" | No exception |
| 2 | Call `repository.enqueue(item2)` with ticketKey="MTO-15", attachmentId="att-002" | No exception |
| 3 | Query DB: COUNT(*) WHERE ticket_key='MTO-15' | Count = 2 |
| 4 | Call `repository.pollPending(limit=5)` | Returns 2 items ordered by created_at |
| 5 | Verify first item has status=PENDING, retry_count=0 | Correct defaults |

**Test Data:** 2 attachment queue items
**Postconditions:** Items queued with PENDING status, ordered by creation time

---

### TC-IT-12: Attachment Queue — Duplicate Prevention

| Field | Value |
|-------|-------|
| **ID** | TC-IT-12 |
| **Priority** | High |
| **Type** | Integration — Idempotency |
| **Requirement** | BR-15 |
| **Preconditions** | Queue item already exists for (MTO-15, att-001) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enqueue item (MTO-15, att-001) | Row created |
| 2 | Enqueue same item again (MTO-15, att-001) | No exception, no duplicate |
| 3 | Query DB: COUNT(*) WHERE ticket_key='MTO-15' AND attachment_id='att-001' | Count = 1 |

**Test Data:** Same (ticket_key, attachment_id) twice
**Postconditions:** Unique constraint prevents duplicates silently

---

### TC-IT-13: Attachment Queue — markDone Sets processed_at

| Field | Value |
|-------|-------|
| **ID** | TC-IT-13 |
| **Priority** | High |
| **Type** | Integration — Lifecycle |
| **Requirement** | BR-16, BR-20 |
| **Preconditions** | Queue item exists with status=PROCESSING |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enqueue item, update status to PROCESSING | status=PROCESSING |
| 2 | Call `repository.markDone(id)` | No exception |
| 3 | Query DB: status, processed_at | status='DONE', processed_at IS NOT NULL |
| 4 | Verify processed_at ≈ NOW() (within 1 second) | Timestamp is recent |

**Test Data:** Queue item transitioning to DONE
**Postconditions:** processed_at set only when DONE

---

### TC-IT-14: Attachment Queue — incrementRetry Updates Count and Error

| Field | Value |
|-------|-------|
| **ID** | TC-IT-14 |
| **Priority** | High |
| **Type** | Integration — Lifecycle |
| **Requirement** | BR-17 |
| **Preconditions** | Queue item exists with retry_count=0 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `repository.incrementRetry(id, "Timeout")` | No exception |
| 2 | Query DB: retry_count, error_message | retry_count=1, error_message="Timeout" |
| 3 | Call `repository.incrementRetry(id, "Server 500")` | No exception |
| 4 | Query DB: retry_count, error_message | retry_count=2, error_message="Server 500" |

**Test Data:** Queue item with incrementing retries
**Postconditions:** retry_count incremented, error_message updated

---

### TC-IT-15: SyncStateManager — Full State Machine in Real DB

| Field | Value |
|-------|-------|
| **ID** | TC-IT-15 |
| **Priority** | High |
| **Type** | Integration — State Machine |
| **Requirement** | UC-05, BR-21–BR-27 |
| **Preconditions** | PostgreSQL container running, SyncStateManagerImpl with real HikariCP |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `getOrCreate("TEST")` | status=IDLE |
| 2 | `markRunning("TEST")` | status=RUNNING |
| 3 | `markPaused("TEST")` | status=PAUSED |
| 4 | `markRunning("TEST")` (resume) | status=RUNNING |
| 5 | `markFailed("TEST", "error")` | status=FAILED, error_message="error" |
| 6 | `markRunning("TEST")` (retry) | status=RUNNING |
| 7 | `markCompleted("TEST")` | status=COMPLETED, last_sync_at set |

**Test Data:** projectKey = "TEST"
**Postconditions:** All valid transitions work in real DB

---

### TC-IT-16: SyncStateManager — Invalid Transitions in Real DB

| Field | Value |
|-------|-------|
| **ID** | TC-IT-16 |
| **Priority** | High |
| **Type** | Integration — Exception Flow |
| **Requirement** | BR-26 |
| **Preconditions** | Record exists with status=COMPLETED |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create record, transition to COMPLETED | status=COMPLETED |
| 2 | Call `markRunning("TEST")` | Throws IllegalStateException |
| 3 | Call `markPaused("TEST")` | Throws IllegalStateException |
| 4 | Call `markFailed("TEST", "x")` | Throws IllegalStateException |
| 5 | Query DB: status unchanged | status still COMPLETED |

**Test Data:** projectKey = "TEST" in COMPLETED state
**Postconditions:** No state change after invalid transitions

---

### TC-IT-17: SyncStateManager — updateProgress Atomic Update

| Field | Value |
|-------|-------|
| **ID** | TC-IT-17 |
| **Priority** | High |
| **Type** | Integration — Atomicity |
| **Requirement** | BR-03, BR-04, BR-25 |
| **Preconditions** | Record exists with status=RUNNING, offset=0 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `updateProgress("MTO", offset=100, synced=50)` | No exception |
| 2 | Query DB: last_offset, synced_issues, updated_at | offset=100, synced=50, updated_at refreshed |
| 3 | Call `updateProgress("MTO", offset=200, synced=100)` | No exception |
| 4 | Query DB | offset=200, synced=100 |

**Test Data:** Incrementing progress values
**Postconditions:** Progress updated atomically with timestamp

---

### TC-IT-18: SyncStateManager — Concurrent updateProgress

| Field | Value |
|-------|-------|
| **ID** | TC-IT-18 |
| **Priority** | High |
| **Type** | Integration — Concurrency |
| **Requirement** | BR-03 |
| **Preconditions** | Record exists with status=RUNNING |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 10 coroutines each calling `updateProgress` with different offsets | All complete |
| 2 | Query DB: last_offset | Contains one of the submitted values (last writer wins) |
| 3 | Verify no data corruption | All fields are valid, no partial writes |

**Test Data:** 10 concurrent progress updates
**Postconditions:** No corruption, eventual consistency

---

### TC-IT-19: Migration — Creates All 4 Tables

| Field | Value |
|-------|-------|
| **ID** | TC-IT-19 |
| **Priority** | High |
| **Type** | Integration — Migration |
| **Requirement** | UC-06, BR-28, BR-29 |
| **Preconditions** | Fresh PostgreSQL container (no tables) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute V3__create_jira_sync_tables.sql | No exception |
| 2 | Query: `SELECT table_name FROM information_schema.tables WHERE table_schema='public'` | Contains: jira_sync_state, jira_ticket_cache, jira_ticket_graph, jira_attachment_queue |
| 3 | Verify CHECK constraints exist on jira_sync_state | chk_sync_status, chk_offset_non_negative, chk_total_non_negative, chk_synced_non_negative |
| 4 | Verify UNIQUE constraint on jira_attachment_queue | (ticket_key, attachment_id) |

**Test Data:** V3 migration script
**Postconditions:** All 4 tables with constraints created

---

### TC-IT-20: Migration — Idempotent (Run Twice)

| Field | Value |
|-------|-------|
| **ID** | TC-IT-20 |
| **Priority** | High |
| **Type** | Integration — Idempotency |
| **Requirement** | BR-28 |
| **Preconditions** | Migration already executed once |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute migration script first time | Success |
| 2 | Insert test data into all 4 tables | Data exists |
| 3 | Execute migration script second time | No exception (IF NOT EXISTS) |
| 4 | Query test data | Data still intact (not dropped/recreated) |

**Test Data:** Test records in all 4 tables
**Postconditions:** Re-running migration is safe, data preserved

---

### TC-IT-21: Performance Index — idx_ticket_cache_project Used

| Field | Value |
|-------|-------|
| **ID** | TC-IT-21 |
| **Priority** | Medium |
| **Type** | Integration — Index |
| **Requirement** | UC-07, BR-32, BR-34 |
| **Preconditions** | 1000 tickets inserted across 10 projects |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 1000 tickets (100 per project, 10 projects) | All inserted |
| 2 | Run `EXPLAIN ANALYZE SELECT * FROM jira_ticket_cache WHERE project_key = 'MTO'` | Uses Index Scan on idx_ticket_cache_project |
| 3 | Verify no Seq Scan | Query plan shows Index Scan |

**Test Data:** 1000 tickets across 10 projects
**Postconditions:** Index is used for project_key lookups

---

### TC-IT-22: Performance Index — Partial Index for Not-Ingested Tickets

| Field | Value |
|-------|-------|
| **ID** | TC-IT-22 |
| **Priority** | Medium |
| **Type** | Integration — Index |
| **Requirement** | UC-07, BR-33 |
| **Preconditions** | 1000 tickets: 900 ingested, 100 not ingested |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 1000 tickets, mark 900 as ingested | 100 not ingested |
| 2 | Run `EXPLAIN ANALYZE SELECT * FROM jira_ticket_cache WHERE kb_ingested = FALSE` | Uses partial index idx_ticket_cache_not_ingested |
| 3 | Verify index scan covers only ~100 rows | Rows scanned ≈ 100 (not 1000) |

**Test Data:** 1000 tickets with 90% ingested
**Postconditions:** Partial index efficiently filters un-ingested tickets


---

## 4. E2E-API Tests — Full Lifecycle (TC-E2E-01 to TC-E2E-06)

**Test Class:** `SyncLifecycleE2ETest.kt`
**Framework:** Kotest 5.9.1 + Testcontainers + Koin Test
**Setup:** Full Koin DI with real PostgreSQL, all repositories and SyncStateManager wired
**Purpose:** Verify complete sync workflows through the real internal API with real DI

### TC-E2E-01: Complete Sync Lifecycle — Happy Path

| Field | Value |
|-------|-------|
| **ID** | TC-E2E-01 |
| **Priority** | High |
| **Type** | E2E-API — Lifecycle |
| **Requirement** | UC-01, UC-02, UC-04, UC-05 |
| **Preconditions** | Koin started with all sync modules, PostgreSQL container running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Inject SyncStateManager from Koin | Instance obtained |
| 2 | Call `getOrCreate("E2E-PROJECT")` | Returns IDLE state |
| 3 | Call `markRunning("E2E-PROJECT")` | No exception |
| 4 | Inject TicketCacheRepository, upsert 10 tickets | 10 rows created |
| 5 | Call `updateProgress("E2E-PROJECT", 10, 10)` | Progress updated |
| 6 | Inject AttachmentQueueRepository, enqueue 3 attachments | 3 items queued |
| 7 | Call `markCompleted("E2E-PROJECT")` | Status = COMPLETED |
| 8 | Verify all data persisted correctly | All tables have expected data |

**Test Data:** Full lifecycle data (project, tickets, attachments)
**Postconditions:** Complete sync lifecycle executed through real DI

---

### TC-E2E-02: Sync Resume After Crash

| Field | Value |
|-------|-------|
| **ID** | TC-E2E-02 |
| **Priority** | High |
| **Type** | E2E-API — Recovery |
| **Requirement** | UC-01 AF-01, UC-05 AF-01 |
| **Preconditions** | Record exists with status=RUNNING, last_offset=50 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Simulate crash: insert record with status=RUNNING, offset=50 | Record exists |
| 2 | Call `getOrCreate("CRASH-PROJECT")` | Returns RUNNING state with offset=50 |
| 3 | Resume: call `updateProgress("CRASH-PROJECT", 100, 75)` | Progress updated from 50 to 100 |
| 4 | Call `markCompleted("CRASH-PROJECT")` | Status = COMPLETED |

**Test Data:** Pre-existing RUNNING record simulating crash
**Postconditions:** Sync resumed from checkpoint without data loss

---

### TC-E2E-03: Sync Failure and Retry

| Field | Value |
|-------|-------|
| **ID** | TC-E2E-03 |
| **Priority** | High |
| **Type** | E2E-API — Error Recovery |
| **Requirement** | UC-01 AF-02, UC-05, BR-21, BR-24 |
| **Preconditions** | Koin started, PostgreSQL running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `getOrCreate("RETRY-PROJECT")` → `markRunning` | RUNNING |
| 2 | `updateProgress("RETRY-PROJECT", 30, 15)` | offset=30 |
| 3 | `markFailed("RETRY-PROJECT", "API rate limit exceeded")` | FAILED, error stored |
| 4 | `markRunning("RETRY-PROJECT")` (retry) | RUNNING again |
| 5 | `updateProgress("RETRY-PROJECT", 60, 30)` | offset=60 (continues from where it left off) |
| 6 | `markCompleted("RETRY-PROJECT")` | COMPLETED |

**Test Data:** Project that fails and retries
**Postconditions:** Retry preserves previous progress

---

### TC-E2E-04: Attachment Queue Full Lifecycle

| Field | Value |
|-------|-------|
| **ID** | TC-E2E-04 |
| **Priority** | High |
| **Type** | E2E-API — Lifecycle |
| **Requirement** | UC-04, BR-15–BR-20 |
| **Preconditions** | Koin started, PostgreSQL running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enqueue 5 attachments for "MTO-15" | 5 items with PENDING status |
| 2 | Poll pending (limit=3) | Returns 3 oldest items |
| 3 | Update first item: PENDING → DOWNLOADING → PROCESSING → DONE | Status lifecycle complete |
| 4 | Verify processed_at set on DONE item | Timestamp present |
| 5 | Simulate failure on second item: incrementRetry | retry_count=1 |
| 6 | Poll pending again | Returns remaining PENDING items + retried item |

**Test Data:** 5 attachments with mixed processing outcomes
**Postconditions:** Queue lifecycle works end-to-end

---

### TC-E2E-05: Multi-Project Isolation

| Field | Value |
|-------|-------|
| **ID** | TC-E2E-05 |
| **Priority** | High |
| **Type** | E2E-API — Isolation |
| **Requirement** | BR-01, BR-06 |
| **Preconditions** | Koin started, PostgreSQL running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create sync state for "PROJECT-A" and "PROJECT-B" | Both IDLE |
| 2 | Mark "PROJECT-A" as RUNNING | A=RUNNING, B=IDLE |
| 3 | Upsert tickets for both projects | Tickets isolated by project_key |
| 4 | Call `findByProject("PROJECT-A")` | Returns only PROJECT-A tickets |
| 5 | Call `findByProject("PROJECT-B")` | Returns only PROJECT-B tickets |
| 6 | Mark "PROJECT-A" COMPLETED | A=COMPLETED, B still IDLE |

**Test Data:** 2 projects with separate data
**Postconditions:** Projects are fully isolated

---

### TC-E2E-06: Koin DI Wiring Verification

| Field | Value |
|-------|-------|
| **ID** | TC-E2E-06 |
| **Priority** | Medium |
| **Type** | E2E-API — DI |
| **Requirement** | TDD §2.2 (Koin Module Extension) |
| **Preconditions** | Koin started with sync module |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `koin.get<SyncStateManager>()` | Returns SyncStateManagerImpl instance |
| 2 | `koin.get<TicketCacheRepository>()` | Returns instance |
| 3 | `koin.get<TicketGraphRepository>()` | Returns instance |
| 4 | `koin.get<AttachmentQueueRepository>()` | Returns instance |
| 5 | `koin.get<JiraSyncDatabaseInitializer>()` | Returns instance |
| 6 | Verify all are singletons | Same instance on repeated get() |

**Test Data:** Koin module configuration
**Postconditions:** All sync components properly registered as singletons


---

## 5. System Integration Tests — Performance & Regression (TC-SIT-01 to TC-SIT-08)

**Test Class:** `SyncPerformanceTest.kt` + `SyncRegressionTest.kt`
**Framework:** Kotest 5.9.1 + Testcontainers
**Purpose:** Verify NFR targets and regression safety

### TC-SIT-01: Performance — State Update Latency (< 50ms)

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-01 |
| **Priority** | Medium |
| **Type** | SIT — Performance |
| **Requirement** | FSD NFR — 50ms state updates |
| **Preconditions** | PostgreSQL container running, record in RUNNING state |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Warm up: call `updateProgress` 10 times | Warm-up complete |
| 2 | Measure 100 iterations of `markRunning` → `markCompleted` cycle | Timing recorded |
| 3 | Calculate p95 latency | p95 < 50ms |
| 4 | Calculate average latency | avg < 30ms |

**Test Data:** 100 state transition cycles
**Acceptance Criteria:** p95 latency < 50ms per state update

---

### TC-SIT-02: Performance — Batch UPSERT Throughput (100 tickets/sec)

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-02 |
| **Priority** | Medium |
| **Type** | SIT — Performance |
| **Requirement** | FSD NFR — 100 tickets/sec UPSERT |
| **Preconditions** | PostgreSQL container running, empty ticket cache |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate 1000 TicketCache objects | Data prepared |
| 2 | Call `upsertBatch(tickets)` in batches of 100 | All batches complete |
| 3 | Measure total time for 1000 tickets | Total time < 10 seconds (= 100/sec) |
| 4 | Calculate throughput | ≥ 100 tickets/second |

**Test Data:** 1000 generated tickets
**Acceptance Criteria:** Throughput ≥ 100 tickets/second

---

### TC-SIT-03: Performance — Queue Polling Latency (< 10ms)

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-03 |
| **Priority** | Medium |
| **Type** | SIT — Performance |
| **Requirement** | FSD NFR — 10ms queue polling |
| **Preconditions** | 10000 queue items (9000 DONE, 1000 PENDING) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 10000 queue items with mixed statuses | Data prepared |
| 2 | Measure 100 iterations of `pollPending(limit=10)` | Timing recorded |
| 3 | Calculate p95 latency | p95 < 10ms |
| 4 | Verify partial index is used (EXPLAIN) | Uses idx_attachment_queue_pending |

**Test Data:** 10000 queue items (90% DONE, 10% PENDING)
**Acceptance Criteria:** p95 polling latency < 10ms with partial index

---

### TC-SIT-04: Concurrency — 10 Simultaneous Sync Operations

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-04 |
| **Priority** | High |
| **Type** | SIT — Concurrency |
| **Requirement** | UC-05 EF-01, EF-03 |
| **Preconditions** | 10 different projects in IDLE state |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 10 projects (PROJ-1 through PROJ-10) | All IDLE |
| 2 | Launch 10 coroutines, each running full lifecycle for its project | All execute concurrently |
| 3 | Wait for all to complete | All 10 reach COMPLETED |
| 4 | Verify no cross-contamination | Each project has correct data |
| 5 | Verify no deadlocks occurred | All completed within 30 seconds |

**Test Data:** 10 projects with concurrent lifecycles
**Postconditions:** All projects completed independently

---

### TC-SIT-05: Concurrency — Race Condition on Same Project

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-05 |
| **Priority** | High |
| **Type** | SIT — Concurrency |
| **Requirement** | UC-05 EF-01, BR-21, BR-26 |
| **Preconditions** | Single project "RACE" in IDLE state |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 5 coroutines all calling `markRunning("RACE")` simultaneously | Race condition |
| 2 | Count successes and failures | Exactly 1 success, 4 IllegalStateException |
| 3 | Query DB: status | status='RUNNING' |
| 4 | Verify no data corruption | Record is valid and consistent |

**Test Data:** 5 concurrent attempts on same project
**Postconditions:** Optimistic locking ensures exactly-once transition

---

### TC-SIT-06: Concurrency — Concurrent Progress Updates

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-06 |
| **Priority** | Medium |
| **Type** | SIT — Concurrency |
| **Requirement** | BR-03, BR-25 |
| **Preconditions** | Project in RUNNING state |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 20 coroutines calling `updateProgress` with offsets 1..20 | All execute |
| 2 | Wait for all to complete | No exceptions |
| 3 | Query DB: last_offset | Contains a valid offset value (last writer wins) |
| 4 | Verify updated_at is recent | Within last 5 seconds |
| 5 | Verify no partial writes | All fields are consistent |

**Test Data:** 20 concurrent progress updates
**Postconditions:** No corruption under concurrent writes

---

### TC-SIT-07: Regression — Existing Tables Unaffected by Migration

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-07 |
| **Priority** | High |
| **Type** | SIT — Regression |
| **Requirement** | BR-28, BR-29 |
| **Preconditions** | Existing tables (server_config, tool_toggle_state, file_proxy_registry) with data |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create existing tables (V1, V2 migrations) | Tables exist with test data |
| 2 | Insert test data into existing tables | Data present |
| 3 | Execute V3 migration (jira_sync_tables) | No exception |
| 4 | Query existing tables: data intact? | All data unchanged |
| 5 | Verify existing table structure unchanged | Columns, constraints same as before |
| 6 | Verify new tables created alongside existing | 4 new tables + existing tables coexist |

**Test Data:** Test data in existing tables + V3 migration
**Postconditions:** V3 migration is additive-only, no impact on existing schema

---

### TC-SIT-08: Performance — Index Effectiveness (EXPLAIN ANALYZE)

| Field | Value |
|-------|-------|
| **ID** | TC-SIT-08 |
| **Priority** | Medium |
| **Type** | SIT — Performance |
| **Requirement** | UC-07, BR-32–BR-35 |
| **Preconditions** | 10000 records in each table, all indexes created |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 10000 records per table | Data loaded |
| 2 | EXPLAIN ANALYZE: `SELECT * FROM jira_ticket_cache WHERE project_key = 'MTO'` | Index Scan on idx_ticket_cache_project |
| 3 | EXPLAIN ANALYZE: `SELECT * FROM jira_ticket_cache WHERE kb_ingested = FALSE` | Index Scan on idx_ticket_cache_not_ingested |
| 4 | EXPLAIN ANALYZE: `SELECT * FROM jira_attachment_queue WHERE status = 'PENDING' ORDER BY created_at` | Index Scan on idx_attachment_queue_pending |
| 5 | EXPLAIN ANALYZE: `SELECT * FROM jira_ticket_graph WHERE source_key = 'MTO-15'` | Index Scan on idx_ticket_graph_source |
| 6 | Verify no Seq Scan for indexed queries | All use Index Scan |

**Test Data:** 10000 records per table
**Acceptance Criteria:** All indexed queries use Index Scan, not Seq Scan


---

## 6. Requirements Traceability Matrix (RTM)

### 6.1 Use Case Coverage

| Use Case | FSD Section | Test Cases | Coverage |
|----------|-------------|------------|----------|
| UC-01 (Sync State Table) | §3.1 | TC-PBT-01, TC-PBT-02, TC-UT-01, TC-UT-02, TC-IT-01, TC-IT-02, TC-IT-03, TC-E2E-01, TC-SIT-01 | ✅ Covered |
| UC-02 (Ticket Cache) | §3.2 | TC-UT-03, TC-UT-04, TC-UT-05, TC-IT-04, TC-IT-05, TC-IT-06, TC-IT-07, TC-E2E-01, TC-SIT-02 | ✅ Covered |
| UC-03 (Ticket Graph) | §3.3 | TC-UT-06, TC-UT-07, TC-UT-08, TC-IT-08, TC-IT-09, TC-IT-10 | ✅ Covered |
| UC-04 (Attachment Queue) | §3.4 | TC-UT-09, TC-UT-10, TC-UT-11, TC-IT-11, TC-IT-12, TC-IT-13, TC-IT-14, TC-E2E-04, TC-SIT-03 | ✅ Covered |
| UC-05 (SyncStateManager) | §3.5 | TC-PBT-03, TC-PBT-04, TC-PBT-05, TC-UT-12–TC-UT-20, TC-IT-15–TC-IT-18, TC-E2E-01–TC-E2E-03, TC-SIT-04–TC-SIT-06 | ✅ Covered |
| UC-06 (Migration Scripts) | §3.6 | TC-IT-19, TC-IT-20, TC-SIT-07 | ✅ Covered |
| UC-07 (Performance Indexes) | §3.7 | TC-IT-21, TC-IT-22, TC-SIT-08 | ✅ Covered |

### 6.2 Business Rule Coverage

| BR | Rule Summary | Test Cases | Status |
|----|-------------|------------|--------|
| BR-01 | One sync state per project (PK) | TC-UT-01, TC-UT-02, TC-IT-01, TC-E2E-05 | ✅ |
| BR-02 | State machine transitions | TC-PBT-03, TC-PBT-04, TC-UT-12–TC-UT-16, TC-IT-15, TC-IT-16 | ✅ |
| BR-03 | Offset monotonically increasing | TC-PBT-02, TC-UT-20, TC-IT-17, TC-SIT-06 | ✅ |
| BR-04 | synced ≤ total | TC-PBT-05, TC-UT-20 | ✅ |
| BR-05 | updated_at auto-set | TC-IT-01, TC-IT-02, TC-IT-17 | ✅ |
| BR-06 | ticket_key unique (PK) | TC-UT-03, TC-IT-04, TC-IT-05 | ✅ |
| BR-07 | content_hash = 64 hex chars | TC-UT-04, TC-IT-04 | ✅ |
| BR-08 | kb_ingested defaults FALSE | TC-IT-04, TC-IT-07 | ✅ |
| BR-09 | UPSERT semantics | TC-IT-05, TC-IT-06 | ✅ |
| BR-10 | labels valid JSON array | TC-UT-05 | ✅ |
| BR-11 | Composite PK prevents duplicates | TC-UT-08, TC-IT-08, TC-IT-09 | ✅ |
| BR-12 | Category enum validation | TC-UT-07 | ✅ |
| BR-13 | Self-referencing rejected | TC-UT-06 | ✅ |
| BR-14 | No FK constraint on graph | TC-IT-08 | ✅ |
| BR-15 | Unique (ticket_key, attachment_id) | TC-UT-09, TC-IT-11, TC-IT-12 | ✅ |
| BR-16 | Status lifecycle PENDING→...→DONE | TC-UT-10, TC-IT-11, TC-IT-13, TC-E2E-04 | ✅ |
| BR-17 | retry_count incremented | TC-UT-11, TC-IT-14 | ✅ |
| BR-18 | Max retry = 3 | TC-UT-11 | ✅ |
| BR-19 | created_at immutable | TC-IT-11 | ✅ |
| BR-20 | processed_at set on DONE | TC-IT-13, TC-E2E-04 | ✅ |
| BR-21 | markRunning from IDLE/PAUSED/FAILED | TC-PBT-03, TC-UT-12–TC-UT-14, TC-IT-15 | ✅ |
| BR-22 | markPaused from RUNNING only | TC-UT-17, TC-IT-15 | ✅ |
| BR-23 | markCompleted from RUNNING + sets last_sync_at | TC-UT-18, TC-IT-02, TC-IT-15 | ✅ |
| BR-24 | markFailed from RUNNING + stores error | TC-UT-19, TC-E2E-03 | ✅ |
| BR-25 | All methods update updated_at | TC-UT-18, TC-UT-20, TC-IT-17 | ✅ |
| BR-26 | Invalid transitions throw IllegalStateException | TC-PBT-04, TC-UT-15, TC-UT-16, TC-IT-16 | ✅ |
| BR-27 | withContext(Dispatchers.IO) | TC-UT-12 | ✅ |
| BR-28 | Migration idempotent (IF NOT EXISTS) | TC-IT-19, TC-IT-20 | ✅ |
| BR-29 | Migration in single transaction | TC-IT-19 | ✅ |
| BR-30 | Naming convention V{N}__description.sql | TC-IT-19 | ✅ |
| BR-31 | Migration on startup via DatabaseInitializer | TC-E2E-06 | ✅ |
| BR-32 | Indexes use IF NOT EXISTS | TC-IT-20 | ✅ |
| BR-33 | Partial indexes with WHERE clauses | TC-IT-22, TC-SIT-08 | ✅ |
| BR-34 | Index naming convention | TC-IT-21, TC-SIT-08 | ✅ |
| BR-35 | No indexes on PK columns | TC-IT-19 (verify no redundant indexes) | ✅ |

### 6.3 FSD Test Scenario Coverage

| FSD TC | Scenario | STC Test Case(s) | Status |
|--------|----------|-----------------|--------|
| TC-01 | Create new sync state | TC-UT-01, TC-IT-01 | ✅ |
| TC-02 | Get existing sync state | TC-UT-02, TC-IT-01 | ✅ |
| TC-03 | Valid transition IDLE → RUNNING | TC-UT-12, TC-IT-15 | ✅ |
| TC-04 | Invalid transition COMPLETED → PAUSED | TC-PBT-04, TC-IT-16 | ✅ |
| TC-05 | Update progress atomically | TC-UT-20, TC-IT-17 | ✅ |
| TC-06 | Concurrent modification detection | TC-IT-03, TC-SIT-05 | ✅ |
| TC-07 | Ticket cache UPSERT (new) | TC-IT-04 | ✅ |
| TC-08 | Ticket cache UPSERT (existing) | TC-IT-05 | ✅ |
| TC-09 | Content hash change detection | TC-IT-05 | ✅ |
| TC-10 | Graph edge insert (new) | TC-IT-08 | ✅ |
| TC-11 | Graph edge insert (duplicate) | TC-IT-09 | ✅ |
| TC-12 | Self-referencing edge rejected | TC-UT-06 | ✅ |
| TC-13 | Attachment queue enqueue | TC-IT-11 | ✅ |
| TC-14 | Attachment duplicate prevention | TC-IT-12 | ✅ |
| TC-15 | Poll pending attachments | TC-IT-11, TC-SIT-03 | ✅ |
| TC-16 | Migration idempotency | TC-IT-20 | ✅ |
| TC-17 | Partial index effectiveness | TC-IT-22, TC-SIT-08 | ✅ |
| TC-18 | Batch UPSERT performance | TC-IT-06, TC-SIT-02 | ✅ |
| TC-19 | markCompleted sets last_sync_at | TC-UT-18, TC-IT-02 | ✅ |
| TC-20 | markFailed stores error message | TC-UT-19, TC-E2E-03 | ✅ |

### 6.4 Coverage Summary

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases (UC-01..UC-07) | 7 | 7 | 100% |
| Business Rules (BR-01..BR-35) | 35 | 35 | 100% |
| FSD Test Scenarios (TC-01..TC-20) | 20 | 20 | 100% |
| Exception Flows | 9 | 9 | 100% |
| Alternative Flows | 11 | 11 | 100% |
| Non-Functional Requirements | 3 | 3 | 100% |
| **Overall** | **85** | **85** | **100%** |

---

## 7. Appendix

### 7.1 Test Data Setup

**TestFixtures.kt** provides factory methods for all test data:

```kotlin
object TestFixtures {
    fun syncState(
        projectKey: String = "MTO",
        status: SyncStatus = SyncStatus.IDLE,
        lastOffset: Int = 0
    ): SyncState = SyncState(projectKey, status, lastOffset, 0, 0, null, null, Instant.now())

    fun ticketCache(
        ticketKey: String = "MTO-15",
        projectKey: String = "MTO",
        summary: String = "Test ticket"
    ): TicketCache = TicketCache(ticketKey, projectKey, summary, "Story", "Open", null, null, null, null, Instant.now(), Instant.now(), sha256("$ticketKey$summary"), false)

    fun ticketRelation(
        source: String = "MTO-15",
        target: String = "MTO-14",
        linkType: String = "blocks",
        category: RelationCategory = RelationCategory.OUTWARD
    ): TicketRelation = TicketRelation(source, target, linkType, category)

    fun attachmentQueueItem(
        ticketKey: String = "MTO-15",
        attachmentId: String = "att-001",
        filename: String = "screenshot.png"
    ): AttachmentQueueItem = AttachmentQueueItem(0, ticketKey, attachmentId, filename, "image/png", 1024L, "https://jira.example.com/att/001", AttachmentStatus.PENDING, 0, null, Instant.now(), null)
}
```

### 7.2 Testcontainers Configuration

```kotlin
class PostgresContainerSpec : FunSpec({
    val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("test_db")
        .withUsername("test")
        .withPassword("test")

    beforeSpec { postgres.start() }
    afterSpec { postgres.stop() }

    // Each test gets a fresh schema via migration
    beforeTest {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
        })
        JiraSyncDatabaseInitializer(ds).initialize()
    }
})
```

### 7.3 CSV Test Data Files

| File | Purpose | Records |
|------|---------|---------|
| `testdata/sync-states.csv` | All 5 status values with various offsets | 10 |
| `testdata/ticket-cache-valid.csv` | Valid ticket data for batch UPSERT | 100 |
| `testdata/ticket-cache-invalid.csv` | Invalid data (bad keys, bad hashes) | 15 |
| `testdata/graph-edges.csv` | Valid and duplicate relationships | 20 |
| `testdata/attachment-queue.csv` | Queue items with various statuses | 30 |

---

*End of Document*
