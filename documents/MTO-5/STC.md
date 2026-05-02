# Software Test Cases (STC)

## MCP Orchestration Server — MTO-5: Create MCP Tool Orchestration

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-5 |
| Title | Create MCP Tool Orchestration |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-02 |
| Status | Draft |
| Related STP | documents/MTO-5/STP.md |
| Related FSD | documents/MTO-5/FSD.md |
| Related TDD | documents/MTO-5/TDD.md |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-02 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |

---

## Test Case Summary

| Level | ID Range | Count | Automated |
|-------|----------|-------|-----------|
| PBT — Property-Based Testing | PBT-001 to PBT-012 | 12 | 12 (100%) |
| UT — Unit Testing | UT-001 to UT-035 | 35 | 35 (100%) |
| IT — Integration Testing | IT-001 to IT-025 | 25 | 25 (100%) |
| E2E-API — End-to-End API Testing | E2E-001 to E2E-015 | 15 | 15 (100%) |
| **Total** | | **87** | **87 (100%)** |

---

## 1. Property-Based Tests (PBT)

### PBT-001: find_tools always returns ≤ top_k results

| Field | Value |
|-------|-------|
| **ID** | PBT-001 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | UC-01, BR-02 |
| **Preconditions** | ToolDiscoveryService initialized with mock VectorDbClient containing 100 tools |

**Property:** For any valid query string and any top_k ∈ [1, 20], `findTools(query, topK)` returns a list with size ≤ top_k.

**Generator:**
- `query`: Arb.string(1..2000)
- `topK`: Arb.int(1..20)

**Assertion:** `result.tools.size <= topK`

**Test Data:** 1000 random inputs
**Framework:** kotest-property

---

### PBT-002: find_tools similarity scores are in [0.0, 1.0]

| Field | Value |
|-------|-------|
| **ID** | PBT-002 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | UC-01, BR-06 |
| **Preconditions** | ToolDiscoveryService initialized with mock VectorDbClient |

**Property:** For any valid query, all returned tools have `similarity_score` in range [0.0, 1.0].

**Generator:**
- `query`: Arb.string(1..2000)

**Assertion:** `result.tools.all { it.similarityScore in 0.0f..1.0f }`

**Test Data:** 1000 random inputs

---

### PBT-003: find_tools results are sorted by similarity descending

| Field | Value |
|-------|-------|
| **ID** | PBT-003 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | BR-06 |
| **Preconditions** | ToolDiscoveryService with ≥10 tools indexed |

**Property:** For any valid query, returned tools are sorted by `similarity_score` in descending order.

**Generator:**
- `query`: Arb.string(1..500)

**Assertion:** `result.tools.zipWithNext().all { (a, b) -> a.similarityScore >= b.similarityScore }`

**Test Data:** 1000 random inputs

---

### PBT-004: find_tools respects similarity threshold

| Field | Value |
|-------|-------|
| **ID** | PBT-004 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | BR-03 |
| **Preconditions** | ToolDiscoveryService with mock VectorDbClient |

**Property:** For any threshold ∈ [0.0, 1.0], all returned tools have `similarity_score ≥ threshold`.

**Generator:**
- `query`: Arb.string(1..500)
- `threshold`: Arb.float(0.0f..1.0f)

**Assertion:** `result.tools.all { it.similarityScore >= threshold }`

**Test Data:** 1000 random inputs

---

### PBT-005: execute_dynamic_tool tool_name lookup is case-sensitive

| Field | Value |
|-------|-------|
| **ID** | PBT-005 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | BR-10 |
| **Preconditions** | ToolRegistry with tools registered in specific casing |

**Property:** `lookupTool("Read_Logs")` ≠ `lookupTool("read_logs")` when only `"read_logs"` is registered.

**Generator:**
- `toolName`: Arb.string(1..100) with random case mutations

**Assertion:** Only exact case match returns a result; case-mutated names return null.

**Test Data:** 1000 random inputs

---

### PBT-006: JSON-RPC request/response serialization roundtrip

| Field | Value |
|-------|-------|
| **ID** | PBT-006 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | TDD §3.1 (MCP Protocol) |
| **Preconditions** | JsonRpcMessage serializer configured |

**Property:** For any valid JsonRpcMessage, `deserialize(serialize(message)) == message`.

**Generator:**
- `id`: Arb.int()
- `method`: Arb.element("tools/call", "tools/list", "initialize", "ping")
- `params`: Arb.json() (random valid JSON objects)

**Assertion:** Roundtrip equality

**Test Data:** 1000 random inputs

---

### PBT-007: Configuration validation rejects invalid values

| Field | Value |
|-------|-------|
| **ID** | PBT-007 |
| **Priority** | Medium |
| **Type** | Property-Based |
| **Requirement** | UC-04, BR-21 |
| **Preconditions** | ConfigValidator initialized |

**Property:** Config with `top_k` outside [1, 20] or `threshold` outside [0.0, 1.0] is rejected.

**Generator:**
- `topK`: Arb.int() (full range including negatives)
- `threshold`: Arb.float() (full range)

**Assertion:** Invalid values → validation error; valid values → success

**Test Data:** 1000 random inputs

---

### PBT-008: ToolEntry composite key uniqueness

| Field | Value |
|-------|-------|
| **ID** | PBT-008 |
| **Priority** | Medium |
| **Type** | Property-Based |
| **Requirement** | BR-16 |
| **Preconditions** | ToolRegistry initialized |

**Property:** Two tools with same `tool_name` but different `server_name` are stored as separate entries.

**Generator:**
- `serverName`: Arb.string(1..50)
- `toolName`: Arb.string(1..100)

**Assertion:** `registry.lookupTool("server1::tool") != registry.lookupTool("server2::tool")`

**Test Data:** 500 random inputs

---

### PBT-009: Input validation — query length boundary

| Field | Value |
|-------|-------|
| **ID** | PBT-009 |
| **Priority** | High |
| **Type** | Property-Based |
| **Requirement** | EF-01, EF-02 |
| **Preconditions** | ToolDiscoveryService initialized |

**Property:** Queries with length > 2000 always return INVALID_PARAMS error. Queries with length 1..2000 never return INVALID_PARAMS for length reason.

**Generator:**
- `query`: Arb.string(0..5000)

**Assertion:** `query.length > 2000 → INVALID_PARAMS` and `query.length in 1..2000 → no length error`

**Test Data:** 1000 random inputs

---

### PBT-010: Exponential backoff timing correctness

| Field | Value |
|-------|-------|
| **ID** | PBT-010 |
| **Priority** | Medium |
| **Type** | Property-Based |
| **Requirement** | BR-24 |
| **Preconditions** | RetryUtils initialized |

**Property:** For attempt N (0-indexed), backoff delay = `baseDelay * 2^N`, capped at maxDelay.

**Generator:**
- `attempt`: Arb.int(0..10)
- `baseDelay`: Arb.long(100..5000)

**Assertion:** `calculateBackoff(attempt, baseDelay) == min(baseDelay * 2^attempt, maxDelay)`

**Test Data:** 1000 random inputs

---

### PBT-011: top_k clamping to valid range

| Field | Value |
|-------|-------|
| **ID** | PBT-011 |
| **Priority** | Medium |
| **Type** | Property-Based |
| **Requirement** | BR-02 |
| **Preconditions** | ToolDiscoveryService initialized |

**Property:** Any top_k value outside [1, 20] is clamped: values < 1 → 1, values > 20 → 20.

**Generator:**
- `topK`: Arb.int(-100..100)

**Assertion:** Effective top_k = `topK.coerceIn(1, 20)`

**Test Data:** 1000 random inputs

---

### PBT-012: threshold clamping to valid range

| Field | Value |
|-------|-------|
| **ID** | PBT-012 |
| **Priority** | Medium |
| **Type** | Property-Based |
| **Requirement** | BR-03 |
| **Preconditions** | ToolDiscoveryService initialized |

**Property:** Any threshold value outside [0.0, 1.0] is clamped: values < 0.0 → 0.0, values > 1.0 → 1.0.

**Generator:**
- `threshold`: Arb.float(-10.0f..10.0f)

**Assertion:** Effective threshold = `threshold.coerceIn(0.0f, 1.0f)`

**Test Data:** 1000 random inputs

---


## 2. Unit Tests (UT)

### 2.1 ToolDiscoveryService

#### UT-001: findTools — valid query returns matching tools

| Field | Value |
|-------|-------|
| **ID** | UT-001 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-01, BR-01, STORY-1 AC1 |
| **Preconditions** | ToolDiscoveryServiceImpl with mock EmbeddingService and mock VectorDbClient. VectorDbClient returns 3 scored results. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "read log files", topK = 5, threshold = 0.7f)` | Method returns FindToolsResponse |
| 2 | Verify EmbeddingService.generateEmbedding was called with "read log files" | Called exactly once |
| 3 | Verify VectorDbClient.search was called with correct vector, limit=5, threshold=0.7 | Called exactly once with correct params |
| 4 | Verify response contains 3 tools with name, description, input_schema, server_name, similarity_score | All fields populated |
| 5 | Verify response.searchMode == "semantic" | Semantic mode used |

**Test Data:** Mock embedding returns FloatArray(768) { 0.01f }; Mock VectorDB returns 3 results with scores 0.95, 0.88, 0.75
**Postconditions:** No state change

---

#### UT-002: findTools — empty query returns INVALID_PARAMS

| Field | Value |
|-------|-------|
| **ID** | UT-002 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-01 |
| **Preconditions** | ToolDiscoveryServiceImpl initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "", topK = 5, threshold = 0.7f)` | Throws InvalidParamsException |
| 2 | Verify exception message contains "Query parameter is required and must be non-empty" | Message matches |
| 3 | Verify EmbeddingService was NOT called | Not invoked |

**Test Data:** Empty string query
**Postconditions:** No state change

---

#### UT-003: findTools — query exceeds max length returns INVALID_PARAMS

| Field | Value |
|-------|-------|
| **ID** | UT-003 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-02 |
| **Preconditions** | ToolDiscoveryServiceImpl initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "a".repeat(2001), topK = 5, threshold = 0.7f)` | Throws InvalidParamsException |
| 2 | Verify exception message contains "Query exceeds maximum length of 2000 characters" | Message matches |

**Test Data:** String of 2001 'a' characters
**Postconditions:** No state change

---

#### UT-004: findTools — no matching tools returns empty array

| Field | Value |
|-------|-------|
| **ID** | UT-004 |
| **Priority** | Medium |
| **Type** | Unit — Alternative Flow |
| **Requirement** | AF-01 |
| **Preconditions** | Mock VectorDbClient returns empty list |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "quantum computing simulation", topK = 5, threshold = 0.7f)` | Returns FindToolsResponse with empty tools list |
| 2 | Verify response.tools is empty | `tools.size == 0` |
| 3 | Verify response.searchMode == "semantic" | Semantic mode used |

**Test Data:** Mock VectorDB returns empty list
**Postconditions:** No state change

---

#### UT-005: findTools — VectorDB unavailable falls back to keyword search

| Field | Value |
|-------|-------|
| **ID** | UT-005 |
| **Priority** | High |
| **Type** | Unit — Alternative Flow |
| **Requirement** | AF-02, BR-05 |
| **Preconditions** | Mock VectorDbClient throws VectorDbUnavailableException. KeywordSearchEngine has tools in memory. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "read logs", topK = 5, threshold = 0.7f)` | Returns FindToolsResponse (not exception) |
| 2 | Verify response.searchMode == "keyword" | Keyword fallback used |
| 3 | Verify KeywordSearchEngine.search was called | Called with query |
| 4 | Verify results contain tools matching keyword "read" or "logs" | Keyword-matched results |

**Test Data:** KeywordSearchEngine has 5 tools, 2 match "read" or "logs"
**Postconditions:** Warning logged

---

#### UT-006: findTools — Embedding service unavailable falls back to keyword search

| Field | Value |
|-------|-------|
| **ID** | UT-006 |
| **Priority** | High |
| **Type** | Unit — Alternative Flow |
| **Requirement** | AF-03 |
| **Preconditions** | Mock EmbeddingService throws EmbeddingServiceException |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "create jira issue", topK = 5, threshold = 0.7f)` | Returns FindToolsResponse (not exception) |
| 2 | Verify response.searchMode == "keyword" | Keyword fallback used |

**Test Data:** EmbeddingService throws on generateEmbedding
**Postconditions:** Warning logged

---

#### UT-007: findTools — tools from disconnected server flagged

| Field | Value |
|-------|-------|
| **ID** | UT-007 |
| **Priority** | Medium |
| **Type** | Unit — Alternative Flow |
| **Requirement** | AF-04, BR-07, BR-27 |
| **Preconditions** | Mock VectorDB returns tool from server with status DISCONNECTED |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "read logs", topK = 5, threshold = 0.7f)` | Returns results including disconnected server's tools |
| 2 | Verify tool from disconnected server has `server_status == "DISCONNECTED"` | Status flag correct |

**Test Data:** ToolRegistry has "read_logs" from "log-server" (DISCONNECTED)
**Postconditions:** No state change

---

#### UT-008: findTools — query trimmed of whitespace

| Field | Value |
|-------|-------|
| **ID** | UT-008 |
| **Priority** | Low |
| **Type** | Unit — Edge Case |
| **Requirement** | FSD §3.1.4 (Input Data — trimmed) |
| **Preconditions** | ToolDiscoveryServiceImpl initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `findTools(query = "  read logs  ", topK = 5, threshold = 0.7f)` | Processes trimmed query "read logs" |
| 2 | Verify EmbeddingService.generateEmbedding called with "read logs" (trimmed) | Trimmed input |

**Test Data:** Query with leading/trailing whitespace

---

### 2.2 ToolExecutionDispatcher

#### UT-009: execute — valid tool name routes to correct upstream server

| Field | Value |
|-------|-------|
| **ID** | UT-009 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-02, STORY-2 AC1 |
| **Preconditions** | ToolRegistry has "read_logs" → "log-server". UpstreamServerManager returns active McpConnection for "log-server". |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "read_logs", arguments = {"path": "/var/log/app.log"})` | Returns ExecuteToolResponse |
| 2 | Verify ToolRegistry.lookupTool("read_logs") was called | Called once |
| 3 | Verify UpstreamServerManager.getConnection("log-server") was called | Called once |
| 4 | Verify McpConnection.sendRequest("tools/call", ...) was called with correct tool_name and arguments | Correct JSON-RPC forwarded |
| 5 | Verify response._meta.upstream_server == "log-server" | Metadata enriched |
| 6 | Verify response._meta.execution_time_ms > 0 | Duration tracked |

**Test Data:** Mock upstream returns `{content: [{type: "text", text: "log data..."}]}`
**Postconditions:** Execution logged

---

#### UT-010: execute — tool not found returns TOOL_NOT_FOUND

| Field | Value |
|-------|-------|
| **ID** | UT-010 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-04 |
| **Preconditions** | ToolRegistry returns null for "nonexistent_tool" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "nonexistent_tool", arguments = {})` | Throws ToolNotFoundException |
| 2 | Verify exception.errorCode == "TOOL_NOT_FOUND" | Correct error code |
| 3 | Verify exception.message contains "nonexistent_tool" | Tool name in message |

**Test Data:** Non-existent tool name
**Postconditions:** No upstream call made

---

#### UT-011: execute — server unavailable returns SERVER_UNAVAILABLE

| Field | Value |
|-------|-------|
| **ID** | UT-011 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-05 |
| **Preconditions** | ToolRegistry has "read_logs" → "log-server". UpstreamServerManager returns null (DISCONNECTED). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "read_logs", arguments = {})` | Throws ServerUnavailableException |
| 2 | Verify exception.errorCode == "SERVER_UNAVAILABLE" | Correct error code |
| 3 | Verify exception.message contains "DISCONNECTED" | Status in message |

**Test Data:** Server in DISCONNECTED state

---

#### UT-012: execute — timeout returns EXECUTION_TIMEOUT

| Field | Value |
|-------|-------|
| **ID** | UT-012 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-06, BR-09 |
| **Preconditions** | Mock McpConnection.sendRequest delays > configured timeout (30s) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "slow_tool", arguments = {})` with timeout = 1s (for test speed) | Throws ExecutionTimeoutException |
| 2 | Verify exception.errorCode == "EXECUTION_TIMEOUT" | Correct error code |
| 3 | Verify exception.message contains timeout value | Timeout in message |

**Test Data:** Mock connection delays 2s, timeout configured to 1s

---

#### UT-013: execute — upstream error passed through

| Field | Value |
|-------|-------|
| **ID** | UT-013 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-07, BR-11 |
| **Preconditions** | Mock McpConnection returns error response from upstream |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "failing_tool", arguments = {})` | Throws UpstreamErrorException |
| 2 | Verify exception.errorCode == "UPSTREAM_ERROR" | Correct error code |
| 3 | Verify exception.upstreamServer == "failing-server" | Server name included |
| 4 | Verify upstream error message is preserved | Pass-through |

**Test Data:** Mock upstream returns `{error: {code: -32000, message: "Internal error"}}`

---

#### UT-014: execute — argument validation failure returns INVALID_PARAMS

| Field | Value |
|-------|-------|
| **ID** | UT-014 |
| **Priority** | Medium |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-08, BR-13 |
| **Preconditions** | Argument validation enabled. Tool has input_schema requiring "path" (string). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "read_logs", arguments = {"path": 123})` | Throws InvalidParamsException |
| 2 | Verify exception.errorCode == "INVALID_PARAMS" | Correct error code |
| 3 | Verify exception.message contains validation details | Details included |

**Test Data:** Integer value where string expected

---

#### UT-015: execute — argument validation disabled skips validation

| Field | Value |
|-------|-------|
| **ID** | UT-015 |
| **Priority** | Medium |
| **Type** | Unit — Alternative Flow |
| **Requirement** | AF-07, BR-13 |
| **Preconditions** | Config: `execution.validate_arguments = false` |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "read_logs", arguments = {"path": 123})` | Forwards to upstream without validation error |
| 2 | Verify McpConnection.sendRequest was called | Arguments passed through |

**Test Data:** Invalid arguments but validation disabled

---

#### UT-016: execute — null arguments handled correctly

| Field | Value |
|-------|-------|
| **ID** | UT-016 |
| **Priority** | Medium |
| **Type** | Unit — Edge Case |
| **Requirement** | UC-02 (arguments optional) |
| **Preconditions** | ToolRegistry has tool with no required arguments |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `execute(toolName = "list_tools", arguments = null)` | Forwards with empty/null arguments |
| 2 | Verify upstream receives `tools/call` with `arguments: {}` or `arguments: null` | Handled gracefully |

---

### 2.3 ToolRegistry

#### UT-017: registerTool — adds tool to registry

| Field | Value |
|-------|-------|
| **ID** | UT-017 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-03, BR-16 |
| **Preconditions** | Empty ToolRegistryImpl |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `registerTool(ToolEntry(name="read_logs", serverName="log-server", ...))` | Tool registered |
| 2 | Call `lookupTool("read_logs")` | Returns the registered ToolEntry |
| 3 | Verify `getToolCount() == 1` | Count correct |
| 4 | Verify `getToolsByServer("log-server")` contains "read_logs" | Server mapping correct |

---

#### UT-018: removeTool — removes tool from registry

| Field | Value |
|-------|-------|
| **ID** | UT-018 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-03, BR-14 |
| **Preconditions** | ToolRegistry with "read_logs" registered |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `removeTool("read_logs")` | Tool removed |
| 2 | Call `lookupTool("read_logs")` | Returns null |
| 3 | Verify `getToolCount() == 0` | Count updated |

---

#### UT-019: removeServerTools — removes all tools from a server

| Field | Value |
|-------|-------|
| **ID** | UT-019 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-03, BR-14 |
| **Preconditions** | ToolRegistry with 3 tools from "log-server" and 2 from "jira-server" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `removeServerTools("log-server")` | All 3 tools from log-server removed |
| 2 | Verify `getToolsByServer("log-server")` is empty | Server tools cleared |
| 3 | Verify `getToolsByServer("jira-server")` still has 2 tools | Other server unaffected |
| 4 | Verify `getToolCount() == 2` | Count updated |

---

#### UT-020: lookupTool — case-sensitive exact match

| Field | Value |
|-------|-------|
| **ID** | UT-020 |
| **Priority** | High |
| **Type** | Unit — Business Rule |
| **Requirement** | BR-10 |
| **Preconditions** | ToolRegistry with "read_logs" registered |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `lookupTool("read_logs")` | Returns ToolEntry |
| 2 | Call `lookupTool("Read_Logs")` | Returns null |
| 3 | Call `lookupTool("READ_LOGS")` | Returns null |
| 4 | Call `lookupTool("read_log")` | Returns null (partial match) |

---

#### UT-021: concurrent access — thread-safe operations

| Field | Value |
|-------|-------|
| **ID** | UT-021 |
| **Priority** | High |
| **Type** | Unit — Concurrency |
| **Requirement** | TDD §5.1 (ConcurrentHashMap) |
| **Preconditions** | Empty ToolRegistryImpl |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 100 coroutines, each registering a unique tool | All 100 tools registered |
| 2 | Verify `getToolCount() == 100` | No lost writes |
| 3 | Launch 50 coroutines removing tools while 50 others read | No ConcurrentModificationException |

---

### 2.4 EmbeddingService

#### UT-022: generateEmbedding — returns correct dimension vector

| Field | Value |
|-------|-------|
| **ID** | UT-022 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | TDD §6.2 (Embedding) |
| **Preconditions** | OpenAiEmbeddingService with mock HTTP client |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `generateEmbedding("read log files")` | Returns FloatArray |
| 2 | Verify result.size == 768 | Correct dimensions |
| 3 | Verify HTTP request sent to `https://api.openai.com/v1/embeddings` | Correct endpoint |
| 4 | Verify request body contains `model: "text-embedding-3-small"` | Correct model |

**Test Data:** Mock HTTP returns 768-dim vector

---

#### UT-023: generateEmbeddings — batch processing

| Field | Value |
|-------|-------|
| **ID** | UT-023 |
| **Priority** | Medium |
| **Type** | Unit — Happy Path |
| **Requirement** | TDD §6.2 (Batch indexing) |
| **Preconditions** | OpenAiEmbeddingService with mock HTTP client |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `generateEmbeddings(listOf("tool1 desc", "tool2 desc", "tool3 desc"))` | Returns List<FloatArray> with 3 elements |
| 2 | Verify single HTTP request sent (batch) | Batched call |
| 3 | Verify each result has 768 dimensions | Correct dimensions |

---

### 2.5 VectorDbClient

#### UT-024: search — returns scored results

| Field | Value |
|-------|-------|
| **ID** | UT-024 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-01 |
| **Preconditions** | QdrantVectorDbClient with mock HTTP client |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `search("mcp_tools", queryVector, limit=5, scoreThreshold=0.7f)` | Returns List<SearchResult> |
| 2 | Verify HTTP POST to `/collections/mcp_tools/points/search` | Correct endpoint |
| 3 | Verify request body contains vector, limit, score_threshold | Correct params |

---

#### UT-025: upsert — stores points correctly

| Field | Value |
|-------|-------|
| **ID** | UT-025 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-03 |
| **Preconditions** | QdrantVectorDbClient with mock HTTP client |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upsert("mcp_tools", listOf(VectorPoint(...)))` | Success (no exception) |
| 2 | Verify HTTP PUT to `/collections/mcp_tools/points` | Correct endpoint |
| 3 | Verify request body contains point id, vector, payload | Correct data |

---

### 2.6 ConfigurationManager

#### UT-026: getConfig — loads valid YAML configuration

| Field | Value |
|-------|-------|
| **ID** | UT-026 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-04, BR-19 |
| **Preconditions** | Valid application.yml file exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getConfig()` | Returns OrchestratorConfig |
| 2 | Verify config.discovery.topK == 5 | Default value loaded |
| 3 | Verify config.upstreamServers has expected entries | Servers parsed |

---

#### UT-027: getConfig — invalid YAML throws ConfigException

| Field | Value |
|-------|-------|
| **ID** | UT-027 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | EF-11, BR-21 |
| **Preconditions** | Invalid YAML file (syntax error) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getConfig()` with invalid YAML | Throws ConfigException |
| 2 | Verify error message contains parse error details | Descriptive error |

---

#### UT-028: reload — applies new config without restart

| Field | Value |
|-------|-------|
| **ID** | UT-028 |
| **Priority** | Medium |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-04, BR-20 |
| **Preconditions** | ConfigurationManager loaded with initial config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Modify config file (change top_k from 5 to 10) | File changed |
| 2 | Call `reload()` | Returns new OrchestratorConfig |
| 3 | Verify new config.discovery.topK == 10 | Updated value |

---

#### UT-029: reload — invalid new config keeps previous config

| Field | Value |
|-------|-------|
| **ID** | UT-029 |
| **Priority** | High |
| **Type** | Unit — Exception Flow |
| **Requirement** | BR-21 |
| **Preconditions** | ConfigurationManager loaded with valid config (topK=5) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Replace config file with invalid YAML | File changed |
| 2 | Call `reload()` | Throws or returns previous config |
| 3 | Verify `getConfig().discovery.topK == 5` | Previous config retained |

---

### 2.7 HealthMonitor

#### UT-030: checkAllServers — connected server stays connected on ping success

| Field | Value |
|-------|-------|
| **ID** | UT-030 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | UC-05 |
| **Preconditions** | Server "log-server" in CONNECTED state. Mock ping returns success. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `checkAllServers()` | Health check completes |
| 2 | Verify server state remains CONNECTED | No state change |
| 3 | Verify lastHealthCheck timestamp updated | Timestamp refreshed |

---

#### UT-031: checkAllServers — connected server transitions to DISCONNECTED on ping failure

| Field | Value |
|-------|-------|
| **ID** | UT-031 |
| **Priority** | High |
| **Type** | Unit — State Transition |
| **Requirement** | UC-05, BR-26 |
| **Preconditions** | Server "log-server" in CONNECTED state. Mock ping throws exception. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `checkAllServers()` | Health check completes |
| 2 | Verify server state changed to DISCONNECTED | State transition |
| 3 | Verify state transition logged | Log entry present |

---

#### UT-032: auto-reconnect — DISCONNECTED server attempts reconnection

| Field | Value |
|-------|-------|
| **ID** | UT-032 |
| **Priority** | High |
| **Type** | Unit — State Transition |
| **Requirement** | UC-05, BR-24, BR-25 |
| **Preconditions** | Server in DISCONNECTED state. Auto-reconnect enabled. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `checkAllServers()` | Reconnect attempt triggered |
| 2 | Verify UpstreamServerManager.connect("log-server") was called | Reconnect attempted |
| 3 | Verify reconnectAttempts incremented | Counter updated |

---

#### UT-033: auto-reconnect — max attempts exceeded transitions to ERROR

| Field | Value |
|-------|-------|
| **ID** | UT-033 |
| **Priority** | High |
| **Type** | Unit — State Transition |
| **Requirement** | BR-25 |
| **Preconditions** | Server in DISCONNECTED state. reconnectAttempts == maxReconnectAttempts (5). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `checkAllServers()` | Health check completes |
| 2 | Verify server state changed to ERROR | State transition |
| 3 | Verify no further reconnect attempts | Stopped retrying |

---

### 2.8 JsonRpcHandler & McpProtocolHandler

#### UT-034: parseRequest — valid JSON-RPC request parsed correctly

| Field | Value |
|-------|-------|
| **ID** | UT-034 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | TDD §3.1 |
| **Preconditions** | JsonRpcHandler initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Parse `{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_tools","arguments":{"query":"test"}}}` | Returns JsonRpcRequest with id=1, method="tools/call" |
| 2 | Verify params.name == "find_tools" | Correct tool name |
| 3 | Verify params.arguments.query == "test" | Correct arguments |

---

#### UT-035: handleInitialize — returns correct MCP capabilities

| Field | Value |
|-------|-------|
| **ID** | UT-035 |
| **Priority** | High |
| **Type** | Unit — Happy Path |
| **Requirement** | TDD §3.4.1 |
| **Preconditions** | McpProtocolHandler initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `handleInitialize(initRequest)` | Returns InitializeResult |
| 2 | Verify result.protocolVersion == "2024-11-05" | Correct version |
| 3 | Verify result.capabilities.tools is present | Tools capability declared |
| 4 | Verify result.serverInfo.name == "mcp-orchestrator" | Correct server name |

---


## 3. Integration Tests (IT)

### 3.1 Tool Discovery Integration

#### IT-001: find_tools — full semantic search pipeline with Qdrant

| Field | Value |
|-------|-------|
| **ID** | IT-001 |
| **Priority** | High |
| **Type** | Integration — Happy Path |
| **Requirement** | UC-01, STORY-1 |
| **Preconditions** | Ktor testApplication running. Qdrant Testcontainer started. 10 tools indexed with real embeddings (mock OpenAI). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send MCP `tools/call` with `find_tools(query: "read log files")` via test client | JSON-RPC response received |
| 2 | Verify response contains tools with similarity scores | Tools returned |
| 3 | Verify tools are sorted by similarity descending | Correct ordering |
| 4 | Verify each tool has name, description, input_schema, server_name | All fields present |
| 5 | Verify response.searchMode == "semantic" | Semantic search used |

**Test Data:** 10 pre-indexed tools in Qdrant with mock embeddings
**Postconditions:** Qdrant state unchanged

---

#### IT-002: find_tools — keyword fallback when Qdrant container stopped

| Field | Value |
|-------|-------|
| **ID** | IT-002 |
| **Priority** | High |
| **Type** | Integration — Fallback |
| **Requirement** | AF-02, BR-05 |
| **Preconditions** | Ktor testApplication running. Qdrant container stopped. ToolRegistry has tools in memory. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Stop Qdrant container | Container stopped |
| 2 | Send `find_tools(query: "read logs")` | Response received (no error) |
| 3 | Verify response.searchMode == "keyword" | Keyword fallback activated |
| 4 | Verify results contain keyword-matched tools | Results present |

---

#### IT-003: find_tools — custom top_k and threshold

| Field | Value |
|-------|-------|
| **ID** | IT-003 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | BR-02, BR-03 |
| **Preconditions** | 10 tools indexed in Qdrant |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "jira", top_k: 2, threshold: 0.8)` | Response received |
| 2 | Verify response.tools.size ≤ 2 | top_k respected |
| 3 | Verify all tools have similarity_score ≥ 0.8 | Threshold respected |

---

#### IT-004: find_tools — total_indexed count accurate

| Field | Value |
|-------|-------|
| **ID** | IT-004 |
| **Priority** | Low |
| **Type** | Integration |
| **Requirement** | FSD §3.1.4 (Output Data) |
| **Preconditions** | 10 tools indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "anything")` | Response received |
| 2 | Verify response.total_indexed == 10 | Accurate count |

---

#### IT-005: find_tools — embedding cache hit on repeated query

| Field | Value |
|-------|-------|
| **ID** | IT-005 |
| **Priority** | Medium |
| **Type** | Integration — Performance |
| **Requirement** | TDD §8.1 (Embedding Cache) |
| **Preconditions** | Embedding cache enabled. Mock embedding service tracks call count. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "read logs")` | First call — embedding generated |
| 2 | Send `find_tools(query: "read logs")` again within 5 minutes | Second call — cache hit |
| 3 | Verify EmbeddingService called only once | Cache working |

---

#### IT-006: find_tools — internal error returns INTERNAL_ERROR

| Field | Value |
|-------|-------|
| **ID** | IT-006 |
| **Priority** | Medium |
| **Type** | Integration — Exception |
| **Requirement** | EF-03 |
| **Preconditions** | Both VectorDB and KeywordSearchEngine throw unexpected exceptions |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "test")` | JSON-RPC error response |
| 2 | Verify error code == "INTERNAL_ERROR" | Correct error |
| 3 | Verify error message == "Tool discovery failed. Please retry." | Correct message |

---

### 3.2 Tool Execution Integration

#### IT-007: execute_dynamic_tool — full proxy to mock upstream server (stdio)

| Field | Value |
|-------|-------|
| **ID** | IT-007 |
| **Priority** | High |
| **Type** | Integration — Happy Path |
| **Requirement** | UC-02, STORY-2 |
| **Preconditions** | MockUpstreamServer running via stdio. Tool "read_logs" registered. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "read_logs", arguments: {path: "/var/log"})` | Response received |
| 2 | Verify MockUpstreamServer received `tools/call` with correct tool_name and arguments | Correct forwarding |
| 3 | Verify response content matches mock upstream response | Pass-through |
| 4 | Verify response._meta.upstream_server == "mock-server" | Metadata present |
| 5 | Verify response._meta.execution_time_ms > 0 | Duration tracked |

---

#### IT-008: execute_dynamic_tool — full proxy to mock upstream server (HTTP)

| Field | Value |
|-------|-------|
| **ID** | IT-008 |
| **Priority** | High |
| **Type** | Integration — Happy Path |
| **Requirement** | UC-02, BR-22 |
| **Preconditions** | MockUpstreamServer running via HTTP. Tool "create_issue" registered. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "create_issue", arguments: {project: "TEST", summary: "Bug"})` | Response received |
| 2 | Verify HTTP request sent to mock server | Correct transport |
| 3 | Verify response content matches mock response | Pass-through |

---

#### IT-009: execute_dynamic_tool — timeout with slow mock server

| Field | Value |
|-------|-------|
| **ID** | IT-009 |
| **Priority** | High |
| **Type** | Integration — Exception |
| **Requirement** | EF-06, BR-09 |
| **Preconditions** | MockUpstreamServer configured with 5s delay. Timeout set to 2s. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "slow_tool", arguments: {})` | Error response |
| 2 | Verify error code == "EXECUTION_TIMEOUT" | Correct error |
| 3 | Verify response time ≈ 2s (not 5s) | Timeout enforced |

---

#### IT-010: execute_dynamic_tool — upstream error forwarded

| Field | Value |
|-------|-------|
| **ID** | IT-010 |
| **Priority** | High |
| **Type** | Integration — Exception |
| **Requirement** | EF-07, BR-11 |
| **Preconditions** | MockUpstreamServer configured to return error for "failing_tool" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "failing_tool", arguments: {})` | Error response |
| 2 | Verify error code == "UPSTREAM_ERROR" | Correct error |
| 3 | Verify upstream error message preserved | Pass-through |

---

#### IT-011: execute_dynamic_tool — schema validation with real JSON Schema

| Field | Value |
|-------|-------|
| **ID** | IT-011 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | EF-08, BR-13 |
| **Preconditions** | Tool "read_logs" has input_schema requiring `path` (string, required). Validation enabled. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "read_logs", arguments: {})` (missing required "path") | Error response |
| 2 | Verify error code == "INVALID_PARAMS" | Correct error |
| 3 | Verify error message mentions missing "path" | Descriptive |

---

### 3.3 Tool Registration & Indexing Integration

#### IT-012: indexing — scan mock upstream server and index tools

| Field | Value |
|-------|-------|
| **ID** | IT-012 |
| **Priority** | High |
| **Type** | Integration — Happy Path |
| **Requirement** | UC-03, STORY-3, BR-18 |
| **Preconditions** | MockUpstreamServer with 5 tools. Qdrant Testcontainer running. Mock EmbeddingService. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger tool indexing for mock server | Indexing completes |
| 2 | Verify Qdrant collection has 5 points | All tools indexed |
| 3 | Verify ToolRegistry has 5 entries | Registry updated |
| 4 | Verify each point has correct payload (name, description, input_schema, server_name) | Metadata correct |

---

#### IT-013: indexing — incremental update (add new tool)

| Field | Value |
|-------|-------|
| **ID** | IT-013 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | BR-14 |
| **Preconditions** | 5 tools already indexed. MockUpstreamServer now returns 6 tools (1 new). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger re-indexing | Indexing completes |
| 2 | Verify Qdrant collection has 6 points | New tool added |
| 3 | Verify ToolRegistry has 6 entries | Registry updated |
| 4 | Verify only 1 new embedding generated (not 6) | Incremental |

---

#### IT-014: indexing — incremental update (remove tool)

| Field | Value |
|-------|-------|
| **ID** | IT-014 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | BR-14 |
| **Preconditions** | 5 tools indexed. MockUpstreamServer now returns 4 tools (1 removed). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger re-indexing | Indexing completes |
| 2 | Verify Qdrant collection has 4 points | Removed tool deleted |
| 3 | Verify ToolRegistry has 4 entries | Registry updated |

---

#### IT-015: indexing — server unreachable during scan skipped

| Field | Value |
|-------|-------|
| **ID** | IT-015 |
| **Priority** | High |
| **Type** | Integration — Resilience |
| **Requirement** | AF-08, BR-15 |
| **Preconditions** | 2 servers configured. Server A reachable (5 tools). Server B unreachable. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger indexing for all servers | Indexing completes (partial) |
| 2 | Verify Server A's 5 tools indexed | Successful server indexed |
| 3 | Verify Server B marked as ERROR | Failed server flagged |
| 4 | Verify warning logged for Server B | Warning present |

---

### 3.4 Configuration Management Integration

#### IT-016: hot-reload — new server added via config change

| Field | Value |
|-------|-------|
| **ID** | IT-016 |
| **Priority** | Medium |
| **Type** | Integration — Happy Path |
| **Requirement** | UC-04, BR-20 |
| **Preconditions** | Orchestrator running with 1 server. New MockUpstreamServer ready. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Modify config file to add new server | Config file changed |
| 2 | Wait for config watcher to detect change | Reload triggered |
| 3 | Verify new server connected | Connection established |
| 4 | Verify new server's tools indexed | Tools searchable |
| 5 | Verify existing server unaffected | No disruption |

---

#### IT-017: hot-reload — server removed via config change

| Field | Value |
|-------|-------|
| **ID** | IT-017 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | UC-04 |
| **Preconditions** | Orchestrator running with 2 servers |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Modify config to remove server B | Config changed |
| 2 | Wait for reload | Reload triggered |
| 3 | Verify server B disconnected | Connection closed |
| 4 | Verify server B's tools removed from registry and Qdrant | Tools cleaned up |
| 5 | Verify server A unaffected | No disruption |

---

#### IT-018: hot-reload — invalid config rejected

| Field | Value |
|-------|-------|
| **ID** | IT-018 |
| **Priority** | High |
| **Type** | Integration — Exception |
| **Requirement** | EF-11, BR-21 |
| **Preconditions** | Orchestrator running with valid config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Replace config file with invalid YAML | File changed |
| 2 | Wait for config watcher | Reload attempted |
| 3 | Verify error logged | Error present |
| 4 | Verify previous config still active | No disruption |
| 5 | Verify all servers still connected | No state change |

---

#### IT-019: config — environment variable substitution

| Field | Value |
|-------|-------|
| **ID** | IT-019 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | TDD §7.5 (Secrets Management) |
| **Preconditions** | Config file with `${OPENAI_API_KEY}` reference. Env var set. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Load config with env var reference | Config loaded |
| 2 | Verify config.embedding.apiKey == actual env var value | Substituted |
| 3 | Verify env var value NOT in any log output | Not logged |

---

### 3.5 Health Monitoring Integration

#### IT-020: health check — detect server disconnect and auto-reconnect

| Field | Value |
|-------|-------|
| **ID** | IT-020 |
| **Priority** | High |
| **Type** | Integration — Lifecycle |
| **Requirement** | UC-05, BR-24 |
| **Preconditions** | MockUpstreamServer running. Health check interval = 1s (for test speed). |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify server state == CONNECTED | Initial state |
| 2 | Kill MockUpstreamServer process | Server down |
| 3 | Wait for health check cycle (1s) | Health check runs |
| 4 | Verify server state == DISCONNECTED | State transition |
| 5 | Restart MockUpstreamServer | Server back up |
| 6 | Wait for reconnect attempt | Auto-reconnect triggered |
| 7 | Verify server state == CONNECTED | Reconnected |
| 8 | Verify tools re-indexed | Tools available again |

---

#### IT-021: health check — exponential backoff timing

| Field | Value |
|-------|-------|
| **ID** | IT-021 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | BR-24 |
| **Preconditions** | Server DISCONNECTED. Auto-reconnect enabled. Mock reconnect always fails. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Observe reconnect attempt 1 | Delay ≈ 1s |
| 2 | Observe reconnect attempt 2 | Delay ≈ 2s |
| 3 | Observe reconnect attempt 3 | Delay ≈ 4s |
| 4 | Verify exponential pattern | 1s, 2s, 4s, 8s... |

---

#### IT-022: health check — max reconnect attempts → ERROR state

| Field | Value |
|-------|-------|
| **ID** | IT-022 |
| **Priority** | High |
| **Type** | Integration — State Transition |
| **Requirement** | BR-25 |
| **Preconditions** | Server DISCONNECTED. maxReconnectAttempts = 3. Mock reconnect always fails. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Wait for 3 reconnect attempts | All fail |
| 2 | Verify server state == ERROR | Final state |
| 3 | Verify no further reconnect attempts | Stopped |

---

### 3.6 MCP Protocol Integration

#### IT-023: MCP initialize handshake

| Field | Value |
|-------|-------|
| **ID** | IT-023 |
| **Priority** | High |
| **Type** | Integration — Protocol |
| **Requirement** | TDD §3.4.1 |
| **Preconditions** | Ktor testApplication running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `initialize` JSON-RPC request | Response received |
| 2 | Verify response.protocolVersion == "2024-11-05" | Correct version |
| 3 | Verify response.capabilities.tools exists | Tools capability |
| 4 | Verify response.serverInfo.name == "mcp-orchestrator" | Correct name |

---

#### IT-024: MCP tools/list returns exactly 2 tools

| Field | Value |
|-------|-------|
| **ID** | IT-024 |
| **Priority** | High |
| **Type** | Integration — Protocol |
| **Requirement** | TDD §3.4.2 |
| **Preconditions** | Ktor testApplication running, initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `tools/list` JSON-RPC request | Response received |
| 2 | Verify response.tools.size == 2 | Exactly 2 tools |
| 3 | Verify tools contain "find_tools" and "execute_dynamic_tool" | Correct names |
| 4 | Verify each tool has description and inputSchema | Full definitions |

---

#### IT-025: MCP ping returns empty result

| Field | Value |
|-------|-------|
| **ID** | IT-025 |
| **Priority** | Medium |
| **Type** | Integration — Protocol |
| **Requirement** | TDD §9.3 |
| **Preconditions** | Ktor testApplication running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `ping` JSON-RPC request | Response received |
| 2 | Verify response.result == {} | Empty result (pong) |

---


## 4. End-to-End API Tests (E2E-API)

### 4.1 Full Discovery Flow

#### E2E-001: Full discovery flow — index → search → results

| Field | Value |
|-------|-------|
| **ID** | E2E-001 |
| **Priority** | High |
| **Type** | E2E-API — Happy Path |
| **Requirement** | UC-01, UC-03, STORY-1, STORY-3 |
| **Preconditions** | Full Orchestrator process started. Qdrant container running. 3 MockUpstreamServers with 5 tools each (15 total). Mock OpenAI embedding service. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator with test config pointing to 3 mock servers | Orchestrator starts, connects to all 3 servers |
| 2 | Wait for indexing to complete (log: "Indexed X tools from Y servers") | 15 tools indexed |
| 3 | Send MCP `initialize` via TestMcpClient | Handshake successful |
| 4 | Send `tools/list` | Returns 2 tools (find_tools, execute_dynamic_tool) |
| 5 | Send `find_tools(query: "read application logs")` | Returns matching tools with similarity scores |
| 6 | Verify results include "read_logs" tool from mock server | Correct tool found |
| 7 | Verify result has name, description, input_schema, server_name, similarity_score | All fields present |
| 8 | Verify response time < 500ms | Performance target met |

**Test Data:** 3 mock servers with tools: log-server (read_logs, tail_logs, search_logs), jira-server (create_issue, search_issues, get_issue, update_issue, delete_issue), db-server (query_db, list_tables, describe_table, execute_sql, backup_db)
**Postconditions:** Orchestrator process stopped, containers cleaned up

---

#### E2E-002: Full execution flow — discover → execute → verify

| Field | Value |
|-------|-------|
| **ID** | E2E-002 |
| **Priority** | High |
| **Type** | E2E-API — Happy Path |
| **Requirement** | UC-01, UC-02, STORY-1, STORY-2 |
| **Preconditions** | Full Orchestrator running with indexed tools |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "create a Jira ticket")` | Returns tools including "create_issue" |
| 2 | Extract tool name and input_schema from result | "create_issue" with schema |
| 3 | Send `execute_dynamic_tool(tool_name: "create_issue", arguments: {project_key: "TEST", summary: "Bug report", issue_type: "Bug"})` | Execution result received |
| 4 | Verify MockUpstreamServer (jira-server) received the `tools/call` request | Correct forwarding |
| 5 | Verify response content matches mock server response | Pass-through |
| 6 | Verify response._meta.upstream_server == "jira-server" | Correct metadata |
| 7 | Verify proxy overhead < 100ms (execution_time_ms - mock_delay) | Performance target |

---

#### E2E-003: Keyword fallback — Qdrant unavailable

| Field | Value |
|-------|-------|
| **ID** | E2E-003 |
| **Priority** | High |
| **Type** | E2E-API — Fallback |
| **Requirement** | AF-02, AF-03, BR-05 |
| **Preconditions** | Orchestrator started WITHOUT Qdrant container. Tools loaded in memory from mock servers. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator without Qdrant | Starts in degraded mode (warning logged) |
| 2 | Send `find_tools(query: "read logs")` | Response received (not error) |
| 3 | Verify response.searchMode == "keyword" | Keyword fallback |
| 4 | Verify results contain tools with "read" or "logs" in name/description | Keyword matching works |
| 5 | Send `execute_dynamic_tool(tool_name: "read_logs", arguments: {path: "/tmp"})` | Execution works normally |

---

#### E2E-004: Server disconnect and reconnect lifecycle

| Field | Value |
|-------|-------|
| **ID** | E2E-004 |
| **Priority** | High |
| **Type** | E2E-API — Lifecycle |
| **Requirement** | UC-05, BR-24, BR-25, BR-27 |
| **Preconditions** | Orchestrator running with 2 mock servers. Health check interval = 2s. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify both servers CONNECTED | Initial state |
| 2 | Send `find_tools(query: "read logs")` | Returns tools from both servers |
| 3 | Kill mock server A | Server A down |
| 4 | Wait 3s (health check detects) | Server A → DISCONNECTED |
| 5 | Send `find_tools(query: "read logs")` | Returns tools from both servers, server A tools flagged DISCONNECTED |
| 6 | Send `execute_dynamic_tool(tool_name: "read_logs")` (hosted on server A) | SERVER_UNAVAILABLE error |
| 7 | Restart mock server A | Server A back up |
| 8 | Wait for auto-reconnect | Server A → CONNECTED |
| 9 | Send `execute_dynamic_tool(tool_name: "read_logs")` | Execution succeeds |

---

#### E2E-005: Config hot-reload — add and remove server

| Field | Value |
|-------|-------|
| **ID** | E2E-005 |
| **Priority** | Medium |
| **Type** | E2E-API — Lifecycle |
| **Requirement** | UC-04, BR-20 |
| **Preconditions** | Orchestrator running with 1 mock server. New MockUpstreamServer ready. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify 5 tools indexed (from server A) | Initial state |
| 2 | Modify config file to add server B (5 more tools) | Config changed |
| 3 | Wait for hot-reload | Reload triggered |
| 4 | Verify 10 tools now indexed | Server B tools added |
| 5 | Send `find_tools(query: "create issue")` | Returns tools from both servers |
| 6 | Modify config to remove server A | Config changed |
| 7 | Wait for hot-reload | Reload triggered |
| 8 | Verify 5 tools indexed (only server B) | Server A tools removed |
| 9 | Verify no requests dropped during reload | Zero downtime |

---

#### E2E-006: Concurrent find_tools requests — performance

| Field | Value |
|-------|-------|
| **ID** | E2E-006 |
| **Priority** | Medium |
| **Type** | E2E-API — Performance |
| **Requirement** | BR-01, FSD §8 |
| **Preconditions** | Orchestrator running with 100 tools indexed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 50 concurrent `find_tools` requests with different queries | All requests sent |
| 2 | Wait for all responses | All 50 responses received |
| 3 | Verify all responses are valid (no errors) | 100% success |
| 4 | Verify p95 latency < 500ms | Performance target met |
| 5 | Verify no response has empty tools (all queries should match something) | Results present |

**Test Data:** 50 different query strings targeting various tool categories

---

#### E2E-007: Execution timeout with slow upstream

| Field | Value |
|-------|-------|
| **ID** | E2E-007 |
| **Priority** | High |
| **Type** | E2E-API — Exception |
| **Requirement** | EF-06, BR-09 |
| **Preconditions** | MockUpstreamServer configured with 60s delay for "slow_tool". Timeout = 5s. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "slow_tool", arguments: {})` | Error response |
| 2 | Verify error code == "EXECUTION_TIMEOUT" | Correct error |
| 3 | Verify response received within ~5s (not 60s) | Timeout enforced |
| 4 | Verify Orchestrator still responsive after timeout | No hang |
| 5 | Send another `find_tools` request | Works normally |

---

#### E2E-008: Tool not found error

| Field | Value |
|-------|-------|
| **ID** | E2E-008 |
| **Priority** | High |
| **Type** | E2E-API — Exception |
| **Requirement** | EF-04 |
| **Preconditions** | Orchestrator running with indexed tools |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `execute_dynamic_tool(tool_name: "nonexistent_tool_xyz", arguments: {})` | Error response |
| 2 | Verify error code == "TOOL_NOT_FOUND" | Correct error |
| 3 | Verify error message contains "nonexistent_tool_xyz" | Tool name in message |
| 4 | Verify error message suggests using find_tools | Helpful message |

---

#### E2E-009: Startup with no servers reachable

| Field | Value |
|-------|-------|
| **ID** | E2E-009 |
| **Priority** | Medium |
| **Type** | E2E-API — Resilience |
| **Requirement** | EF-10 |
| **Preconditions** | Config has 2 servers, both unreachable |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator | Starts successfully (not crash) |
| 2 | Verify critical error logged | Error logged |
| 3 | Send `tools/list` | Returns 2 tools (find_tools, execute_dynamic_tool) |
| 4 | Send `find_tools(query: "anything")` | Returns empty results (no tools indexed) |
| 5 | Verify health monitor is running and will retry | Retry scheduled |

---

#### E2E-010: Startup with Qdrant and index 100 tools under 5s

| Field | Value |
|-------|-------|
| **ID** | E2E-010 |
| **Priority** | Medium |
| **Type** | E2E-API — Performance |
| **Requirement** | FSD §8 (Indexing < 5s for 100 tools) |
| **Preconditions** | MockUpstreamServer with 100 tools. Qdrant container running. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator and measure time to "Ready" state | Startup completes |
| 2 | Verify 100 tools indexed | All tools present |
| 3 | Verify total startup time < 10s | Startup target met |
| 4 | Verify indexing time < 5s (from log timestamps) | Indexing target met |

---

#### E2E-011: Security — secrets not in logs

| Field | Value |
|-------|-------|
| **ID** | E2E-011 |
| **Priority** | High |
| **Type** | E2E-API — Security |
| **Requirement** | FSD §7, TDD §7.3 |
| **Preconditions** | Orchestrator running with OPENAI_API_KEY set. Log output captured. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator with OPENAI_API_KEY="sk-test-secret-key-12345" | Starts normally |
| 2 | Trigger tool indexing (which calls embedding service) | Indexing completes |
| 3 | Send several find_tools and execute_dynamic_tool requests | Requests processed |
| 4 | Scan ALL log output for "sk-test-secret-key-12345" | NOT FOUND in logs |
| 5 | Scan ALL log output for any string matching `sk-*` pattern | NOT FOUND (redacted) |

---

#### E2E-012: MCP protocol compliance — full session lifecycle

| Field | Value |
|-------|-------|
| **ID** | E2E-012 |
| **Priority** | High |
| **Type** | E2E-API — Protocol |
| **Requirement** | TDD §3.4 |
| **Preconditions** | Full Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `initialize` with protocolVersion "2024-11-05" | Success response with matching version |
| 2 | Send `notifications/initialized` | No response (notification) |
| 3 | Send `tools/list` | Returns 2 tools with full schemas |
| 4 | Send `ping` | Returns empty result |
| 5 | Send `tools/call` with `find_tools` | Valid response |
| 6 | Send `tools/call` with `execute_dynamic_tool` | Valid response |
| 7 | Verify all JSON-RPC responses have `jsonrpc: "2.0"` and matching `id` | Protocol compliant |

---

#### E2E-013: Input validation — boundary values

| Field | Value |
|-------|-------|
| **ID** | E2E-013 |
| **Priority** | Medium |
| **Type** | E2E-API — Boundary |
| **Requirement** | EF-01, EF-02, BR-02, BR-03 |
| **Preconditions** | Orchestrator running with indexed tools |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "")` | INVALID_PARAMS error |
| 2 | Send `find_tools(query: "a")` (min valid) | Valid response |
| 3 | Send `find_tools(query: "a" * 2000)` (max valid) | Valid response |
| 4 | Send `find_tools(query: "a" * 2001)` (over max) | INVALID_PARAMS error |
| 5 | Send `find_tools(query: "test", top_k: 0)` | Clamped to 1 or error |
| 6 | Send `find_tools(query: "test", top_k: 1)` (min valid) | Returns ≤ 1 result |
| 7 | Send `find_tools(query: "test", top_k: 20)` (max valid) | Returns ≤ 20 results |
| 8 | Send `find_tools(query: "test", top_k: 21)` | Clamped to 20 or error |
| 9 | Send `find_tools(query: "test", threshold: 0.0)` | Returns all tools above 0.0 |
| 10 | Send `find_tools(query: "test", threshold: 1.0)` | Returns only exact matches (likely empty) |

---

#### E2E-014: Memory usage with 1000 tools

| Field | Value |
|-------|-------|
| **ID** | E2E-014 |
| **Priority** | Low |
| **Type** | E2E-API — Performance |
| **Requirement** | FSD §8 (< 256 MB heap for 1000 tools) |
| **Preconditions** | MockUpstreamServers providing 1000 tools total. JVM started with -Xmx256m. |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator with 1000 tools | Starts without OutOfMemoryError |
| 2 | Verify all 1000 tools indexed | All present |
| 3 | Send 100 find_tools requests | All succeed |
| 4 | Check JVM heap usage | < 256 MB |

---

#### E2E-015: Execution logging — metrics captured

| Field | Value |
|-------|-------|
| **ID** | E2E-015 |
| **Priority** | Medium |
| **Type** | E2E-API — Observability |
| **Requirement** | BR-12, TDD §9.1 |
| **Preconditions** | Orchestrator running with log output captured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `find_tools(query: "read logs")` | Response received |
| 2 | Verify log contains: query (truncated), result_count, search_mode, duration_ms | Discovery logged |
| 3 | Send `execute_dynamic_tool(tool_name: "read_logs", arguments: {path: "/tmp"})` | Response received |
| 4 | Verify log contains: tool_name, server_name, duration_ms, success | Execution logged |
| 5 | Verify log does NOT contain argument values (path: "/tmp") | Arguments not logged |

---


## 5. Requirements Traceability Matrix (RTM)

### 5.1 Use Case Coverage

| Requirement | Source | PBT | UT | IT | E2E-API | Status |
|-------------|--------|-----|----|----|---------|--------|
| UC-01: find_tools (semantic search) | FSD §3.1 | PBT-001..004, 009, 011, 012 | UT-001..008 | IT-001..006 | E2E-001, 003, 006, 013 | ✅ Covered |
| UC-02: execute_dynamic_tool (proxy) | FSD §3.2 | PBT-005 | UT-009..016 | IT-007..011 | E2E-002, 007, 008 | ✅ Covered |
| UC-03: Tool Registration & Indexing | FSD §3.3 | PBT-008 | UT-017..019 | IT-012..015 | E2E-001, 010 | ✅ Covered |
| UC-04: Configuration Management | FSD §3.4 | PBT-007 | UT-026..029 | IT-016..019 | E2E-005 | ✅ Covered |
| UC-05: Health Monitoring | FSD §3.5 | PBT-010 | UT-030..033 | IT-020..022 | E2E-004 | ✅ Covered |

### 5.2 Business Rule Coverage

| Rule | Description | Test Cases | Status |
|------|-------------|------------|--------|
| BR-01 | find_tools < 500ms p95 | E2E-001, E2E-006 | ✅ Covered |
| BR-02 | Top-K configurable (1–20) | PBT-001, PBT-011, IT-003, E2E-013 | ✅ Covered |
| BR-03 | Similarity threshold configurable (0.0–1.0) | PBT-004, PBT-012, IT-003, E2E-013 | ✅ Covered |
| BR-04 | Results include name, description, input_schema | UT-001, IT-001, E2E-001 | ✅ Covered |
| BR-05 | Keyword fallback when VDB down | UT-005, IT-002, E2E-003 | ✅ Covered |
| BR-06 | Results sorted by similarity descending | PBT-002, PBT-003, IT-001 | ✅ Covered |
| BR-07 | DISCONNECTED server tools flagged | UT-007, E2E-004 | ✅ Covered |
| BR-08 | Proxy overhead < 100ms | E2E-002 | ✅ Covered |
| BR-09 | Execution timeout configurable (default 30s) | UT-012, IT-009, E2E-007 | ✅ Covered |
| BR-10 | Tool name case-sensitive exact match | PBT-005, UT-020 | ✅ Covered |
| BR-11 | Upstream errors passed through | UT-013, IT-010 | ✅ Covered |
| BR-12 | All executions logged | E2E-015 | ✅ Covered |
| BR-13 | Schema validation optional/configurable | UT-014, UT-015, IT-011 | ✅ Covered |
| BR-14 | Incremental indexing (add/remove/update) | UT-018, UT-019, IT-013, IT-014 | ✅ Covered |
| BR-15 | Indexing resilient — one server failure doesn't block others | IT-015 | ✅ Covered |
| BR-16 | Composite key: server_name + tool_name | PBT-008, UT-017 | ✅ Covered |
| BR-17 | Same embedding model for indexing and query | UT-022 (implicit) | ✅ Covered |
| BR-18 | Indexing at startup and on config change | IT-012, IT-016, E2E-001 | ✅ Covered |
| BR-19 | Config supports YAML and JSON | UT-026 | ✅ Covered |
| BR-20 | Hot-reload without downtime | IT-016, IT-017, E2E-005 | ✅ Covered |
| BR-21 | Invalid config rejected, keep previous | UT-029, IT-018 | ✅ Covered |
| BR-22 | stdio and HTTP transport supported | IT-007, IT-008 | ✅ Covered |
| BR-23 | Health check interval configurable | UT-030 (implicit) | ✅ Covered |
| BR-24 | Exponential backoff for reconnect | PBT-010, UT-032, IT-021 | ✅ Covered |
| BR-25 | Max reconnect attempts configurable | UT-033, IT-022 | ✅ Covered |
| BR-26 | State transitions logged | UT-031 | ✅ Covered |
| BR-27 | DISCONNECTED server tools remain in search | UT-007, E2E-004 | ✅ Covered |

### 5.3 Error Code Coverage

| Error Code | Source | Test Cases | Status |
|------------|--------|------------|--------|
| INVALID_PARAMS (empty query) | EF-01 | PBT-009, UT-002, E2E-013 | ✅ Covered |
| INVALID_PARAMS (query too long) | EF-02 | PBT-009, UT-003, E2E-013 | ✅ Covered |
| INTERNAL_ERROR | EF-03 | IT-006 | ✅ Covered |
| TOOL_NOT_FOUND | EF-04 | UT-010, E2E-008 | ✅ Covered |
| SERVER_UNAVAILABLE | EF-05 | UT-011, E2E-004 | ✅ Covered |
| EXECUTION_TIMEOUT | EF-06 | UT-012, IT-009, E2E-007 | ✅ Covered |
| UPSTREAM_ERROR | EF-07 | UT-013, IT-010 | ✅ Covered |
| INVALID_PARAMS (schema validation) | EF-08 | UT-014, IT-011 | ✅ Covered |
| VECTOR_DB_UNAVAILABLE | FSD §9 | UT-005, IT-002, E2E-003 | ✅ Covered |

### 5.4 Story Acceptance Criteria Coverage

| Story | AC | Test Cases | Status |
|-------|-----|------------|--------|
| STORY-1 | AC1: Semantic search on Vector DB | IT-001, E2E-001 | ✅ |
| STORY-1 | AC2: Top-K configurable (default 5) | PBT-001, IT-003 | ✅ |
| STORY-1 | AC3: Results include name, description, input_schema | UT-001, E2E-001 | ✅ |
| STORY-1 | AC4: Response time < 500ms (p95) | E2E-006 | ✅ |
| STORY-1 | AC5: Keyword fallback if VDB unavailable | UT-005, E2E-003 | ✅ |
| STORY-2 | AC1: Routes to correct upstream server | UT-009, IT-007 | ✅ |
| STORY-2 | AC2: Proxy overhead < 100ms | E2E-002 | ✅ |
| STORY-2 | AC3: Error codes (TOOL_NOT_FOUND, etc.) | UT-010..013, E2E-007, E2E-008 | ✅ |
| STORY-2 | AC4: stdio and HTTP transport | IT-007, IT-008 | ✅ |
| STORY-2 | AC5: Transparent proxy (as-is response) | UT-009, IT-007 | ✅ |
| STORY-3 | AC1: Auto-scan and extract metadata | IT-012 | ✅ |
| STORY-3 | AC2: Vectorize descriptions | UT-022, IT-012 | ✅ |
| STORY-3 | AC3: Store in Vector DB | UT-025, IT-012 | ✅ |
| STORY-3 | AC4: Incremental updates | IT-013, IT-014 | ✅ |
| STORY-3 | AC5: Resilient to server failures | IT-015 | ✅ |
| STORY-4 | AC1: Config via application.yml | UT-026 | ✅ |
| STORY-4 | AC2: Hot-reload | UT-028, IT-016, E2E-005 | ✅ |
| STORY-4 | AC3: stdio and HTTP transport types | IT-007, IT-008 | ✅ |
| STORY-4 | AC4: Validate config on startup | UT-027 | ✅ |
| STORY-5 | AC1: Periodic health checks | UT-030, IT-020 | ✅ |
| STORY-5 | AC2: Server states (CONNECTED, etc.) | UT-031..033, IT-020, E2E-004 | ✅ |
| STORY-5 | AC3: Auto-reconnect | UT-032, IT-020, E2E-004 | ✅ |
| STORY-5 | AC4: Health status exposed | IT-025 (ping) | ✅ |
| STORY-6 | AC1: input_schema in results | UT-001, E2E-001 | ✅ |
| STORY-6 | AC2: Original schema (not modified) | IT-012 | ✅ |
| STORY-7 | AC1: Auto tools/list on connect | IT-012 | ✅ |
| STORY-7 | AC2: Extract name, description, inputSchema | IT-012 | ✅ |
| STORY-7 | AC3: Index into Vector DB | IT-012 | ✅ |
| STORY-7 | AC4: Re-index on reconnect | IT-020 | ✅ |

### 5.5 Coverage Summary

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases (UC-01..UC-05) | 5 | 5 | 100% |
| Business Rules (BR-01..BR-27) | 27 | 27 | 100% |
| Error Codes | 9 | 9 | 100% |
| Story Acceptance Criteria | 28 | 28 | 100% |
| Alternative Flows (AF-01..AF-10) | 10 | 8 | 80% |
| Exception Flows (EF-01..EF-12) | 12 | 10 | 83% |
| **Overall** | **91** | **87** | **96%** |

> **Note:** AF-09 (Embedding unavailable during indexing) and AF-10 (Incremental update — already indexed) are implicitly covered by IT-013/IT-015. EF-09 (Vector DB completely unavailable) is covered by E2E-003. EF-10 (All servers unreachable) is covered by E2E-009. EF-12 (New server unreachable) is covered by IT-015.

---

## 6. Appendix

### 6.1 Test Data Files

| File | Description | Used By |
|------|-------------|---------|
| `testdata/tool-definitions.json` | 15 sample MCP tool definitions (5 per mock server) | IT-001..015, E2E-001..015 |
| `testdata/test-config.yml` | Test application.yml with mock server configs | IT-016..019, E2E-005 |
| `testdata/invalid-config.yml` | Invalid YAML for error testing | UT-027, IT-018 |
| `testdata/boundary-queries.csv` | Boundary value queries (empty, max length, special chars) | PBT-009, E2E-013 |
| `testdata/mock-embeddings.json` | Pre-computed 768-dim vectors for deterministic testing | IT-001..006, E2E-001..015 |

### 6.2 Test Environment Setup

```bash
# Start Qdrant container for IT/E2E tests
docker run -d --name qdrant-test -p 6333:6333 qdrant/qdrant:latest

# Run all tests
./gradlew test

# Run specific test level
./gradlew test --tests "*.pbt.*"     # PBT only
./gradlew test --tests "*.unit.*"    # UT only
./gradlew test --tests "*.it.*"      # IT only
./gradlew test --tests "*.e2e.*"     # E2E-API only
```

### 6.3 Mock Server Configuration

```yaml
# test-config.yml
orchestrator:
  server:
    transport: stdio
  discovery:
    top_k: 5
    similarity_threshold: 0.7
    fallback_to_keyword: true
  execution:
    timeout_seconds: 5  # Short timeout for tests
    validate_arguments: true
  embedding:
    provider: mock  # Uses MockEmbeddingService
  vector_db:
    provider: qdrant
    host: localhost
    port: 6333
    collection_name: mcp_tools_test
  health:
    check_interval_seconds: 2  # Fast checks for tests
    auto_reconnect: true
    max_reconnect_attempts: 3
  upstream_servers:
    - name: log-server
      transport: stdio
      command: mock-mcp-server
      args: ["--tools", "testdata/log-tools.json"]
    - name: jira-server
      transport: stdio
      command: mock-mcp-server
      args: ["--tools", "testdata/jira-tools.json"]
    - name: db-server
      transport: http
      url: "http://localhost:9999/mcp"
```

### 6.4 Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing — tests that verify invariants with random inputs |
| UT | Unit Testing — tests for individual functions/methods in isolation |
| IT | Integration Testing — tests for component interactions with real/mocked dependencies |
| E2E-API | End-to-End API Testing — tests against a fully running server instance |
| MCP | Model Context Protocol — open standard for AI tool communication |
| JSON-RPC | JSON Remote Procedure Call — the wire protocol used by MCP |
| Vector DB | Vector Database for semantic similarity search |
| ANN | Approximate Nearest Neighbor — fast vector search algorithm |
| RTM | Requirements Traceability Matrix |
| STP | Software Test Plan |
| STC | Software Test Cases |
