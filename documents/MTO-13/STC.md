# Software Test Cases (STC)

## MCPOrchestration â MTO-13: HTTP Streamable Transport & Multi-Module Architecture

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-13 |
| Title | HTTP Streamable Transport & Multi-Module Architecture |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-06 |
| Status | Draft |
| Related STP | STP-v1.0-MTO-13.docx |
| Related FSD | documents/MTO-13/FSD.md |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-06 | QA Agent | Initiate document â 103 test cases covering 56 acceptance criteria across 9 parts |

---

## Test Case Summary

| Category | ID Range | Count | Priority |
|----------|----------|-------|----------|
| Functional â Happy Path | TC-001 to TC-020 | 20 | High |
| Functional â Alternative Flows | TC-100 to TC-112 | 13 | High |
| Functional â Exception/Error Flows | TC-200 to TC-215 | 16 | High |
| Business Rule Validation | TC-300 to TC-314 | 15 | High |
| Boundary & Negative Testing | TC-400 to TC-411 | 12 | Medium |
| Non-Functional (Performance, Security) | TC-600 to TC-611 | 12 | Medium |
| Integration Testing | TC-700 to TC-714 | 15 | High |

**Test Level Distribution:**

| Level | Prefix | Count |
|-------|--------|-------|
| PBT | PBT-01 to PBT-12 | 12 |
| UT | UT-01 to UT-35 | 35 |
| IT | IT-01 to IT-28 | 28 |
| E2E-API | E2E-API-01 to E2E-API-20 | 20 |
| SIT | SIT-01 to SIT-08 | 8 |

---

## 1. Functional Test Cases â Happy Path

### TC-001: HTTP Streamable â Initialize Session Successfully

| Field | Value |
|-------|-------|
| **ID** | TC-001 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #1, AC #2, AC #4, UC-A1 |
| **Preconditions** | Orchestrator running with `transport: httpstreamable` config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with JSON-RPC `initialize` request | HTTP 200 response |
| 2 | Verify response Content-Type header | `application/json` |
| 3 | Verify response contains `Mcp-Session-Id` header | Valid UUID v4 format |
| 4 | Verify response body is valid JSON-RPC 2.0 with `result.protocolVersion` | `"2025-03-26"` |
| 5 | Verify `result.capabilities.tools.listChanged` is `true` | Capabilities include tool list change notification |

**Test Data:** `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}`
**Postconditions:** Session created in server session store

---

### TC-002: HTTP Streamable â Execute Tool Call with JSON Response

| Field | Value |
|-------|-------|
| **ID** | TC-002 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #2, AC #3, UC-A2 |
| **Preconditions** | Valid session established (Mcp-Session-Id obtained) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with `tools/list` request + `Mcp-Session-Id` header | HTTP 200 |
| 2 | Verify Content-Type is `application/json` | Single JSON response (not SSE) |
| 3 | Verify response body contains `result.tools` array | Array of tool definitions |

**Test Data:** `{"jsonrpc":"2.0","id":2,"method":"tools/list"}` with header `Mcp-Session-Id: {session-id}`
**Postconditions:** Session last activity updated

---

### TC-003: HTTP Streamable â SSE Streaming Response

| Field | Value |
|-------|-------|
| **ID** | TC-003 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #3, UC-A2 AF-A2.1 |
| **Preconditions** | Valid session; client sends `Accept: text/event-stream` |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with `tools/call` + `Accept: text/event-stream` header | HTTP 200 |
| 2 | Verify Content-Type is `text/event-stream` | SSE stream response |
| 3 | Read SSE events | Each event has `id:` and `data:` fields |
| 4 | Verify event IDs are monotonically increasing | `evt-1`, `evt-2`, etc. |
| 5 | Verify final event signals completion | Last event contains complete result |

**Test Data:** Long-running tool call that produces streaming output
**Postconditions:** Events buffered in session for resumability

---

### TC-004: HTTP Streamable â Stream Resumability

| Field | Value |
|-------|-------|
| **ID** | TC-004 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #5, UC-A3 |
| **Preconditions** | Previous SSE stream was interrupted; client has Last-Event-ID |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with same request + `Last-Event-ID: evt-2` header | HTTP 200 |
| 2 | Verify server replays events after evt-2 | Receives evt-3, evt-4, etc. |
| 3 | Verify no duplicate events | evt-1 and evt-2 are NOT resent |

**Test Data:** `Last-Event-ID: evt-2` header on reconnection request
**Postconditions:** Stream continues from interruption point

---

### TC-005: HTTP Streamable â Backward Compatibility (stdio still works)

| Field | Value |
|-------|-------|
| **ID** | TC-005 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional / Regression |
| **Requirement** | AC #6, BR-A5 |
| **Preconditions** | Orchestrator configured with `transport: stdio` |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator with `transport: stdio` config | Server starts successfully |
| 2 | Send `initialize` via stdin | Receive valid JSON-RPC response on stdout |
| 3 | Send `tools/list` via stdin | Receive tool list on stdout |
| 4 | Send `tools/call` for a built-in tool | Receive execution result on stdout |

**Test Data:** Standard stdio JSON-RPC messages
**Postconditions:** stdio mode fully functional

---

### TC-006: HTTP Streamable â upload_file Works Over HTTP

| Field | Value |
|-------|-------|
| **ID** | TC-006 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #7, BR-A6 |
| **Preconditions** | HTTP Streamable session active; test file available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upload_file` tool via HTTP Streamable with file content | HTTP 200 |
| 2 | Verify file is received and processed correctly | Tool returns success with file metadata |
| 3 | Verify file content integrity | Content matches original |

**Test Data:** Binary file content sent via HTTP POST
**Postconditions:** File uploaded successfully

---

### TC-007: Hidden Tool â get_drawio_reference Discoverable via find_tools

| Field | Value |
|-------|-------|
| **ID** | TC-007 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Functional |
| **Requirement** | AC #8, UC-B1, BR-B1, BR-B2 |
| **Preconditions** | Orchestrator running; `.antigravity/steering/drawio.md` exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `tools/list` | `get_drawio_reference` is NOT in the list |
| 2 | Call `find_tools` with query "draw.io diagram reference" | `get_drawio_reference` appears in results |
| 3 | Call `get_drawio_reference` (no params) | Returns content of drawio.md file |
| 4 | Verify returned content is non-empty markdown | Content contains draw.io XML reference |

**Test Data:** Query: "draw.io diagram reference"
**Postconditions:** Agent has draw.io reference documentation

---

### TC-008: Hidden Tool â export_drawio Exports Diagram

| Field | Value |
|-------|-------|
| **ID** | TC-008 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Functional |
| **Requirement** | AC #9, UC-B2 |
| **Preconditions** | draw.io CLI installed; test .drawio file exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `export_drawio` with valid `file_path` and `format: "png"` | Success response |
| 2 | Verify response contains `output_path` | Absolute path to exported PNG |
| 3 | Verify response contains `bytes_written` > 0 | File has content |
| 4 | Verify exported file exists on disk | File is readable |

**Test Data:** `{"file_path": "test-fixtures/sample.drawio", "format": "png"}`
**Postconditions:** PNG file created at output_path

---

### TC-009: Gradle Multi-Module â All Modules Build Successfully

| Field | Value |
|-------|-------|
| **ID** | TC-009 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #10, AC #11, AC #12, AC #13 |
| **Preconditions** | Project refactored into multi-module structure |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew :orchestrator-core:build` | Build succeeds |
| 2 | Run `./gradlew :orchestrator-client:build` | Build succeeds |
| 3 | Run `./gradlew :orchestrator-server:build` | Build succeeds |
| 4 | Run `./gradlew :orchestrator-bridge:build` | Build succeeds |
| 5 | Run `./gradlew build` (root) | All modules build in correct order |

**Test Data:** N/A (build system test)
**Postconditions:** All JARs produced

---

### TC-010: Gradle Multi-Module â Existing Tests Pass After Refactor

| Field | Value |
|-------|-------|
| **ID** | TC-010 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Regression |
| **Requirement** | AC #14, BR-C3 |
| **Preconditions** | Multi-module refactor complete |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew test` (all modules) | All existing tests pass |
| 2 | Verify test count matches pre-refactor count | No tests lost |
| 3 | Verify no test modifications were needed | Tests run unmodified |

**Test Data:** Existing test suite
**Postconditions:** Zero regression

---

### TC-011: Kotlin Bridge â Starts and Connects to Orchestrator

| Field | Value |
|-------|-------|
| **ID** | TC-011 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #15, AC #16, AC #20 |
| **Preconditions** | Orchestrator running at http://localhost:8080/mcp |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Bridge: `java -jar mcp-bridge-all.jar --url http://localhost:8080/mcp` | Process starts |
| 2 | Send `initialize` via stdin to Bridge | Bridge responds with capabilities |
| 3 | Verify Bridge established HTTP Streamable connection to Orchestrator | Bridge logs show "Connected to Orchestrator" |
| 4 | Send `tools/list` via stdin | Bridge returns meta-tools (find_tools, execute_dynamic_tool) |

**Test Data:** Bridge JAR with Orchestrator URL
**Postconditions:** Bridge connected and serving stdio

---

### TC-012: Kotlin Bridge â Proxy find_tools and execute_dynamic_tool

| Field | Value |
|-------|-------|
| **ID** | TC-012 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #18, UC-D2 |
| **Preconditions** | Bridge connected to Orchestrator |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `find_tools` with query "search knowledge" via Bridge stdin | Bridge proxies to Orchestrator |
| 2 | Verify response contains matching tools | Tools from upstream servers returned |
| 3 | Call `execute_dynamic_tool` with a discovered tool | Bridge proxies execution |
| 4 | Verify execution result returned via stdout | Correct tool output |

**Test Data:** `{"tool_name": "kb_search_smart", "arguments": {"query": "test"}}`
**Postconditions:** Tool executed via Bridge proxy

---

### TC-013: Kotlin Bridge â File Content via HTTP Binary

| Field | Value |
|-------|-------|
| **ID** | TC-013 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #17 |
| **Preconditions** | Bridge connected; file-handling tool available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call a tool that involves file content via Bridge | Bridge transmits file via HTTP binary |
| 2 | Verify file is NOT base64-encoded in stdio transport | HTTP binary transfer used |
| 3 | Verify file content integrity at destination | Content matches original |

**Test Data:** Test file with known content and checksum
**Postconditions:** File transferred efficiently

---

### TC-014: Kotlin Bridge â Auto-Reconnect on Orchestrator Restart

| Field | Value |
|-------|-------|
| **ID** | TC-014 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #21, UC-D3 |
| **Preconditions** | Bridge connected to Orchestrator |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Stop Orchestrator process | Bridge detects connection loss |
| 2 | Restart Orchestrator | Orchestrator available again |
| 3 | Wait up to 15 seconds | Bridge auto-reconnects |
| 4 | Send `tools/list` via Bridge stdin | Bridge responds normally (reconnected) |

**Test Data:** N/A
**Postconditions:** Bridge reconnected with new session

---

### TC-015: Kotlin Bridge â Fat JAR Packaging

| Field | Value |
|-------|-------|
| **ID** | TC-015 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Functional |
| **Requirement** | AC #22, BR-C5 |
| **Preconditions** | Build completed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew :orchestrator-bridge:buildFatJar` | Build succeeds |
| 2 | Verify `mcp-bridge-all.jar` exists | File present in build output |
| 3 | Run `java -jar mcp-bridge-all.jar --help` | Shows usage information |

**Test Data:** N/A
**Postconditions:** Standalone JAR is functional

---

### TC-016: Node.js Bridge â Starts and Connects via stdio

| Field | Value |
|-------|-------|
| **ID** | TC-016 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Functional |
| **Requirement** | AC #23, AC #24, AC #28 |
| **Preconditions** | Node.js 20+ installed; Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set env `ORCHESTRATOR_URL=http://localhost:8080/mcp` | Environment configured |
| 2 | Start Node.js bridge via `npx` | Process starts, stdio ready |
| 3 | Send `initialize` via stdin | Bridge responds with capabilities |
| 4 | Verify HTTP Streamable connection to Orchestrator | Bridge connected |

**Test Data:** Environment variable `ORCHESTRATOR_URL`
**Postconditions:** Node.js bridge operational

---

### TC-017: Node.js Bridge â Proxy and Token Optimization

| Field | Value |
|-------|-------|
| **ID** | TC-017 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Functional |
| **Requirement** | AC #26, AC #27 |
| **Preconditions** | Node.js bridge connected |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `find_tools` via Node.js bridge | Tools discovered and returned |
| 2 | Call `execute_dynamic_tool` via bridge | Tool executed successfully |
| 3 | Verify token optimization (tools promoted after find_tools) | Promoted tools in tools/list |

**Test Data:** Query: "search knowledge base"
**Postconditions:** Tools promoted for direct access

---

### TC-018: Smart Tool Promotion â Orchestrator stdio Mode

| Field | Value |
|-------|-------|
| **ID** | TC-018 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #31, AC #33, AC #34, AC #35 |
| **Preconditions** | Orchestrator in stdio mode with smart-promotion enabled |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `tools/list` | Returns 6-7 meta-tools only |
| 2 | Call `find_tools` with query "jira" | Returns matching tools from upstream |
| 3 | Verify `notifications/tools/list_changed` sent | IDE notified of tool list change |
| 4 | Call `tools/list` again | Now includes promoted tools |
| 5 | Call promoted tool directly (e.g., `jira_get_issue`) | Tool executes successfully |

**Test Data:** Query targeting upstream Jira tools
**Postconditions:** Tools promoted and callable directly

---

### TC-019: Stream Write Tool â Write and Append Mode

| Field | Value |
|-------|-------|
| **ID** | TC-019 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | AC #42, AC #44, AC #46, AC #47 |
| **Preconditions** | Orchestrator running; target directory exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` with mode "write" and content "Section 1" | Success response |
| 2 | Verify response: `{file_path, bytes_written, total_size, mode: "write"}` | Correct metadata |
| 3 | Call `stream_write_file` with mode "append" and content "Section 2" | Success response |
| 4 | Verify `total_size` increased | File grew by appended content |
| 5 | Read file from disk | Contains "Section 1" + "Section 2" |

**Test Data:** `file_path: "C:/temp/test-output.md"`, content: "## Section 1\n"
**Postconditions:** File contains both sections

---

### TC-020: Embed Images Tool â Replace References with Base64

| Field | Value |
|-------|-------|
| **ID** | TC-020 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Functional |
| **Requirement** | AC #51, AC #52, AC #53 |
| **Preconditions** | Markdown file with image references exists; images exist on disk |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `embed_images` with markdown file path | Success response |
| 2 | Verify response contains `images_embedded` count > 0 | Images were processed |
| 3 | Verify output contains `data:image/png;base64,...` URIs | References replaced |
| 4 | Verify `images_failed` count | 0 (all images found) |

**Test Data:** Markdown file: `![logo](images/logo.png)\n![diagram](images/arch.png)`
**Postconditions:** Self-contained markdown with embedded images

---

## 2. Functional Test Cases â Alternative Flows

### TC-100: HTTP Streamable â SSE Response When Client Requests text/event-stream

| Field | Value |
|-------|-------|
| **ID** | TC-100 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #3, UC-A1 AF-A1.1, BR-A1 |
| **Preconditions** | Valid session established |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with `Accept: text/event-stream` header | HTTP 200 |
| 2 | Verify Content-Type is `text/event-stream` | SSE stream returned |
| 3 | Verify SSE events contain valid JSON-RPC responses | Properly formatted events |

**Test Data:** Header: `Accept: text/event-stream`
**Postconditions:** Client receives SSE stream

---

### TC-101: HTTP Streamable â Client Reconnects with Existing Session

| Field | Value |
|-------|-------|
| **ID** | TC-101 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #4, UC-A1 AF-A1.2 |
| **Preconditions** | Session previously created and still valid |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with existing valid `Mcp-Session-Id` | HTTP 200 |
| 2 | Verify session state is resumed | No re-initialization needed |
| 3 | Call `tools/list` with same session | Returns current tool list |

**Test Data:** Previously obtained session ID
**Postconditions:** Session continues without re-init

---

### TC-102: Smart Tool Promotion â Bridge Mode (2 Meta-Tools)

| Field | Value |
|-------|-------|
| **ID** | TC-102 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #32 |
| **Preconditions** | Bridge connected to Orchestrator |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `tools/list` on Bridge | Returns exactly 2 meta-tools: find_tools, execute_dynamic_tool |
| 2 | Call `find_tools` with a query | Tools discovered |
| 3 | Call `tools/list` again | Promoted tools now included |

**Test Data:** Query: "knowledge base search"
**Postconditions:** Bridge tool list expanded

---

### TC-103: Smart Tool Promotion â Incremental Growth

| Field | Value |
|-------|-------|
| **ID** | TC-103 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #36 |
| **Preconditions** | Some tools already promoted |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `find_tools` with query "jira" | Jira tools promoted |
| 2 | Call `tools/list` | Contains jira tools |
| 3 | Call `find_tools` with query "database" | DB tools promoted |
| 4 | Call `tools/list` | Contains BOTH jira AND database tools |

**Test Data:** Two different queries targeting different upstream servers
**Postconditions:** Tool list grew incrementally

---

### TC-104: Smart Tool Promotion â Compact Schema

| Field | Value |
|-------|-------|
| **ID** | TC-104 |
| **Level** | UT |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #37 |
| **Preconditions** | Tool with verbose schema available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote a tool with long description (>100 chars) | Tool promoted |
| 2 | Verify promoted tool description â¤ 100 characters | Description truncated |
| 3 | Verify only required parameters in promoted schema | Optional params stripped |

**Test Data:** Tool with 500-char description and 10 parameters (3 required)
**Postconditions:** Compact schema generated

---

### TC-105: Smart Tool Promotion â Direct Routing on Success

| Field | Value |
|-------|-------|
| **ID** | TC-105 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #40 |
| **Preconditions** | Tool promoted and previously executed successfully |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call promoted tool directly | Routed directly to upstream server |
| 2 | Verify no discovery overhead | No find_tools call made internally |
| 3 | Verify response time is minimal | Direct routing bypasses discovery |

**Test Data:** Previously promoted tool name
**Postconditions:** Tool executed via direct route

---

### TC-106: Node.js Bridge â File Content via HTTP Binary

| Field | Value |
|-------|-------|
| **ID** | TC-106 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #25 |
| **Preconditions** | Node.js bridge connected |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call tool with file content via Node.js bridge | File transmitted via HTTP binary |
| 2 | Verify file NOT base64-encoded in stdio | Efficient transfer |
| 3 | Verify file integrity | Content matches |

**Test Data:** Test file with known SHA-256 hash
**Postconditions:** File transferred correctly

---

### TC-107: Node.js Bridge â Auto-Reconnect

| Field | Value |
|-------|-------|
| **ID** | TC-107 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #29 |
| **Preconditions** | Node.js bridge connected |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Stop Orchestrator | Bridge detects disconnection |
| 2 | Restart Orchestrator | Available again |
| 3 | Wait for reconnection | Bridge reconnects automatically |
| 4 | Send request via bridge | Request succeeds |

**Test Data:** N/A
**Postconditions:** Bridge reconnected

---

### TC-108: Node.js Bridge â npx Packaging

| Field | Value |
|-------|-------|
| **ID** | TC-108 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #30 |
| **Preconditions** | npm package built |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `npx @orchestrator/mcp-bridge` | Bridge starts |
| 2 | Verify stdio interface is active | Accepts JSON-RPC input |
| 3 | Send `initialize` | Valid response received |

**Test Data:** Package published or linked locally
**Postconditions:** Bridge running via npx

---

### TC-109: Stream Write â Available on Both Orchestrator and Bridge

| Field | Value |
|-------|-------|
| **ID** | TC-109 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #43 |
| **Preconditions** | Both Orchestrator and Bridge running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` via Orchestrator directly | Success â file written |
| 2 | Call `stream_write_file` via Bridge | Success â file written locally |
| 3 | Verify both produce same behavior | Identical response format |

**Test Data:** Same file_path and content for both calls
**Postconditions:** Tool available on both components

---

### TC-110: Embed Images â Optional output_path

| Field | Value |
|-------|-------|
| **ID** | TC-110 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #52 |
| **Preconditions** | Markdown file with images exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `embed_images` WITHOUT output_path | Returns content in response body |
| 2 | Call `embed_images` WITH output_path | Saves result to specified file |
| 3 | Verify saved file content matches response content | Identical output |

**Test Data:** `output_path: "C:/temp/embedded.md"`
**Postconditions:** File saved when output_path provided

---

### TC-111: Large-Text Input Proxy â Detects Large Text Parameters

| Field | Value |
|-------|-------|
| **ID** | TC-111 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #54, AC #55 |
| **Preconditions** | Upstream tool with large-text parameter registered |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Register tool with param named "markdown" (no maxLength) | Detection triggered |
| 2 | Verify FileProxyDetector identifies param as large-text | Confidence 0.75 |
| 3 | Call tool with large text content (>10KB) | Content routed through file proxy |
| 4 | Verify content is raw text (NOT base64) | Text transfer, not binary |

**Test Data:** 15KB markdown content in "markdown" parameter
**Postconditions:** Large text handled efficiently

---

### TC-112: Large-Text Input Proxy â Detection Caching

| Field | Value |
|-------|-------|
| **ID** | TC-112 |
| **Level** | UT |
| **Priority** | Low |
| **Type** | Functional â Alternative Flow |
| **Requirement** | AC #56 |
| **Preconditions** | Tool detection already performed once |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call tool with large-text param (first time) | Detection runs, result cached |
| 2 | Call same tool again | Detection result from cache (no re-analysis) |
| 3 | Disconnect upstream server | Cache invalidated for that server |
| 4 | Reconnect and call again | Detection re-runs |

**Test Data:** Same tool called multiple times
**Postconditions:** Cache invalidation works correctly

---

## 3. Functional Test Cases â Exception/Error Flows

### TC-200: HTTP Streamable â Malformed JSON-RPC Request

| Field | Value |
|-------|-------|
| **ID** | TC-200 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-A1 EF-A1.1, Error Code -32700 |
| **Preconditions** | Orchestrator running with HTTP Streamable |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with invalid JSON body: `{not valid json` | HTTP 400 |
| 2 | Verify response contains JSON-RPC error code -32700 | Parse error |
| 3 | Verify error message: "Parse error" | Clear error description |

**Test Data:** `{not valid json`
**Postconditions:** No session created; server stable

---

### TC-201: HTTP Streamable â Invalid Session ID

| Field | Value |
|-------|-------|
| **ID** | TC-201 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-A2 EF-A2.1, Error Code -32001 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with `Mcp-Session-Id: invalid-not-uuid` | HTTP 404 |
| 2 | Verify JSON-RPC error code -32001 | SESSION_NOT_FOUND |
| 3 | Send with non-existent UUID: `Mcp-Session-Id: 00000000-0000-4000-8000-000000000000` | HTTP 404 |

**Test Data:** Invalid and non-existent session IDs
**Postconditions:** Request rejected; no state change

---

### TC-202: HTTP Streamable â Server Overloaded (Max Sessions)

| Field | Value |
|-------|-------|
| **ID** | TC-202 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-A1 EF-A1.2, Error Code -32003 |
| **Preconditions** | Server configured with max_sessions: 2 (for testing) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create session 1 (initialize) | Success |
| 2 | Create session 2 (initialize) | Success |
| 3 | Attempt to create session 3 | HTTP 503 |
| 4 | Verify `Retry-After` header present | Contains retry delay |
| 5 | Verify JSON-RPC error code -32003 | SERVER_OVERLOADED |

**Test Data:** max_sessions config set to 2
**Postconditions:** Existing sessions unaffected

---

### TC-203: HTTP Streamable â Last-Event-ID Not Found

| Field | Value |
|-------|-------|
| **ID** | TC-203 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-A3 EF-A3.1, Error Code -32002 |
| **Preconditions** | Valid session; event buffer has been cleared |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send POST `/mcp` with `Last-Event-ID: evt-99999` (non-existent) | HTTP 404 |
| 2 | Verify JSON-RPC error code -32002 | EVENT_NOT_FOUND |
| 3 | Verify error message indicates client must re-issue request | Clear guidance |

**Test Data:** `Last-Event-ID: evt-99999`
**Postconditions:** Client must retry without Last-Event-ID

---

### TC-204: Hidden Tool â File Not Found (get_drawio_reference)

| Field | Value |
|-------|-------|
| **ID** | TC-204 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-B1 EF-B1.1 |
| **Preconditions** | `.antigravity/steering/drawio.md` does NOT exist |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `get_drawio_reference` | Error response |
| 2 | Verify error code: `FILE_NOT_FOUND` | Specific error code |
| 3 | Verify message indicates expected path | Helpful error message |

**Test Data:** Remove/rename drawio.md before test
**Postconditions:** Server stable; clear error returned

---

### TC-205: Hidden Tool â draw.io CLI Not Installed

| Field | Value |
|-------|-------|
| **ID** | TC-205 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-B2 EF-B2.2 |
| **Preconditions** | draw.io CLI not available on system PATH |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `export_drawio` with valid file_path and format | Error response |
| 2 | Verify error code: `CLI_NOT_FOUND` | Specific error code |
| 3 | Verify message includes installation URL | "Install from https://www.drawio.com/" |

**Test Data:** `{"file_path": "test.drawio", "format": "png"}`
**Postconditions:** Graceful degradation

---

### TC-206: Hidden Tool â Export Failed (Invalid drawio file)

| Field | Value |
|-------|-------|
| **ID** | TC-206 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-B2 EF-B2.3 |
| **Preconditions** | draw.io CLI installed; file exists but is corrupted |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `export_drawio` with corrupted .drawio file | Error response |
| 2 | Verify error code: `EXPORT_FAILED` | Specific error code |
| 3 | Verify message contains stderr output from CLI | Diagnostic info |

**Test Data:** Corrupted XML file with .drawio extension
**Postconditions:** No partial output file created

---

### TC-207: Kotlin Bridge â Orchestrator Unreachable at Startup

| Field | Value |
|-------|-------|
| **ID** | TC-207 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-D1 AF-D1.1, EF-D1.1 |
| **Preconditions** | Orchestrator NOT running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Bridge with Orchestrator URL pointing to non-running server | Bridge starts in degraded mode |
| 2 | Send `tools/list` via stdin | Bridge returns error or empty list |
| 3 | Start Orchestrator | Orchestrator becomes available |
| 4 | Wait for auto-reconnect | Bridge connects and becomes functional |

**Test Data:** URL: `http://localhost:8080/mcp` (server not running)
**Postconditions:** Bridge recovers when Orchestrator starts

---

### TC-208: Smart Tool Promotion â Promoted Tool Fails (Auto-Demote)

| Field | Value |
|-------|-------|
| **ID** | TC-208 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | AC #39 |
| **Preconditions** | Tool promoted; upstream server becomes unavailable |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call promoted tool | Execution fails (upstream down) |
| 2 | Verify tool is auto-demoted | Removed from promoted list |
| 3 | Verify retry via `execute_dynamic_tool` | Fallback mechanism triggered |
| 4 | Verify `notifications/tools/list_changed` sent | IDE notified of demotion |

**Test Data:** Disconnect upstream server after promotion
**Postconditions:** Tool demoted; system stable

---

### TC-209: Stream Write â Invalid Path (Traversal Attempt)

| Field | Value |
|-------|-------|
| **ID** | TC-209 |
| **Level** | UT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | AC #48, AC #49 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` with path containing `..` | Error: INVALID_PATH |
| 2 | Call with relative path (no drive letter) | Error: INVALID_PATH |
| 3 | Call with path to non-existent parent directory | Error: OUTPUT_DIR_NOT_FOUND |

**Test Data:** Paths: `"C:/temp/../../../etc/passwd"`, `"relative/path.txt"`, `"C:/nonexistent/dir/file.txt"`
**Postconditions:** No file written; security maintained

---

### TC-210: Stream Write â Output Not Writable

| Field | Value |
|-------|-------|
| **ID** | TC-210 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Exception Flow |
| **Requirement** | AC #49 |
| **Preconditions** | Target file/directory is read-only |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` targeting a read-only file | Error: OUTPUT_NOT_WRITABLE |
| 2 | Verify error message is descriptive | Indicates permission issue |

**Test Data:** Read-only file path
**Postconditions:** No data written

---

### TC-211: HTTP Streamable â Invalid JSON-RPC Structure

| Field | Value |
|-------|-------|
| **ID** | TC-211 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | Error Code -32600 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send valid JSON but missing `jsonrpc` field | HTTP 400, error -32600 |
| 2 | Send with `jsonrpc: "1.0"` (wrong version) | HTTP 400, error -32600 |
| 3 | Send without `method` field | HTTP 400, error -32600 |

**Test Data:** `{"id":1,"method":"test"}` (missing jsonrpc field)
**Postconditions:** Request rejected cleanly

---

### TC-212: Hidden Tool â Invalid Format Parameter

| Field | Value |
|-------|-------|
| **ID** | TC-212 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-B2 EF-B2.4 |
| **Preconditions** | draw.io CLI installed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `export_drawio` with `format: "bmp"` (unsupported) | Error: INVALID_PARAMS |
| 2 | Verify message: "Format must be one of: png, svg, pdf" | Clear guidance |

**Test Data:** `{"file_path": "test.drawio", "format": "bmp"}`
**Postconditions:** No export attempted

---

### TC-213: Kotlin Bridge â HTTP Timeout

| Field | Value |
|-------|-------|
| **ID** | TC-213 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Functional â Exception Flow |
| **Requirement** | UC-D2 EF-D2.2 |
| **Preconditions** | Orchestrator configured with very slow response |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call tool via Bridge that takes >30s | Bridge returns timeout error |
| 2 | Verify error message suggests retry | Helpful error |
| 3 | Verify Bridge remains functional after timeout | Can process next request |

**Test Data:** Tool that sleeps for 60 seconds
**Postconditions:** Bridge stable after timeout

---

### TC-214: Smart Tool Promotion â Cache Invalidation on reset_tools

| Field | Value |
|-------|-------|
| **ID** | TC-214 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | AC #38 |
| **Preconditions** | Tools promoted in cache |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify promoted tools in `tools/list` | Tools present |
| 2 | Call `reset_tools` | Cache cleared |
| 3 | Call `tools/list` | Only meta-tools remain (promoted tools gone) |
| 4 | Verify `notifications/tools/list_changed` sent | IDE notified |

**Test Data:** N/A
**Postconditions:** Clean slate; tools must be re-discovered

---

### TC-215: Smart Tool Promotion â TTL Expiry

| Field | Value |
|-------|-------|
| **ID** | TC-215 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Functional â Exception Flow |
| **Requirement** | AC #38 |
| **Preconditions** | Tools promoted; TTL set to short duration for testing (e.g., 5 seconds) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote tools via `find_tools` | Tools in promoted list |
| 2 | Wait for TTL expiry (>5 seconds) | Cache cleanup runs |
| 3 | Call `tools/list` | Expired tools removed |
| 4 | Verify `notifications/tools/list_changed` sent | IDE notified of removal |

**Test Data:** TTL config: 5 seconds (test override)
**Postconditions:** Stale tools evicted

---

## 4. Business Rule Validation

### TC-300: BR-A1 â JSON Response Unless Client Requests SSE

| Field | Value |
|-------|-------|
| **ID** | TC-300 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-A1 |
| **Preconditions** | Valid session |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send request WITHOUT `Accept: text/event-stream` | Response is `application/json` |
| 2 | Send request WITH `Accept: text/event-stream` | Response MAY be `text/event-stream` |
| 3 | Send request with `Accept: application/json` | Response is `application/json` |

**Test Data:** Various Accept header values
**Postconditions:** Content negotiation works per BR-A1

---

### TC-301: BR-A2 â Session ID Must Be UUID v4

| Field | Value |
|-------|-------|
| **ID** | TC-301 |
| **Level** | PBT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-A2 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 100 sessions | All return Mcp-Session-Id |
| 2 | Verify each ID matches UUID v4 regex | `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$` |
| 3 | Verify all IDs are unique | No duplicates |

**Test Data:** 100 initialize requests
**Postconditions:** All sessions have valid UUID v4 IDs

---

### TC-302: BR-A3 â Session TTL (30 minutes default)

| Field | Value |
|-------|-------|
| **ID** | TC-302 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-A3 |
| **Preconditions** | Session created; TTL set to short value for testing |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create session | Session active |
| 2 | Wait for TTL expiry | Session expires |
| 3 | Use expired session ID | HTTP 404, SESSION_NOT_FOUND |

**Test Data:** TTL config override for testing
**Postconditions:** Expired session cleaned up

---

### TC-303: BR-A4 â SSE Event IDs Monotonically Increasing

| Field | Value |
|-------|-------|
| **ID** | TC-303 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-A4 |
| **Preconditions** | SSE stream active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Receive multiple SSE events | Events have `id` field |
| 2 | Parse event IDs (evt-1, evt-2, evt-3...) | Numeric part increases |
| 3 | Verify no gaps or duplicates | Monotonically increasing |

**Test Data:** Long-running tool producing multiple events
**Postconditions:** Event ordering guaranteed

---

### TC-304: BR-A7 â Concurrent Sessions (Minimum 10)

| Field | Value |
|-------|-------|
| **ID** | TC-304 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-A7 |
| **Preconditions** | Orchestrator running with default config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 10 concurrent sessions | All succeed |
| 2 | Send `tools/list` on each session simultaneously | All respond correctly |
| 3 | Verify session isolation | Each session has independent state |

**Test Data:** 10 parallel HTTP clients
**Postconditions:** All sessions active and isolated

---

### TC-305: BR-B1 â Hidden Tools NOT in tools/list

| Field | Value |
|-------|-------|
| **ID** | TC-305 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-B1 |
| **Preconditions** | Orchestrator running with hidden tools registered |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `tools/list` | Get full tool list |
| 2 | Search for `get_drawio_reference` in list | NOT found |
| 3 | Search for `export_drawio` in list | NOT found |

**Test Data:** N/A
**Postconditions:** Hidden tools remain hidden from default listing

---

### TC-306: BR-C1 â orchestrator-core Has No Project Dependencies

| Field | Value |
|-------|-------|
| **ID** | TC-306 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-C1 |
| **Preconditions** | Multi-module project structure |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Read `orchestrator-core/build.gradle.kts` | No `project(":...")` dependencies |
| 2 | Run `./gradlew :orchestrator-core:dependencies` | Only external dependencies listed |
| 3 | Verify no imports from other modules in core source | No cross-module imports |

**Test Data:** Build configuration files
**Postconditions:** Core module is independent

---

### TC-307: BR-C2 â No Circular Dependencies

| Field | Value |
|-------|-------|
| **ID** | TC-307 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-C2 |
| **Preconditions** | Multi-module project |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew build` | Build succeeds (no circular dep error) |
| 2 | Verify dependency graph: core â (none), client â core, server â core+client, bridge â core | Acyclic graph |

**Test Data:** N/A
**Postconditions:** Build order is deterministic

---

### TC-308: BR-C4/C5 â Fat JAR Outputs

| Field | Value |
|-------|-------|
| **ID** | TC-308 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-C4, BR-C5 |
| **Preconditions** | Build completed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew :orchestrator-server:buildFatJar` | Produces `mcp-orchestrator-all.jar` |
| 2 | Run `./gradlew :orchestrator-bridge:buildFatJar` | Produces `mcp-bridge-all.jar` |
| 3 | Verify both JARs are self-contained (can run standalone) | `java -jar` works without classpath |

**Test Data:** N/A
**Postconditions:** Standalone JARs produced

---

### TC-309: Smart Promotion â Unique Tool Names in Session

| Field | Value |
|-------|-------|
| **ID** | TC-309 |
| **Level** | UT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | AC #36, Validation Rule |
| **Preconditions** | Promotion cache active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote tool "kb_search" | Added to cache |
| 2 | Attempt to promote another tool with same name "kb_search" | Replaces existing (no duplicate) |
| 3 | Verify `tools/list` has exactly one "kb_search" | No duplicates |

**Test Data:** Two tools with same name from different queries
**Postconditions:** Uniqueness maintained

---

### TC-310: Smart Promotion â Max Promoted Tools Limit

| Field | Value |
|-------|-------|
| **ID** | TC-310 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | AC #36, Validation Rule (max 50) |
| **Preconditions** | max_promoted config set to 5 (for testing) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote 5 tools | All added |
| 2 | Promote 6th tool | LRU tool evicted; 6th added |
| 3 | Verify total promoted count = 5 | Never exceeds max |

**Test Data:** 6 different tool names
**Postconditions:** Cache bounded by max_promoted

---

### TC-311: Stream Write â Mode Validation

| Field | Value |
|-------|-------|
| **ID** | TC-311 |
| **Level** | UT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | AC #44, Validation Rule |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call with `mode: "write"` | Success |
| 2 | Call with `mode: "append"` | Success |
| 3 | Call with `mode: "delete"` (invalid) | Error: INVALID_PATH or validation error |

**Test Data:** Various mode values
**Postconditions:** Only valid modes accepted

---

### TC-312: Smart Promotion â Config Default Enabled

| Field | Value |
|-------|-------|
| **ID** | TC-312 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | AC #41 |
| **Preconditions** | No explicit smart-promotion config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator without smart-promotion config | Starts successfully |
| 2 | Call `find_tools` | Tools are promoted (feature enabled by default) |
| 3 | Set `smart-promotion.enabled: false` and restart | Feature disabled |
| 4 | Call `find_tools` | Tools NOT promoted (returned but not added to tools/list) |

**Test Data:** Config with/without smart-promotion setting
**Postconditions:** Default behavior is enabled

---

### TC-313: Bridge â Configurable Orchestrator URL

| Field | Value |
|-------|-------|
| **ID** | TC-313 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | AC #20, AC #28 |
| **Preconditions** | Orchestrator running on non-default port |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Kotlin Bridge with `--url http://localhost:9090/mcp` | Connects to port 9090 |
| 2 | Start Node.js Bridge with `ORCHESTRATOR_URL=http://localhost:9090/mcp` | Connects to port 9090 |
| 3 | Verify both bridges connect successfully | Functional on custom URL |

**Test Data:** Custom port 9090
**Postconditions:** URL configuration works for both bridges

---

### TC-314: Embed Images â Reports Embedded and Failed Counts

| Field | Value |
|-------|-------|
| **ID** | TC-314 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | AC #53 |
| **Preconditions** | Markdown with 3 images (2 exist, 1 missing) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `embed_images` with markdown containing 3 image refs | Response received |
| 2 | Verify `images_embedded: 2` | Two images converted |
| 3 | Verify `images_failed: 1` | One image not found |
| 4 | Verify failed image keeps original reference | `![alt](missing.png)` unchanged |

**Test Data:** Markdown with refs to existing and non-existing images
**Postconditions:** Graceful handling of missing images

---

## 5. Boundary & Negative Testing

### TC-400: Stream Write â Empty Content

| Field | Value |
|-------|-------|
| **ID** | TC-400 |
| **Level** | PBT |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | AC #44 |
| **Preconditions** | Valid file path |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` with `content: ""` (empty string) | Success (creates empty file or appends nothing) |
| 2 | Verify `bytes_written: 0` | Zero bytes written |
| 3 | Verify file exists (write mode) or unchanged (append mode) | Consistent behavior |

**Test Data:** Empty string content
**Postconditions:** No error on empty content

---

### TC-401: Stream Write â Very Large Content (10MB)

| Field | Value |
|-------|-------|
| **ID** | TC-401 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | AC #45, AC #50 |
| **Preconditions** | Sufficient disk space |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` with 10MB content in single call | Success |
| 2 | Verify `bytes_written` matches content length | Correct byte count |
| 3 | Monitor memory usage during write | RAM does NOT spike by 10MB |

**Test Data:** 10MB string content (repeated pattern)
**Postconditions:** File written without memory pressure

---

### TC-402: Session ID â Non-UUID Format Rejected

| Field | Value |
|-------|-------|
| **ID** | TC-402 |
| **Level** | PBT |
| **Priority** | High |
| **Type** | Negative |
| **Requirement** | BR-A2 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send request with `Mcp-Session-Id: "abc"` | HTTP 404 |
| 2 | Send with `Mcp-Session-Id: "12345"` | HTTP 404 |
| 3 | Send with `Mcp-Session-Id: ""` (empty) | HTTP 404 |
| 4 | Send with UUID v1 format (version byte â  4) | HTTP 404 |

**Test Data:** Various invalid UUID formats
**Postconditions:** All rejected with SESSION_NOT_FOUND

---

### TC-403: Stream Write â Path Traversal Variations

| Field | Value |
|-------|-------|
| **ID** | TC-403 |
| **Level** | PBT |
| **Priority** | High |
| **Type** | Negative / Security |
| **Requirement** | AC #48 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Path: `C:/temp/../../windows/system32/test.txt` | INVALID_PATH |
| 2 | Path: `C:/temp/./../../etc/passwd` | INVALID_PATH |
| 3 | Path: `C:/temp/..\\..\\windows\\system32\\test.txt` | INVALID_PATH |
| 4 | Path: `C:/temp/%2e%2e/secret.txt` (URL-encoded) | INVALID_PATH |

**Test Data:** 100+ randomly generated paths with traversal sequences (PBT)
**Postconditions:** All traversal attempts blocked

---

### TC-404: Stream Write â Relative Path Rejected

| Field | Value |
|-------|-------|
| **ID** | TC-404 |
| **Level** | PBT |
| **Priority** | High |
| **Type** | Negative |
| **Requirement** | AC #48 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Path: `relative/path/file.txt` | INVALID_PATH |
| 2 | Path: `./file.txt` | INVALID_PATH |
| 3 | Path: `file.txt` | INVALID_PATH |
| 4 | Path: `~/file.txt` | INVALID_PATH (not absolute) |

**Test Data:** Various relative path formats
**Postconditions:** Only absolute paths accepted

---

### TC-405: JSON-RPC â Missing Required Fields

| Field | Value |
|-------|-------|
| **ID** | TC-405 |
| **Level** | PBT |
| **Priority** | High |
| **Type** | Negative |
| **Requirement** | UC-A1 EF-A1.1 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send `{}` (empty object) | Error -32600 |
| 2 | Send `{"jsonrpc":"2.0"}` (no method, no id) | Error -32600 |
| 3 | Send `{"jsonrpc":"2.0","id":1}` (no method) | Error -32600 |
| 4 | Send `{"method":"test"}` (no jsonrpc, no id) | Error -32600 |

**Test Data:** Randomly generated partial JSON-RPC objects (PBT)
**Postconditions:** All invalid structures rejected

---

### TC-406: Smart Promotion â TTL Reset on Successful Call

| Field | Value |
|-------|-------|
| **ID** | TC-406 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | Validation Rule: TTL resets on success |
| **Preconditions** | Tool promoted; TTL = 10 seconds (test config) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote tool at T=0 | TTL starts counting |
| 2 | Wait 8 seconds (near expiry) | Tool still active |
| 3 | Call tool successfully at T=8 | TTL resets to 10s |
| 4 | Wait 8 more seconds (T=16) | Tool still active (TTL reset at T=8) |
| 5 | Wait 12 seconds without calling (T=28) | Tool expires at T=18 |

**Test Data:** TTL config: 10 seconds
**Postconditions:** TTL resets on each successful call

---

### TC-407: export_drawio â File Without .drawio Extension

| Field | Value |
|-------|-------|
| **ID** | TC-407 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Negative |
| **Requirement** | UC-B2 validation |
| **Preconditions** | File exists but has wrong extension |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `export_drawio` with `file_path: "test.xml"` | Error or validation failure |
| 2 | Call with `file_path: "test.png"` | Error |
| 3 | Call with `file_path: "test"` (no extension) | Error |

**Test Data:** Files with non-.drawio extensions
**Postconditions:** Only .drawio files accepted

---

### TC-408: HTTP Streamable â Concurrent Requests on Same Session

| Field | Value |
|-------|-------|
| **ID** | TC-408 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-A7 |
| **Preconditions** | Single session established |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 5 concurrent `tools/call` requests on same session | All processed |
| 2 | Verify all responses are correct | No cross-contamination |
| 3 | Verify session state is consistent | No race conditions |

**Test Data:** 5 parallel requests with same session ID
**Postconditions:** Session handles concurrency

---

### TC-409: Stream Write â Special Characters in Content

| Field | Value |
|-------|-------|
| **ID** | TC-409 |
| **Level** | PBT |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | AC #44 |
| **Preconditions** | Valid file path |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Write content with Unicode characters (emoji, CJK) | Success |
| 2 | Write content with null bytes | Success or handled gracefully |
| 3 | Write content with very long lines (>10000 chars) | Success |
| 4 | Read file back and verify content integrity | Exact match |

**Test Data:** Random Unicode strings (PBT generator)
**Postconditions:** All valid UTF-8 content preserved

---

### TC-410: Smart Promotion â Empty find_tools Result

| Field | Value |
|-------|-------|
| **ID** | TC-410 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | AC #33 |
| **Preconditions** | No upstream tools match query |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `find_tools` with query "xyznonexistent123" | Empty results |
| 2 | Verify no tools promoted | Promotion cache unchanged |
| 3 | Verify no `notifications/tools/list_changed` sent | No notification for empty result |

**Test Data:** Query with no matches
**Postconditions:** System handles empty results gracefully

---

### TC-411: Embed Images â Already Embedded Data URIs Skipped

| Field | Value |
|-------|-------|
| **ID** | TC-411 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | AC #51 |
| **Preconditions** | Markdown with already-embedded images |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `embed_images` on markdown with `![img](data:image/png;base64,...)` | Success |
| 2 | Verify already-embedded images are NOT re-processed | Content unchanged |
| 3 | Verify `images_embedded: 0` for already-embedded file | No double-encoding |

**Test Data:** Markdown with existing data URIs
**Postconditions:** Idempotent operation

---

## 6. Non-Functional Testing

### TC-600: Performance â HTTP Streamable Latency < 100ms

| Field | Value |
|-------|-------|
| **ID** | TC-600 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Non-Functional â Performance |
| **Requirement** | BRD NFR: HTTP Streamable latency |
| **Preconditions** | Orchestrator running; no upstream tool execution (measure transport only) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 100 `tools/list` requests via HTTP Streamable | All succeed |
| 2 | Measure round-trip time for each | Average < 100ms |
| 3 | Verify P95 latency | P95 < 150ms |
| 4 | Verify P99 latency | P99 < 200ms |

**Acceptance Criteria:** Average response time < 100ms (excluding upstream execution)

---

### TC-601: Performance â Smart Tool Promotion Token Reduction

| Field | Value |
|-------|-------|
| **ID** | TC-601 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Non-Functional â Performance |
| **Requirement** | BRD NFR: 73-80% token reduction |
| **Preconditions** | Orchestrator with 50+ upstream tools available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Measure token count of full `tools/list` (all tools exposed) | Baseline token count |
| 2 | Measure token count with Smart Promotion (only meta-tools + promoted) | Reduced token count |
| 3 | Calculate reduction: (baseline - promoted) / baseline Ã 100% | 73-80% reduction |

**Acceptance Criteria:** Token reduction â¥ 73%

---

### TC-602: Performance â Stream Write Throughput

| Field | Value |
|-------|-------|
| **ID** | TC-602 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Non-Functional â Performance |
| **Requirement** | BRD NFR: Write throughput matches native I/O |
| **Preconditions** | Orchestrator running; fast disk available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Write 1MB file via `stream_write_file` in 10 append calls | Success |
| 2 | Measure total time | < 1 second |
| 3 | Compare with native file write (baseline) | Within 2x of native speed |

**Acceptance Criteria:** No artificial buffering delays; throughput â¥ 50% of native I/O

---

### TC-603: Reliability â Bridge Auto-Reconnect Within 15 Seconds

| Field | Value |
|-------|-------|
| **ID** | TC-603 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Non-Functional â Reliability |
| **Requirement** | BRD NFR: Auto-reconnect within 15s |
| **Preconditions** | Bridge connected to Orchestrator |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Kill Orchestrator process | Bridge detects loss |
| 2 | Restart Orchestrator immediately | Available again |
| 3 | Measure time until Bridge reconnects | â¤ 15 seconds |
| 4 | Verify Bridge is fully functional after reconnect | Can process requests |

**Acceptance Criteria:** Reconnection time â¤ 15 seconds

---

### TC-604: Reliability â Stream Resumability Within Session TTL

| Field | Value |
|-------|-------|
| **ID** | TC-604 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Non-Functional â Reliability |
| **Requirement** | BRD NFR: Stream resumability |
| **Preconditions** | Active SSE stream; session within TTL |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start SSE stream, receive events evt-1 through evt-5 | Events received |
| 2 | Disconnect client (simulate network drop) | Stream interrupted |
| 3 | Reconnect with `Last-Event-ID: evt-3` within TTL | Stream resumes |
| 4 | Verify events evt-4, evt-5 replayed | No data loss |

**Acceptance Criteria:** All missed events replayed on reconnection

---

### TC-605: Security â Session Isolation

| Field | Value |
|-------|-------|
| **ID** | TC-605 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Non-Functional â Security |
| **Requirement** | BRD NFR: Session isolation |
| **Preconditions** | Two active sessions |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create session A and promote tools | Session A has promoted tools |
| 2 | Create session B | Session B has only meta-tools |
| 3 | Use session A's ID with session B's client | Verify isolation (A's state not accessible from B) |
| 4 | Verify promoted tools are per-session | Each session independent |

**Acceptance Criteria:** One client cannot access another's session state

---

### TC-606: Security â Path Validation (stream_write_file)

| Field | Value |
|-------|-------|
| **ID** | TC-606 |
| **Level** | PBT |
| **Priority** | High |
| **Type** | Non-Functional â Security |
| **Requirement** | BRD NFR: Path traversal prevention |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate 1000 random paths with traversal sequences | All rejected |
| 2 | Generate 1000 random absolute paths without traversal | All accepted (if parent exists) |
| 3 | Verify no file written outside intended directory | Security maintained |

**Acceptance Criteria:** 100% traversal attempts blocked; 0 false positives on valid paths

---

### TC-607: Scalability â 50 Promoted Tools Without Degradation

| Field | Value |
|-------|-------|
| **ID** | TC-607 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Non-Functional â Scalability |
| **Requirement** | BRD NFR: Handle 50 promoted tools |
| **Preconditions** | 50+ upstream tools available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote 50 tools via multiple `find_tools` calls | All promoted |
| 2 | Call `tools/list` | Returns all 50 + meta-tools |
| 3 | Measure response time of `tools/list` | No significant degradation |
| 4 | Call each promoted tool | All execute correctly |

**Acceptance Criteria:** `tools/list` response time < 200ms with 50 promoted tools

---

### TC-608: Compatibility â Backward Compatibility (SSE mode)

| Field | Value |
|-------|-------|
| **ID** | TC-608 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Non-Functional â Compatibility |
| **Requirement** | BRD NFR: Backward compatibility, BR-A5 |
| **Preconditions** | Orchestrator with SSE transport configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure `transport: sse` | Server starts |
| 2 | Connect via SSE endpoint | Connection established |
| 3 | Send MCP requests via SSE | Responses received correctly |
| 4 | Verify all existing tools work | No regression |

**Acceptance Criteria:** SSE mode unchanged after HTTP Streamable addition

---

### TC-609: Maintainability â Module Independence

| Field | Value |
|-------|-------|
| **ID** | TC-609 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Non-Functional â Maintainability |
| **Requirement** | BRD NFR: Module independence |
| **Preconditions** | Multi-module project |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew :orchestrator-core:test` independently | Tests pass |
| 2 | Run `./gradlew :orchestrator-client:test` independently | Tests pass |
| 3 | Run `./gradlew :orchestrator-server:test` independently | Tests pass |
| 4 | Run `./gradlew :orchestrator-bridge:test` independently | Tests pass |

**Acceptance Criteria:** Each module testable in isolation

---

### TC-610: Compatibility â MCP Spec 2025-03-26 Conformance

| Field | Value |
|-------|-------|
| **ID** | TC-610 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Non-Functional â Compatibility |
| **Requirement** | BRD NFR: MCP Spec conformance |
| **Preconditions** | Orchestrator running with HTTP Streamable |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify `initialize` response includes `protocolVersion: "2025-03-26"` | Correct version |
| 2 | Verify session management via `Mcp-Session-Id` header | Per spec |
| 3 | Verify SSE format: `id:` + `data:` fields | Per spec |
| 4 | Verify `notifications/tools/list_changed` format | Per spec |

**Acceptance Criteria:** Full conformance with MCP spec 2025-03-26 transport section

---

### TC-611: Security â Embed Images Path Resolution

| Field | Value |
|-------|-------|
| **ID** | TC-611 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Non-Functional â Security |
| **Requirement** | AC #51 |
| **Preconditions** | Markdown with image references |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Markdown with `![img](../../etc/passwd)` | Image NOT embedded (path traversal) |
| 2 | Markdown with `![img](images/logo.png)` (relative, valid) | Image embedded correctly |
| 3 | Verify relative paths resolved against markdown file's directory | Correct resolution |

**Acceptance Criteria:** No path traversal via image references

---

## 7. Integration Testing

### TC-700: Integration â Bridge â Orchestrator Full Lifecycle

| Field | Value |
|-------|-------|
| **ID** | TC-700 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #15, AC #16, AC #18 |
| **Preconditions** | Orchestrator running; Bridge JAR available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Orchestrator with HTTP Streamable | Running on :8080 |
| 2 | Start Kotlin Bridge pointing to Orchestrator | Bridge connects |
| 3 | Send `initialize` via Bridge stdin | Bridge responds |
| 4 | Call `find_tools` via Bridge | Tools discovered from Orchestrator |
| 5 | Call discovered tool via Bridge | Execution proxied and result returned |

**Test Data:** Full MCP lifecycle messages
**Postconditions:** End-to-end communication verified

---

### TC-701: Integration â Orchestrator â Upstream Server

| Field | Value |
|-------|-------|
| **ID** | TC-701 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #40 |
| **Preconditions** | Mock upstream server running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure upstream server in Orchestrator config | Server registered |
| 2 | Call `find_tools` matching upstream tools | Tools discovered |
| 3 | Execute upstream tool via `execute_dynamic_tool` | Result from upstream |
| 4 | Verify request/response format between Orchestrator and upstream | JSON-RPC 2.0 compliant |

**Test Data:** Mock upstream with 3 test tools
**Postconditions:** Upstream integration verified

---

### TC-702: Integration â Smart Promotion + Bridge Combined

| Field | Value |
|-------|-------|
| **ID** | TC-702 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #19, AC #31, AC #32, AC #34 |
| **Preconditions** | Bridge connected; Smart Promotion enabled |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `tools/list` via Bridge | 2 meta-tools only |
| 2 | Call `find_tools` via Bridge | Tools discovered |
| 3 | Verify Bridge receives `notifications/tools/list_changed` | Notification received |
| 4 | Call `tools/list` via Bridge again | Promoted tools included |
| 5 | Call promoted tool directly via Bridge | Executes successfully |

**Test Data:** Query targeting upstream tools
**Postconditions:** Full promotion lifecycle via Bridge

---

### TC-703: Integration â Node.js Bridge â Orchestrator

| Field | Value |
|-------|-------|
| **ID** | TC-703 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | AC #23, AC #24, AC #26 |
| **Preconditions** | Orchestrator running; Node.js bridge available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start Node.js bridge with ORCHESTRATOR_URL | Connects |
| 2 | Send `initialize` via stdin | Bridge responds |
| 3 | Call `find_tools` | Tools discovered |
| 4 | Call `execute_dynamic_tool` | Tool executed |

**Test Data:** Same test scenarios as Kotlin bridge
**Postconditions:** Node.js bridge functionally equivalent

---

### TC-704: Integration â Kotlin vs Node.js Bridge Parity

| Field | Value |
|-------|-------|
| **ID** | TC-704 |
| **Level** | E2E-API |
| **Priority** | Medium |
| **Type** | Integration / Compatibility |
| **Requirement** | AC #15-22 vs AC #23-30 |
| **Preconditions** | Both bridges available; same Orchestrator |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run identical test sequence on Kotlin bridge | Record responses |
| 2 | Run identical test sequence on Node.js bridge | Record responses |
| 3 | Compare response structures | Functionally equivalent |
| 4 | Compare error handling behavior | Same error codes and messages |

**Test Data:** Standardized test sequence
**Postconditions:** Both bridges produce equivalent results

---

### TC-705: Integration â Stream Write in Loop Pattern

| Field | Value |
|-------|-------|
| **ID** | TC-705 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #50 |
| **Preconditions** | Orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `stream_write_file` mode "write" with "# Document\n" | File created |
| 2 | Loop 10 times: call with mode "append" and "## Section N\n" | Each appends |
| 3 | Verify final file contains all 11 sections | Complete document |
| 4 | Verify `total_size` grows with each call | Monotonically increasing |
| 5 | Monitor process memory | RAM does NOT increase proportionally |

**Test Data:** 10 append calls with ~1KB each
**Postconditions:** File built incrementally; RAM stable

---

### TC-706: Integration â Hidden Tools + Smart Promotion

| Field | Value |
|-------|-------|
| **ID** | TC-706 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | AC #8, BR-B5 |
| **Preconditions** | Smart Promotion enabled; hidden tools registered |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `tools/list` | Hidden tools NOT listed |
| 2 | Call `find_tools` with "drawio" | `get_drawio_reference` found |
| 3 | Verify hidden tool MAY be promoted | Tool appears in next `tools/list` |
| 4 | Call promoted hidden tool directly | Executes successfully |

**Test Data:** Query: "drawio diagram"
**Postconditions:** Hidden tools promotable via Smart Promotion

---

### TC-707: Integration â Multi-Module Build Produces Correct Artifacts

| Field | Value |
|-------|-------|
| **ID** | TC-707 |
| **Level** | IT |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #10, BR-C4, BR-C5 |
| **Preconditions** | Multi-module project |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew clean build` | All modules build |
| 2 | Verify `orchestrator-server/build/libs/mcp-orchestrator-all.jar` exists | Fat JAR produced |
| 3 | Verify `orchestrator-bridge/build/libs/mcp-bridge-all.jar` exists | Fat JAR produced |
| 4 | Run server JAR: `java -jar mcp-orchestrator-all.jar` | Server starts |
| 5 | Run bridge JAR: `java -jar mcp-bridge-all.jar` | Bridge starts |

**Test Data:** N/A
**Postconditions:** Both artifacts are runnable

---

### TC-708: Integration â Session Cleanup Background Job

| Field | Value |
|-------|-------|
| **ID** | TC-708 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | BR-A3, TDD Â§4.2.1 |
| **Preconditions** | Session TTL set to 5 seconds; cleanup interval 2 seconds |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 5 sessions | All active |
| 2 | Wait 7 seconds (past TTL) | Sessions should expire |
| 3 | Verify cleanup job ran | Expired sessions removed |
| 4 | Attempt to use expired session | HTTP 404 SESSION_NOT_FOUND |

**Test Data:** Short TTL config for testing
**Postconditions:** Memory freed from expired sessions

---

### TC-709: Integration â Promotion Cache Eviction (LRU)

| Field | Value |
|-------|-------|
| **ID** | TC-709 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | TDD Â§4.2.2 |
| **Preconditions** | max_promoted = 3 (test config) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Promote tools A, B, C (in order) | All in cache |
| 2 | Call tool A (updates lastUsedAt) | A is most recently used |
| 3 | Promote tool D | LRU tool (B) evicted |
| 4 | Verify B is no longer promoted | B removed from tools/list |
| 5 | Verify A, C, D are promoted | Correct eviction |

**Test Data:** 4 tools with max_promoted = 3
**Postconditions:** LRU eviction works correctly

---

### TC-710: Integration â Large-Text Proxy + Upstream Tool

| Field | Value |
|-------|-------|
| **ID** | TC-710 |
| **Level** | IT |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | AC #54, AC #55 |
| **Preconditions** | Upstream tool with "markdown" parameter registered |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call upstream tool with 20KB markdown content | Request processed |
| 2 | Verify content routed through file proxy | Efficient transfer |
| 3 | Verify upstream receives full content | No truncation |
| 4 | Verify response returned correctly | End-to-end success |

**Test Data:** 20KB markdown document
**Postconditions:** Large text handled transparently

---

### TC-711: Integration â HTTP Streamable + upload_file

| Field | Value |
|-------|-------|
| **ID** | TC-711 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #7, BR-A6 |
| **Preconditions** | HTTP Streamable session active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upload_file` via HTTP Streamable transport | File uploaded |
| 2 | Verify file content received correctly | Binary integrity |
| 3 | Verify response format matches expected | Success with metadata |

**Test Data:** Test binary file (PNG image, 50KB)
**Postconditions:** File upload works over HTTP Streamable

---

### TC-712: Integration â Orchestrator Config Validation

| Field | Value |
|-------|-------|
| **ID** | TC-712 |
| **Level** | UT |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | AC #1 |
| **Preconditions** | Various config files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Config with `transport: httpstreamable` | Valid, server starts |
| 2 | Config with `transport: stdio` | Valid, server starts |
| 3 | Config with `transport: sse` | Valid, server starts |
| 4 | Config with `transport: invalid` | Error on startup |

**Test Data:** YAML config files with different transport values
**Postconditions:** Only valid transport modes accepted

---

### TC-713: Integration â Bridge Token Optimization (Part D AC #19)

| Field | Value |
|-------|-------|
| **ID** | TC-713 |
| **Level** | E2E-API |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | AC #19, AC #27 |
| **Preconditions** | Bridge connected with Smart Promotion |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Measure initial `tools/list` token count (2 meta-tools) | Small token footprint |
| 2 | Call `find_tools` â tools promoted | Token count increases slightly |
| 3 | Compare with full tool list exposure | Significant reduction |
| 4 | Verify promoted tools use compact schema | Descriptions â¤ 100 chars |

**Test Data:** Token counting on JSON responses
**Postconditions:** Token optimization verified

---

### TC-714: Integration â Embed Images + Stream Write Combined

| Field | Value |
|-------|-------|
| **ID** | TC-714 |
| **Level** | IT |
| **Priority** | Low |
| **Type** | Integration |
| **Requirement** | AC #51, AC #42 |
| **Preconditions** | Both tools available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Use `stream_write_file` to create markdown with image refs | File created |
| 2 | Call `embed_images` on the created file | Images embedded |
| 3 | Use `stream_write_file` to save embedded result to new file | Saved |
| 4 | Verify final file is self-contained | All images inline |

**Test Data:** Markdown content with relative image paths
**Postconditions:** Tools work together in pipeline

---

## 8. Requirements Traceability Matrix (RTM)

### Part A â HTTP Streamable Transport (AC #1â7)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 1 | Server supports `transport: httpstreamable` config | TC-001, TC-712 | â |
| 2 | Single POST endpoint `/mcp` receives JSON-RPC | TC-001, TC-002 | â |
| 3 | Responds with application/json or text/event-stream | TC-002, TC-003, TC-100, TC-300 | â |
| 4 | Support Mcp-Session-Id header | TC-001, TC-101, TC-201, TC-301, TC-402 | â |
| 5 | Support Last-Event-ID for stream resumability | TC-004, TC-203, TC-604 | â |
| 6 | Backward compatible (stdio + SSE still work) | TC-005, TC-608 | â |
| 7 | upload_file works over HTTP Streamable | TC-006, TC-711 | â |

### Part B â Hidden Utility Tools (AC #8â9)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 8 | get_drawio_reference â hidden, discoverable via find_tools | TC-007, TC-204, TC-305, TC-706 | â |
| 9 | export_drawio â accepts file_path/format, calls CLI | TC-008, TC-205, TC-206, TC-212, TC-407 | â |

### Part C â Gradle Multi-Module Refactor (AC #10â14)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 10 | Refactor into 4 modules | TC-009, TC-707 | â |
| 11 | Shared models in orchestrator-core | TC-009, TC-306 | â |
| 12 | Server logic in orchestrator-server | TC-009 | â |
| 13 | Upstream client in orchestrator-client | TC-009 | â |
| 14 | Existing tests pass after refactor | TC-010, TC-609 | â |

### Part D â MCP Client Bridge Kotlin (AC #15â22)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 15 | Kotlin MCP server runs stdio | TC-011, TC-700 | â |
| 16 | Connects to Orchestrator via HTTP Streamable | TC-011, TC-700 | â |
| 17 | File content via HTTP binary | TC-013 | â |
| 18 | Proxy find_tools and execute_dynamic_tool | TC-012, TC-700 | â |
| 19 | Token optimization | TC-702, TC-713 | â |
| 20 | Configurable Orchestrator URL | TC-011, TC-313 | â |
| 21 | Auto-reconnect | TC-014, TC-603 | â |
| 22 | Fat JAR: mcp-bridge-all.jar | TC-015, TC-308, TC-707 | â |

### Part E â MCP Client Bridge Node.js (AC #23â30)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 23 | Node.js MCP server runs stdio | TC-016, TC-703 | â |
| 24 | Connects via HTTP Streamable | TC-016, TC-703 | â |
| 25 | File content via HTTP binary | TC-106 | â |
| 26 | Proxy find_tools and execute_dynamic_tool | TC-017, TC-703 | â |
| 27 | Token optimization | TC-017, TC-713 | â |
| 28 | Configurable URL via env var | TC-016, TC-313 | â |
| 29 | Auto-reconnect | TC-107 | â |
| 30 | npx runnable packaging | TC-108 | â |

### Part F â Smart Tool Promotion (AC #31â41)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 31 | Orchestrator stdio: 6 meta-tools, promote after find_tools | TC-018 | â |
| 32 | Bridge: 2 meta-tools, promote after find_tools | TC-102, TC-702 | â |
| 33 | First find_tools â cache and promote | TC-018, TC-410 | â |
| 34 | Send notifications/tools/list_changed | TC-018, TC-702 | â |
| 35 | Second call: direct invocation, 0 discovery tokens | TC-018, TC-601 | â |
| 36 | Tool list grows incrementally | TC-103, TC-309, TC-310 | â |
| 37 | Compact schema (â¤100 chars description) | TC-104 | â |
| 38 | Cache invalidation: restart, reset_tools, TTL | TC-214, TC-215 | â |
| 39 | Fallback: fail â auto-demote â retry | TC-208 | â |
| 40 | Success â route directly to upstream | TC-105, TC-701 | â |
| 41 | Config: smart-promotion.enabled default true | TC-312 | â |

### Part G â Stream Write Tool (AC #42â50)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 42 | stream_write_file â direct to disk, no RAM buffer | TC-019, TC-602 | â |
| 43 | Available on BOTH Orchestrator and Bridge | TC-109 | â |
| 44 | Input: file_path, content, mode, encoding | TC-019, TC-311, TC-400, TC-409 | â |
| 45 | Writes immediately, no accumulation | TC-019, TC-401 | â |
| 46 | Append mode â multiple calls append | TC-019, TC-705 | â |
| 47 | Response: {file_path, bytes_written, total_size, mode} | TC-019 | â |
| 48 | Path validation: absolute, no traversal, parent exists | TC-209, TC-403, TC-404, TC-606 | â |
| 49 | Error codes: INVALID_PATH, OUTPUT_DIR_NOT_FOUND, etc. | TC-209, TC-210 | â |
| 50 | Loop pattern: RAM unchanged | TC-401, TC-705 | â |

### Part H â Embed Images Tool (AC #51â53)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 51 | Replace image refs with base64 data URIs | TC-020, TC-411, TC-611 | â |
| 52 | Optional output_path | TC-110 | â |
| 53 | Reports images_embedded/images_failed | TC-020, TC-314 | â |

### Part I â Large-Text Input Proxy (AC #54â56)

| AC # | Requirement | Test Cases | Coverage |
|------|-------------|------------|----------|
| 54 | Detects large-text parameters by name/description | TC-111 | â |
| 55 | Routes through file proxy (raw text, not base64) | TC-111, TC-710 | â |
| 56 | Detection results cached, invalidated on disconnect | TC-112 | â |

---

### Coverage Summary

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Acceptance Criteria | 56 | 56 | 100% |
| Use Cases (UC-A1 to UC-D3) | 9 | 9 | 100% |
| Business Rules (BR-A1 to BR-C5) | 17 | 17 | 100% |
| Error Codes | 8 | 8 | 100% |
| Non-Functional Requirements | 13 | 13 | 100% |
| **Overall** | **103 test cases** | **56/56 ACs** | **100%** |

---

## 9. Appendix

### Test Data Setup

**JSON-RPC Test Fixtures:**
```json
// Valid initialize request
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}

// Valid tools/list request
{"jsonrpc":"2.0","id":2,"method":"tools/list"}

// Valid tools/call request
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"stream_write_file","arguments":{"file_path":"C:/temp/test.md","content":"# Test","mode":"write"}}}
```

**Mock Upstream Server Configuration:**
```yaml
upstream-servers:
  - name: test-upstream
    command: "java -jar mock-upstream.jar"
    tools:
      - name: test_tool_1
        description: "A test tool for integration testing"
      - name: test_tool_2
        description: "Another test tool"
```

### Environment Configuration

**Test Config (short TTL for testing):**
```yaml
server:
  transport: httpstreamable
  port: 8080
  session:
    ttl_seconds: 5
    max_sessions: 10
    event_buffer_size: 100
    cleanup_interval_seconds: 2
  smart-promotion:
    enabled: true
    ttl_seconds: 10
    max_promoted: 5
```

### PBT Property Definitions

| Property | Generator | Assertion |
|----------|-----------|-----------|
| Session ID format | Random UUID generation | Always matches UUID v4 regex |
| Path validation | Random strings with/without traversal | Traversal always rejected; valid absolute paths accepted |
| JSON-RPC parsing | Random JSON objects | Invalid structures always return -32600/-32700 |
| Event ID ordering | Concurrent event generation | IDs always monotonically increasing |
| Compact schema | Random tool definitions | Description always â¤ 100 chars; only required params |
| Stream write content | Random UTF-8 strings | Content always preserved exactly on read-back |
