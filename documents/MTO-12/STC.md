# Software Test Cases (STC)

## MCP Tool Orchestration — MTO-12: Auto File Proxy (Input + Output)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-12 |
| Title | Auto File Proxy — Wrapper tool tự động cho upstream MCP tools nhận/trả file |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-05 |
| Status | Draft |
| Related STP | STP-v1.0-MTO-12.docx |
| Related FSD | FSD-v1.0-MTO-12.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-05 | QA Agent | Initiate document — auto-generated from FSD use cases and business rules |

---

## Test Case Summary

| Category | ID Range | Count | Priority |
|----------|----------|-------|----------|
| Functional — Happy Path | TC-001 to TC-009 | 9 | High |
| Functional — Alternative Flows | TC-100 to TC-108 | 9 | High |
| Functional — Exception/Error Flows | TC-200 to TC-216 | 17 | High |
| Business Rule Validation | TC-300 to TC-310 | 11 | High |
| Boundary & Negative Testing | TC-400 to TC-407 | 8 | Medium |
| Non-Functional (Performance, Security) | TC-600 to TC-609 | 10 | Medium |
| Integration Testing | TC-700 to TC-711 | 12 | High |

**Test Level Classification:**

| Prefix | Level | Count | Automation |
|--------|-------|-------|------------|
| PBT-01 to PBT-08 | Property-Based Test | 8 | Automated (kotest-property) |
| UT-01 to UT-24 | Unit Test | 24 | Automated (kotest + MockK) |
| IT-01 to IT-12 | Integration Test | 12 | Automated (Ktor test engine + Testcontainers) |
| E2E-API-01 to E2E-API-18 | E2E API Test | 18 | Automated (Ktor test host + kotest) |
| SIT-01 to SIT-06 | Manual SIT | 6 | Manual |

---

## 1. Functional Test Cases — Happy Path

### TC-001: Input File Parameter Auto-Detection — Schema Type

| Field | Value |
|-------|-------|
| **ID** | TC-001 |
| **Level** | UT-01 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-001, BR-001, Story #1 |
| **Preconditions** | Upstream tool schema with parameter type "base64" available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Provide tool inputSchema with parameter `{"type": "base64", "description": "File content"}` | Parameter detected |
| 2 | Call `FileProxyDetector.detectInputFileParams()` | Returns DetectionResult with method=SCHEMA_TYPE, confidence=1.0 |
| 3 | Verify detection result contains correct toolName, serverName, paramName | All fields populated correctly |

**Test Data:** `{"name": "convert_pdf", "inputSchema": {"type": "object", "properties": {"content": {"type": "base64", "description": "PDF file content"}}}}`
**Postconditions:** Detection result stored in memory cache

---

### TC-002: Input File Proxy — STDIO Mode Happy Path

| Field | Value |
|-------|-------|
| **ID** | TC-002 |
| **Level** | E2E-API-01 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-002, BR-005, BR-008, BR-009, Story #2 |
| **Preconditions** | Wrapper tool generated, test file exists at `/tmp/test/sample.pdf` (10KB), upstream tool mocked |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper tool with `{file_path: "/tmp/test/sample.pdf"}` | Request accepted |
| 2 | Verify file is read from disk | File bytes loaded |
| 3 | Verify base64 encoding applied | Valid base64 string generated |
| 4 | Verify upstream tool called with base64 content | Upstream receives `{content: "base64string..."}` |
| 5 | Verify registry record created with status=PENDING before processing | Record exists in DB |
| 6 | Verify registry record deleted after successful processing | Record removed from DB |
| 7 | Verify response returned to agent without file content | Response contains upstream result only |

**Test Data:** 10KB PDF file at `/tmp/test/sample.pdf`
**Postconditions:** Registry empty, upstream tool called once, agent receives clean response

---

### TC-003: Input File Proxy — HTTP/SSE Mode Happy Path (Upload + Use)

| Field | Value |
|-------|-------|
| **ID** | TC-003 |
| **Level** | E2E-API-02 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-003, BR-010, BR-011, BR-012, Story #3 |
| **Preconditions** | Transport mode is HTTP/SSE, upload_file tool registered, test file exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `upload_file` tool with `{file_path: "/tmp/test/report.pdf"}` | Returns file_id (valid UUID), file_name, file_size, expires_in |
| 2 | Verify file copied to temp directory | File exists at `{temp-dir}/{file_id}` |
| 3 | Verify registry record created with direction=INPUT, status=PENDING | Record in DB |
| 4 | Call wrapper tool with `{file_id: "{returned_uuid}"}` | Request accepted |
| 5 | Verify file_id resolved to temp file path | File content read from temp |
| 6 | Verify base64 encoding and upstream call | Upstream receives base64 content |
| 7 | Verify temp file deleted after processing | File removed from disk |
| 8 | Verify registry record cleaned up | Record removed from DB |

**Test Data:** 5KB PDF file, HTTP/SSE transport mode
**Postconditions:** Temp file deleted, registry empty, upstream called with correct content

---

### TC-004: Database Registry Lifecycle — Full Cycle

| Field | Value |
|-------|-------|
| **ID** | TC-004 |
| **Level** | IT-01 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-004, BR-014, BR-015, BR-016, BR-017, Story #4 |
| **Preconditions** | PostgreSQL available (Testcontainers), file_proxy_registry table exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate session ID (UUID) | Valid UUID created |
| 2 | Create registry entry with status=PENDING | INSERT succeeds, record retrievable by file_id |
| 3 | Update status to PROCESSED with processed_at timestamp | UPDATE succeeds |
| 4 | Delete entry | DELETE succeeds, findByFileId returns null |
| 5 | Verify session_id index used for queries | Query plan uses idx_file_proxy_session |

**Test Data:** FileProxyEntry with all fields populated
**Postconditions:** Table empty after test

---

### TC-005: Wrapper Tool Hides Original in find_tools

| Field | Value |
|-------|-------|
| **ID** | TC-005 |
| **Level** | E2E-API-03 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-005, BR-019, BR-020, BR-021, Story #5 |
| **Preconditions** | Upstream tool detected and wrapper generated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Register upstream tool "convert_pdf" with base64 param | Tool in registry |
| 2 | Run detection and wrapper generation | Wrapper created with same name |
| 3 | Call find_tools with query "convert_pdf" | Returns wrapper tool (not original) |
| 4 | Verify wrapper has file_path param instead of base64 param | Schema shows file_path |
| 5 | Verify wrapper description mentions file proxy capability | Description enhanced |
| 6 | Verify non-file params preserved unchanged | Other params intact |
| 7 | Verify original tool still callable internally | Internal dispatch works |

**Test Data:** Mock upstream tool "convert_pdf" with params: content (base64), output_format (string)
**Postconditions:** find_tools returns wrapper only; original hidden but functional

---

### TC-006: Output File Proxy — Save artifacts path to output_path

| Field | Value |
|-------|-------|
| **ID** | TC-006 |
| **Level** | E2E-API-04 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-006, BR-023, BR-026, BR-027, Story #6 |
| **Preconditions** | Output proxy wrapper generated, upstream returns artifacts[].path, output directory exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper tool with `{report_id: "123", output_path: "/tmp/output/result.pdf"}` | Request accepted |
| 2 | Verify upstream tool called with `{report_id: "123"}` (output_path stripped) | Upstream receives only original params |
| 3 | Upstream returns `{artifacts: [{path: "/tmp/upstream/report.pdf"}]}` | Response intercepted |
| 4 | Verify file copied from upstream path to output_path | File exists at /tmp/output/result.pdf |
| 5 | Verify response to agent contains `{saved_to, bytes_written, source_type}` | Confirmation response returned |

**Test Data:** Mock upstream response with artifacts[].path pointing to a real temp file
**Postconditions:** File saved at output_path, registry cleaned up

---

### TC-007: Output File Proxy — Decode base64 response to output_path

| Field | Value |
|-------|-------|
| **ID** | TC-007 |
| **Level** | E2E-API-05 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-006, BR-023, Story #6 |
| **Preconditions** | Output proxy wrapper generated, upstream returns base64 content field |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper tool with `{input: "data", output_path: "/tmp/output/decoded.bin"}` | Request accepted |
| 2 | Upstream returns response with base64-encoded field (>1000 chars) | Response intercepted |
| 3 | Verify base64 decoded and written to output_path | File exists with correct content |
| 4 | Verify response contains save confirmation | `{saved_to: "/tmp/output/decoded.bin", bytes_written: N, source_type: "BASE64_CONTENT"}` |

**Test Data:** Mock upstream response with 2000-char base64 string representing binary content
**Postconditions:** Decoded file at output_path matches original binary content

---

### TC-008: Output File Detection — Static (Schema-Based)

| Field | Value |
|-------|-------|
| **ID** | TC-008 |
| **Level** | UT-02 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-007, BR-028, BR-031, Story #7 |
| **Preconditions** | Upstream tool with outputSchema declaring file type |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Provide tool with `outputSchema` containing `"type": "file"` | Detected as output file tool |
| 2 | Provide tool named "export_report" | Detected by name pattern (export_*) |
| 3 | Provide tool named "generate_pdf" | Detected by name pattern (generate_*) |
| 4 | Provide tool named "convert_image" | Detected by name pattern (convert_*) |
| 5 | Provide tool named "render_chart" | Detected by name pattern (render_*) |

**Test Data:** Various tool definitions with output indicators
**Postconditions:** All output-producing tools correctly flagged

---

### TC-009: Lifecycle Cleanup — Startup Purge Previous Sessions

| Field | Value |
|-------|-------|
| **ID** | TC-009 |
| **Level** | IT-02 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-009, BR-037, BR-018, Story #9 |
| **Preconditions** | PostgreSQL with stale records from previous session, orphan temp files on disk |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 5 registry records with old session_id and create corresponding temp files | Records and files exist |
| 2 | Generate new session_id and run startupCleanup() | Cleanup executes |
| 3 | Verify all old session records deleted from DB | 0 records with old session_id |
| 4 | Verify all orphan temp files deleted from disk | Temp directory clean |
| 5 | Verify cleanup summary logged (records=5, files=5) | Log output correct |
| 6 | Verify server ready to accept requests after cleanup | New operations succeed |

**Test Data:** 5 FileProxyEntry records with session_id != current, 5 temp files in temp-directory
**Postconditions:** DB clean, temp directory clean, new session active

---

## 2. Functional Test Cases — Alternative Flows

### TC-100: Detection — Multiple File Parameters in Same Tool

| Field | Value |
|-------|-------|
| **ID** | TC-100 |
| **Level** | UT-03 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-001 AF-1, BR-004 |
| **Preconditions** | Tool schema with 2 base64 parameters |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Provide tool with params: `file_content` (base64) and `image_data` (base64) | Both detected |
| 2 | Verify detection returns 2 DetectionResult entries | List size = 2 |
| 3 | Verify wrapper generates `file_path_1` and `file_path_2` params | Both file params in wrapper schema |

**Test Data:** Tool schema: `{file_content: {type: "base64"}, image_data: {type: "base64"}, format: {type: "string"}}`
**Postconditions:** Wrapper handles both file parameters independently

---

### TC-101: Detection — Re-detection on Server Reconnect (Schema Changed)

| Field | Value |
|-------|-------|
| **ID** | TC-101 |
| **Level** | IT-03 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-001 AF-2, BR-003 |
| **Preconditions** | Existing wrapper for tool, upstream server reconnects with changed schema |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initial detection creates wrapper for tool with 1 file param | Wrapper exists |
| 2 | Simulate server reconnect with updated schema (2 file params now) | Re-detection triggered |
| 3 | Verify wrapper regenerated with new schema | Wrapper now has 2 file_path params |

**Test Data:** Initial schema: 1 base64 param; Updated schema: 2 base64 params
**Postconditions:** Wrapper reflects latest upstream schema

---

### TC-102: Input Proxy STDIO — Relative Path Resolution

| Field | Value |
|-------|-------|
| **ID** | TC-102 |
| **Level** | UT-04 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-002 AF-2 |
| **Preconditions** | File exists relative to temp-directory |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with relative path `./test.pdf` | Path resolved relative to temp-directory |
| 2 | Verify WARN log emitted about relative path | Log contains warning |
| 3 | Verify file is still processed correctly | Upstream receives base64 content |

**Test Data:** Relative path `./test.pdf`, temp-directory `/tmp/mcp-file-proxy`
**Postconditions:** File processed, warning logged

---

### TC-103: Output Proxy — No output_path (Passthrough Mode)

| Field | Value |
|-------|-------|
| **ID** | TC-103 |
| **Level** | E2E-API-06 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-006 AF-1, BR-026 |
| **Preconditions** | Output proxy wrapper exists, no output_path in call |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper tool with `{report_id: "123"}` (no output_path) | Request forwarded to upstream |
| 2 | Upstream returns response with artifacts[].path | Response passes through unchanged |
| 3 | Verify agent receives exact upstream response | No file save attempted |
| 4 | Verify no registry record created for output | Registry unchanged |

**Test Data:** Tool call without output_path parameter
**Postconditions:** Response identical to calling original tool directly

---

### TC-104: Output Proxy — File Already Exists at output_path (Overwrite)

| Field | Value |
|-------|-------|
| **ID** | TC-104 |
| **Level** | E2E-API-07 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-006 AF-2, BR-027 |
| **Preconditions** | File already exists at output_path |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create existing file at `/tmp/output/result.pdf` (old content) | File exists |
| 2 | Call wrapper with output_path pointing to same location | Request accepted |
| 3 | Verify file overwritten with new content | File content matches upstream output |
| 4 | Verify response confirms save | `{saved_to: "/tmp/output/result.pdf", bytes_written: N}` |

**Test Data:** Existing 1KB file at output_path; upstream returns 5KB file
**Postconditions:** File at output_path contains new content (5KB)

---

### TC-105: HTTP/SSE — file_id Used Before Upload (Error)

| Field | Value |
|-------|-------|
| **ID** | TC-105 |
| **Level** | E2E-API-08 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-003 AF-1 |
| **Preconditions** | No file uploaded, random UUID used |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper tool with `{file_id: "550e8400-e29b-41d4-a716-446655440000"}` (never uploaded) | Error returned |
| 2 | Verify error message: "File not found — file_id may have expired" | Correct error code FILE_ID_NOT_FOUND |

**Test Data:** Random valid UUID that was never uploaded
**Postconditions:** No side effects, error returned to agent

---

### TC-106: Wrapper Creation Failure — Original Tool Remains Visible

| Field | Value |
|-------|-------|
| **ID** | TC-106 |
| **Level** | IT-04 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-005 AF-1, BR-022 |
| **Preconditions** | WrapperToolGenerator configured to fail for specific tool |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger detection for tool where wrapper generation throws exception | Error logged |
| 2 | Call find_tools for the tool | Original tool returned (not hidden) |
| 3 | Call execute_dynamic_tool with original tool | Works normally |

**Test Data:** Tool with malformed schema that causes wrapper generation to fail
**Postconditions:** Original tool fully functional, error logged

---

### TC-107: Registry — Database Unavailable (Degraded Mode)

| Field | Value |
|-------|-------|
| **ID** | TC-107 |
| **Level** | IT-05 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-004 AF-1, BR-018 |
| **Preconditions** | PostgreSQL stopped/unavailable |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Stop PostgreSQL container | DB unavailable |
| 2 | Call input proxy wrapper with valid file_path | Proxy still processes file |
| 3 | Verify WARN log: "Registry unavailable — operating in degraded mode" | Warning logged |
| 4 | Verify upstream tool still called with base64 content | Upstream receives correct data |
| 5 | Verify response returned to agent | Agent gets upstream response |

**Test Data:** Valid file, PostgreSQL container stopped
**Postconditions:** Proxy operates without registry, no cleanup guarantee

---

### TC-108: Tool Requires Both Input AND Output Proxy

| Field | Value |
|-------|-------|
| **ID** | TC-108 |
| **Level** | E2E-API-09 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-005 AF-2, UC-006 |
| **Preconditions** | Tool detected for both input (base64 param) and output (artifacts response) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{file_path: "/tmp/input.pdf", output_path: "/tmp/output/result.pdf"}` | Both directions handled |
| 2 | Verify input file read and encoded to base64 | Upstream receives base64 |
| 3 | Verify upstream response intercepted for output | File extracted from response |
| 4 | Verify output file saved to output_path | File exists at output_path |
| 5 | Verify agent receives save confirmation (not raw file content) | Clean response |

**Test Data:** Input file 5KB, upstream returns artifacts[].path
**Postconditions:** Input processed, output saved, both registry records cleaned

---

## 3. Functional Test Cases — Exception/Error Flows

### TC-200: Input Proxy — File Not Found

| Field | Value |
|-------|-------|
| **ID** | TC-200 |
| **Level** | UT-05 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-002 EF-1, Error Code FILE_NOT_FOUND |
| **Preconditions** | File does not exist at specified path |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{file_path: "/tmp/nonexistent/file.pdf"}` | Error returned |
| 2 | Verify error: `"File not found: /tmp/nonexistent/file.pdf"` | Error code FILE_NOT_FOUND |
| 3 | Verify no registry record created | Registry empty |

**Test Data:** Non-existent file path `/tmp/nonexistent/file.pdf`
**Postconditions:** No side effects, clear error to agent

---

### TC-201: Input Proxy — File Exceeds Max Size

| Field | Value |
|-------|-------|
| **ID** | TC-201 |
| **Level** | UT-06 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-002 EF-2, BR-006, Error Code FILE_TOO_LARGE |
| **Preconditions** | File exists but exceeds configured max-size-mb (50MB default) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 60MB test file | File exists on disk |
| 2 | Call wrapper with path to 60MB file | Error returned before file read |
| 3 | Verify error: `"File exceeds maximum size (50MB). Actual: 60.00MB"` | Error code FILE_TOO_LARGE |
| 4 | Verify file was NOT read into memory (size check uses metadata) | No OOM risk |

**Test Data:** 60MB file, config max-size-mb=50
**Postconditions:** File not read, no memory impact, clear error

---

### TC-202: Input Proxy — File Not Readable (Permission Denied)

| Field | Value |
|-------|-------|
| **ID** | TC-202 |
| **Level** | UT-07 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-002 EF-3, Error Code FILE_NOT_READABLE |
| **Preconditions** | File exists but has no read permission for orchestrator process |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create file with permissions `000` (no access) | File exists but unreadable |
| 2 | Call wrapper with path to unreadable file | Error returned |
| 3 | Verify error: `"Cannot read file: {path} — permission denied"` | Error code FILE_NOT_READABLE |

**Test Data:** File with restrictive permissions
**Postconditions:** No side effects, clear error

---

### TC-203: Input Proxy — Path Traversal Detected

| Field | Value |
|-------|-------|
| **ID** | TC-203 |
| **Level** | UT-08 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-002 EF-4, BR-007, Error Code INVALID_PATH |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{file_path: "/home/user/../etc/passwd"}` | Error returned |
| 2 | Verify error: `"Invalid file path: path traversal not allowed"` | Error code INVALID_PATH |
| 3 | Verify WARN security log emitted | Security event logged |

**Test Data:** Path with `../` traversal sequence
**Postconditions:** Request rejected, security event logged

---

### TC-204: Input Proxy — Upstream Tool Call Fails

| Field | Value |
|-------|-------|
| **ID** | TC-204 |
| **Level** | E2E-API-10 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-002 EF-5, BR-009 |
| **Preconditions** | Valid file, upstream tool configured to return error |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with valid file_path | File read and encoded |
| 2 | Upstream tool returns error response | Error intercepted |
| 3 | Verify registry record updated to status=FAILED | Status changed |
| 4 | Verify registry record deleted (cleanup) | Record removed |
| 5 | Verify upstream error returned to agent | Agent sees upstream error |

**Test Data:** Valid 1KB file, mock upstream returns `{isError: true, content: [{text: "Processing failed"}]}`
**Postconditions:** Registry cleaned up, upstream error propagated

---

### TC-205: HTTP/SSE — Invalid file_id Format

| Field | Value |
|-------|-------|
| **ID** | TC-205 |
| **Level** | UT-09 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003 EF-1, BR-010, Error Code INVALID_FILE_ID |
| **Preconditions** | HTTP/SSE mode active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{file_id: "not-a-uuid"}` | Error returned |
| 2 | Verify error: `"Invalid file_id format — expected UUID"` | Error code INVALID_FILE_ID |

**Test Data:** Invalid UUID string "not-a-uuid"
**Postconditions:** No side effects

---

### TC-206: HTTP/SSE — file_id Not Found in Registry

| Field | Value |
|-------|-------|
| **ID** | TC-206 |
| **Level** | UT-10 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003 EF-2, Error Code FILE_ID_NOT_FOUND |
| **Preconditions** | Valid UUID format but no matching registry record |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{file_id: "550e8400-e29b-41d4-a716-446655440000"}` | Error returned |
| 2 | Verify error: `"File not found — file_id may have expired"` | Error code FILE_ID_NOT_FOUND |

**Test Data:** Valid UUID `550e8400-e29b-41d4-a716-446655440000` not in registry
**Postconditions:** No side effects

---

### TC-207: HTTP/SSE — File Expired (TTL Exceeded)

| Field | Value |
|-------|-------|
| **ID** | TC-207 |
| **Level** | IT-06 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003 EF-3, BR-011, Error Code FILE_EXPIRED |
| **Preconditions** | File uploaded, TTL exceeded (created_at > ttl-minutes ago) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert registry record with created_at = 2 hours ago | Record exists but expired |
| 2 | Call wrapper with the expired file_id | Error returned |
| 3 | Verify error: `"File expired — please re-upload"` | Error code FILE_EXPIRED |

**Test Data:** Registry record with created_at 2 hours in the past, TTL=60min
**Postconditions:** Expired record flagged

---

### TC-208: HTTP/SSE — Upload Fails (Disk Full)

| Field | Value |
|-------|-------|
| **ID** | TC-208 |
| **Level** | UT-11 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003 EF-4, Error Code UPLOAD_FAILED |
| **Preconditions** | Temp directory has no writable space |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure temp directory to a full/read-only filesystem | Write will fail |
| 2 | Call upload_file with valid file_path | Error returned |
| 3 | Verify error: `"Upload failed — insufficient storage"` | Error code UPLOAD_FAILED |

**Test Data:** Valid file, temp directory not writable
**Postconditions:** No partial file left on disk

---

### TC-209: Output Proxy — Output Directory Does Not Exist

| Field | Value |
|-------|-------|
| **ID** | TC-209 |
| **Level** | UT-12 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-1, BR-024, Error Code OUTPUT_DIR_NOT_FOUND |
| **Preconditions** | Parent directory of output_path does not exist |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{output_path: "/nonexistent/dir/file.pdf"}` | Error returned |
| 2 | Verify error: `"Output directory does not exist: /nonexistent/dir"` | Error code OUTPUT_DIR_NOT_FOUND |

**Test Data:** output_path with non-existent parent directory
**Postconditions:** No file created, clear error

---

### TC-210: Output Proxy — Output Path Not Writable

| Field | Value |
|-------|-------|
| **ID** | TC-210 |
| **Level** | UT-13 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-2, BR-024, Error Code OUTPUT_NOT_WRITABLE |
| **Preconditions** | Parent directory exists but is not writable |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create directory with read-only permissions | Directory exists but not writable |
| 2 | Call wrapper with output_path in that directory | Error returned |
| 3 | Verify error: `"Cannot write to output path: {path} — permission denied"` | Error code OUTPUT_NOT_WRITABLE |

**Test Data:** Read-only directory as parent of output_path
**Postconditions:** No file created

---

### TC-211: Output Proxy — No File Content in Upstream Response

| Field | Value |
|-------|-------|
| **ID** | TC-211 |
| **Level** | E2E-API-11 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-3, Error Code NO_FILE_IN_RESPONSE |
| **Preconditions** | Output proxy enabled, upstream returns text-only response |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with output_path | Request forwarded to upstream |
| 2 | Upstream returns `{content: [{type: "text", text: "No file generated"}]}` | No file content detected |
| 3 | Verify response returned as-is to agent | Passthrough behavior |
| 4 | Verify WARN log: "Upstream response does not contain file content" | Warning logged |

**Test Data:** Upstream response without artifacts or base64 fields
**Postconditions:** Agent receives upstream response unchanged, warning logged

---

### TC-212: Detection — Malformed Tool Schema

| Field | Value |
|-------|-------|
| **ID** | TC-212 |
| **Level** | UT-14 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-001 EF-1 |
| **Preconditions** | Upstream tool with malformed/missing inputSchema |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Provide tool with null inputSchema | Detection skips tool |
| 2 | Verify WARN log emitted | Warning logged |
| 3 | Verify other tools still detected normally | No crash, other tools processed |

**Test Data:** Tool definition with `inputSchema = null`
**Postconditions:** Tool skipped, system continues normally

---

### TC-213: Detection — Zero File Parameters Found

| Field | Value |
|-------|-------|
| **ID** | TC-213 |
| **Level** | UT-15 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-001 EF-2 |
| **Preconditions** | All upstream tools have only string/number params (no file content) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run detection on tools with no file parameters | Zero results |
| 2 | Verify INFO log: "No file proxy candidates found" | Info logged |
| 3 | Verify system operates normally without any wrappers | All tools accessible as-is |

**Test Data:** 3 tools with only string/number parameters
**Postconditions:** No wrappers created, system fully functional

---

### TC-214: Cleanup — File Deletion Failure (File Locked)

| Field | Value |
|-------|-------|
| **ID** | TC-214 |
| **Level** | IT-07 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-009 EF-1 |
| **Preconditions** | Temp file locked by another process |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create temp file and lock it (simulate external process) | File locked |
| 2 | Trigger per-request cleanup for that file | Deletion fails |
| 3 | Verify WARN log: "Cleanup failed for file_id: {id}" | Warning logged |
| 4 | Verify tool call result NOT masked by cleanup error | Agent receives correct response |
| 5 | Verify background TTL job will retry later | Record remains for retry |

**Test Data:** Locked temp file
**Postconditions:** File remains (will be cleaned by background job), tool call succeeds

---

### TC-215: Cleanup — Shutdown Timeout Exceeded

| Field | Value |
|-------|-------|
| **ID** | TC-215 |
| **Level** | IT-08 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-009 EF-3, BR-038 |
| **Preconditions** | Shutdown cleanup takes longer than configured timeout |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure shutdown-timeout-seconds=1 | Very short timeout |
| 2 | Create 1000 registry records with temp files | Large cleanup needed |
| 3 | Trigger shutdown cleanup | Cleanup starts |
| 4 | Verify timeout fires after 1 second | Cleanup interrupted |
| 5 | Verify WARN log about timeout | Warning logged |
| 6 | Verify remaining orphans handled on next startup | Startup cleanup catches them |

**Test Data:** 1000 records, 1-second timeout
**Postconditions:** Partial cleanup done, rest deferred to next startup

---

### TC-216: Output Proxy — Path Traversal in output_path

| Field | Value |
|-------|-------|
| **ID** | TC-216 |
| **Level** | UT-16 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-1, BR-025, Error Code INVALID_PATH |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{output_path: "/home/user/../../etc/shadow"}` | Error returned |
| 2 | Verify error: `"Invalid file path: path traversal not allowed"` | Error code INVALID_PATH |
| 3 | Verify security WARN log | Security event logged |

**Test Data:** output_path with `../` traversal
**Postconditions:** Request rejected, no file written

---

## 4. Business Rule Validation

### TC-300: BR-002 — No False Positive on "content" String Param

| Field | Value |
|-------|-------|
| **ID** | TC-300 |
| **Level** | UT-17 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-002 |
| **Preconditions** | Tool with param named "content" but type "string" and non-file description |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Provide param: `{name: "content", type: "string", description: "Message text content"}` | NOT detected as file param |
| 2 | Verify detectInputFileParams returns empty list | No false positive |

**Test Data:** `{"content": {"type": "string", "description": "Message text content to send"}}`
**Postconditions:** Parameter correctly excluded from detection

---

### TC-301: BR-005 — file_path Must Be Absolute

| Field | Value |
|-------|-------|
| **ID** | TC-301 |
| **Level** | UT-18 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-005 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call with `{file_path: "relative/path/file.pdf"}` | Rejected |
| 2 | Verify error indicates absolute path required | Clear error message |
| 3 | Call with `{file_path: "/absolute/path/file.pdf"}` (file exists) | Accepted |

**Test Data:** Relative path "relative/path/file.pdf" and absolute path "/tmp/test/file.pdf"
**Postconditions:** Only absolute paths accepted

---

### TC-302: BR-009 — Registry Record Created Before Processing

| Field | Value |
|-------|-------|
| **ID** | TC-302 |
| **Level** | IT-09 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-009, BR-014 |
| **Preconditions** | PostgreSQL available, valid file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Instrument code to check DB state before upstream call | Observation point set |
| 2 | Call input proxy with valid file | Processing starts |
| 3 | Verify registry record exists with status=PENDING BEFORE upstream call | Record created first |
| 4 | After completion, verify record deleted | Cleanup successful |

**Test Data:** Valid 1KB file
**Postconditions:** Verified ordering: INSERT → process → DELETE

---

### TC-303: BR-016 — Session ID Changes on Every Restart

| Field | Value |
|-------|-------|
| **ID** | TC-303 |
| **Level** | IT-10 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-016 |
| **Preconditions** | Application can be started twice |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start application, capture session_id_1 | UUID generated |
| 2 | Stop application | Shutdown |
| 3 | Start application again, capture session_id_2 | New UUID generated |
| 4 | Verify session_id_1 != session_id_2 | Different UUIDs |

**Test Data:** Two application startups
**Postconditions:** Each startup has unique session ID

---

### TC-304: BR-019 — Wrapper Name Matches Original Exactly

| Field | Value |
|-------|-------|
| **ID** | TC-304 |
| **Level** | UT-19 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-019 |
| **Preconditions** | Upstream tool "convert_pdf" detected |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate wrapper for tool "convert_pdf" | Wrapper created |
| 2 | Verify wrapper.name == "convert_pdf" | Exact match |
| 3 | Verify find_tools returns tool with name "convert_pdf" | Same name visible |

**Test Data:** Original tool name "convert_pdf"
**Postconditions:** Wrapper indistinguishable by name from original

---

### TC-305: BR-020 — Non-File Parameters Preserved in Wrapper

| Field | Value |
|-------|-------|
| **ID** | TC-305 |
| **Level** | UT-20 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-020 |
| **Preconditions** | Tool with mixed params (file + non-file) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Original tool has params: content (base64), format (string), quality (integer) | 3 params |
| 2 | Generate wrapper | Wrapper created |
| 3 | Verify wrapper has: file_path (string), format (string), quality (integer) | file param replaced, others preserved |
| 4 | Verify format and quality have same type, description, required status | Exact preservation |

**Test Data:** Tool with 3 params: 1 file (base64), 2 non-file (string, integer)
**Postconditions:** Non-file params identical in wrapper schema

---

### TC-306: BR-033/BR-035 — Per-Server Size Override Takes Precedence

| Field | Value |
|-------|-------|
| **ID** | TC-306 |
| **Level** | UT-21 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-033, BR-035 |
| **Preconditions** | Config: global max=50MB, pdf-tools override=100MB |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call proxy for server "pdf-tools" with 60MB file | Accepted (under 100MB limit) |
| 2 | Call proxy for server "other-server" with 60MB file | Rejected (over 50MB global limit) |
| 3 | Verify per-server override used for "pdf-tools" | 100MB limit applied |

**Test Data:** 60MB file, config: `{max-size-mb: 50, servers: {pdf-tools: {max-size-mb: 100}}}`
**Postconditions:** Per-server override correctly applied

---

### TC-307: BR-036 — Size Check Before File Read (Metadata Only)

| Field | Value |
|-------|-------|
| **ID** | TC-307 |
| **Level** | UT-22 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-036 |
| **Preconditions** | Large file exists (60MB) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call proxy with 60MB file (max=50MB) | Rejected |
| 2 | Verify file content was NOT loaded into memory | No readBytes() call |
| 3 | Verify only Files.size() was called (metadata check) | Size from file metadata |

**Test Data:** 60MB file, max-size-mb=50
**Postconditions:** No memory spike from large file, fast rejection

---

### TC-308: BR-030 — Base64 Detection Threshold (Length > 1000)

| Field | Value |
|-------|-------|
| **ID** | TC-308 |
| **Level** | UT-23 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-030 |
| **Preconditions** | Response with string fields of various lengths |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Response field with 500-char valid base64 string | NOT detected as file output |
| 2 | Response field with 2000-char valid base64 string | Detected as file output |
| 3 | Response field with 2000-char non-base64 string (contains spaces) | NOT detected |

**Test Data:** Strings of 500 chars (base64), 2000 chars (base64), 2000 chars (non-base64)
**Postconditions:** Only long valid base64 strings trigger detection

---

### TC-309: BR-039 — Per-Request Cleanup Does Not Mask Tool Result

| Field | Value |
|-------|-------|
| **ID** | TC-309 |
| **Level** | IT-11 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-039 |
| **Preconditions** | Cleanup configured to throw exception |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Mock registry.deleteEntry() to throw RuntimeException | Cleanup will fail |
| 2 | Call input proxy with valid file | Processing succeeds |
| 3 | Verify upstream response returned to agent (not cleanup error) | Agent gets correct response |
| 4 | Verify WARN log about cleanup failure | Warning logged |

**Test Data:** Valid file, mock cleanup failure
**Postconditions:** Agent receives correct response despite cleanup failure

---

### TC-310: BR-013 — Temp Files Created with Restrictive Permissions

| Field | Value |
|-------|-------|
| **ID** | TC-310 |
| **Level** | IT-12 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-013 |
| **Preconditions** | HTTP/SSE mode, file upload |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Upload file via upload_file tool | Temp file created |
| 2 | Check file permissions on disk | Permissions are `rw-------` (600) |
| 3 | Verify other users cannot read the temp file | Access denied for others |

**Test Data:** Any valid file upload
**Postconditions:** Temp file has owner-only read/write permissions

---

## 5. Boundary & Negative Testing

### TC-400: Boundary — File Size Exactly at Max Limit

| Field | Value |
|-------|-------|
| **ID** | TC-400 |
| **Level** | PBT-01 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-006, BR-033 |
| **Preconditions** | Config max-size-mb=50 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create file of exactly 50MB (52,428,800 bytes) | File at boundary |
| 2 | Call proxy with this file | Accepted (≤ max) |
| 3 | Create file of 50MB + 1 byte (52,428,801 bytes) | File over boundary |
| 4 | Call proxy with this file | Rejected (> max) |

**Test Data:** Files at 50MB exactly and 50MB+1 byte
**Postconditions:** Boundary correctly enforced

---

### TC-401: Boundary — Empty File (0 bytes)

| Field | Value |
|-------|-------|
| **ID** | TC-401 |
| **Level** | PBT-02 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | UC-002 |
| **Preconditions** | Empty file exists on disk |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create 0-byte file | File exists but empty |
| 2 | Call proxy with empty file | Accepted (valid file) |
| 3 | Verify base64 encoding of empty content | Empty string or minimal base64 |
| 4 | Verify upstream called with empty base64 | Upstream receives empty content param |

**Test Data:** 0-byte file
**Postconditions:** Empty file processed without error

---

### TC-402: Boundary — File Path Maximum Length

| Field | Value |
|-------|-------|
| **ID** | TC-402 |
| **Level** | PBT-03 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | UC-002, Data Spec file_path VARCHAR(500) |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create file with path length = 500 characters | At DB column limit |
| 2 | Call proxy with this path | Accepted |
| 3 | Create file with path length = 501 characters | Over DB column limit |
| 4 | Call proxy with this path | Handled gracefully (truncation or error) |

**Test Data:** Paths of 500 and 501 characters
**Postconditions:** Boundary handled without crash

---

### TC-403: Negative — Non-Absolute Path Variants

| Field | Value |
|-------|-------|
| **ID** | TC-403 |
| **Level** | PBT-04 |
| **Priority** | Medium |
| **Type** | Negative |
| **Requirement** | BR-005, BR-023 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call with `file_path: ""` (empty string) | Rejected |
| 2 | Call with `file_path: "file.pdf"` (no path separator) | Rejected |
| 3 | Call with `file_path: "./relative/file.pdf"` | Rejected |
| 4 | Call with `file_path: "~/home/file.pdf"` (tilde) | Rejected |
| 5 | Call with `file_path: null` | Rejected (missing required param) |

**Test Data:** Various invalid path formats
**Postconditions:** All non-absolute paths rejected with clear error

---

### TC-404: Negative — Path Traversal Variants

| Field | Value |
|-------|-------|
| **ID** | TC-404 |
| **Level** | PBT-05 |
| **Priority** | High |
| **Type** | Negative / Security |
| **Requirement** | BR-007, BR-025 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `"/home/user/../etc/passwd"` | Rejected |
| 2 | `"/home/user/..\\etc\\passwd"` (backslash) | Rejected |
| 3 | `"/home/user/%2e%2e/etc/passwd"` (URL encoded) | Rejected |
| 4 | `"/home/user/....//etc/passwd"` (double dot variations) | Rejected |
| 5 | `"/tmp/safe/../../etc/shadow"` | Rejected |

**Test Data:** Multiple path traversal attack vectors
**Postconditions:** All traversal attempts blocked

---

### TC-405: Negative — Invalid UUID Formats for file_id

| Field | Value |
|-------|-------|
| **ID** | TC-405 |
| **Level** | PBT-06 |
| **Priority** | Medium |
| **Type** | Negative |
| **Requirement** | BR-010 |
| **Preconditions** | HTTP/SSE mode |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `file_id: ""` (empty) | INVALID_FILE_ID error |
| 2 | `file_id: "12345"` (short number) | INVALID_FILE_ID error |
| 3 | `file_id: "not-a-uuid-at-all"` | INVALID_FILE_ID error |
| 4 | `file_id: "550e8400-e29b-41d4-a716"` (truncated UUID) | INVALID_FILE_ID error |
| 5 | `file_id: "550e8400-e29b-41d4-a716-446655440000-extra"` (too long) | INVALID_FILE_ID error |

**Test Data:** Various invalid UUID formats
**Postconditions:** All invalid formats rejected

---

### TC-406: Boundary — TTL Expiration Timing

| Field | Value |
|-------|-------|
| **ID** | TC-406 |
| **Level** | PBT-07 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-011, BR-041 |
| **Preconditions** | TTL configured to 60 minutes |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Upload file, wait 59 minutes (simulated) | File still valid |
| 2 | Call wrapper with file_id at 59 minutes | Succeeds |
| 3 | Upload file, wait 61 minutes (simulated) | File expired |
| 4 | Call wrapper with file_id at 61 minutes | FILE_EXPIRED error |

**Test Data:** Files with created_at at TTL boundary
**Postconditions:** TTL boundary correctly enforced

---

### TC-407: Boundary — Max Concurrent Operations (50)

| Field | Value |
|-------|-------|
| **ID** | TC-407 |
| **Level** | PBT-08 |
| **Priority** | Medium |
| **Type** | Boundary / Performance |
| **Requirement** | FSD §8 NFR — 50 concurrent operations |
| **Preconditions** | 50 test files prepared, system under load |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 50 concurrent proxy requests (coroutines) | All start processing |
| 2 | Verify all 50 complete without errors | All succeed |
| 3 | Verify no registry record leaks | Registry empty after all complete |
| 4 | Launch 51st concurrent request | Still succeeds (no hard limit, just target) |

**Test Data:** 50 x 1KB files, concurrent coroutine launches
**Postconditions:** All operations complete, system stable

---

## 6. Non-Functional Testing (Performance, Security)

### TC-600: Performance — Proxy Layer Overhead < 100ms

| Field | Value |
|-------|-------|
| **ID** | TC-600 |
| **Level** | E2E-API-12 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD §8 — Proxy overhead < 100ms p95 |
| **Preconditions** | System running, 1KB test file (minimal I/O) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute 100 proxy requests with 1KB file | All complete |
| 2 | Measure time from proxy intercept to upstream dispatch (excluding file I/O) | Collect latencies |
| 3 | Calculate p95 latency | p95 < 100ms |

**Acceptance Criteria:** p95 proxy overhead (excluding file read time) < 100ms

---

### TC-601: Performance — 10MB File Encode < 500ms

| Field | Value |
|-------|-------|
| **ID** | TC-601 |
| **Level** | E2E-API-13 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD §8 — File read + base64 encode (10MB) < 500ms p95 |
| **Preconditions** | 10MB test file on disk |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute 20 proxy requests with 10MB file | All complete |
| 2 | Measure end-to-end encoding time (read + encode) | Collect latencies |
| 3 | Calculate p95 latency | p95 < 500ms |

**Acceptance Criteria:** p95 file read + base64 encode for 10MB file < 500ms

---

### TC-602: Performance — Registry DB Operation < 10ms

| Field | Value |
|-------|-------|
| **ID** | TC-602 |
| **Level** | IT-13 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD §8 — Registry DB operation < 10ms p95 |
| **Preconditions** | PostgreSQL (Testcontainers), table with indexes |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute 100 INSERT operations | Measure latencies |
| 2 | Execute 100 UPDATE operations | Measure latencies |
| 3 | Execute 100 DELETE operations | Measure latencies |
| 4 | Calculate p95 for each operation type | All p95 < 10ms |

**Acceptance Criteria:** p95 for INSERT/UPDATE/DELETE single record < 10ms

---

### TC-603: Performance — Startup Cleanup < 5s (1000 Records)

| Field | Value |
|-------|-------|
| **ID** | TC-603 |
| **Level** | IT-14 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD §8 — Startup cleanup < 5s for < 1000 records |
| **Preconditions** | 1000 stale records in DB, 1000 orphan temp files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Pre-populate DB with 1000 records (old session_id) | Records exist |
| 2 | Create 1000 temp files | Files exist |
| 3 | Run startupCleanup() and measure duration | Cleanup completes |
| 4 | Verify duration < 5 seconds | Performance target met |
| 5 | Verify all records and files deleted | Clean state |

**Acceptance Criteria:** Startup cleanup for 1000 records + files completes in < 5 seconds

---

### TC-604: Security — Path Traversal via Encoded Characters

| Field | Value |
|-------|-------|
| **ID** | TC-604 |
| **Level** | E2E-API-14 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD §7.3 — Path Security |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call with `file_path: "/home/%2e%2e/etc/passwd"` | Rejected |
| 2 | Call with `file_path: "/home/\u002e\u002e/etc/passwd"` (unicode) | Rejected |
| 3 | Verify canonical path resolution catches encoded traversal | Security validation passes |

**Acceptance Criteria:** Zero successful path traversal attacks regardless of encoding

---

### TC-605: Security — Symlink to Sensitive File

| Field | Value |
|-------|-------|
| **ID** | TC-605 |
| **Level** | E2E-API-15 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD §7.3 — Symlink attacks |
| **Preconditions** | Symlink created pointing to sensitive file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create symlink `/tmp/safe/link.pdf` → `/etc/shadow` | Symlink exists |
| 2 | Call proxy with `{file_path: "/tmp/safe/link.pdf"}` | Rejected after symlink resolution |
| 3 | Verify toRealPath() resolves symlink before validation | Canonical path checked |

**Acceptance Criteria:** Symlink attacks detected and blocked

---

### TC-606: Security — Temp File Permissions Enforcement

| Field | Value |
|-------|-------|
| **ID** | TC-606 |
| **Level** | E2E-API-16 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD §7.2 — Temp file permissions rw------- |
| **Preconditions** | HTTP/SSE mode, file upload |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Upload file via upload_file tool | Temp file created |
| 2 | Check POSIX permissions of temp file | `rw-------` (600) |
| 3 | Attempt to read file as different user | Access denied |

**Acceptance Criteria:** 100% of temp files created with owner-only permissions

---

### TC-607: Security — Oversized File DoS Prevention

| Field | Value |
|-------|-------|
| **ID** | TC-607 |
| **Level** | E2E-API-17 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD §7.3 — Large file DoS |
| **Preconditions** | 1GB file exists on disk |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call proxy with 1GB file (max=50MB) | Rejected immediately |
| 2 | Verify rejection happens BEFORE file content is read | No memory spike |
| 3 | Measure time to reject | < 10ms (metadata check only) |

**Acceptance Criteria:** Oversized files rejected before any content loading

---

### TC-608: Performance — Throughput ≥ 20 req/s

| Field | Value |
|-------|-------|
| **ID** | TC-608 |
| **Level** | E2E-API-18 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD §8 — Throughput ≥ 20 req/s |
| **Preconditions** | System running, 1KB test files |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 200 proxy requests over 10 seconds (20 req/s rate) | All processed |
| 2 | Verify all requests complete successfully | 0 errors |
| 3 | Verify no request timeout | All within 30s |

**Acceptance Criteria:** System sustains ≥ 20 proxy requests per second without errors

---

### TC-609: Security — Temp Directory Escape Prevention

| Field | Value |
|-------|-------|
| **ID** | TC-609 |
| **Level** | UT-24 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD §7.4 — Temp directory escape |
| **Preconditions** | Temp directory configured as /tmp/mcp-file-proxy |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Attempt to resolve file_id to path outside temp directory | Rejected |
| 2 | Verify validateTempPath checks canonical path starts with temp-directory | Validation passes |
| 3 | Attempt symlink from temp dir to external location | Rejected after resolution |

**Acceptance Criteria:** All temp file operations confined to configured temp directory

---

## 7. Integration Testing

### TC-700: IT — End-to-End Input Proxy with Real PostgreSQL

| Field | Value |
|-------|-------|
| **ID** | TC-700 |
| **Level** | IT-01 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-002, UC-004 |
| **Preconditions** | Testcontainers PostgreSQL running, file_proxy_registry table created |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create test file on disk | File exists |
| 2 | Call input proxy handler with real DB registry | Record inserted |
| 3 | Verify record in PostgreSQL with correct fields | All fields match |
| 4 | Complete proxy operation | Record deleted |
| 5 | Verify table empty | No orphan records |

**Test Data:** 1KB test file, real PostgreSQL via Testcontainers
**Postconditions:** DB clean, file processed

---

### TC-701: IT — End-to-End Output Proxy with File System

| Field | Value |
|-------|-------|
| **ID** | TC-701 |
| **Level** | IT-02 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-006 |
| **Preconditions** | Output directory exists, mock upstream response prepared |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create source file simulating upstream artifact | Source file exists |
| 2 | Call output proxy with output_path | File copied |
| 3 | Verify output file content matches source | Byte-for-byte match |
| 4 | Verify output file size matches bytes_written in response | Consistent |

**Test Data:** 5KB source file, output_path in @TempDir
**Postconditions:** Output file exists with correct content

---

### TC-702: IT — Startup Cleanup with Real DB and Files

| Field | Value |
|-------|-------|
| **ID** | TC-702 |
| **Level** | IT-03 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-009, BR-037 |
| **Preconditions** | Stale records in DB, orphan files on disk |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert 10 records with old session_id into real PostgreSQL | Records exist |
| 2 | Create 10 corresponding temp files | Files exist |
| 3 | Run startupCleanup with new session_id | Cleanup executes |
| 4 | Query DB for old session records | 0 records found |
| 5 | Check temp directory for orphan files | 0 orphan files |
| 6 | Verify CleanupSummary: recordsDeleted=10, filesDeleted=10 | Summary correct |

**Test Data:** 10 stale records + 10 orphan files
**Postconditions:** DB and filesystem clean

---

### TC-703: IT — Background TTL Cleanup Job

| Field | Value |
|-------|-------|
| **ID** | TC-703 |
| **Level** | IT-04 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | UC-009, BR-040, BR-041 |
| **Preconditions** | Records with old created_at timestamps |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert records with created_at = 2 hours ago | Expired records exist |
| 2 | Start background cleanup job with TTL=60min | Job runs |
| 3 | Wait for cleanup interval to fire | Job executes |
| 4 | Verify expired records deleted | 0 expired records remain |
| 5 | Verify non-expired records untouched | Recent records still exist |

**Test Data:** Mix of expired (2h old) and fresh (5min old) records
**Postconditions:** Only expired records removed

---

### TC-704: IT — Wrapper Tool Discovery Integration

| Field | Value |
|-------|-------|
| **ID** | TC-704 |
| **Level** | IT-05 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-005, BR-019, BR-021 |
| **Preconditions** | Full tool registry with detection and wrapper generation |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Register upstream tool with base64 param in ToolRegistry | Tool registered |
| 2 | Trigger detection and wrapper generation | Wrapper created |
| 3 | Query find_tools via MCP protocol | Wrapper returned, original hidden |
| 4 | Execute wrapper via execute_dynamic_tool | Routed through FileProxyService |
| 5 | Verify original tool still callable internally | Internal dispatch works |

**Test Data:** Mock upstream tool "analyze_image" with base64 "image_data" param
**Postconditions:** Wrapper visible externally, original accessible internally

---

### TC-705: IT — HTTP/SSE Upload + Resolve Lifecycle

| Field | Value |
|-------|-------|
| **ID** | TC-705 |
| **Level** | IT-06 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-003 |
| **Preconditions** | HTTP/SSE transport mode, real DB, real filesystem |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call upload_file with valid file | file_id returned |
| 2 | Verify temp file exists at `{temp-dir}/{file_id}` | File on disk |
| 3 | Verify registry record with direction=INPUT, status=PENDING | Record in DB |
| 4 | Call wrapper with returned file_id | File resolved and processed |
| 5 | Verify temp file deleted | File removed |
| 6 | Verify registry record deleted | Record removed |

**Test Data:** 2KB test file, HTTP/SSE mode
**Postconditions:** Full lifecycle complete, no orphans

---

### TC-706: IT — Concurrent Proxy Operations (Thread Safety)

| Field | Value |
|-------|-------|
| **ID** | TC-706 |
| **Level** | IT-07 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD §8 — 50 concurrent operations |
| **Preconditions** | 50 test files, real DB |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 50 coroutines each calling input proxy | All start |
| 2 | Verify all 50 complete without exceptions | 0 errors |
| 3 | Verify registry table empty after all complete | No leaked records |
| 4 | Verify no file handle leaks | All files closed |

**Test Data:** 50 x 1KB files, concurrent coroutine execution
**Postconditions:** System stable, no resource leaks

---

### TC-707: IT — Graceful Degradation (DB Down During Operation)

| Field | Value |
|-------|-------|
| **ID** | TC-707 |
| **Level** | IT-08 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-004 AF-1, FSD §8 — Availability |
| **Preconditions** | PostgreSQL container can be stopped mid-operation |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start proxy operation | Processing begins |
| 2 | Stop PostgreSQL container during processing | DB unavailable |
| 3 | Verify proxy continues without registry | Degraded mode |
| 4 | Verify upstream tool still called | Upstream receives base64 |
| 5 | Verify response returned to agent | Agent gets result |
| 6 | Verify WARN log about degraded mode | Warning logged |

**Test Data:** Valid file, PostgreSQL stopped mid-operation
**Postconditions:** Operation completes in degraded mode

---

### TC-708: IT — Detection Re-run on Server Reconnect

| Field | Value |
|-------|-------|
| **ID** | TC-708 |
| **Level** | IT-09 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | BR-003 |
| **Preconditions** | Upstream server previously connected, now reconnecting |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initial connection: detect tool with 1 file param | 1 wrapper created |
| 2 | Simulate server disconnect | Server offline |
| 3 | Simulate server reconnect with updated schema (2 file params) | Re-detection triggered |
| 4 | Verify wrapper regenerated with 2 file_path params | Updated wrapper |

**Test Data:** Tool schema v1 (1 param) → v2 (2 params)
**Postconditions:** Wrapper reflects latest schema

---

### TC-709: IT — Output Runtime Detection (First Call)

| Field | Value |
|-------|-------|
| **ID** | TC-709 |
| **Level** | IT-10 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-007, BR-029 |
| **Preconditions** | Tool not flagged by static detection, first call returns artifacts |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call tool (no output proxy yet) | Response returned as-is |
| 2 | Verify response inspected for file content | Runtime detection runs |
| 3 | Response contains `artifacts[].path` | Tool flagged for output proxy |
| 4 | Verify wrapper updated to include output_path param | Wrapper enhanced |
| 5 | Second call with output_path | Output proxy active |

**Test Data:** Tool returning `{artifacts: [{path: "/tmp/result.pdf"}]}` on first call
**Postconditions:** Tool now has output proxy capability

---

### TC-710: IT — Both Input + Output Proxy in Single Call

| Field | Value |
|-------|-------|
| **ID** | TC-710 |
| **Level** | IT-11 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | UC-002, UC-006 |
| **Preconditions** | Tool detected for both input and output proxy |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call wrapper with `{file_path: "/tmp/input.pdf", output_path: "/tmp/output/result.pdf"}` | Both handled |
| 2 | Verify input file read and encoded | Upstream receives base64 |
| 3 | Verify upstream response intercepted | Output extracted |
| 4 | Verify output file saved to output_path | File exists |
| 5 | Verify 2 registry records created and cleaned | Both records managed |

**Test Data:** Input file 3KB, upstream returns artifacts[].path
**Postconditions:** Input processed, output saved, registry clean

---

### TC-711: IT — Configuration Validation (Invalid Values)

| Field | Value |
|-------|-------|
| **ID** | TC-711 |
| **Level** | UT-25 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | BR-034 |
| **Preconditions** | Invalid configuration values |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set max-size-mb = 0 | Invalid |
| 2 | Verify config validation rejects and uses default (50MB) | Default applied, WARN logged |
| 3 | Set max-size-mb = -1 | Invalid |
| 4 | Verify config validation rejects | Default applied, WARN logged |

**Test Data:** Invalid config values: 0, -1, null
**Postconditions:** System uses safe defaults when config invalid

---

## 8. Manual SIT Test Cases

### SIT-01: End-to-End Input Proxy — Real Upstream Server

| Field | Value |
|-------|-------|
| **ID** | SIT-01 |
| **Level** | SIT |
| **Priority** | High |
| **Type** | Manual — Exploratory |
| **Requirement** | UC-002, Story #2 |
| **Preconditions** | Orchestrator running with real upstream MCP server that has base64 tool |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start orchestrator with upstream server configured | Server starts, detection runs |
| 2 | Observe startup logs for detection results | `[FileProxy] Detected: tool=X, param=Y` logged |
| 3 | Call find_tools and verify wrapper visible | Wrapper tool in response |
| 4 | Call wrapper with real file path | File processed, response returned |
| 5 | Verify no file content in MCP response to client | Clean response |

**Test Data:** Real PDF file, real upstream MCP server
**Postconditions:** Full flow verified with real components

---

### SIT-02: Startup Cleanup Timing — Verify Blocking Behavior

| Field | Value |
|-------|-------|
| **ID** | SIT-02 |
| **Level** | SIT |
| **Priority** | High |
| **Type** | Manual — Timing |
| **Requirement** | BR-037, BR-018 |
| **Preconditions** | Stale records from previous session in DB |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert stale records into DB manually | Records exist |
| 2 | Start orchestrator and observe startup sequence | Cleanup runs first |
| 3 | Attempt to call tool DURING cleanup | Request queued/rejected until cleanup done |
| 4 | Verify cleanup completes before first tool call succeeds | Blocking behavior confirmed |

**Test Data:** 100 stale records in DB
**Postconditions:** Cleanup verified as blocking prerequisite

---

### SIT-03: Graceful Shutdown — Observe Cleanup on SIGTERM

| Field | Value |
|-------|-------|
| **ID** | SIT-03 |
| **Level** | SIT |
| **Priority** | Medium |
| **Type** | Manual — Timing |
| **Requirement** | UC-009, BR-038 |
| **Preconditions** | Orchestrator running with active proxy operations |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start proxy operations (create some registry records) | Records in DB |
| 2 | Send SIGTERM to orchestrator process | Shutdown initiated |
| 3 | Observe shutdown logs | `[FileProxy] Cleanup: records=N, files=N` logged |
| 4 | Verify DB records for current session deleted | 0 records remain |
| 5 | Verify temp files deleted | Temp directory clean |

**Test Data:** Active proxy operations during shutdown
**Postconditions:** Clean shutdown, no orphans

---

### SIT-04: Degraded Mode — DB Connection Lost Mid-Operation

| Field | Value |
|-------|-------|
| **ID** | SIT-04 |
| **Level** | SIT |
| **Priority** | High |
| **Type** | Manual — Exploratory |
| **Requirement** | UC-004 AF-1 |
| **Preconditions** | Orchestrator running, PostgreSQL accessible |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start a proxy operation | Processing begins |
| 2 | Kill PostgreSQL process during operation | DB connection lost |
| 3 | Observe orchestrator behavior | Continues in degraded mode |
| 4 | Verify tool call still completes | Agent receives response |
| 5 | Observe WARN logs about registry unavailability | Warnings present |
| 6 | Restart PostgreSQL | Connection restored |
| 7 | Verify next operation uses registry normally | Normal mode resumed |

**Test Data:** Valid file, PostgreSQL killed mid-operation
**Postconditions:** System recovers gracefully

---

### SIT-05: Feature Toggle — Disable File Proxy

| Field | Value |
|-------|-------|
| **ID** | SIT-05 |
| **Level** | SIT |
| **Priority** | Medium |
| **Type** | Manual — Configuration |
| **Requirement** | TDD §10.2 — Feature Flags |
| **Preconditions** | Orchestrator with file-proxy.enabled=false |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set `file-proxy.enabled: false` in config | Feature disabled |
| 2 | Start orchestrator | No detection runs |
| 3 | Call find_tools | Original tools visible (no wrappers) |
| 4 | Call original tool directly | Works normally |
| 5 | Verify upload_file tool NOT registered | Tool not discoverable |
| 6 | Verify zero overhead on existing functionality | No proxy interception |

**Test Data:** Config with file-proxy.enabled=false
**Postconditions:** System operates as if file proxy doesn't exist

---

### SIT-06: Long-Running Stability — Zero Orphan Files After 24h

| Field | Value |
|-------|-------|
| **ID** | SIT-06 |
| **Level** | SIT |
| **Priority** | Medium |
| **Type** | Manual — Stability |
| **Requirement** | FSD §8 — Zero orphan file guarantee |
| **Preconditions** | Orchestrator running for extended period with periodic proxy operations |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run orchestrator with periodic proxy operations (every 5 min) for 24h | System running |
| 2 | After 24h, check temp directory | 0 orphan files |
| 3 | Check registry table | 0 stale records (only active operations) |
| 4 | Check disk space usage trend | No accumulation |
| 5 | Review logs for cleanup failures | 0 unresolved cleanup failures |

**Test Data:** Automated script sending proxy requests every 5 minutes for 24 hours
**Postconditions:** Zero orphan files, stable disk usage

---

## 9. Requirements Traceability Matrix (RTM)

| Requirement | Source | Test Cases | Coverage |
|-------------|--------|------------|----------|
| UC-001 (Auto-Detection Input) | FSD 3.1 | TC-001, TC-100, TC-101, TC-212, TC-213, TC-300 | ✅ |
| UC-002 (Input Proxy STDIO) | FSD 3.2 | TC-002, TC-102, TC-200, TC-201, TC-202, TC-203, TC-204, TC-700 | ✅ |
| UC-003 (Input Proxy HTTP/SSE) | FSD 3.3 | TC-003, TC-105, TC-205, TC-206, TC-207, TC-208, TC-705 | ✅ |
| UC-004 (Database Registry) | FSD 3.4 | TC-004, TC-107, TC-302, TC-700, TC-702, TC-707 | ✅ |
| UC-005 (Wrapper Hiding) | FSD 3.5 | TC-005, TC-106, TC-304, TC-305, TC-704 | ✅ |
| UC-006 (Output Proxy) | FSD 3.6 | TC-006, TC-007, TC-103, TC-104, TC-108, TC-209, TC-210, TC-211, TC-216, TC-701, TC-710 | ✅ |
| UC-007 (Output Detection) | FSD 3.7 | TC-008, TC-308, TC-709 | ✅ |
| UC-008 (Max File Size) | FSD 3.8 | TC-201, TC-306, TC-307, TC-400, TC-711 | ✅ |
| UC-009 (Lifecycle Cleanup) | FSD 3.9 | TC-009, TC-214, TC-215, TC-303, TC-309, TC-702, TC-703 | ✅ |
| BR-001 (≥1 heuristic match) | FSD 3.1.3 | TC-001 | ✅ |
| BR-002 (No false positive) | FSD 3.1.3 | TC-300 | ✅ |
| BR-003 (Detection on startup + reconnect) | FSD 3.1.3 | TC-101, TC-708 | ✅ |
| BR-004 (Multiple file params) | FSD 3.1.3 | TC-100 | ✅ |
| BR-005 (Absolute path) | FSD 3.2.3 | TC-301, TC-403 | ✅ |
| BR-006 (Max size) | FSD 3.2.3 | TC-201, TC-400 | ✅ |
| BR-007 (No ../) | FSD 3.2.3 | TC-203, TC-404 | ✅ |
| BR-008 (No file in context) | FSD 3.2.3 | TC-002 | ✅ |
| BR-009 (Registry before/after) | FSD 3.2.3 | TC-002, TC-204, TC-302 | ✅ |
| BR-010 (Valid UUID) | FSD 3.3.3 | TC-205, TC-405 | ✅ |
| BR-011 (TTL expiration) | FSD 3.3.3 | TC-207, TC-406 | ✅ |
| BR-012 (HTTP/SSE only) | FSD 3.3.3 | TC-003 | ✅ |
| BR-013 (Restrictive permissions) | FSD 3.3.3 | TC-310, TC-606 | ✅ |
| BR-014 (Record before processing) | FSD 3.4.3 | TC-302 | ✅ |
| BR-015 (Delete after success) | FSD 3.4.3 | TC-004 | ✅ |
| BR-016 (Session ID changes) | FSD 3.4.3 | TC-303 | ✅ |
| BR-017 (Status transitions) | FSD 3.4.3 | TC-004, TC-204 | ✅ |
| BR-018 (Startup before requests) | FSD 3.4.3 | SIT-02 | ✅ |
| BR-019 (Wrapper name match) | FSD 3.5.3 | TC-304 | ✅ |
| BR-020 (Non-file params preserved) | FSD 3.5.3 | TC-305 | ✅ |
| BR-021 (Original callable internally) | FSD 3.5.3 | TC-005, TC-704 | ✅ |
| BR-022 (Graceful degradation) | FSD 3.5.3 | TC-106 | ✅ |
| BR-023 (output_path absolute) | FSD 3.6.3 | TC-216 | ✅ |
| BR-024 (Parent dir exists) | FSD 3.6.3 | TC-209, TC-210 | ✅ |
| BR-025 (No traversal output) | FSD 3.6.3 | TC-216 | ✅ |
| BR-026 (Passthrough without output_path) | FSD 3.6.3 | TC-103 | ✅ |
| BR-027 (Overwrite default) | FSD 3.6.3 | TC-104 | ✅ |
| BR-028 (Static detection once) | FSD 3.7.3 | TC-008 | ✅ |
| BR-029 (Runtime first call only) | FSD 3.7.3 | TC-709 | ✅ |
| BR-030 (Base64 threshold >1000) | FSD 3.7.3 | TC-308 | ✅ |
| BR-031 (Name patterns) | FSD 3.7.3 | TC-008 | ✅ |
| BR-032 (No flag on error response) | FSD 3.7.3 | TC-709 | ✅ |
| BR-033 (Default 50MB) | FSD 3.8.3 | TC-306, TC-400 | ✅ |
| BR-034 (Positive integer) | FSD 3.8.3 | TC-711 | ✅ |
| BR-035 (Per-server override) | FSD 3.8.3 | TC-306 | ✅ |
| BR-036 (Size check before read) | FSD 3.8.3 | TC-307, TC-607 | ✅ |
| BR-037 (Startup cleanup blocking) | FSD 3.9.3 | TC-009, SIT-02 | ✅ |
| BR-038 (Shutdown timeout) | FSD 3.9.3 | TC-215, SIT-03 | ✅ |
| BR-039 (Cleanup no mask) | FSD 3.9.3 | TC-309 | ✅ |
| BR-040 (Background 15min) | FSD 3.9.3 | TC-703 | ✅ |
| BR-041 (TTL 60min) | FSD 3.9.3 | TC-406, TC-703 | ✅ |
| Story #1 AC (Auto-detect) | BRD 2.3 | TC-001, TC-100, TC-212, TC-213 | ✅ |
| Story #2 AC (STDIO proxy) | BRD 2.3 | TC-002, TC-200–TC-204 | ✅ |
| Story #3 AC (HTTP/SSE proxy) | BRD 2.3 | TC-003, TC-205–TC-208 | ✅ |
| Story #4 AC (Registry) | BRD 2.3 | TC-004, TC-302, TC-303 | ✅ |
| Story #5 AC (Wrapper hiding) | BRD 2.3 | TC-005, TC-106, TC-304, TC-305 | ✅ |
| Story #6 AC (Output proxy) | BRD 2.3 | TC-006, TC-007, TC-209–TC-211 | ✅ |
| Story #7 AC (Output detection) | BRD 2.3 | TC-008, TC-308, TC-709 | ✅ |
| Story #8 AC (Max file size) | BRD 2.3 | TC-201, TC-306, TC-400 | ✅ |
| Story #9 AC (Cleanup) | BRD 2.3 | TC-009, TC-214, TC-215, SIT-02, SIT-03 | ✅ |
| NFR — Proxy overhead <100ms | FSD §8 | TC-600 | ✅ |
| NFR — 10MB encode <500ms | FSD §8 | TC-601 | ✅ |
| NFR — DB op <10ms | FSD §8 | TC-602 | ✅ |
| NFR — 50 concurrent ops | FSD §8 | TC-407, TC-706 | ✅ |
| NFR — Startup cleanup <5s | FSD §8 | TC-603 | ✅ |
| NFR — Throughput ≥20 req/s | FSD §8 | TC-608 | ✅ |
| NFR — Zero orphan files | FSD §8 | SIT-06 | ✅ |
| NFR — Graceful degradation | FSD §8 | TC-107, TC-707, SIT-04 | ✅ |
| Security — Path traversal | FSD §7.3 | TC-203, TC-216, TC-404, TC-604 | ✅ |
| Security — Symlink attacks | FSD §7.3 | TC-605 | ✅ |
| Security — Temp permissions | FSD §7.2 | TC-310, TC-606 | ✅ |
| Security — DoS prevention | FSD §7.3 | TC-607 | ✅ |
| Error: FILE_NOT_FOUND | FSD §9.1 | TC-200 | ✅ |
| Error: FILE_TOO_LARGE | FSD §9.1 | TC-201 | ✅ |
| Error: FILE_NOT_READABLE | FSD §9.1 | TC-202 | ✅ |
| Error: INVALID_PATH | FSD §9.1 | TC-203, TC-216 | ✅ |
| Error: INVALID_FILE_ID | FSD §9.1 | TC-205 | ✅ |
| Error: FILE_ID_NOT_FOUND | FSD §9.1 | TC-206 | ✅ |
| Error: FILE_EXPIRED | FSD §9.1 | TC-207 | ✅ |
| Error: UPLOAD_FAILED | FSD §9.1 | TC-208 | ✅ |
| Error: OUTPUT_DIR_NOT_FOUND | FSD §9.1 | TC-209 | ✅ |
| Error: OUTPUT_NOT_WRITABLE | FSD §9.1 | TC-210 | ✅ |
| Error: NO_FILE_IN_RESPONSE | FSD §9.1 | TC-211 | ✅ |
| Error: REGISTRY_UNAVAILABLE | FSD §9.1 | TC-107, TC-707 | ✅ |
| Error: CLEANUP_FAILED | FSD §9.1 | TC-214 | ✅ |
| Error: DETECTION_FAILED | FSD §9.1 | TC-212 | ✅ |
| Error: WRAPPER_CREATION_FAILED | FSD §9.1 | TC-106 | ✅ |

**Coverage Summary:**

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases (UC-001 to UC-009) | 9 | 9 | 100% |
| Business Rules (BR-001 to BR-041) | 41 | 41 | 100% |
| User Stories (Story #1 to #9) | 9 | 9 | 100% |
| Error Codes | 15 | 15 | 100% |
| Non-Functional Requirements | 8 | 8 | 100% |
| Security Requirements | 4 | 4 | 100% |
| **Overall** | **86** | **86** | **100%** |

---

## 10. Appendix

### Test Data Setup

**Test File Generation (Kotlin):**

```kotlin
// FileProxyTestFixtures.kt
object FileProxyTestFixtures {
    fun createTempFile(content: ByteArray, name: String = "test.pdf"): Path {
        val tempDir = Files.createTempDirectory("file-proxy-test")
        val file = tempDir.resolve(name)
        Files.write(file, content)
        return file
    }

    fun createLargeFile(sizeMb: Int): Path {
        val tempDir = Files.createTempDirectory("file-proxy-test")
        val file = tempDir.resolve("large-${sizeMb}mb.bin")
        RandomAccessFile(file.toFile(), "rw").use { it.setLength(sizeMb.toLong() * 1024 * 1024) }
        return file
    }

    fun createFileWithPermissions(content: ByteArray, permissions: String): Path {
        val file = createTempFile(content)
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions))
        return file
    }
}
```

**Database Setup (SQL):**

```sql
-- Insert stale records for cleanup testing
INSERT INTO file_proxy_registry (file_id, session_id, file_path, file_name, file_size, direction, status, created_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'old-session-id', '/tmp/mcp-file-proxy/stale1.pdf', 'stale1.pdf', 1024, 'INPUT', 'PENDING', NOW() - INTERVAL '2 hours'),
    ('22222222-2222-2222-2222-222222222222', 'old-session-id', '/tmp/mcp-file-proxy/stale2.pdf', 'stale2.pdf', 2048, 'INPUT', 'PENDING', NOW() - INTERVAL '3 hours');
```

### Environment Configuration for Testing

```yaml
# test-application.yml
orchestrator:
  file-proxy:
    enabled: true
    max-size-mb: 50
    temp-directory: "${java.io.tmpdir}/mcp-file-proxy-test"
    ttl-minutes: 60
    cleanup-interval-minutes: 1  # Faster for testing
    shutdown-timeout-seconds: 5
    servers:
      pdf-tools:
        max-size-mb: 100
```

### Mock Upstream Tool Schemas

```json
{
  "tools": [
    {
      "name": "convert_pdf",
      "description": "Convert PDF to text format",
      "inputSchema": {
        "type": "object",
        "properties": {
          "content": {
            "type": "base64",
            "description": "Base64-encoded PDF file content"
          },
          "output_format": {
            "type": "string",
            "description": "Output format: text, markdown, html"
          }
        },
        "required": ["content"]
      }
    },
    {
      "name": "export_report",
      "description": "Export report to PDF file",
      "inputSchema": {
        "type": "object",
        "properties": {
          "report_id": {"type": "string"}
        },
        "required": ["report_id"]
      },
      "outputSchema": {
        "type": "object",
        "properties": {
          "artifacts": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "path": {"type": "string"}
              }
            }
          }
        }
      }
    }
  ]
}
```
