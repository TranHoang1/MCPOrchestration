# Software Test Cases (STC)

## Jira Project Sync Service — MTO-16: Jira REST Client — Direct API Integration

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-16 |
| Title | Jira REST Client — Direct API Integration |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2025-07-14 |
| Status | Draft |
| Related STP | STP-v1-MTO-16.docx |
| Related FSD | FSD-v1-MTO-16.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-07-14 | QA Agent | Initiate document — auto-generated from FSD use cases and business rules |

---

## Test Case Summary

| Category | ID Range | Count | Priority |
|----------|----------|-------|----------|
| Functional — Happy Path | TC-001 to TC-017 | 17 | High |
| Functional — Alternative Flows | TC-100 to TC-109 | 10 | High |
| Functional — Exception/Error Flows | TC-200 to TC-212 | 13 | High |
| Business Rule Validation | TC-300 to TC-320 | 21 | High |
| Boundary & Negative Testing | TC-400 to TC-412 | 13 | Medium |
| Non-Functional (Performance, Security, Observability) | TC-600 to TC-606 | 7 | High |
| Integration Testing | TC-700 to TC-709 | 10 | High |
| Regression Testing | TC-800 to TC-803 | 4 | Medium |
| E2E-API Testing | TC-900 to TC-905 | 6 | High |
| **Total** | | **93** | |

---

## 1. Functional Test Cases — Happy Path

### TC-001: Search Issues — Valid JQL with Pagination

| Field | Value |
|-------|-------|
| **ID** | TC-001 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | IT |
| **Requirement** | UC-01, BR-01, BR-02, BR-03 |
| **Preconditions** | WireMock stub configured for POST /rest/api/3/search returning 120 issues |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="project = MTO", fields=["summary","status"], startAt=0, maxResults=50)` | Rate limiter token acquired |
| 2 | Verify HTTP request sent | POST /rest/api/3/search with correct JSON body and Basic Auth header |
| 3 | Verify response deserialized | JiraSearchResponse with issues.size=50, total=120, startAt=0 |

**Test Data:** JQL = "project = MTO AND updated >= -1d", fields = ["summary", "status", "assignee"], startAt = 0, maxResults = 50
**Postconditions:** Rate limiter token count decremented by 1

---

### TC-002: Get Issue — Valid Issue Key with Fields

| Field | Value |
|-------|-------|
| **ID** | TC-002 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | IT |
| **Requirement** | UC-02, BR-09, BR-10 |
| **Preconditions** | WireMock stub for GET /rest/api/3/issue/MTO-16 returning full issue JSON |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getIssue(key="MTO-16", fields=["summary","status"], expand=["changelog"])` | Rate limiter token acquired |
| 2 | Verify HTTP request | GET /rest/api/3/issue/MTO-16?fields=summary,status&expand=changelog with Basic Auth |
| 3 | Verify response | JiraIssue with key="MTO-16", fields populated, changelog present |

**Test Data:** key = "MTO-16", fields = ["summary", "status", "assignee"], expand = ["changelog"]
**Postconditions:** Issue object contains all requested fields

---

### TC-003: Get Attachments — Issue with Multiple Attachments

| Field | Value |
|-------|-------|
| **ID** | TC-003 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | IT |
| **Requirement** | UC-03, BR-12, BR-13 |
| **Preconditions** | WireMock stub for GET /rest/api/3/issue/MTO-16?fields=attachment returning 3 attachments |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getAttachments(issueKey="MTO-16")` | Rate limiter token acquired |
| 2 | Verify HTTP request | GET /rest/api/3/issue/MTO-16?fields=attachment |
| 3 | Verify response | List of 3 JiraAttachment objects with filename, size, mimeType, content URL |

**Test Data:** issueKey = "MTO-16", expected attachments: ["report.pdf" (2.5MB), "screenshot.png" (150KB), "data.csv" (45KB)]
**Postconditions:** All 3 attachments have non-null content URLs

---

### TC-004: Download Attachment — Valid URL Same Domain

| Field | Value |
|-------|-------|
| **ID** | TC-004 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | IT |
| **Requirement** | UC-04, BR-15, BR-16, BR-17 |
| **Preconditions** | WireMock stub for GET /rest/api/3/attachment/content/12345 returning binary content |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `downloadAttachment(url="https://jira.example.com/rest/api/3/attachment/content/12345")` | URL domain validated against config.baseUrl |
| 2 | Verify HTTP request | GET with Basic Auth header included |
| 3 | Verify response | ByteArray with correct content, contentLength matches |

**Test Data:** url = "https://jira.example.com/rest/api/3/attachment/content/12345", expected size = 2500 bytes
**Postconditions:** Binary content matches expected file content

---

### TC-005: Rate Limiter — Token Acquisition (Happy Path)

| Field | Value |
|-------|-------|
| **ID** | TC-005 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | UT |
| **Requirement** | UC-05, BR-19, BR-20 |
| **Preconditions** | TokenBucketRateLimiter initialized with rateLimit=10 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `rateLimiter.acquire()` when bucket has tokens | Returns immediately (< 1ms) |
| 2 | Verify token count | Decremented from 10 to 9 |
| 3 | Call acquire() 9 more times rapidly | All return immediately (burst capacity) |

**Test Data:** rateLimit = 10, initial tokens = 10
**Postconditions:** Bucket empty (0 tokens), next acquire() will suspend

---

### TC-006: Retry Handler — Success on First Attempt

| Field | Value |
|-------|-------|
| **ID** | TC-006 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | UT |
| **Requirement** | UC-06, BR-25 |
| **Preconditions** | RetryHandler configured with maxRetries=3, initialDelay=1000ms |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute request that returns 200 OK | No retry triggered |
| 2 | Verify total attempts | Exactly 1 attempt |
| 3 | Verify no delay applied | Total elapsed < 100ms |

**Test Data:** Mock HTTP response: 200 OK with valid JSON body
**Postconditions:** Result returned directly without retry wrapper overhead

---

### TC-007: Configuration — All Valid Environment Variables

| Field | Value |
|-------|-------|
| **ID** | TC-007 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | UT |
| **Requirement** | UC-07, BR-34, BR-35, BR-36, BR-37, BR-38, BR-39 |
| **Preconditions** | All environment variables set with valid values |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set JIRA_BASE_URL="https://jira.example.com", JIRA_EMAIL="sync@example.com", JIRA_API_TOKEN="token123" | Environment configured |
| 2 | Set JIRA_RATE_LIMIT=15, JIRA_TIMEOUT_MS=20000, JIRA_MAX_RETRIES=5 | Optional vars configured |
| 3 | Call `JiraClientConfig.fromEnvironment()` | JiraClientConfig created successfully |
| 4 | Verify all fields | baseUrl, email, apiToken, rateLimit=15, timeoutMs=20000, maxRetries=5 |

**Test Data:** JIRA_BASE_URL="https://jira.example.com", JIRA_EMAIL="sync@example.com", JIRA_API_TOKEN="valid-token-123"
**Postconditions:** Config object immutable, toString() masks credentials

---

### TC-008: Search Issues — Empty Result Set

| Field | Value |
|-------|-------|
| **ID** | TC-008 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | IT |
| **Requirement** | UC-01 AF-03 |
| **Preconditions** | WireMock stub returns empty issues array with total=0 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="project = NONEXIST")` | Request sent successfully |
| 2 | Verify response | JiraSearchResponse with issues=emptyList(), total=0 |

**Test Data:** JQL = "project = NONEXIST AND updated >= -1d"
**Postconditions:** No exception thrown, empty list returned

---

### TC-009: Get Attachments — Issue with No Attachments

| Field | Value |
|-------|-------|
| **ID** | TC-009 |
| **Priority** | Medium |
| **Type** | Functional |
| **Level** | IT |
| **Requirement** | UC-03 AF-01 |
| **Preconditions** | WireMock stub returns issue with empty attachment array |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getAttachments(issueKey="MTO-99")` | Request sent |
| 2 | Verify response | Empty list returned |

**Test Data:** issueKey = "MTO-99" (issue exists but has no attachments)
**Postconditions:** No exception, empty list

---

### TC-010: Configuration — Defaults Applied for Optional Variables

| Field | Value |
|-------|-------|
| **ID** | TC-010 |
| **Priority** | High |
| **Type** | Functional |
| **Level** | UT |
| **Requirement** | UC-07, BR-37, BR-38, BR-39, BR-40, BR-41 |
| **Preconditions** | Only required env vars set, optional vars absent |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set only JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN | Required vars present |
| 2 | Call `JiraClientConfig.fromEnvironment()` | Config created with defaults |
| 3 | Verify defaults | rateLimit=10, timeoutMs=30000, maxRetries=3, connectTimeoutMs=10000, socketTimeoutMs=30000 |

**Test Data:** JIRA_BASE_URL="https://jira.example.com", JIRA_EMAIL="sync@example.com", JIRA_API_TOKEN="token"
**Postconditions:** All optional fields have documented default values

---

## 2. Functional Test Cases — Alternative Flows

### TC-100: Search Issues — Rate Limiter Suspends (No Tokens)

| Field | Value |
|-------|-------|
| **ID** | TC-100 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Level** | IT |
| **Requirement** | UC-01 AF-01, BR-19, BR-21 |
| **Preconditions** | Rate limiter bucket empty (all tokens consumed) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Exhaust all tokens (10 rapid calls) | Bucket empty |
| 2 | Call `searchIssues(...)` | Coroutine suspends |
| 3 | Wait for token refill (~100ms at 10 req/s) | Token becomes available |
| 4 | Verify request completes | Response returned after suspension |

**Test Data:** rateLimit=10, all 10 tokens consumed before test call
**Postconditions:** Request completed successfully after brief suspension

---

### TC-101: Search Issues — Partial Page (Last Page)

| Field | Value |
|-------|-------|
| **ID** | TC-101 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Level** | IT |
| **Requirement** | UC-01 AF-02 |
| **Preconditions** | WireMock returns 20 issues when maxResults=50 (total=70, startAt=50) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="project=MTO", startAt=50, maxResults=50)` | Request sent |
| 2 | Verify response | issues.size=20, total=70, startAt=50 |

**Test Data:** startAt=50, maxResults=50, total=70 (last page has 20 items)
**Postconditions:** Normal response, no error

---

### TC-102: Get Issue — With Expand Parameters

| Field | Value |
|-------|-------|
| **ID** | TC-102 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Level** | IT |
| **Requirement** | UC-02, BR-10 |
| **Preconditions** | WireMock stub returns issue with changelog and renderedFields |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getIssue(key="MTO-16", expand=["changelog","renderedFields"])` | Request includes expand param |
| 2 | Verify URL | /rest/api/3/issue/MTO-16?expand=changelog,renderedFields |
| 3 | Verify response includes expanded data | changelog object present |

**Test Data:** key="MTO-16", expand=["changelog", "renderedFields"]
**Postconditions:** Expanded fields deserialized correctly

---

### TC-103: Get Attachments — Filter Invalid Content URLs

| Field | Value |
|-------|-------|
| **ID** | TC-103 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Level** | IT |
| **Requirement** | UC-03, BR-14 |
| **Preconditions** | WireMock returns 3 attachments, 1 with null content URL |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getAttachments(issueKey="MTO-16")` | Response received |
| 2 | Verify filtering | Only 2 attachments returned (null URL filtered) |
| 3 | Verify warning logged | Warning log entry for filtered attachment |

**Test Data:** 3 attachments in response, attachment[2].content = null
**Postconditions:** 2 valid attachments returned, 1 filtered with warning

---

### TC-104: Retry — 429 with Retry-After Header

| Field | Value |
|-------|-------|
| **ID** | TC-104 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Level** | IT |
| **Requirement** | UC-01 EF-04, UC-05, BR-06, BR-22, BR-32 |
| **Preconditions** | WireMock returns 429 with Retry-After: 2 on first call, 200 on second |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` | First request returns 429 |
| 2 | Verify rate limiter paused | All requests paused for ~2 seconds |
| 3 | Verify retry after pause | Second request sent after 2s, returns 200 |
| 4 | Verify final result | Successful JiraSearchResponse returned |

**Test Data:** First response: 429 + Retry-After: 2, Second response: 200 OK
**Postconditions:** Rate limiter resumed, result returned successfully

---

### TC-105: Retry — Exponential Backoff on 503

| Field | Value |
|-------|-------|
| **ID** | TC-105 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Level** | IT |
| **Requirement** | UC-06, BR-25, BR-26, BR-29, BR-30 |
| **Preconditions** | WireMock returns 503 twice, then 200 on third attempt |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` | First attempt: 503 |
| 2 | Verify first retry delay | ~1000ms ± 20% jitter (800-1200ms) |
| 3 | Second attempt | 503 again |
| 4 | Verify second retry delay | ~2000ms ± 20% jitter (1600-2400ms) |
| 5 | Third attempt | 200 OK — success |

**Test Data:** Responses: [503, 503, 200], maxRetries=3
**Postconditions:** Result returned after 3rd attempt, total elapsed ~3-4s

---

### TC-106: Configuration — Trailing Slash Removed from Base URL

| Field | Value |
|-------|-------|
| **ID** | TC-106 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Level** | UT |
| **Requirement** | UC-07, BR-34 |
| **Preconditions** | JIRA_BASE_URL set with trailing slash |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set JIRA_BASE_URL="https://jira.example.com/" | Trailing slash present |
| 2 | Call `JiraClientConfig.fromEnvironment()` | Config created |
| 3 | Verify baseUrl | "https://jira.example.com" (no trailing slash) |

**Test Data:** JIRA_BASE_URL = "https://jira.example.com/"
**Postconditions:** URL normalized without trailing slash

---

## 3. Functional Test Cases — Exception/Error Flows

### TC-200: Search Issues — Invalid JQL (400 Response)

| Field | Value |
|-------|-------|
| **ID** | TC-200 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-01 EF-01, BR-08 |
| **Preconditions** | WireMock returns 400 with Jira error messages JSON |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="INVALID SYNTAX ===")` | Request sent to Jira |
| 2 | Jira returns 400 | JiraValidationException thrown |
| 3 | Verify exception | Contains Jira error messages, statusCode=400, no retry attempted |

**Test Data:** JQL = "INVALID SYNTAX ===", Response: 400 with `{"errorMessages":["Error in JQL query"]}`
**Postconditions:** No retry, exception propagated to caller

---

### TC-201: Search Issues — Authentication Failure (401)

| Field | Value |
|-------|-------|
| **ID** | TC-201 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-01 EF-02, BR-08 |
| **Preconditions** | WireMock returns 401 Unauthorized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` with invalid credentials | Request sent |
| 2 | Jira returns 401 | JiraAuthException thrown |
| 3 | Verify no retry | Exactly 1 attempt, no backoff |
| 4 | Verify exception message | "Invalid credentials", statusCode=401 |

**Test Data:** Config with invalid API token
**Postconditions:** No retry, JiraAuthException with correlationId

---

### TC-202: Search Issues — Permission Denied (403)

| Field | Value |
|-------|-------|
| **ID** | TC-202 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-01 EF-03, BR-08 |
| **Preconditions** | WireMock returns 403 Forbidden |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="project = RESTRICTED")` | Request sent |
| 2 | Jira returns 403 | JiraAuthException thrown |
| 3 | Verify message | "Insufficient permissions for JQL query" |
| 4 | Verify no retry | Exactly 1 attempt |

**Test Data:** JQL targeting restricted project
**Postconditions:** JiraAuthException, no retry

---

### TC-203: Get Issue — Not Found (404)

| Field | Value |
|-------|-------|
| **ID** | TC-203 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-02 EF-02, BR-08 |
| **Preconditions** | WireMock returns 404 for non-existent issue |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getIssue(key="XYZ-999")` | Request sent |
| 2 | Jira returns 404 | JiraNotFoundException thrown |
| 3 | Verify exception | Contains issue key "XYZ-999", no retry |

**Test Data:** key = "XYZ-999" (valid format but non-existent)
**Postconditions:** JiraNotFoundException with correlationId

---

### TC-204: Server Error — 500 with Retry Exhausted

| Field | Value |
|-------|-------|
| **ID** | TC-204 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-01 EF-05, EF-07, BR-07, BR-28 |
| **Preconditions** | WireMock returns 500 for all 4 attempts (initial + 3 retries) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` | First attempt: 500 |
| 2 | Retry 1 after ~1s | 500 again |
| 3 | Retry 2 after ~2s | 500 again |
| 4 | Retry 3 after ~4s | 500 again |
| 5 | Max retries exhausted | RetryExhaustedException thrown |
| 6 | Verify exception | attempts=4, includes last JiraServerException as cause |

**Test Data:** All responses: 500 Internal Server Error, maxRetries=3
**Postconditions:** RetryExhaustedException with attempt count and elapsed time

---

### TC-205: Connection Timeout with Retry

| Field | Value |
|-------|-------|
| **ID** | TC-205 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-01 EF-06, BR-30 |
| **Preconditions** | WireMock configured with delay > timeout, then normal response |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` with timeout=1000ms | Request times out |
| 2 | JiraTimeoutException caught by retry handler | Retry triggered |
| 3 | Second attempt succeeds | Normal response returned |

**Test Data:** First request: 5000ms delay (timeout=1000ms), Second request: immediate response
**Postconditions:** Successful result after 1 retry

---

### TC-206: Download Attachment — SSRF Blocked (Different Domain)

| Field | Value |
|-------|-------|
| **ID** | TC-206 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-04 EF-02, BR-16 |
| **Preconditions** | Config baseUrl = "https://jira.example.com" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `downloadAttachment(url="https://evil.com/malware.exe")` | URL domain validation |
| 2 | Domain mismatch detected | JiraValidationException thrown immediately |
| 3 | Verify no HTTP request sent | No network call made |

**Test Data:** url = "https://evil.com/rest/api/3/attachment/content/999", config.baseUrl = "https://jira.example.com"
**Postconditions:** Request blocked before network call, SSRF prevented

---

### TC-207: Download Attachment — Invalid URL Scheme

| Field | Value |
|-------|-------|
| **ID** | TC-207 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-04, BR-15 |
| **Preconditions** | Config baseUrl = "https://jira.example.com" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `downloadAttachment(url="ftp://jira.example.com/file")` | URL scheme validation |
| 2 | Invalid scheme detected | JiraValidationException thrown |

**Test Data:** url = "ftp://jira.example.com/attachment/123"
**Postconditions:** Request blocked, only http/https allowed

---

### TC-208: Configuration — Missing Required Variables

| Field | Value |
|-------|-------|
| **ID** | TC-208 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-07 EF-01, BR-34, BR-35, BR-36, BR-43 |
| **Preconditions** | JIRA_BASE_URL and JIRA_API_TOKEN not set |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Unset JIRA_BASE_URL and JIRA_API_TOKEN | Required vars missing |
| 2 | Call `JiraClientConfig.fromEnvironment()` | ConfigException thrown |
| 3 | Verify exception message | Lists ALL missing vars: "JIRA_BASE_URL, JIRA_API_TOKEN" |

**Test Data:** Only JIRA_EMAIL set, JIRA_BASE_URL and JIRA_API_TOKEN absent
**Postconditions:** Fail-fast with comprehensive error listing all missing vars

---

### TC-209: Configuration — Invalid URL Format

| Field | Value |
|-------|-------|
| **ID** | TC-209 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-07 EF-02, BR-34 |
| **Preconditions** | JIRA_BASE_URL set to invalid URL |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set JIRA_BASE_URL="not-a-valid-url" | Invalid URL format |
| 2 | Call `JiraClientConfig.fromEnvironment()` | ConfigException thrown |
| 3 | Verify message | Indicates URL format issue |

**Test Data:** JIRA_BASE_URL = "not-a-valid-url"
**Postconditions:** Clear error message about URL format

---

### TC-210: Search Issues — Blank JQL Validation

| Field | Value |
|-------|-------|
| **ID** | TC-210 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-01, BR-01 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="")` | Input validation |
| 2 | Blank JQL detected | JiraValidationException thrown immediately |
| 3 | Verify no HTTP request | No network call made |

**Test Data:** jql = "" (empty string)
**Postconditions:** Fail-fast validation, no API call

---

### TC-211: Search Issues — maxResults Out of Range

| Field | Value |
|-------|-------|
| **ID** | TC-211 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-01, BR-02 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="project=MTO", maxResults=150)` | Input validation |
| 2 | maxResults > 100 detected | JiraValidationException thrown |
| 3 | Verify no HTTP request | No network call made |

**Test Data:** maxResults = 150 (exceeds Jira limit of 100)
**Postconditions:** Fail-fast, no API call

---

### TC-212: Get Issue — Invalid Key Format

| Field | Value |
|-------|-------|
| **ID** | TC-212 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | UT |
| **Requirement** | UC-02, BR-09 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getIssue(key="invalid-key-format")` | Input validation |
| 2 | Key doesn't match `[A-Z]+-\d+` | JiraValidationException thrown |
| 3 | Verify no HTTP request | No network call made |

**Test Data:** key = "invalid-key-format" (doesn't match regex)
**Postconditions:** Fail-fast validation

---

### TC-213: Rate Limiter — 429 with Missing Retry-After Header

| Field | Value |
|-------|-------|
| **ID** | TC-213 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-05, BR-24 |
| **Preconditions** | WireMock returns 429 WITHOUT Retry-After header |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` | 429 received without Retry-After |
| 2 | Verify default pause | Rate limiter pauses for 60 seconds (default) |
| 3 | Verify retry after default pause | Request retried after 60s |

**Test Data:** Response: 429 with no Retry-After header
**Postconditions:** Default 60s pause applied per BR-24

---

### TC-214: Retry — No Retry on 404

| Field | Value |
|-------|-------|
| **ID** | TC-214 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Level** | IT |
| **Requirement** | UC-06, BR-31 |
| **Preconditions** | WireMock returns 404 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getIssue(key="MTO-999")` | 404 received |
| 2 | Verify no retry | JiraNotFoundException thrown immediately |
| 3 | Verify attempt count | Exactly 1 attempt, no backoff delay |

**Test Data:** key = "MTO-999", Response: 404
**Postconditions:** Immediate exception, no retry (4xx except 429 = permanent failure)

---

## 4. Business Rule Validation

### TC-300: BR-01 — JQL Not Blank

| Field | Value |
|-------|-------|
| **ID** | TC-300 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-01 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: forAll(blankStrings) { jql -> searchIssues(jql) throws JiraValidationException } | All blank/whitespace strings rejected |

**Test Data:** Generated: "", " ", "\t", "\n", "   \t\n  " (all blank variants)

---

### TC-301: BR-02 — maxResults Range 1-100

| Field | Value |
|-------|-------|
| **ID** | TC-301 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-02 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: forAll(Arb.int(1..100)) { n -> searchIssues(jql="x", maxResults=n) does NOT throw validation error } | All values 1-100 accepted |
| 2 | Property: forAll(Arb.int().filter { it < 1 || it > 100 }) { n -> searchIssues(jql="x", maxResults=n) throws } | All values outside 1-100 rejected |

**Test Data:** Generated: random integers in [1,100] (valid) and outside (invalid)

---

### TC-302: BR-03 — startAt >= 0

| Field | Value |
|-------|-------|
| **ID** | TC-302 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-03 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: forAll(Arb.int(0..Int.MAX_VALUE)) { n -> accepted } | Non-negative values pass |
| 2 | Property: forAll(Arb.negativeInt()) { n -> throws JiraValidationException } | Negative values rejected |

**Test Data:** Generated: random non-negative and negative integers

---

### TC-303: BR-04 — Fields List Items Not Empty

| Field | Value |
|-------|-------|
| **ID** | TC-303 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | UT |
| **Requirement** | BR-04 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(jql="x", fields=["summary", "", "status"])` | Validation detects empty string |
| 2 | JiraValidationException thrown | Error message indicates empty field name |

**Test Data:** fields = ["summary", "", "status"] (contains empty string)

---

### TC-304: BR-05 — Rate Limiter Token Required Before Request

| Field | Value |
|-------|-------|
| **ID** | TC-304 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | IT |
| **Requirement** | BR-05 |
| **Preconditions** | Rate limiter with 0 tokens, WireMock ready |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Exhaust all tokens | Bucket empty |
| 2 | Call any API method | Coroutine suspends (no HTTP request yet) |
| 3 | Verify WireMock received 0 requests | No request sent while suspended |
| 4 | Wait for token refill | Request sent after token acquired |

**Test Data:** rateLimit=10, all tokens consumed
**Postconditions:** Request only sent after token acquisition

---

### TC-305: BR-06 — 429 Triggers Rate-Limit-Aware Retry

| Field | Value |
|-------|-------|
| **ID** | TC-305 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | IT |
| **Requirement** | BR-06, BR-22 |
| **Preconditions** | WireMock returns 429 with Retry-After: 3 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` | 429 received |
| 2 | Verify rate limiter paused globally | isPaused() = true |
| 3 | Verify pause duration | ~3 seconds (from Retry-After header) |
| 4 | Verify retry after pause | Request retried successfully |

**Test Data:** Retry-After: 3 seconds
**Postconditions:** Rate limiter resumed after pause

---

### TC-306: BR-07 — 5xx Triggers Exponential Backoff

| Field | Value |
|-------|-------|
| **ID** | TC-306 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | IT |
| **Requirement** | BR-07, BR-25, BR-26 |
| **Preconditions** | WireMock returns 502 then 200 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `searchIssues(...)` | 502 received |
| 2 | Verify retry delay | ~1000ms ± 20% jitter |
| 3 | Second attempt | 200 OK |

**Test Data:** Responses: [502, 200]
**Postconditions:** Success after 1 retry with exponential backoff

---

### TC-307: BR-08 — 4xx (Except 429) Not Retried

| Field | Value |
|-------|-------|
| **ID** | TC-307 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | IT |
| **Requirement** | BR-08, BR-31 |
| **Preconditions** | WireMock returns various 4xx codes |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger 400 response | JiraValidationException, no retry |
| 2 | Trigger 401 response | JiraAuthException, no retry |
| 3 | Trigger 403 response | JiraAuthException, no retry |
| 4 | Trigger 404 response | JiraNotFoundException, no retry |
| 5 | Verify for each: exactly 1 WireMock request | No retry on any 4xx (except 429) |

**Test Data:** Status codes: 400, 401, 403, 404

---

### TC-308: BR-09 — Issue Key Format Validation

| Field | Value |
|-------|-------|
| **ID** | TC-308 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-09 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: forAll(validIssueKeys) { key -> getIssue(key) does not throw validation error } | Valid keys accepted |
| 2 | Property: forAll(invalidIssueKeys) { key -> getIssue(key) throws JiraValidationException } | Invalid keys rejected |

**Test Data:** Valid: "MTO-1", "ABC-999", "PROJ-12345". Invalid: "mto-1", "123-ABC", "MTO", "MTO-", "-123"

---

### TC-309: BR-19 — Default Rate 10 req/s

| Field | Value |
|-------|-------|
| **ID** | TC-309 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | UT |
| **Requirement** | BR-19 |
| **Preconditions** | TokenBucketRateLimiter with default config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create rate limiter with rateLimit=10 | Bucket initialized with 10 tokens |
| 2 | Acquire 10 tokens rapidly | All succeed immediately |
| 3 | 11th acquire | Suspends until refill |

**Test Data:** rateLimit = 10 (default)

---

### TC-310: BR-20 — Burst Capacity Equals Rate Limit

| Field | Value |
|-------|-------|
| **ID** | TC-310 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | UT |
| **Requirement** | BR-20 |
| **Preconditions** | Rate limiter with rateLimit=10, fully refilled |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Acquire 10 tokens in < 10ms | All 10 succeed (burst) |
| 2 | Verify 11th token | Suspends (bucket empty) |

**Test Data:** rateLimit=10, burst=10

---

### TC-311: BR-23 — Thread-Safe Concurrent Access

| Field | Value |
|-------|-------|
| **ID** | TC-311 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | IT |
| **Requirement** | BR-23 |
| **Preconditions** | Rate limiter with rateLimit=10 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 20 concurrent coroutines each calling acquire() | All coroutines compete for tokens |
| 2 | Verify total tokens consumed | Exactly 20 tokens consumed (10 immediate + 10 after refill) |
| 3 | Verify no race condition | No duplicate token grants, no exceptions |

**Test Data:** 20 concurrent coroutines, rateLimit=10

---

### TC-312: BR-25..BR-27 — Backoff Delay Calculation

| Field | Value |
|-------|-------|
| **ID** | TC-312 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-25, BR-26, BR-27 |
| **Preconditions** | RetryHandler configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: delay(attempt=1) in range [800, 1200] (1000 ± 20%) | First delay correct |
| 2 | Property: delay(attempt=2) in range [1600, 2400] (2000 ± 20%) | Second delay doubled |
| 3 | Property: delay(attempt=N) <= 30000ms for all N | Max cap enforced |

**Test Data:** Generated: attempt numbers 1..10

---

### TC-313: BR-29 — Jitter ±20%

| Field | Value |
|-------|-------|
| **ID** | TC-313 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-29 |
| **Preconditions** | RetryHandler configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: forAll(attempts) { calculateDelay(it) is within baseDelay * [0.8, 1.2] } | Jitter within ±20% |
| 2 | Statistical: over 1000 samples, mean ≈ baseDelay | Jitter is symmetric |

**Test Data:** 1000 generated delay calculations

---

### TC-314: BR-33 — Retry Logged with Correlation ID

| Field | Value |
|-------|-------|
| **ID** | TC-314 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Level** | IT |
| **Requirement** | BR-33 |
| **Preconditions** | Log capture configured, WireMock returns 503 then 200 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call API method that triggers retry | 503 → retry → 200 |
| 2 | Capture log output | WARN log entry present |
| 3 | Verify log fields | Contains: correlationId, attempt number (1/3), delay (ms), statusCode (503) |

**Test Data:** Response sequence: [503, 200]

---

### TC-315: BR-34 — Base URL Validation

| Field | Value |
|-------|-------|
| **ID** | TC-315 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-34 |
| **Preconditions** | Various URL formats |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Valid URLs: "https://jira.example.com", "http://localhost:8080" | Accepted |
| 2 | Invalid: "", "ftp://jira.com", "not-a-url", "jira.com" (no scheme) | Rejected with ConfigException |

**Test Data:** Valid: https/http URLs. Invalid: blank, ftp, no scheme, malformed

---

### TC-316: BR-37 — Rate Limit Range 1-100

| Field | Value |
|-------|-------|
| **ID** | TC-316 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-37 |
| **Preconditions** | Config validation |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: forAll(Arb.int(1..100)) { valid } | Values 1-100 accepted |
| 2 | Property: forAll(Arb.int().filter { it < 1 || it > 100 }) { rejected } | Out of range rejected |

**Test Data:** Generated integers

---

### TC-317: BR-42 — Config Immutable After Init

| Field | Value |
|-------|-------|
| **ID** | TC-317 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Level** | UT |
| **Requirement** | BR-42 |
| **Preconditions** | JiraClientConfig created |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create JiraClientConfig | Data class instance created |
| 2 | Verify it's a data class (val properties only) | No mutable setters available |
| 3 | Verify copy() creates new instance | Original unchanged |

**Test Data:** Standard config values

---

### TC-318: BR-43 — Fail-Fast All Missing Vars Reported

| Field | Value |
|-------|-------|
| **ID** | TC-318 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | UT |
| **Requirement** | BR-43 |
| **Preconditions** | Multiple required vars missing |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Unset ALL required vars (BASE_URL, EMAIL, API_TOKEN) | 3 vars missing |
| 2 | Call `JiraClientConfig.fromEnvironment()` | ConfigException thrown |
| 3 | Verify exception lists ALL 3 missing vars | Not just the first one found |

**Test Data:** All 3 required vars absent
**Postconditions:** Single exception with complete list of missing vars

---

### TC-319: BR-10 — Expand Values from Allowed Set

| Field | Value |
|-------|-------|
| **ID** | TC-319 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Level** | UT |
| **Requirement** | BR-10 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `getIssue(key="MTO-1", expand=["changelog","renderedFields"])` | Valid expand values accepted |
| 2 | Call `getIssue(key="MTO-1", expand=["invalidExpand"])` | JiraValidationException thrown |

**Test Data:** Valid: changelog, renderedFields, transitions, operations, editmeta. Invalid: "foo", "bar"

---

### TC-320: BR-16 — SSRF Domain Validation

| Field | Value |
|-------|-------|
| **ID** | TC-320 |
| **Priority** | High |
| **Type** | Business Rule |
| **Level** | PBT |
| **Requirement** | BR-16 |
| **Preconditions** | Config baseUrl = "https://jira.example.com" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Property: URLs with domain "jira.example.com" → accepted | Same domain passes |
| 2 | Property: URLs with any other domain → rejected | Different domain blocked |
| 3 | Edge cases: subdomain "sub.jira.example.com", port "jira.example.com:8080" | Verify strict matching |

**Test Data:** Generated URLs with various domains

---

## 5. Boundary & Negative Testing

### TC-400: JQL Maximum Length (10000 chars)

| Field | Value |
|-------|-------|
| **ID** | TC-400 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-01, FSD §3.1.4 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call searchIssues with JQL of exactly 10000 chars | Accepted (at boundary) |
| 2 | Call searchIssues with JQL of 10001 chars | JiraValidationException (exceeds max) |

**Test Data:** JQL = "project = " + "A" * 9990 (10000 chars), JQL = "project = " + "A" * 9991 (10001 chars)

---

### TC-401: maxResults Boundary Values

| Field | Value |
|-------|-------|
| **ID** | TC-401 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-02 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | maxResults = 0 | JiraValidationException |
| 2 | maxResults = 1 | Accepted (min boundary) |
| 3 | maxResults = 100 | Accepted (max boundary) |
| 4 | maxResults = 101 | JiraValidationException |
| 5 | maxResults = -1 | JiraValidationException |
| 6 | maxResults = Int.MAX_VALUE | JiraValidationException |

**Test Data:** Values: 0, 1, 2, 50, 99, 100, 101, -1, Int.MAX_VALUE

---

### TC-402: startAt Boundary Values

| Field | Value |
|-------|-------|
| **ID** | TC-402 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-03 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | startAt = 0 | Accepted (min boundary) |
| 2 | startAt = -1 | JiraValidationException |
| 3 | startAt = Int.MAX_VALUE | Accepted (large but valid) |

**Test Data:** Values: -1, 0, 1, 1000, Int.MAX_VALUE

---

### TC-403: Rate Limit Configuration Boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-403 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-37 |
| **Preconditions** | Config validation |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | JIRA_RATE_LIMIT = 0 | ConfigException |
| 2 | JIRA_RATE_LIMIT = 1 | Accepted (min) |
| 3 | JIRA_RATE_LIMIT = 100 | Accepted (max) |
| 4 | JIRA_RATE_LIMIT = 101 | ConfigException |

**Test Data:** Values: 0, 1, 50, 100, 101

---

### TC-404: Timeout Configuration Boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-404 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-38 |
| **Preconditions** | Config validation |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | JIRA_TIMEOUT_MS = 999 | ConfigException (below min 1000) |
| 2 | JIRA_TIMEOUT_MS = 1000 | Accepted (min) |
| 3 | JIRA_TIMEOUT_MS = 300000 | Accepted (max) |
| 4 | JIRA_TIMEOUT_MS = 300001 | ConfigException (above max) |

**Test Data:** Values: 999, 1000, 30000, 300000, 300001

---

### TC-405: Max Retries Configuration Boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-405 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-39 |
| **Preconditions** | Config validation |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | JIRA_MAX_RETRIES = -1 | ConfigException |
| 2 | JIRA_MAX_RETRIES = 0 | Accepted (no retries) |
| 3 | JIRA_MAX_RETRIES = 10 | Accepted (max) |
| 4 | JIRA_MAX_RETRIES = 11 | ConfigException |

**Test Data:** Values: -1, 0, 3, 10, 11

---

### TC-406: Backoff Delay Cap at 30000ms

| Field | Value |
|-------|-------|
| **ID** | TC-406 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-27 |
| **Preconditions** | RetryHandler with maxDelay=30000 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Calculate delay for attempt 1 | ~1000ms |
| 2 | Calculate delay for attempt 5 | ~16000ms (1000 * 2^4) |
| 3 | Calculate delay for attempt 6 | 30000ms (capped, not 32000) |
| 4 | Calculate delay for attempt 10 | 30000ms (still capped) |

**Test Data:** Attempts 1-10, verify cap at 30000ms

---

### TC-407: Issue Key — Edge Cases

| Field | Value |
|-------|-------|
| **ID** | TC-407 |
| **Priority** | Medium |
| **Type** | Negative |
| **Level** | UT |
| **Requirement** | BR-09 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | key = null | NullPointerException or JiraValidationException |
| 2 | key = "" | JiraValidationException |
| 3 | key = "mto-16" (lowercase) | JiraValidationException |
| 4 | key = "MTO-0" | Accepted (valid format) |
| 5 | key = "A-1" (single char project) | Accepted |
| 6 | key = "MTO-" (no number) | JiraValidationException |
| 7 | key = "-16" (no project) | JiraValidationException |

**Test Data:** Various edge case key formats

---

### TC-408: Download URL — Edge Cases

| Field | Value |
|-------|-------|
| **ID** | TC-408 |
| **Priority** | Medium |
| **Type** | Negative |
| **Level** | UT |
| **Requirement** | BR-15, BR-16 |
| **Preconditions** | Config baseUrl = "https://jira.example.com" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | url = "" | JiraValidationException |
| 2 | url = "javascript:alert(1)" | JiraValidationException (invalid scheme) |
| 3 | url = "https://jira.example.com.evil.com/file" | JiraValidationException (subdomain attack) |
| 4 | url = "https://JIRA.EXAMPLE.COM/file" (case) | Depends on implementation (case-insensitive domain match) |

**Test Data:** Various malicious/edge-case URLs

---

### TC-409: Retry-After Header — Edge Cases

| Field | Value |
|-------|-------|
| **ID** | TC-409 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | IT |
| **Requirement** | BR-24 |
| **Preconditions** | WireMock returns 429 with various Retry-After values |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Retry-After: 0 | Immediate retry (or minimum 1s) |
| 2 | Retry-After: -5 | Default to 60s (invalid value) |
| 3 | Retry-After: "abc" | Default to 60s (unparseable) |
| 4 | Retry-After: 3600 | Use 3600s (1 hour — valid but long) |

**Test Data:** Retry-After values: 0, -5, "abc", 3600

---

### TC-410: Concurrent Rate Limiter — Stress Test

| Field | Value |
|-------|-------|
| **ID** | TC-410 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | IT |
| **Requirement** | BR-23, BR-20 |
| **Preconditions** | Rate limiter with rateLimit=10 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 100 concurrent coroutines calling acquire() | All compete for tokens |
| 2 | Measure total time for all 100 to complete | ~10 seconds (100 tokens / 10 per second) |
| 3 | Verify no exceptions | No ConcurrentModificationException or deadlock |

**Test Data:** 100 concurrent coroutines, rateLimit=10

---

### TC-411: Large Attachment Download (>10MB)

| Field | Value |
|-------|-------|
| **ID** | TC-411 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | IT |
| **Requirement** | BR-18 |
| **Preconditions** | WireMock returns 15MB binary response |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `downloadAttachment(url)` for 15MB file | Download starts |
| 2 | Verify streaming (no OOM) | Memory usage stays reasonable |
| 3 | Verify complete content | ByteArray size = 15MB, content correct |

**Test Data:** 15MB binary file served by WireMock

---

### TC-412: Fields List — Empty List vs Null

| Field | Value |
|-------|-------|
| **ID** | TC-412 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Level** | UT |
| **Requirement** | BR-04, FSD §3.1.4 |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call searchIssues with fields = emptyList() | Accepted (returns all fields) |
| 2 | Call searchIssues with fields = null | Accepted (returns all fields) |
| 3 | Call searchIssues with fields = [""] | JiraValidationException (empty string item) |

**Test Data:** fields: emptyList(), null, [""]

---

## 6. Non-Functional Testing (Performance, Security)

### TC-600: Rate Limiter Sustained Throughput — 10 req/s

| Field | Value |
|-------|-------|
| **ID** | TC-600 |
| **Priority** | High |
| **Type** | Non-Functional — Performance |
| **Level** | IT |
| **Requirement** | FSD §8 Performance: 10 req/s sustained |
| **Preconditions** | WireMock running, rate limiter configured at 10 req/s |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure TokenBucketRateLimiter(rateLimit=10) | Limiter initialized |
| 2 | Send 100 requests sequentially, measure total time | ~10 seconds (100/10 per second) |
| 3 | Verify all requests completed successfully | 100 successful responses |
| 4 | Verify no request was rejected | All suspended, none thrown |

**Test Data:** 100 sequential HTTP GET requests to WireMock
**Acceptance Criteria:** Total time between 9.5s and 11s for 100 requests at 10 req/s

---

### TC-601: Connection Pooling — No Per-Request TCP Handshake

| Field | Value |
|-------|-------|
| **ID** | TC-601 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Level** | IT |
| **Requirement** | FSD §8 Performance: Connection reuse |
| **Preconditions** | WireMock running, Ktor CIO client configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 10 sequential requests to same host | All succeed |
| 2 | Verify WireMock received all on same connection (Connection: keep-alive) | Single TCP connection reused |
| 3 | Measure latency of request 1 vs request 10 | Request 10 latency ≤ request 1 (no handshake overhead) |

**Test Data:** 10 GET /rest/api/3/search requests
**Acceptance Criteria:** Connection header shows keep-alive, no new TCP connections per request

---

### TC-602: Request Timeout Enforcement

| Field | Value |
|-------|-------|
| **ID** | TC-602 |
| **Priority** | High |
| **Type** | Non-Functional — Performance |
| **Level** | IT |
| **Requirement** | FSD §8 Performance: Request latency within timeout |
| **Preconditions** | WireMock configured with delay, timeout=5000ms |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure client with timeout=5000ms | Client initialized |
| 2 | WireMock responds with 6000ms delay | Delay exceeds timeout |
| 3 | Call searchIssues() | JiraTimeoutException thrown |
| 4 | Verify exception contains timeoutMs=5000 | Correct timeout value in exception |
| 5 | Verify elapsed time ~5000ms (not 6000ms) | Client didn't wait for full response |

**Test Data:** WireMock fixed delay: 6000ms, client timeout: 5000ms
**Acceptance Criteria:** Exception thrown within 5000-5500ms window

---

### TC-603: Credential Safety — Token Not in Logs

| Field | Value |
|-------|-------|
| **ID** | TC-603 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Level** | UT |
| **Requirement** | FSD §8 Security: API token never in logs/exceptions |
| **Preconditions** | Client configured with token="secret-api-token-12345" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger a JiraAuthException (401 response) | Exception thrown |
| 2 | Inspect exception.message | Does NOT contain "secret-api-token-12345" |
| 3 | Inspect exception.toString() | Does NOT contain token |
| 4 | Capture log output during request | No log line contains token value |

**Test Data:** Token: "secret-api-token-12345"
**Acceptance Criteria:** Token string never appears in any exception message or log output

---

### TC-604: SSRF Protection — Domain Restriction

| Field | Value |
|-------|-------|
| **ID** | TC-604 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Level** | UT |
| **Requirement** | FSD §8 Security: SSRF protection, BR-16 |
| **Preconditions** | Config baseUrl = "https://jira.example.com" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | downloadAttachment("https://jira.example.com/file.pdf") | Accepted (same domain) |
| 2 | downloadAttachment("https://evil.com/steal-data") | JiraValidationException (different domain) |
| 3 | downloadAttachment("https://jira.example.com.evil.com/file") | JiraValidationException (subdomain attack) |
| 4 | downloadAttachment("http://169.254.169.254/metadata") | JiraValidationException (internal IP) |

**Test Data:** Various URLs with different domains
**Acceptance Criteria:** Only URLs matching configured baseUrl domain are accepted

---

### TC-605: Correlation ID Uniqueness

| Field | Value |
|-------|-------|
| **ID** | TC-605 |
| **Priority** | Medium |
| **Type** | Non-Functional — Observability |
| **Level** | UT |
| **Requirement** | FSD §8 Observability: Unique correlation ID per request |
| **Preconditions** | JiraRestClient initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Make 100 requests | 100 correlation IDs generated |
| 2 | Collect all correlation IDs | All 100 are unique (no duplicates) |
| 3 | Verify format (UUID v4) | Matches UUID pattern [a-f0-9-]{36} |

**Test Data:** 100 sequential requests
**Acceptance Criteria:** 100% unique correlation IDs, valid UUID format

---

### TC-606: Fail-Fast Startup Validation

| Field | Value |
|-------|-------|
| **ID** | TC-606 |
| **Priority** | High |
| **Type** | Non-Functional — Availability |
| **Level** | UT |
| **Requirement** | FSD §8 Availability: Fail-fast within 1s |
| **Preconditions** | Missing required environment variables |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Unset JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN | All required vars missing |
| 2 | Measure time to call JiraClientConfig.fromEnvironment() | ConfigException thrown |
| 3 | Verify elapsed time < 1000ms | Validation completes within 1s |
| 4 | Verify exception lists ALL missing vars | Not just the first one |

**Test Data:** All 3 required vars absent
**Acceptance Criteria:** ConfigException within 1s listing all missing variables

---


## 7. Integration Testing

### TC-700: Full Search → Parse → Return Lifecycle (WireMock)

| Field | Value |
|-------|-------|
| **ID** | TC-700 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-01, FSD §5.1 |
| **Preconditions** | WireMock running with Jira search endpoint stub |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: GET /rest/api/3/search → 200 with JSON body (3 issues) | Stub ready |
| 2 | Create JiraRestClientImpl with WireMock baseUrl | Client initialized |
| 3 | Call searchIssues(jql="project=MTO", maxResults=50) | Returns SearchResult |
| 4 | Verify SearchResult.issues.size == 3 | Correct count |
| 5 | Verify each issue has key, summary, status fields populated | Deserialization correct |
| 6 | Verify WireMock received request with correct query params | jql, maxResults, startAt in URL |

**Test Data:** WireMock fixture: 3 issues with keys MTO-1, MTO-2, MTO-3
**Postconditions:** No side effects, WireMock can verify request count

---

### TC-701: Get Issue with Expand Fields (WireMock)

| Field | Value |
|-------|-------|
| **ID** | TC-701 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-02, BR-10 |
| **Preconditions** | WireMock running with issue endpoint stub |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: GET /rest/api/3/issue/MTO-1?expand=changelog → 200 | Stub ready |
| 2 | Call getIssue(key="MTO-1", expand=["changelog"]) | Returns JiraIssue |
| 3 | Verify issue.key == "MTO-1" | Correct key |
| 4 | Verify issue.changelog is populated | Expand field included |
| 5 | Verify WireMock request has ?expand=changelog query param | Correct URL construction |

**Test Data:** WireMock fixture: issue MTO-1 with changelog data
**Postconditions:** Single request made to WireMock

---

### TC-702: Get Attachments → Download Attachment Pipeline

| Field | Value |
|-------|-------|
| **ID** | TC-702 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-03, UC-04, BR-15, BR-16 |
| **Preconditions** | WireMock with attachment list and binary download stubs |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: GET /rest/api/3/issue/MTO-1/attachments → 200 (2 attachments) | Stub ready |
| 2 | Configure WireMock: GET /secure/attachment/10001/file.pdf → 200 binary | Download stub ready |
| 3 | Call getAttachments(issueKey="MTO-1") | Returns List<JiraAttachment> with 2 items |
| 4 | Call downloadAttachment(attachments[0].contentUrl) | Returns ByteArray |
| 5 | Verify ByteArray content matches WireMock fixture | Binary content correct |
| 6 | Verify domain validation passed (same domain as baseUrl) | SSRF check OK |

**Test Data:** WireMock: 2 attachment metadata + 1 binary file (1KB PDF)
**Postconditions:** 2 HTTP requests made (list + download)

---

### TC-703: Rate Limiter Integration — Requests Throttled

| Field | Value |
|-------|-------|
| **ID** | TC-703 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-05, BR-20, BR-21, BR-22 |
| **Preconditions** | WireMock running, rate limiter at 5 req/s |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure rate limiter: rateLimit=5 | 5 tokens per second |
| 2 | Send 10 requests as fast as possible | First 5 immediate, next 5 delayed |
| 3 | Measure total elapsed time | ~2 seconds (10 requests / 5 per second) |
| 4 | Verify all 10 requests succeeded | No exceptions thrown |
| 5 | Verify WireMock received exactly 10 requests | All requests reached server |

**Test Data:** 10 rapid-fire searchIssues calls, rateLimit=5
**Acceptance Criteria:** Total time 1.8s-2.2s, all requests succeed

---

### TC-704: Retry Logic Integration — 429 Then Success

| Field | Value |
|-------|-------|
| **ID** | TC-704 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-06, BR-24, BR-25, BR-26 |
| **Preconditions** | WireMock with scenario: first 2 calls → 429, third → 200 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock scenario: call 1 → 429 (Retry-After: 1), call 2 → 429 (Retry-After: 1), call 3 → 200 | Scenario ready |
| 2 | Call searchIssues(jql="project=MTO") | Eventually returns SearchResult |
| 3 | Verify WireMock received 3 requests | 2 retries + 1 success |
| 4 | Verify total elapsed time ≥ 2s | Respected Retry-After headers |
| 5 | Verify no exception thrown to caller | Retry handled transparently |

**Test Data:** WireMock scenario with state transitions
**Postconditions:** 3 total requests, successful result returned

---

### TC-705: Retry Logic Integration — 503 Exponential Backoff

| Field | Value |
|-------|-------|
| **ID** | TC-705 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-06, BR-26, BR-27 |
| **Preconditions** | WireMock: first 2 calls → 503, third → 200 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: call 1 → 503, call 2 → 503, call 3 → 200 | Scenario ready |
| 2 | Configure retry: initialDelay=1000ms, multiplier=2.0 | Backoff configured |
| 3 | Call getIssue(key="MTO-1") | Eventually returns JiraIssue |
| 4 | Verify delay between attempt 1→2 ≈ 1000ms (±30%) | First backoff |
| 5 | Verify delay between attempt 2→3 ≈ 2000ms (±30%) | Exponential increase |
| 6 | Verify total elapsed ≈ 3000ms | Sum of backoff delays |

**Test Data:** WireMock scenario, initialDelay=1000, multiplier=2.0
**Acceptance Criteria:** Exponential backoff pattern observed (1s, 2s)

---

### TC-706: Retry Exhausted — Exception Propagated

| Field | Value |
|-------|-------|
| **ID** | TC-706 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | UC-06, BR-28 |
| **Preconditions** | WireMock always returns 503, maxRetries=3 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: always return 503 | Permanent failure |
| 2 | Configure retry: maxRetries=3 | 3 attempts max |
| 3 | Call searchIssues(jql="project=MTO") | RetryExhaustedException thrown |
| 4 | Verify exception.attempts == 3 | Correct attempt count |
| 5 | Verify exception.cause is JiraServerException | Original exception preserved |
| 6 | Verify WireMock received exactly 4 requests (1 initial + 3 retries) | Correct retry count |

**Test Data:** WireMock always 503, maxRetries=3
**Postconditions:** RetryExhaustedException with full context

---

### TC-707: Auth Header Sent Correctly (Basic Auth)

| Field | Value |
|-------|-------|
| **ID** | TC-707 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | FSD §5.1, BR-34 |
| **Preconditions** | WireMock running, client configured with email+token |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure client: email="user@example.com", token="api-token-123" | Client initialized |
| 2 | Call searchIssues(jql="project=MTO") | Request sent |
| 3 | Verify WireMock received Authorization header | Header present |
| 4 | Decode Base64 value | Contains "user@example.com:api-token-123" |
| 5 | Verify header format: "Basic {base64}" | Correct Basic Auth format |

**Test Data:** email: "user@example.com", token: "api-token-123"
**Postconditions:** Auth header verified on WireMock

---

### TC-708: JSON Deserialization — Unknown Fields Ignored

| Field | Value |
|-------|-------|
| **ID** | TC-708 |
| **Priority** | Medium |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | BR-11, FSD §3.2.4 |
| **Preconditions** | WireMock returns JSON with extra unknown fields |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: return issue JSON with extra fields (futureField, newProperty) | Stub ready |
| 2 | Call getIssue(key="MTO-1") | Returns JiraIssue successfully |
| 3 | Verify known fields populated correctly | key, summary, status correct |
| 4 | Verify no exception from unknown fields | Lenient deserialization |

**Test Data:** JSON with known fields + 3 unknown fields
**Acceptance Criteria:** Unknown fields silently ignored, no JsonDecodingException

---

### TC-709: Concurrent Requests — Thread Safety

| Field | Value |
|-------|-------|
| **ID** | TC-709 |
| **Priority** | High |
| **Type** | Integration |
| **Level** | IT |
| **Requirement** | BR-20, FSD §8 Scalability |
| **Preconditions** | WireMock running, rate limiter configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 20 coroutines, each calling searchIssues() | 20 concurrent requests |
| 2 | All coroutines complete without exception | No ConcurrentModificationException |
| 3 | Verify rate limiter enforced (not all 20 hit server simultaneously) | Requests throttled |
| 4 | Verify all 20 got valid responses | All deserialized correctly |

**Test Data:** 20 concurrent coroutines, rateLimit=10
**Acceptance Criteria:** No thread-safety exceptions, rate limiting enforced

---


## 8. Regression Testing

### TC-800: Existing MCP Orchestrator Startup Unaffected

| Field | Value |
|-------|-------|
| **ID** | TC-800 |
| **Priority** | High |
| **Type** | Regression |
| **Level** | IT |
| **Requirement** | No regression on existing MCP orchestrator functionality |
| **Preconditions** | Full application with JiraRestClient module added |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start application with all modules (including new JiraRestClient) | Application starts successfully |
| 2 | Verify MCP orchestrator endpoints still respond | Existing functionality intact |
| 3 | Verify no new startup errors in logs | Clean startup |
| 4 | Verify existing configuration not broken by new env vars | Backward compatible |

**Test Data:** Standard application configuration
**Postconditions:** Application running with all modules functional

---

### TC-801: Existing HTTP Client Usage Not Affected

| Field | Value |
|-------|-------|
| **ID** | TC-801 |
| **Priority** | Medium |
| **Type** | Regression |
| **Level** | IT |
| **Requirement** | No regression on existing HttpMcpConnection |
| **Preconditions** | Application running with both old and new HTTP clients |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify HttpMcpConnection still uses its own Ktor client instance | Separate client |
| 2 | Verify JiraRestClientImpl uses its own Ktor client instance | Separate client |
| 3 | Verify no shared state between the two clients | Independent lifecycle |
| 4 | Verify closing JiraRestClient doesn't affect MCP connections | No side effects |

**Test Data:** Both clients configured and active
**Postconditions:** Both clients operate independently

---

### TC-802: Gradle Build — No Dependency Conflicts

| Field | Value |
|-------|-------|
| **ID** | TC-802 |
| **Priority** | High |
| **Type** | Regression |
| **Level** | UT |
| **Requirement** | Build stability |
| **Preconditions** | build.gradle.kts with new Ktor/WireMock dependencies |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run `./gradlew dependencies` | No version conflicts |
| 2 | Run `./gradlew compileKotlin` | Compilation succeeds |
| 3 | Run `./gradlew test` | All existing tests still pass |
| 4 | Verify no duplicate class warnings | Clean classpath |

**Test Data:** Full project build
**Acceptance Criteria:** Zero compilation errors, all pre-existing tests pass

---

### TC-803: Coroutine Scope — No Leaked Coroutines

| Field | Value |
|-------|-------|
| **ID** | TC-803 |
| **Priority** | Medium |
| **Type** | Regression |
| **Level** | UT |
| **Requirement** | Resource management, no coroutine leaks |
| **Preconditions** | JiraRestClient created and used |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create JiraRestClient in TestScope | Client initialized |
| 2 | Make several requests | Requests complete |
| 3 | Close/dispose client | Client closed |
| 4 | Verify TestScope has no active children | No leaked coroutines |
| 5 | Verify Ktor HttpClient engine is closed | Resources released |

**Test Data:** TestScope with structured concurrency
**Acceptance Criteria:** No active coroutines after client disposal

---


## 9. E2E-API Testing (Full Lifecycle Scenarios)

### TC-900: Complete Search → Get Issue → Get Attachments → Download Workflow

| Field | Value |
|-------|-------|
| **ID** | TC-900 |
| **Priority** | High |
| **Type** | E2E-API |
| **Level** | E2E-API |
| **Requirement** | UC-01, UC-02, UC-03, UC-04 |
| **Preconditions** | WireMock with full Jira API simulation (search, issue, attachments, download) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call searchIssues(jql="project=MTO", maxResults=10) | Returns SearchResult with 3 issues |
| 2 | For first issue, call getIssue(key=issues[0].key) | Returns full JiraIssue with all fields |
| 3 | Call getAttachments(issueKey=issues[0].key) | Returns List<JiraAttachment> with 2 items |
| 4 | Call downloadAttachment(attachments[0].contentUrl) | Returns ByteArray with file content |
| 5 | Verify entire workflow completed without exception | End-to-end success |
| 6 | Verify rate limiter allowed all 4 requests (within limit) | No throttling for 4 requests |

**Test Data:** WireMock stubs for complete workflow: 3 issues, 2 attachments, 1 binary file
**Postconditions:** 4 HTTP requests made in sequence, all successful

---

### TC-901: Search with Pagination — Multi-Page Retrieval

| Field | Value |
|-------|-------|
| **ID** | TC-901 |
| **Priority** | High |
| **Type** | E2E-API |
| **Level** | E2E-API |
| **Requirement** | UC-01, BR-02, BR-03, BR-05 |
| **Preconditions** | WireMock with paginated search results (total=25, maxResults=10) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call searchIssues(jql="project=MTO", maxResults=10, startAt=0) | Returns page 1: 10 issues, total=25 |
| 2 | Call searchIssues(jql="project=MTO", maxResults=10, startAt=10) | Returns page 2: 10 issues |
| 3 | Call searchIssues(jql="project=MTO", maxResults=10, startAt=20) | Returns page 3: 5 issues |
| 4 | Verify total across all pages = 25 | Pagination consistent |
| 5 | Verify no duplicate issue keys across pages | Correct offset handling |

**Test Data:** WireMock: 25 issues split across 3 pages
**Acceptance Criteria:** All 25 unique issues retrieved across 3 paginated calls

---

### TC-902: Rate Limiting Under Load — Burst Then Throttle

| Field | Value |
|-------|-------|
| **ID** | TC-902 |
| **Priority** | High |
| **Type** | E2E-API |
| **Level** | E2E-API |
| **Requirement** | UC-05, BR-20, BR-21, BR-22, BR-23 |
| **Preconditions** | WireMock running, rate limiter at 5 req/s |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 5 requests immediately (burst) | All 5 complete instantly (tokens available) |
| 2 | Send 6th request immediately | Suspended ~1 second (waiting for token refill) |
| 3 | After 1 second, verify 6th request completes | Token refilled, request proceeds |
| 4 | Verify total time for 10 requests ≈ 2 seconds | Correct throttling behavior |

**Test Data:** 10 requests, rateLimit=5
**Acceptance Criteria:** First 5 instant, remaining throttled at 5/s rate

---

### TC-903: Retry + Rate Limit Combined Scenario

| Field | Value |
|-------|-------|
| **ID** | TC-903 |
| **Priority** | High |
| **Type** | E2E-API |
| **Level** | E2E-API |
| **Requirement** | UC-05, UC-06, BR-20, BR-24, BR-25 |
| **Preconditions** | WireMock: first call → 429 (Retry-After: 2), second call → 200 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call searchIssues() — server returns 429 with Retry-After: 2 | First attempt fails |
| 2 | Verify client waits ~2 seconds (respects Retry-After) | Backoff applied |
| 3 | Retry fires — server returns 200 | Second attempt succeeds |
| 4 | Verify rate limiter token consumed for BOTH attempts | Rate limiter counts retries |
| 5 | Verify final result returned to caller | Transparent retry |

**Test Data:** WireMock scenario: 429 → 200, Retry-After: 2
**Acceptance Criteria:** Caller receives successful result after transparent retry

---

### TC-904: Error Scenario — Auth Failure Propagation

| Field | Value |
|-------|-------|
| **ID** | TC-904 |
| **Priority** | High |
| **Type** | E2E-API |
| **Level** | E2E-API |
| **Requirement** | FSD §9.1, BR-30 |
| **Preconditions** | WireMock returns 401 for all requests |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure WireMock: always return 401 Unauthorized | Auth failure simulated |
| 2 | Call searchIssues(jql="project=MTO") | JiraAuthException thrown |
| 3 | Verify exception.statusCode == 401 | Correct status code |
| 4 | Verify exception.correlationId is present | Traceability maintained |
| 5 | Verify NO retry attempted (401 is not retryable) | Only 1 request to WireMock |

**Test Data:** WireMock always 401
**Acceptance Criteria:** JiraAuthException thrown immediately, no retry for auth failures

---

### TC-905: Configuration Lifecycle — Create, Use, Close

| Field | Value |
|-------|-------|
| **ID** | TC-905 |
| **Priority** | Medium |
| **Type** | E2E-API |
| **Level** | E2E-API |
| **Requirement** | UC-07, BR-33, BR-34, BR-42 |
| **Preconditions** | Valid environment variables set |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set all required env vars (BASE_URL, EMAIL, TOKEN) | Environment ready |
| 2 | Call JiraClientConfig.fromEnvironment() | Config created successfully |
| 3 | Create JiraRestClientImpl(config) | Client initialized |
| 4 | Make one successful request | Client works |
| 5 | Close client (dispose resources) | Ktor engine closed |
| 6 | Verify subsequent requests throw IllegalStateException | Client properly closed |

**Test Data:** Valid config: baseUrl=WireMock URL, email="test@test.com", token="token"
**Postconditions:** Client lifecycle complete, resources released

---


## 10. Requirements Traceability Matrix (RTM)

### 10.1 Use Case Coverage

| Requirement | Source | Test Cases | Coverage |
|-------------|--------|------------|----------|
| UC-01: Search Issues | FSD §3.1 | TC-001, TC-002, TC-003, TC-004, TC-005, TC-100, TC-101, TC-200, TC-201, TC-202, TC-400, TC-401, TC-402, TC-700, TC-900, TC-901 | Covered |
| UC-02: Get Issue | FSD §3.2 | TC-006, TC-007, TC-102, TC-103, TC-203, TC-204, TC-407, TC-701, TC-900 | Covered |
| UC-03: Get Attachments | FSD §3.3 | TC-008, TC-009, TC-104, TC-205, TC-206, TC-702, TC-900 | Covered |
| UC-04: Download Attachment | FSD §3.4 | TC-010, TC-011, TC-105, TC-207, TC-208, TC-408, TC-411, TC-702, TC-900 | Covered |
| UC-05: Rate Limiting | FSD §3.5 | TC-012, TC-013, TC-100, TC-106, TC-209, TC-410, TC-600, TC-601, TC-703, TC-902, TC-903 | Covered |
| UC-06: Retry Logic | FSD §3.6 | TC-014, TC-015, TC-107, TC-108, TC-210, TC-211, TC-406, TC-409, TC-704, TC-705, TC-706, TC-903 | Covered |
| UC-07: Configuration | FSD §3.7 | TC-016, TC-017, TC-109, TC-212, TC-300, TC-301, TC-315, TC-316, TC-317, TC-318, TC-403, TC-404, TC-405, TC-606, TC-905 | Covered |

### 10.2 Business Rule Coverage

| Requirement | Source | Test Cases | Coverage |
|-------------|--------|------------|----------|
| BR-01: JQL not blank | FSD §3.1.3 | TC-300, TC-200 | Covered |
| BR-02: maxResults 1-100 | FSD §3.1.3 | TC-301, TC-401 | Covered |
| BR-03: startAt ≥ 0 | FSD §3.1.3 | TC-302, TC-402 | Covered |
| BR-04: fields list validation | FSD §3.1.3 | TC-303, TC-412 | Covered |
| BR-05: pagination via startAt+maxResults | FSD §3.1.3 | TC-003, TC-901 | Covered |
| BR-06: total from response metadata | FSD §3.1.3 | TC-004, TC-901 | Covered |
| BR-07: empty result = empty list | FSD §3.1.3 | TC-005, TC-103 | Covered |
| BR-08: JSON deserialization lenient | FSD §3.2.3 | TC-304, TC-708 | Covered |
| BR-09: issue key format [A-Z]+-\\d+ | FSD §3.2.3 | TC-305, TC-407 | Covered |
| BR-10: expand values from allowed set | FSD §3.2.3 | TC-306, TC-319 | Covered |
| BR-11: unknown JSON fields ignored | FSD §3.2.3 | TC-304, TC-708 | Covered |
| BR-12: attachment metadata fields | FSD §3.3.3 | TC-008, TC-702 | Covered |
| BR-13: empty attachments = empty list | FSD §3.3.3 | TC-104, TC-009 | Covered |
| BR-14: attachment sorted by created date | FSD §3.3.3 | TC-307 | Covered |
| BR-15: download URL must be HTTPS | FSD §3.4.3 | TC-308, TC-408 | Covered |
| BR-16: SSRF domain validation | FSD §3.4.3 | TC-320, TC-604, TC-408 | Covered |
| BR-17: binary content as ByteArray | FSD §3.4.3 | TC-010, TC-702 | Covered |
| BR-18: streaming for large files | FSD §3.4.3 | TC-411 | Covered |
| BR-19: Content-Type from response header | FSD §3.4.3 | TC-309 | Covered |
| BR-20: token bucket algorithm | FSD §3.5.3 | TC-310, TC-703, TC-709 | Covered |
| BR-21: configurable rate (1-100 req/s) | FSD §3.5.3 | TC-311, TC-316, TC-403 | Covered |
| BR-22: suspend (not reject) when no tokens | FSD §3.5.3 | TC-012, TC-100, TC-703 | Covered |
| BR-23: thread-safe (Mutex) | FSD §3.5.3 | TC-312, TC-410, TC-709 | Covered |
| BR-24: 429 → use Retry-After header | FSD §3.6.3 | TC-313, TC-409, TC-704 | Covered |
| BR-25: 429 without Retry-After → default 60s | FSD §3.6.3 | TC-107, TC-409 | Covered |
| BR-26: 5xx → exponential backoff | FSD §3.6.3 | TC-014, TC-705 | Covered |
| BR-27: backoff capped at 30s | FSD §3.6.3 | TC-314, TC-406 | Covered |
| BR-28: max retries configurable (default 3) | FSD §3.6.3 | TC-015, TC-706 | Covered |
| BR-29: jitter ±20% on backoff | FSD §3.6.3 | TC-314 | Covered |
| BR-30: 401/403 → no retry | FSD §3.6.3 | TC-210, TC-904 | Covered |
| BR-31: 404 → no retry | FSD §3.6.3 | TC-211 | Covered |
| BR-32: 400 → no retry | FSD §3.6.3 | TC-212 | Covered |
| BR-33: env var based config | FSD §3.7.3 | TC-016, TC-905 | Covered |
| BR-34: required vars: BASE_URL, EMAIL, TOKEN | FSD §3.7.3 | TC-300, TC-318, TC-707 | Covered |
| BR-35: optional vars with defaults | FSD §3.7.3 | TC-017, TC-109 | Covered |
| BR-36: fail-fast on missing required | FSD §3.7.3 | TC-318, TC-606 | Covered |
| BR-37: rate limit range 1-100 | FSD §3.7.3 | TC-316, TC-403 | Covered |
| BR-38: timeout range 1000-300000ms | FSD §3.7.3 | TC-315, TC-404 | Covered |
| BR-39: max retries range 0-10 | FSD §3.7.3 | TC-405 | Covered |
| BR-40: base URL must be valid HTTP(S) | FSD §3.7.3 | TC-315 | Covered |
| BR-41: trim whitespace from env vars | FSD §3.7.3 | TC-109 | Covered |
| BR-42: config immutable after creation | FSD §3.7.3 | TC-317 | Covered |
| BR-43: fail-fast reports ALL missing vars | FSD §3.7.3 | TC-318, TC-606 | Covered |

### 10.3 Error Code Coverage

| Error Scenario | FSD Reference | Test Cases | Coverage |
|----------------|---------------|------------|----------|
| JiraValidationException (400) | FSD §9.1 | TC-200, TC-300-320, TC-400-412 | Covered |
| JiraAuthException (401/403) | FSD §9.1 | TC-201, TC-210, TC-904 | Covered |
| JiraNotFoundException (404) | FSD §9.1 | TC-203, TC-211 | Covered |
| JiraRateLimitException (429) | FSD §9.1 | TC-209, TC-704, TC-903 | Covered |
| JiraServerException (5xx) | FSD §9.1 | TC-202, TC-705, TC-706 | Covered |
| JiraTimeoutException | FSD §9.1 | TC-207, TC-602 | Covered |
| RetryExhaustedException | FSD §9.1 | TC-211, TC-706 | Covered |
| ConfigException | FSD §9.1 | TC-212, TC-318, TC-403-405, TC-606 | Covered |

### 10.4 Coverage Summary

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases (UC-01..UC-07) | 7 | 7 | 100% |
| Business Rules (BR-01..BR-43) | 43 | 43 | 100% |
| Error Scenarios | 8 | 8 | 100% |
| Non-Functional Requirements | 13 | 13 | 100% |
| **Overall** | **71** | **71** | **100%** |

---


## 11. Appendix

### 11.1 Test Data Setup

#### WireMock Fixtures Required

| Fixture File | Endpoint | Response |
|-------------|----------|----------|
| search-3-issues.json | GET /rest/api/3/search | 200 — 3 issues |
| search-paginated-p1.json | GET /rest/api/3/search?startAt=0 | 200 — 10 issues, total=25 |
| search-paginated-p2.json | GET /rest/api/3/search?startAt=10 | 200 — 10 issues |
| search-paginated-p3.json | GET /rest/api/3/search?startAt=20 | 200 — 5 issues |
| search-empty.json | GET /rest/api/3/search | 200 — 0 issues |
| issue-mto-1.json | GET /rest/api/3/issue/MTO-1 | 200 — full issue |
| issue-mto-1-changelog.json | GET /rest/api/3/issue/MTO-1?expand=changelog | 200 — issue + changelog |
| issue-not-found.json | GET /rest/api/3/issue/INVALID-999 | 404 — not found |
| attachments-2-items.json | GET /rest/api/3/issue/MTO-1/attachments | 200 — 2 attachments |
| attachments-empty.json | GET /rest/api/3/issue/MTO-2/attachments | 200 — empty list |
| download-file.pdf | GET /secure/attachment/10001/file.pdf | 200 — binary content |
| error-400-invalid-jql.json | GET /rest/api/3/search | 400 — JQL parse error |
| error-401-unauthorized.json | Any endpoint | 401 — auth failure |
| error-403-forbidden.json | Any endpoint | 403 — forbidden |
| error-429-rate-limited.json | Any endpoint | 429 — Retry-After: 60 |
| error-500-server.json | Any endpoint | 500 — internal error |
| error-503-unavailable.json | Any endpoint | 503 — service unavailable |

#### Environment Variables for Testing

| Variable | Test Value | Purpose |
|----------|-----------|---------|
| JIRA_BASE_URL | http://localhost:{wiremock_port} | WireMock base URL |
| JIRA_EMAIL | test@example.com | Test email |
| JIRA_API_TOKEN | test-api-token-12345 | Test token (not real) |
| JIRA_RATE_LIMIT | 10 | Default rate limit |
| JIRA_TIMEOUT_MS | 30000 | Default timeout |
| JIRA_MAX_RETRIES | 3 | Default retries |

### 11.2 Test Level Distribution

| Level | ID Range | Count | Automation |
|-------|----------|-------|------------|
| PBT (Property-Based) | TC-310, TC-312, TC-316, TC-320, TC-600 subset | 8 | 100% Automated (Kotest) |
| UT (Unit Test) | TC-001..TC-017, TC-200..TC-212, TC-300..TC-320, TC-400..TC-412, TC-603..TC-606 | 52 | 100% Automated (Kotest + MockK) |
| IT (Integration Test) | TC-100..TC-109, TC-600..TC-602, TC-700..TC-709 | 22 | 100% Automated (WireMock + Kotest) |
| E2E-API | TC-900..TC-905 | 6 | 100% Automated (WireMock standalone) |
| SIT (Manual) | — | 5 | Manual (real Jira instance) |
| **Total** | | **93** | **95% Automated** |

### 11.3 SIT Manual Test Scenarios

| # | Scenario | Environment | Steps |
|---|----------|-------------|-------|
| 1 | Real Jira search returns results | Staging + real Jira | Configure real credentials → searchIssues → verify results match Jira UI |
| 2 | Real Jira get issue with all fields | Staging + real Jira | getIssue with known key → verify all fields populated |
| 3 | Real Jira download attachment | Staging + real Jira | Find issue with attachment → download → verify file content |
| 4 | Real Jira rate limiting (429 response) | Staging + real Jira | Send rapid requests until 429 → verify retry logic works |
| 5 | End-to-end sync cycle | Staging + real Jira | Full search → get → download cycle for 10 issues |

### 11.4 Tools & Dependencies

| Tool | Version | Purpose |
|------|---------|---------|
| Kotest | 5.x | Test framework (BDD specs, property testing) |
| MockK | 1.13+ | Kotlin mocking library |
| WireMock | 3.x | HTTP mock server |
| Ktor Test | 2.x | Ktor client testing utilities |
| JUnit Platform | 5.x | Test runner (Gradle integration) |
| Kotlin Coroutines Test | 1.7+ | Virtual time, TestScope |

### 11.5 Glossary

| Term | Definition |
|------|------------|
| PBT | Property-Based Testing — generates random inputs to verify invariants |
| UT | Unit Testing — tests individual functions in isolation |
| IT | Integration Testing — tests component interactions with real/stubbed dependencies |
| E2E-API | End-to-End API Testing — tests full API lifecycle on running server |
| SIT | System Integration Testing — manual tests with real external systems |
| WireMock | HTTP mock server for stubbing external API responses |
| RTM | Requirements Traceability Matrix |
| SSRF | Server-Side Request Forgery |
| JQL | Jira Query Language |
| CIO | Coroutine-based I/O — Ktor's non-blocking HTTP engine |

---

*End of Document*
