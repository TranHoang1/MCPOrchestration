# Software Test Cases (STC)

## MCPOrchestration — MTO-38: KB Server — Test Cases

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-38 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-38.docx |

---

## 1. Unit Test Cases

### TC-UT-01: Config Loading from YAML

| Field | Value |
|-------|-------|
| Priority | High |
| Component | KbConfigLoader |
| Precondition | Valid application.yml exists |

**Steps:**
1. Call KbConfigLoader.load(null) (classpath default)
2. Verify config.kb.server.port == 9181
3. Verify config.kb.server.transport == "stdio"
4. Verify config.kb.database.schema == "kb"
5. Verify config.kb.queue.hpq_capacity == 100

**Expected:** All config values match application.yml defaults.

---

### TC-UT-08: DualPriorityQueue HPQ Priority

| Field | Value |
|-------|-------|
| Priority | Critical |
| Component | DualPriorityQueue |
| Precondition | Queue initialized |

**Steps:**
1. Enqueue task A in NPQ
2. Enqueue task B in HPQ
3. Call select() to dequeue
4. Verify task B (HPQ) is returned first
5. Call select() again
6. Verify task A (NPQ) is returned second

**Expected:** HPQ tasks always processed before NPQ when both have items.

---

### TC-UT-11: CrashRecovery Restores Tasks

| Field | Value |
|-------|-------|
| Priority | Critical |
| Component | CrashRecoveryService |
| Precondition | DB has IN_PROGRESS tasks from previous crash |

**Steps:**
1. Insert 3 tasks with status=IN_PROGRESS in DB
2. Call crashRecoveryService.recover()
3. Verify all 3 tasks reset to PENDING
4. Verify tasks re-enqueued in DualPriorityQueue

**Expected:** All stuck IN_PROGRESS tasks recovered and re-queued.

---

### TC-UT-14: PiiMaskingEngine Mask + Encrypt

| Field | Value |
|-------|-------|
| Priority | Critical |
| Component | PiiMaskingEngine |
| Precondition | Encryption key configured |

**Steps:**
1. Input: "Contact john@example.com or 0912345678"
2. Call maskingEngine.mask(input)
3. Verify output contains "[EMAIL_1]" instead of email
4. Verify output contains "[PHONE_1]" instead of phone
5. Verify encrypted mapping stored
6. Call maskingEngine.unmask(output, mapping)
7. Verify original text restored

**Expected:** PII detected, masked, encrypted, and reversible.

---

### TC-UT-16: Rate Limiter Enforces Limits

| Field | Value |
|-------|-------|
| Priority | High |
| Component | RateLimiter |
| Precondition | Rate limit = 10/hour |

**Steps:**
1. Call rateLimiter.check("session1") 10 times
2. Verify all 10 calls return ALLOWED
3. Call rateLimiter.check("session1") 11th time
4. Verify returns RATE_LIMITED
5. Call rateLimiter.check("session2")
6. Verify returns ALLOWED (different session)

**Expected:** Rate limit enforced per-session, not globally.

---

## 2. Integration Test Cases

### TC-IT-01: Search with Real pgvector

| Field | Value |
|-------|-------|
| Priority | High |
| Components | KbSearchHandler + KbVectorClient + PostgreSQL |
| Precondition | Testcontainers PostgreSQL with pgvector running |

**Steps:**
1. Insert 5 KB entries with embeddings into pgvector
2. Call kb_search with query matching entry #3
3. Verify entry #3 appears in top results
4. Verify score > 0.5
5. Verify results sorted by score descending

**Expected:** Vector similarity search returns relevant results from real DB.

---

### TC-IT-06: PII Mask + Encrypt + Unmask Cycle

| Field | Value |
|-------|-------|
| Priority | Critical |
| Components | PiiMaskingEngine + Encryption + PostgreSQL |
| Precondition | Testcontainers PostgreSQL running, encryption key set |

**Steps:**
1. Ingest content with PII: "Email: test@corp.com, Phone: 0901234567"
2. Verify stored content has PII masked
3. Verify encrypted PII mapping in DB
4. Call kb_unmask_pii with issue_key
5. Verify original PII values returned
6. Verify audit event logged for unmask

**Expected:** Full PII lifecycle works end-to-end with real DB.

---

## 3. E2E-API Test Cases

### TC-E2E-01: kb_search Returns Ranked Results

| Field | Value |
|-------|-------|
| Priority | High |
| Tool | kb_search |
| Precondition | KB has 5+ entries ingested |

**Steps:**
1. Send MCP tools/call: kb_search with query="knowledge base architecture"
2. Verify response is valid JSON
3. Verify response has "results" array
4. Verify results[0].score >= results[1].score (sorted)
5. Verify each result has: issue_key, content, score, created_at

**Expected:** Search returns properly formatted, ranked results.

---

### TC-E2E-03: kb_ingest Stores and Indexes

| Field | Value |
|-------|-------|
| Priority | High |
| Tool | kb_ingest |
| Precondition | Server running, DB available |

**Steps:**
1. Send MCP tools/call: kb_ingest with title="MTO-99 Test Entry", content="Test content for verification"
2. Verify response: status="ingested", issue_key="MTO-99"
3. Send kb_read with issue_key="MTO-99"
4. Verify content matches what was ingested
5. Send kb_search with query="Test content for verification"
6. Verify MTO-99 appears in results

**Expected:** Ingested content is stored, indexed, and searchable.

---

### TC-E2E-11: kb_unmask_pii with Rate Limit

| Field | Value |
|-------|-------|
| Priority | High |
| Tool | kb_unmask_pii |
| Precondition | Entry with masked PII exists, rate limit = 10/hour |

**Steps:**
1. Call kb_unmask_pii 10 times (within limit)
2. Verify all 10 return unmasked content
3. Call kb_unmask_pii 11th time
4. Verify error: KB_RATE_LIMITED
5. Verify audit events logged for all 11 attempts

**Expected:** Rate limiting enforced after threshold, all attempts audited.

---

## 4. System Integration Test Cases

### TC-SIT-01: Gradle Compilation

| Field | Value |
|-------|-------|
| Priority | Critical |
| Precondition | Source code present |

**Steps:**
1. Run: ./gradlew :kb-server:compileKotlin
2. Verify exit code = 0
3. Verify no compilation errors in output

**Expected:** kb-server module compiles cleanly.

---

### TC-SIT-02: ShadowJar Build

| Field | Value |
|-------|-------|
| Priority | Critical |
| Precondition | Compilation passes |

**Steps:**
1. Run: ./gradlew :kb-server:shadowJar
2. Verify kb-server/build/libs/kb-server-all.jar exists
3. Verify JAR size > 10MB (all deps bundled)
4. Run: java -jar kb-server-all.jar --help (or verify it starts)

**Expected:** Fat JAR produced and executable.

---

### TC-SIT-03: Orchestrator Unaffected

| Field | Value |
|-------|-------|
| Priority | Critical |
| Precondition | kb-server module added to project |

**Steps:**
1. Run: ./gradlew :orchestrator-server:compileKotlin
2. Verify exit code = 0
3. Run: ./gradlew :orchestrator-server:test
4. Verify all existing tests pass

**Expected:** orchestrator-server compilation and tests unaffected by kb-server addition.

---

### TC-SIT-05: End-to-End Agent Flow

| Field | Value |
|-------|-------|
| Priority | High |
| Precondition | Both servers running |

**Steps:**
1. Start orchestrator-server with kb-server as upstream
2. Send find_tools("knowledge base") to orchestrator
3. Verify kb_* tools returned
4. Send execute_dynamic_tool(kb_ingest, {title, content})
5. Verify success response
6. Send execute_dynamic_tool(kb_search, {query})
7. Verify ingested content found

**Expected:** Full flow from agent through orchestrator to kb-server works.

---

## 5. Test Data

### 5.1 Sample KB Entries

| issue_key | title | content_summary |
|-----------|-------|-----------------|
| MTO-TEST-1 | Architecture Overview | System architecture description |
| MTO-TEST-2 | API Design | REST API specification |
| MTO-TEST-3 | Database Schema | PostgreSQL schema design |
| MTO-TEST-4 | Security Policy | Access control rules |
| MTO-TEST-5 | Deployment Guide | Docker deployment steps |

### 5.2 PII Test Data

| Type | Test Value | Expected Mask |
|------|-----------|---------------|
| Email | test@example.com | [EMAIL_1] |
| Phone | 0912345678 | [PHONE_1] |
| Bank Account | 1234567890123 | [BANK_ACCOUNT_1] |
| ID Card | 079123456789 | [ID_CARD_1] |
