# Software Test Cases (STC)

## MCP Orchestrator — MTO-94: Per-User Credentials + Scalable Process Pool

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-94 |
| Title | Per-User Credentials + Scalable Process Pool for MCP Orchestrator |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-07-06 |
| Status | Draft |
| Related STP | STP-v1.0-MTO-94.docx |
| Related FSD | FSD-v1.0-MTO-94.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-06 | QA Agent | Initiate document — 125 test cases from FSD use cases and business rules |

---

## Test Case Summary

| Category | ID Range | Count | Priority |
|----------|----------|-------|----------|
| Functional — Happy Path | TC-001 to TC-012 | 12 | High |
| Functional — Alternative Flows | TC-100 to TC-112 | 13 | High |
| Functional — Exception/Error Flows | TC-200 to TC-218 | 19 | High |
| Business Rule Validation | TC-300 to TC-318 | 19 | High |
| Boundary & Negative Testing | TC-400 to TC-414 | 15 | Medium |
| UI/UX Testing | TC-500 to TC-511 | 12 | Medium |
| Non-Functional (Performance, Security) | TC-600 to TC-614 | 15 | Medium |
| Integration Testing | TC-700 to TC-709 | 10 | High |
| Regression Testing | TC-800 to TC-809 | 10 | Medium |
| **Total** | | **125** | |

---

## Test Level Classification

| Prefix | Level | Automation | Tools |
|--------|-------|------------|-------|
| PBT-XX | Property-Based Test | Automated | kotest-property |
| UT-XX | Unit Test | Automated | kotest + mockk |
| IT-XX | Integration Test (Ktor testApplication) | Automated | Ktor test engine |
| E2E-API-XX | REST endpoint E2E (real server) | Automated | Ktor client + JUnit 5 |
| E2E-UI-XX | Browser UI E2E (Cucumber scenarios) | Automated | Cucumber + Serenity + WebDriver |
| SIT-XX | Manual exploratory / edge cases only | Manual | Browser |

---

## 1. Functional Test Cases — Happy Path

### TC-001: Login with valid credentials

| Field | Value |
|-------|-------|
| **ID** | TC-001 |
| **Level** | E2E-API-01 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-001, BR-001, MTO-95 AC#1 |
| **Preconditions** | User "john.doe" exists with active status and password hash for "SecurePass123" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with body {"username":"john.doe","password":"SecurePass123"} | HTTP 200 returned |
| 2 | Verify response contains "token" field | JWT token string present (3-part base64url) |
| 3 | Verify response contains "expires_at" field | ISO-8601 timestamp within 4 hours from now |
| 4 | Verify response contains "user" object with id, email, name, roles | All user fields populated correctly |
| 5 | Decode JWT and verify claims contain sub, email, roles, iat, exp, type="session" | All required claims present |

**Test Data:** username=john.doe, password=SecurePass123
**Postconditions:** Audit log contains AUTH_LOGIN_SUCCESS event

---

### TC-002: Generate bridge token from profile

| Field | Value |
|-------|-------|
| **ID** | TC-002 |
| **Level** | E2E-API-02 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-002, BR-002, BR-004, MTO-95 AC#2 |
| **Preconditions** | User is authenticated with valid session token |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/bridge-token with Authorization: Bearer {session_token} and body {"expiry_days":30} | HTTP 200 returned |
| 2 | Verify response contains "bridge_token" field | Long-lived JWT token string |
| 3 | Verify response contains "expires_at" approximately 30 days from now | Correct expiry |
| 4 | Verify response contains "token_id" (UUID) | Valid UUID format |
| 5 | Decode bridge token and verify claims contain type="bridge", sub, email, roles | Bridge token claims correct |

**Test Data:** expiry_days=30, session token from TC-001
**Postconditions:** Previous bridge token (if any) is revoked; audit log contains AUTH_TOKEN_GENERATED

---

### TC-003: JWT validation on protected endpoint

| Field | Value |
|-------|-------|
| **ID** | TC-003 |
| **Level** | E2E-API-03 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-003, BR-003, MTO-95 AC#3 |
| **Preconditions** | Valid session token available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/credentials/servers with Authorization: Bearer {valid_token} | HTTP 200 returned |
| 2 | Verify response body is valid JSON with "servers" array | Authenticated access granted |
| 3 | Verify request was processed with correct user context | Response reflects authenticated user data |

**Test Data:** Valid JWT session token
**Postconditions:** None

---

### TC-004: Create credential schema for upstream server

| Field | Value |
|-------|-------|
| **ID** | TC-004 |
| **Level** | E2E-API-04 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-004, BR-008, BR-010, MTO-96 AC#1, AC#2, AC#3 |
| **Preconditions** | Admin user authenticated; server "atlassian" exists in mcp_servers |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/credential-schemas/atlassian with 3 fields (jira_url, jira_email, jira_token) | HTTP 200 returned |
| 2 | Verify response contains server_name="atlassian" | Correct server |
| 3 | Verify response fields array has 3 entries with generated UUIDs | All fields persisted |
| 4 | Verify each field has correct field_key, field_label, field_type, field_required | Schema matches input |
| 5 | GET /api/admin/credential-schemas/atlassian to confirm persistence | Same schema returned |

**Test Data:** fields=[{field_key:"jira_url",field_label:"Jira URL",field_type:"url",field_required:true},{field_key:"jira_email",field_label:"Jira Email",field_type:"email",field_required:true},{field_key:"jira_token",field_label:"API Token",field_type:"secret",field_required:true}]
**Postconditions:** credential_schemas table has 3 rows for "atlassian"

---

### TC-005: Save user credentials for upstream server

| Field | Value |
|-------|-------|
| **ID** | TC-005 |
| **Level** | E2E-API-05 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-006, BR-014, BR-015, MTO-97 AC#1, AC#2, AC#3 |
| **Preconditions** | User authenticated; credential schema exists for "atlassian" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/credentials/atlassian with credentials map | HTTP 200 returned |
| 2 | Verify response status="COMPLETE" | All required fields filled |
| 3 | Verify secret fields show masked values (****XXXX format) | jira_token shows "****FgA..." |
| 4 | Verify non-secret fields show full values | jira_url and jira_email visible |
| 5 | Query database: user_credentials row exists with encrypted data | credentials_encrypted is non-null, not plaintext |

**Test Data:** credentials={"jira_url":"https://mycompany.atlassian.net","jira_email":"john@company.com","jira_token":"ATATT3xFgA0123456789"}
**Postconditions:** user_credentials table has encrypted entry for user+atlassian

---

### TC-006: Resolve credentials for tool execution

| Field | Value |
|-------|-------|
| **ID** | TC-006 |
| **Level** | IT-01 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-007, BR-021, BR-025, MTO-98 AC#1, AC#2 |
| **Preconditions** | User has saved credentials for "atlassian"; server config has placeholders |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call CredentialResolver.resolveCredentials(userId, "atlassian", serverConfig) | ResolvedConfig returned |
| 2 | Verify resolved_command has no {placeholder} patterns remaining | All placeholders replaced |
| 3 | Verify resolved_args contain actual credential values | "--url=https://mycompany.atlassian.net" etc. |
| 4 | Verify pool_key is SHA-256 hash of serverName + sorted credential values | Deterministic hash |
| 5 | Verify decrypted values are not logged anywhere | No credential values in log output |

**Test Data:** serverConfig with command="npx", args=["@anthropic/jira-mcp","--url={jira_url}","--email={jira_email}","--token={jira_token}"]
**Postconditions:** Decrypted values cleared from memory scope

---

### TC-007: Pool acquire warm process

| Field | Value |
|-------|-------|
| **ID** | TC-007 |
| **Level** | IT-02 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-008, BR-027, MTO-99 AC#1 |
| **Preconditions** | Process pool has an idle process for the given pool_key |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call ProcessPoolManager.acquireProcess(poolKey, resolvedConfig) | PooledConnection returned |
| 2 | Measure acquisition time | < 100ms |
| 3 | Verify returned connection state is BUSY | State transition from IDLE to BUSY |
| 4 | Verify pool metrics show one less idle process | idle_count decremented |

**Test Data:** poolKey="sha256:test-pool-key", existing idle process in pool
**Postconditions:** Process marked as BUSY, available for tool execution

---

### TC-008: Pool acquire cold start

| Field | Value |
|-------|-------|
| **ID** | TC-008 |
| **Level** | IT-03 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-008 AF-1, MTO-99 AC#2 |
| **Preconditions** | No idle process exists for pool_key; pool below maxInstancesPerServer |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call ProcessPoolManager.acquireProcess(poolKey, resolvedConfig) | PooledConnection returned |
| 2 | Measure acquisition time | < 5 seconds |
| 3 | Verify new process was spawned with resolved config | Process running with correct command/args |
| 4 | Verify pool size incremented | active_count increased by 1 |
| 5 | Verify totalProcessCount incremented | System-wide counter updated |

**Test Data:** poolKey for new user credentials, mock upstream server script
**Postconditions:** New process running and marked BUSY

---

### TC-009: Bridge client starts with --token

| Field | Value |
|-------|-------|
| **ID** | TC-009 |
| **Level** | IT-04 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-010, BR-036, MTO-100 AC#1 |
| **Preconditions** | Valid bridge token available; orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge with --token eyJhbGciOiJIUzI1NiIs... | Bridge starts successfully (no error) |
| 2 | Verify bridge logs show "Token configured" message | Token accepted |
| 3 | Trigger a tool call through bridge | Request sent to orchestrator |
| 4 | Verify HTTP request contains Authorization: Bearer header | Header present with correct token |
| 5 | Verify token value is NOT in any log output | Security: token not logged |

**Test Data:** Valid 3-part JWT bridge token
**Postconditions:** Bridge running with authenticated connection

---

### TC-010: SSO login flow end-to-end

| Field | Value |
|-------|-------|
| **ID** | TC-010 |
| **Level** | E2E-UI-01 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-011, BR-041, BR-043, MTO-101 AC#1, AC#2, AC#3 |
| **Preconditions** | SSO configured with mock IdP (Keycloak); user exists in IdP |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page | Login page displayed with SSO button |
| 2 | Click "Login with SSO" button | Browser redirected to IdP login page |
| 3 | Enter IdP credentials and submit | IdP authenticates user |
| 4 | Verify redirect back to orchestrator callback | /api/auth/sso/callback called with code and state |
| 5 | Verify user is logged in and redirected to portal | Admin Portal home page displayed |
| 6 | Verify local user record created/updated | User exists in users table with auth_mode=sso |

**Test Data:** IdP user: sso.user@company.com, password: SsoPass123
**Postconditions:** Local user created via JIT provisioning; session token issued

---

### TC-011: Credential validation endpoint

| Field | Value |
|-------|-------|
| **ID** | TC-011 |
| **Level** | E2E-API-06 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-007, MTO-98 |
| **Preconditions** | User has complete credentials for "atlassian" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/credentials/atlassian/validate with auth header | HTTP 200 returned |
| 2 | Verify response valid=true | All placeholders resolvable |
| 3 | Verify resolved_placeholders contains all field keys | ["jira_url","jira_email","jira_token"] |
| 4 | Verify missing_placeholders is empty array | No missing fields |

**Test Data:** User with complete atlassian credentials
**Postconditions:** None

---

### TC-012: Pool metrics API

| Field | Value |
|-------|-------|
| **ID** | TC-012 |
| **Level** | E2E-API-07 |
| **Priority** | High |
| **Type** | Functional |
| **Requirement** | UC-009, MTO-99 AC#10 |
| **Preconditions** | Admin authenticated; at least one pool active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/admin/pool/metrics with admin auth | HTTP 200 returned |
| 2 | Verify response contains total_processes count | Integer >= 0 |
| 3 | Verify response contains pools array with per-pool metrics | Each pool has server_name, active_count, idle_count |
| 4 | Verify utilization_percent is calculated correctly | (active / max_total) * 100 |

**Test Data:** Admin session token
**Postconditions:** None

---

## 2. Functional Test Cases — Alternative Flows

### TC-100: Login with SSO-mode user (redirect to IdP)

| Field | Value |
|-------|-------|
| **ID** | TC-100 |
| **Level** | E2E-API-08 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-001 AF-1, BR-042 |
| **Preconditions** | User "sso.user" has auth_mode=sso |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with {"username":"sso.user","password":"any"} | HTTP 400 returned |
| 2 | Verify error code indicates SSO redirect needed | Response contains redirect URL to IdP |

**Test Data:** username=sso.user (auth_mode=sso)
**Postconditions:** None

---

### TC-101: Login with locked account (>5 failed attempts)

| Field | Value |
|-------|-------|
| **ID** | TC-101 |
| **Level** | E2E-API-09 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-001 AF-2, BR-005 |
| **Preconditions** | User "locked.user" has failed_login_attempts=6, locked_until in future |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with {"username":"locked.user","password":"correct"} | HTTP 423 returned |
| 2 | Verify error code is ACCOUNT_LOCKED | Error message includes lockout duration |

**Test Data:** username=locked.user, locked_until=now+15min
**Postconditions:** None

---

### TC-102: Generate bridge token with custom expiry

| Field | Value |
|-------|-------|
| **ID** | TC-102 |
| **Level** | E2E-API-10 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-002 AF-1, BR-002 |
| **Preconditions** | User authenticated with session token |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/bridge-token with {"expiry_days":7} | HTTP 200 returned |
| 2 | Verify expires_at is approximately 7 days from now | Custom expiry applied |
| 3 | Decode token and verify exp claim matches | Token expiry = iat + 7 days |

**Test Data:** expiry_days=7
**Postconditions:** Bridge token with 7-day expiry created

---

### TC-103: JWT validation with deprecated X-User-Email header

| Field | Value |
|-------|-------|
| **ID** | TC-103 |
| **Level** | E2E-API-11 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-003 AF-1, BR-006 |
| **Preconditions** | User "john.doe@company.com" exists; no Authorization header |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/credentials/servers with X-User-Email: john.doe@company.com (no Bearer token) | HTTP 200 returned |
| 2 | Verify response is valid (backward compatible) | Request processed successfully |
| 3 | Check server logs for deprecation warning | Warning logged about X-User-Email usage |

**Test Data:** X-User-Email: john.doe@company.com
**Postconditions:** Deprecation warning in logs

---

### TC-104: Update existing credential schema (add new field)

| Field | Value |
|-------|-------|
| **ID** | TC-104 |
| **Level** | E2E-API-12 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-004 AF-1, BR-012 |
| **Preconditions** | Schema exists for "atlassian" with 3 fields; users have saved credentials |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/credential-schemas/atlassian with 4 fields (add jira_project) | HTTP 200 returned |
| 2 | Verify response has 4 fields | New field added |
| 3 | GET /api/credentials/atlassian as existing user | Existing credentials still intact |
| 4 | Verify new field shows has_value=false | New field not yet filled |

**Test Data:** Add field {field_key:"jira_project",field_label:"Default Project",field_type:"text",field_required:false}
**Postconditions:** Schema updated; existing user credentials NOT invalidated (BR-012)

---

### TC-105: Save credentials with partial update (merge)

| Field | Value |
|-------|-------|
| **ID** | TC-105 |
| **Level** | E2E-API-13 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-006 AF-1, BR-016 |
| **Preconditions** | User has existing credentials for "atlassian" (all 3 fields filled) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/credentials/atlassian with {"credentials":{"jira_token":"NEW_TOKEN_VALUE"}} | HTTP 200 returned |
| 2 | Verify jira_url and jira_email still have values (not cleared) | Merge behavior confirmed |
| 3 | Verify jira_token shows new masked value | Updated field reflected |

**Test Data:** Only update jira_token, leave others unchanged
**Postconditions:** Credentials merged, not replaced

---

### TC-106: Resolve credentials — server with no placeholders (backward compat)

| Field | Value |
|-------|-------|
| **ID** | TC-106 |
| **Level** | IT-05 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-007 AF-1, BR-024 |
| **Preconditions** | Server "local-tools" has no {placeholder} in config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call CredentialResolver.resolveCredentials(userId, "local-tools", configWithNoPlaceholders) | ResolvedConfig returned |
| 2 | Verify resolved config is identical to input config | No modification |
| 3 | Verify pool_key = SHA-256(serverName) only | No credential hash in key |

**Test Data:** serverConfig with command="node", args=["local-server.js"], no placeholders
**Postconditions:** Backward compatible behavior confirmed

---

### TC-107: Pool acquire — pool at max, queue and wait

| Field | Value |
|-------|-------|
| **ID** | TC-107 |
| **Level** | IT-06 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-008 AF-2 |
| **Preconditions** | Pool for key at maxInstancesPerServer; all processes BUSY |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call acquireProcess (all instances busy) | Request queued |
| 2 | Release one process from another coroutine after 500ms | Queued request unblocked |
| 3 | Verify acquisition succeeds within timeout | PooledConnection returned |
| 4 | Measure total wait time | ~500ms (time until release) |

**Test Data:** maxInstancesPerServer=2, both processes busy
**Postconditions:** Request served after process released

---

### TC-108: Bridge token via environment variable

| Field | Value |
|-------|-------|
| **ID** | TC-108 |
| **Level** | UT-01 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-010 AF-1, BR-037 |
| **Preconditions** | MCP_BRIDGE_TOKEN env var set with valid JWT |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge without --token but with MCP_BRIDGE_TOKEN env var | Bridge starts successfully |
| 2 | Verify token from env var is used for Authorization header | Header contains env var token |
| 3 | Start bridge with --token AND MCP_BRIDGE_TOKEN set | CLI --token takes precedence |

**Test Data:** MCP_BRIDGE_TOKEN=eyJhbGciOiJIUzI1NiIs...
**Postconditions:** Bridge uses correct token source

---

### TC-109: SSO JIT provisioning — first-time user

| Field | Value |
|-------|-------|
| **ID** | TC-109 |
| **Level** | E2E-API-14 |
| **Priority** | High |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-011 AF-1, BR-043 |
| **Preconditions** | SSO configured; user exists in IdP but NOT in local users table |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Complete SSO login flow for new user | Callback processes successfully |
| 2 | Verify local user record created | users table has new row |
| 3 | Verify user has default role from config | role = "developer" (default_role) |
| 4 | Verify auth_mode = "sso" | Correct auth mode set |
| 5 | Verify JWT session token issued | User can access protected endpoints |

**Test Data:** IdP user: new.user@company.com (not in local DB)
**Postconditions:** User auto-created with default role

---

### TC-110: Pool scale down — idle process terminated

| Field | Value |
|-------|-------|
| **ID** | TC-110 |
| **Level** | IT-07 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-009 (Scale Down), BR-029 |
| **Preconditions** | Pool has 3 processes; 2 idle for > idleTimeoutMs |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Wait for idle reaper to run (or trigger manually) | Idle processes detected |
| 2 | Verify 2 idle processes terminated | Pool size reduced to 1 |
| 3 | Verify minimum 1 process maintained | At least 1 process remains |
| 4 | Verify terminated processes are gracefully shut down | No orphan processes |

**Test Data:** idleTimeoutMs=1000 (short for testing), 2 processes idle > 1s
**Postconditions:** Pool scaled down, resources freed

---

### TC-111: Token refresh within valid window

| Field | Value |
|-------|-------|
| **ID** | TC-111 |
| **Level** | E2E-API-15 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | BR-001, FSD 3.1.6 refresh endpoint |
| **Preconditions** | Session token expiring within 30 minutes |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/refresh with token expiring in 20 min | HTTP 200 returned |
| 2 | Verify new token issued with fresh expiry | New expires_at = now + session_expiry_hours |
| 3 | Verify old token still valid until its original expiry | Both tokens work temporarily |

**Test Data:** Session token with exp = now + 20min
**Postconditions:** New session token issued

---

### TC-112: Delete credential schema field with user data (confirmation flow)

| Field | Value |
|-------|-------|
| **ID** | TC-112 |
| **Level** | E2E-API-16 |
| **Priority** | Medium |
| **Type** | Functional — Alternative Flow |
| **Requirement** | UC-005, BR-013 |
| **Preconditions** | Schema field "jira_token" exists; 5 users have data for it |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | DELETE /api/admin/credential-schemas/atlassian/jira_token (no confirm) | HTTP 200 with warning |
| 2 | Verify response deleted=false, warning="FIELD_HAS_DATA", affected_users=5 | Warning returned |
| 3 | DELETE /api/admin/credential-schemas/atlassian/jira_token?confirm=true | HTTP 200 with deleted=true |
| 4 | Verify field removed from schema | GET schema shows 2 fields |
| 5 | Verify user credentials cleaned up | jira_token removed from user_credentials JSONB |

**Test Data:** 5 users with jira_token filled
**Postconditions:** Field deleted, user data cleaned

---

## 3. Functional Test Cases — Exception/Error Flows

### TC-200: Login with invalid password

| Field | Value |
|-------|-------|
| **ID** | TC-200 |
| **Level** | E2E-API-17 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-001 EF-2, FSD 9.1 INVALID_CREDENTIALS |
| **Preconditions** | User "john.doe" exists with active status |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with {"username":"john.doe","password":"WrongPass"} | HTTP 401 returned |
| 2 | Verify error code is "INVALID_CREDENTIALS" | Correct error code |
| 3 | Verify message is "Invalid username or password" | Generic message (no username/password hint) |
| 4 | Verify failed_login_attempts incremented for user | Counter increased |

**Test Data:** username=john.doe, password=WrongPass (incorrect)
**Postconditions:** failed_login_attempts incremented; audit log AUTH_LOGIN_FAILED

---

### TC-201: Login with non-existent username

| Field | Value |
|-------|-------|
| **ID** | TC-201 |
| **Level** | E2E-API-18 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-001 EF-1, FSD 9.1 INVALID_CREDENTIALS |
| **Preconditions** | Username "nonexistent" does not exist in users table |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with {"username":"nonexistent","password":"any"} | HTTP 401 returned |
| 2 | Verify error code is "INVALID_CREDENTIALS" | Same error as wrong password (no enumeration) |
| 3 | Verify response time is similar to valid-user-wrong-password | Timing attack prevention |

**Test Data:** username=nonexistent
**Postconditions:** Audit log AUTH_LOGIN_FAILED

---

### TC-202: Login with disabled account

| Field | Value |
|-------|-------|
| **ID** | TC-202 |
| **Level** | E2E-API-19 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-001 EF-3, FSD 9.1 ACCOUNT_DISABLED |
| **Preconditions** | User "disabled.user" has active=false |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with {"username":"disabled.user","password":"correct"} | HTTP 403 returned |
| 2 | Verify error code is "ACCOUNT_DISABLED" | Correct error code |
| 3 | Verify message is "Account is disabled. Contact administrator" | User-friendly message |

**Test Data:** username=disabled.user (active=false)
**Postconditions:** None

---

### TC-203: Access protected endpoint with expired JWT

| Field | Value |
|-------|-------|
| **ID** | TC-203 |
| **Level** | E2E-API-20 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003 EF-2, FSD 9.1 TOKEN_EXPIRED |
| **Preconditions** | JWT token with exp in the past |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/credentials/servers with expired Bearer token | HTTP 401 returned |
| 2 | Verify error code is "TOKEN_EXPIRED" | Correct error code |
| 3 | Verify message indicates re-login needed | "Token has expired. Please login again or regenerate bridge token" |

**Test Data:** JWT with exp = now - 1 hour
**Postconditions:** None

---

### TC-204: Access protected endpoint with malformed JWT

| Field | Value |
|-------|-------|
| **ID** | TC-204 |
| **Level** | E2E-API-21 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003 EF-1, FSD 9.1 INVALID_TOKEN |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/credentials/servers with Authorization: Bearer not-a-jwt | HTTP 401 returned |
| 2 | Verify error code is "INVALID_TOKEN" | Correct error code |
| 3 | Verify message is "Token is malformed or signature verification failed" | Clear error |

**Test Data:** Authorization: Bearer not-a-valid-jwt-token
**Postconditions:** Security event logged

---

### TC-205: Access protected endpoint with revoked bridge token

| Field | Value |
|-------|-------|
| **ID** | TC-205 |
| **Level** | E2E-API-22 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-003, BR-004, FSD 9.1 |
| **Preconditions** | Bridge token generated, then new token generated (old one revoked) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Use the OLD (revoked) bridge token to access /api/credentials/servers | HTTP 401 returned |
| 2 | Verify error code is "INVALID_TOKEN" | Revoked token rejected |
| 3 | Use the NEW bridge token | HTTP 200 — access granted |

**Test Data:** Old revoked bridge token
**Postconditions:** None

---

### TC-206: Create schema for non-existent server

| Field | Value |
|-------|-------|
| **ID** | TC-206 |
| **Level** | E2E-API-23 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-004 EF-1, FSD 9.1 SERVER_NOT_FOUND |
| **Preconditions** | Server "nonexistent-server" not in mcp_servers table |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/credential-schemas/nonexistent-server with valid fields | HTTP 404 returned |
| 2 | Verify error code is "SERVER_NOT_FOUND" | Correct error |
| 3 | Verify message includes server name | "Upstream server 'nonexistent-server' not registered" |

**Test Data:** serverName=nonexistent-server
**Postconditions:** None

---

### TC-207: Create schema with duplicate field_key

| Field | Value |
|-------|-------|
| **ID** | TC-207 |
| **Level** | E2E-API-24 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-004 EF-2, BR-009 |
| **Preconditions** | Admin authenticated; server "atlassian" exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/credential-schemas/atlassian with two fields having same field_key | HTTP 409 returned |
| 2 | Verify error code is "DUPLICATE_FIELD_KEY" | Correct error |
| 3 | Verify message includes the duplicate key name | "Field key 'jira_url' already exists" |

**Test Data:** fields with duplicate field_key="jira_url"
**Postconditions:** Schema not modified

---

### TC-208: Save credentials with missing required field

| Field | Value |
|-------|-------|
| **ID** | TC-208 |
| **Level** | E2E-API-25 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-1, FSD MISSING_REQUIRED_FIELD |
| **Preconditions** | Schema for "atlassian" has jira_token as required; user omits it |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/credentials/atlassian with {"credentials":{"jira_url":"https://x.atlassian.net"}} (missing jira_token, jira_email) | HTTP 400 returned |
| 2 | Verify error code is "MISSING_REQUIRED_FIELD" | Correct error |
| 3 | Verify message identifies the missing field | "Required field 'jira_token' is missing" |

**Test Data:** Incomplete credentials (missing required fields)
**Postconditions:** Credentials not saved

---

### TC-209: Save credentials with invalid URL format

| Field | Value |
|-------|-------|
| **ID** | TC-209 |
| **Level** | E2E-API-26 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-2, BR-018 |
| **Preconditions** | Schema has jira_url with field_type="url" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/credentials/atlassian with {"credentials":{"jira_url":"not-a-url","jira_email":"j@c.com","jira_token":"tok"}} | HTTP 400 returned |
| 2 | Verify error code is "INVALID_FIELD_FORMAT" | Correct error |
| 3 | Verify message identifies field and expected type | "Field 'jira_url' must be a valid url" |

**Test Data:** jira_url="not-a-url" (invalid URL)
**Postconditions:** Credentials not saved

---

### TC-210: Resolve credentials — user missing required field

| Field | Value |
|-------|-------|
| **ID** | TC-210 |
| **Level** | IT-08 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-007 EF-1, FSD 9.1 MISSING_CREDENTIAL |
| **Preconditions** | User has jira_url but NOT jira_token; server config uses {jira_token} |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call CredentialResolver.resolveCredentials(userId, "atlassian", config) | MissingCredentialException thrown |
| 2 | Verify exception contains server_name="atlassian" | Correct server identified |
| 3 | Verify exception contains missing_fields=["jira_token"] | Missing field identified |

**Test Data:** User with partial credentials (jira_url only)
**Postconditions:** No resolution performed; clear error for user

---

### TC-211: Pool exhausted — all instances busy, timeout exceeded

| Field | Value |
|-------|-------|
| **ID** | TC-211 |
| **Level** | IT-09 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-008 EF-1, FSD 9.1 POOL_EXHAUSTED |
| **Preconditions** | All pools at max; total at maxTotalInstances; all BUSY |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call acquireProcess when all instances busy | Request queued |
| 2 | Wait for acquireTimeoutMs to elapse | Timeout reached |
| 3 | Verify PoolExhaustedException thrown | Correct exception |
| 4 | Verify HTTP 503 returned to client | "All process instances are busy. Please retry." |

**Test Data:** maxTotalInstances=2, both busy, acquireTimeoutMs=1000
**Postconditions:** Request rejected gracefully

---

### TC-212: Bridge starts with invalid token format

| Field | Value |
|-------|-------|
| **ID** | TC-212 |
| **Level** | UT-02 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-010 EF-1, BR-039 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge with --token "not-a-jwt" | Bridge refuses to start |
| 2 | Verify exit code is 1 | Non-zero exit |
| 3 | Verify error message logged | "Invalid token format. Expected JWT (header.payload.signature)" |

**Test Data:** --token "not-a-jwt" (not 3-part base64url)
**Postconditions:** Bridge process terminated

---

### TC-213: SSO callback with invalid state (CSRF protection)

| Field | Value |
|-------|-------|
| **ID** | TC-213 |
| **Level** | E2E-API-27 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-011 EF-3, BR-041 |
| **Preconditions** | SSO configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/auth/sso/callback?code=valid_code&state=tampered_state | HTTP 400 returned |
| 2 | Verify error code is "INVALID_STATE" | CSRF protection working |
| 3 | Verify no user session created | No JWT issued |

**Test Data:** state=tampered_value (not matching stored state)
**Postconditions:** Attack prevented; security event logged

---

### TC-214: SSO with IdP unreachable

| Field | Value |
|-------|-------|
| **ID** | TC-214 |
| **Level** | E2E-API-28 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-011 EF-1, FSD 9.1 SSO_PROVIDER_UNAVAILABLE |
| **Preconditions** | SSO configured but IdP endpoint is down |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Attempt SSO token exchange with IdP down | HTTP 503 returned |
| 2 | Verify error code is "SSO_PROVIDER_UNAVAILABLE" | Correct error |
| 3 | Verify message suggests retry or local login | User-friendly guidance |

**Test Data:** Mock IdP returning connection timeout
**Postconditions:** None

---

### TC-215: Access admin endpoint with non-admin role

| Field | Value |
|-------|-------|
| **ID** | TC-215 |
| **Level** | E2E-API-29 |
| **Priority** | High |
| **Type** | Functional — Exception Flow |
| **Requirement** | FSD 7.1 Authorization Matrix |
| **Preconditions** | User with role="developer" (not admin) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/admin/credential-schemas with developer token | HTTP 403 returned |
| 2 | PUT /api/admin/credential-schemas/atlassian with developer token | HTTP 403 returned |
| 3 | GET /api/admin/pool/metrics with developer token | HTTP 403 returned |

**Test Data:** JWT with roles=["developer"]
**Postconditions:** Unauthorized access blocked

---

### TC-216: Save credentials exceeding 10KB payload limit

| Field | Value |
|-------|-------|
| **ID** | TC-216 |
| **Level** | E2E-API-30 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-006 EF-4, BR-017 |
| **Preconditions** | Schema allows text fields |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/credentials/server with credentials containing >10KB of data | HTTP 400 returned |
| 2 | Verify error code is "PAYLOAD_TOO_LARGE" | Correct error |
| 3 | Verify message indicates 10KB limit | "Credential data exceeds maximum size (10KB)" |

**Test Data:** credentials with a field value of 11000 characters
**Postconditions:** Credentials not saved

---

### TC-217: Token refresh outside valid window

| Field | Value |
|-------|-------|
| **ID** | TC-217 |
| **Level** | E2E-API-31 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | FSD 3.1.6 refresh endpoint |
| **Preconditions** | Session token with > 30 min until expiry |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/refresh with token expiring in 3 hours | HTTP 400 returned |
| 2 | Verify error code is "TOKEN_NOT_REFRESHABLE" | Correct error |
| 3 | Verify message indicates not yet eligible | "Token not yet eligible for refresh" |

**Test Data:** Session token with exp = now + 3 hours
**Postconditions:** None

---

### TC-218: Create schema with invalid field_key format

| Field | Value |
|-------|-------|
| **ID** | TC-218 |
| **Level** | E2E-API-32 |
| **Priority** | Medium |
| **Type** | Functional — Exception Flow |
| **Requirement** | UC-004 EF-3, BR-008 |
| **Preconditions** | Admin authenticated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/credential-schemas/atlassian with field_key="Invalid Key!" | HTTP 400 returned |
| 2 | Verify error code is "INVALID_FIELD_KEY" | Correct error |
| 3 | Verify message explains format requirement | "Field key must be lowercase alphanumeric + underscores" |

**Test Data:** field_key="Invalid Key!" (uppercase, spaces, special chars)
**Postconditions:** Schema not modified

---

## 4. Business Rule Validation

### TC-300: BR-001 — Session token expiry within configured range

| Field | Value |
|-------|-------|
| **ID** | TC-300 |
| **Level** | UT-03 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-001 |
| **Preconditions** | auth.jwt.session-expiry-hours=4 in config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate session token | Token created |
| 2 | Decode token and check exp claim | exp = iat + 4 hours (within 1 second tolerance) |
| 3 | Change config to session-expiry-hours=2 and regenerate | exp = iat + 2 hours |

**Test Data:** Config: session-expiry-hours=4, then 2

---

### TC-301: BR-002 — Bridge token max expiry 365 days

| Field | Value |
|-------|-------|
| **ID** | TC-301 |
| **Level** | UT-04 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-002 |
| **Preconditions** | bridge-token-max-expiry-days=365 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Request bridge token with expiry_days=365 | Token created with 365-day expiry |
| 2 | Request bridge token with expiry_days=366 | Rejected — exceeds maximum |
| 3 | Request bridge token with expiry_days=0 | Rejected — below minimum |

**Test Data:** expiry_days: 365, 366, 0

---

### TC-302: BR-003 — JWT contains required claims

| Field | Value |
|-------|-------|
| **ID** | TC-302 |
| **Level** | PBT-01 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-003 |
| **Preconditions** | Any user with valid credentials |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate JWT for random valid user (property-based) | Token created |
| 2 | Decode and verify sub claim = user.id | Always present |
| 3 | Verify email claim = user.email | Always present |
| 4 | Verify roles claim = [user.role] | Always present |
| 5 | Verify iat and exp claims present and iat < exp | Always valid |

**Test Data:** Property-based: random users with various roles/emails

---

### TC-303: BR-004 — New bridge token invalidates previous

| Field | Value |
|-------|-------|
| **ID** | TC-303 |
| **Level** | IT-10 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-004 |
| **Preconditions** | User has existing bridge token |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate bridge token (token A) | Token A active |
| 2 | Generate new bridge token (token B) | Token B active |
| 3 | Verify token A is revoked in bridge_tokens table | revoked=true |
| 4 | Use token A to access API | HTTP 401 — revoked |
| 5 | Use token B to access API | HTTP 200 — valid |

**Test Data:** Two sequential bridge token generations

---

### TC-304: BR-008 — field_key format validation

| Field | Value |
|-------|-------|
| **ID** | TC-304 |
| **Level** | PBT-02 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-008 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Test valid keys: "jira_url", "api_token", "a", "field_123" | All accepted |
| 2 | Test invalid keys: "UPPER", "has space", "123start", "special!", "" | All rejected |
| 3 | Test boundary: 50-char key (max), 51-char key (over max) | 50 accepted, 51 rejected |

**Test Data:** Property-based: random strings matching/not matching ^[a-z][a-z0-9_]{0,49}$

---

### TC-305: BR-014 — Credentials encrypted with AES-256-GCM

| Field | Value |
|-------|-------|
| **ID** | TC-305 |
| **Level** | IT-11 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-014 |
| **Preconditions** | User saves credentials |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Save credentials via API | HTTP 200 |
| 2 | Query user_credentials table directly | credentials_encrypted is NOT plaintext JSON |
| 3 | Verify encrypted value is base64-encoded ciphertext | Not readable |
| 4 | Decrypt using TokenEncryptionService | Original values recovered |

**Test Data:** credentials={"jira_token":"ATATT3xFgA0123456789"}

---

### TC-306: BR-015 — Secret fields masked in responses

| Field | Value |
|-------|-------|
| **ID** | TC-306 |
| **Level** | UT-05 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-015 |
| **Preconditions** | User has saved secret-type credential |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/credentials/atlassian | HTTP 200 |
| 2 | Verify jira_token masked_value starts with "****" | Masked format |
| 3 | Verify only last 4 chars visible | e.g., "****6789" |
| 4 | Verify non-secret fields (jira_url) show full value | URL fully visible |

**Test Data:** jira_token="ATATT3xFgA0123456789" -> masked "****6789"

---

### TC-307: BR-021 — Placeholder format validation

| Field | Value |
|-------|-------|
| **ID** | TC-307 |
| **Level** | PBT-03 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-021 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Test valid placeholders: {jira_url}, {api_token}, {a} | All recognized and resolved |
| 2 | Test invalid patterns: {UPPER}, {has-dash}, {123}, {} | Not treated as placeholders |
| 3 | Test nested/escaped: {{double}}, \{escaped\} | Not resolved |

**Test Data:** Property-based: random placeholder patterns

---

### TC-308: BR-022 — Resolved values never logged

| Field | Value |
|-------|-------|
| **ID** | TC-308 |
| **Level** | IT-12 |
| **Priority** | High |
| **Type** | Business Rule — Security |
| **Requirement** | BR-022 |
| **Preconditions** | Credential resolution occurs |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Resolve credentials with known secret value "MY_SECRET_TOKEN" | Resolution succeeds |
| 2 | Capture all log output during resolution | Logs collected |
| 3 | Search logs for "MY_SECRET_TOKEN" | NOT found in any log line |
| 4 | Search logs for any credential value | No credential values logged |

**Test Data:** Known secret value to search for in logs

---

### TC-309: BR-025 — Pool key deterministic computation

| Field | Value |
|-------|-------|
| **ID** | TC-309 |
| **Level** | PBT-04 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-025 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Compute pool key for same server + same credentials twice | Identical hash both times |
| 2 | Compute pool key with credentials in different insertion order | Same hash (sorted) |
| 3 | Compute pool key with different credential values | Different hash |
| 4 | Compute pool key with different server name | Different hash |

**Test Data:** Property-based: random server names and credential maps

---

### TC-310: BR-027 — Users with same credentials share process

| Field | Value |
|-------|-------|
| **ID** | TC-310 |
| **Level** | IT-13 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-027, MTO-99 AC#5 |
| **Preconditions** | Two users with identical credentials for same server |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Resolve credentials for user A | pool_key_A computed |
| 2 | Resolve credentials for user B (same values) | pool_key_B computed |
| 3 | Verify pool_key_A == pool_key_B | Same hash |
| 4 | Acquire process for both users | Same process instance shared |

**Test Data:** Two users with identical jira credentials

---

### TC-311: BR-028 — Scale up triggered by slow response

| Field | Value |
|-------|-------|
| **ID** | TC-311 |
| **Level** | IT-14 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | BR-028 |
| **Preconditions** | Pool with 1 process; slowResponseThresholdMs=1000 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Simulate requests with avg response time > 1000ms | Threshold exceeded |
| 2 | Wait for responseTimeMonitor to detect | Scale-up triggered |
| 3 | Verify new process spawned | Pool size = 2 |
| 4 | Verify pool metrics updated | active_count or idle_count increased |

**Test Data:** slowResponseThresholdMs=1000, simulated 1500ms responses

---

### TC-312: BR-030 — maxInstancesPerServer validation

| Field | Value |
|-------|-------|
| **ID** | TC-312 |
| **Level** | UT-06 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | BR-030 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Configure maxInstancesPerServer=0 | Validation error (min 1) |
| 2 | Configure maxInstancesPerServer=1 | Accepted |
| 3 | Configure maxInstancesPerServer=20 | Accepted |
| 4 | Configure maxInstancesPerServer=21 | Validation error (max 20) |

**Test Data:** Values: 0, 1, 20, 21

---

### TC-313: BR-035 — Backward compat: no-schema servers use shared pool

| Field | Value |
|-------|-------|
| **ID** | TC-313 |
| **Level** | IT-15 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-035, MTO-99 AC#9 |
| **Preconditions** | Server "local-tools" has no credential schema |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute tool on "local-tools" as user A | Process acquired |
| 2 | Execute tool on "local-tools" as user B | Same process used |
| 3 | Verify pool_key = SHA-256(serverName) only | No credential component |
| 4 | Verify pool max size = 1 for this server | Shared single process |

**Test Data:** Server without credential schema

---

### TC-314: BR-036 — Token format: three base64url segments

| Field | Value |
|-------|-------|
| **ID** | TC-314 |
| **Level** | PBT-05 |
| **Priority** | High |
| **Type** | Business Rule |
| **Requirement** | BR-036 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Validate "eyJhbGciOiJIUzI1NiIs.eyJzdWIiOiIxMjM0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV" | Valid format |
| 2 | Validate "only-one-part" | Invalid — not 3 parts |
| 3 | Validate "two.parts" | Invalid — only 2 parts |
| 4 | Validate "a.b.c.d" | Invalid — 4 parts |
| 5 | Validate "" (empty) | Invalid |

**Test Data:** Property-based: random strings with varying dot counts

---

### TC-315: BR-037 — CLI --token takes precedence over env var

| Field | Value |
|-------|-------|
| **ID** | TC-315 |
| **Level** | UT-07 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | BR-037 |
| **Preconditions** | Both --token and MCP_BRIDGE_TOKEN set with different values |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set MCP_BRIDGE_TOKEN=token_from_env | Env var set |
| 2 | Start bridge with --token token_from_cli | Bridge starts |
| 3 | Verify Authorization header uses token_from_cli | CLI takes precedence |

**Test Data:** Two different valid JWT tokens

---

### TC-316: BR-041 — SSO globally toggleable

| Field | Value |
|-------|-------|
| **ID** | TC-316 |
| **Level** | IT-16 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | BR-041 |
| **Preconditions** | SSO config exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set sso_enabled=false | SSO disabled |
| 2 | GET /api/auth/sso/authorize | HTTP 404 or redirect to login with error |
| 3 | Set sso_enabled=true | SSO enabled |
| 4 | GET /api/auth/sso/authorize | HTTP 302 redirect to IdP |

**Test Data:** Toggle sso_enabled between true/false

---

### TC-317: BR-045 — Issuer URL must be HTTPS

| Field | Value |
|-------|-------|
| **ID** | TC-317 |
| **Level** | UT-08 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | BR-045 |
| **Preconditions** | Admin configuring SSO |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/sso/config with issuer_url="http://insecure.com" | HTTP 400 INVALID_ISSUER |
| 2 | PUT /api/admin/sso/config with issuer_url="https://secure.com" | Accepted |
| 3 | PUT /api/admin/sso/config with issuer_url="ftp://wrong.com" | HTTP 400 INVALID_ISSUER |

**Test Data:** Various URL schemes

---

### TC-318: BR-046 — Scopes must include "openid"

| Field | Value |
|-------|-------|
| **ID** | TC-318 |
| **Level** | UT-09 |
| **Priority** | Medium |
| **Type** | Business Rule |
| **Requirement** | BR-046 |
| **Preconditions** | Admin configuring SSO |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/admin/sso/config with scopes="profile email" (no openid) | HTTP 400 MISSING_OPENID_SCOPE |
| 2 | PUT /api/admin/sso/config with scopes="openid profile email" | Accepted |

**Test Data:** Scopes with and without "openid"

---

## 5. Boundary & Negative Testing

### TC-400: Username boundary — empty string

| Field | Value |
|-------|-------|
| **ID** | TC-400 |
| **Level** | UT-10 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | FSD 3.1.4 username validation |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/auth/login with {"username":"","password":"pass"} | HTTP 400 or 401 |
| 2 | Verify validation error returned | Username cannot be empty |

**Test Data:** username="" (empty)

---

### TC-401: Username boundary — max length (100 chars)

| Field | Value |
|-------|-------|
| **ID** | TC-401 |
| **Level** | UT-11 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | FSD 3.1.4 username max 100 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login with 100-char username (valid format) | Accepted (if user exists) |
| 2 | Login with 101-char username | Rejected — exceeds max length |

**Test Data:** username="a" * 100, username="a" * 101

---

### TC-402: Password boundary — minimum 8 characters

| Field | Value |
|-------|-------|
| **ID** | TC-402 |
| **Level** | UT-12 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | FSD 3.1.4 password min 8 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login with 7-char password | Rejected — too short |
| 2 | Login with 8-char password | Accepted (if correct) |
| 3 | Login with empty password | Rejected |

**Test Data:** password="1234567" (7), "12345678" (8), "" (empty)

---

### TC-403: Bridge token expiry boundary — min/max days

| Field | Value |
|-------|-------|
| **ID** | TC-403 |
| **Level** | UT-13 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-002, FSD 3.1.4 expiry_days 1-90 |
| **Preconditions** | User authenticated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Request bridge token with expiry_days=0 | Rejected — below minimum |
| 2 | Request bridge token with expiry_days=1 | Accepted — minimum |
| 3 | Request bridge token with expiry_days=90 | Accepted — maximum |
| 4 | Request bridge token with expiry_days=91 | Rejected — above maximum |

**Test Data:** expiry_days: 0, 1, 90, 91

---

### TC-404: field_key boundary — max 50 characters

| Field | Value |
|-------|-------|
| **ID** | TC-404 |
| **Level** | UT-14 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-008 |
| **Preconditions** | Admin authenticated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create schema with 50-char field_key | Accepted |
| 2 | Create schema with 51-char field_key | Rejected — exceeds max |
| 3 | Create schema with 1-char field_key "a" | Accepted — minimum |

**Test Data:** field_key of lengths 1, 50, 51

---

### TC-405: Credential JSONB payload — 10KB limit

| Field | Value |
|-------|-------|
| **ID** | TC-405 |
| **Level** | UT-15 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-017 |
| **Preconditions** | Schema with text field |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Save credentials with total payload = 9999 bytes | Accepted |
| 2 | Save credentials with total payload = 10000 bytes | Accepted (at limit) |
| 3 | Save credentials with total payload = 10001 bytes | Rejected — PAYLOAD_TOO_LARGE |

**Test Data:** Text field with varying lengths to hit boundary

---

### TC-406: Pool config — maxInstancesPerServer boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-406 |
| **Level** | UT-16 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-030 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set maxInstancesPerServer=0 | Validation error |
| 2 | Set maxInstancesPerServer=1 | Accepted (min) |
| 3 | Set maxInstancesPerServer=20 | Accepted (max) |
| 4 | Set maxInstancesPerServer=21 | Validation error |

**Test Data:** Values: 0, 1, 20, 21

---

### TC-407: Pool config — idleTimeoutMs boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-407 |
| **Level** | UT-17 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-032 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set idleTimeoutMs=29999 | Validation error (below 30000) |
| 2 | Set idleTimeoutMs=30000 | Accepted (min) |
| 3 | Set idleTimeoutMs=3600000 | Accepted (max) |
| 4 | Set idleTimeoutMs=3600001 | Validation error (above max) |

**Test Data:** Values: 29999, 30000, 3600000, 3600001

---

### TC-408: Username with special characters

| Field | Value |
|-------|-------|
| **ID** | TC-408 |
| **Level** | UT-18 |
| **Priority** | Medium |
| **Type** | Negative |
| **Requirement** | FSD 3.1.4 username format |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login with username="john doe" (space) | Rejected — invalid format |
| 2 | Login with username="john@doe" (at sign) | Rejected — invalid format |
| 3 | Login with username="john.doe" (dot) | Accepted — dots allowed |
| 4 | Login with username="john_doe" (underscore) | Accepted — underscores allowed |

**Test Data:** Various special characters in username

---

### TC-409: SQL injection in login fields

| Field | Value |
|-------|-------|
| **ID** | TC-409 |
| **Level** | E2E-API-33 |
| **Priority** | High |
| **Type** | Negative — Security |
| **Requirement** | Security best practice |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login with username="' OR 1=1 --" | HTTP 401 (not 200) — injection blocked |
| 2 | Login with password="' OR 1=1 --" | HTTP 401 — injection blocked |
| 3 | Verify no SQL error in response | Generic error message only |

**Test Data:** SQL injection payloads

---

### TC-410: XSS in credential field values

| Field | Value |
|-------|-------|
| **ID** | TC-410 |
| **Level** | E2E-API-34 |
| **Priority** | High |
| **Type** | Negative — Security |
| **Requirement** | Security best practice |
| **Preconditions** | Schema with text field |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Save credential with value "<script>alert('xss')</script>" | Stored as-is (encrypted) |
| 2 | Retrieve credential (masked) | No script execution in response |
| 3 | Verify API response is JSON (not HTML) | Content-Type: application/json |

**Test Data:** XSS payload in credential value

---

### TC-411: Concurrent bridge token generation (race condition)

| Field | Value |
|-------|-------|
| **ID** | TC-411 |
| **Level** | IT-17 |
| **Priority** | Medium |
| **Type** | Negative — Concurrency |
| **Requirement** | BR-004 |
| **Preconditions** | User authenticated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 5 concurrent POST /api/auth/bridge-token requests | All complete |
| 2 | Verify only 1 active (non-revoked) bridge token exists | Exactly 1 active |
| 3 | Verify the last-generated token is the active one | Consistent state |

**Test Data:** 5 concurrent requests from same user

---

### TC-412: Pool config — slowResponseThresholdMs boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-412 |
| **Level** | UT-19 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-033 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set slowResponseThresholdMs=999 | Validation error (below 1000) |
| 2 | Set slowResponseThresholdMs=1000 | Accepted (min) |
| 3 | Set slowResponseThresholdMs=60000 | Accepted (max) |
| 4 | Set slowResponseThresholdMs=60001 | Validation error (above max) |

**Test Data:** Values: 999, 1000, 60000, 60001

---

### TC-413: field_label boundary — max 100 characters

| Field | Value |
|-------|-------|
| **ID** | TC-413 |
| **Level** | UT-20 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | FSD 3.2.4 field_label max 100 |
| **Preconditions** | Admin authenticated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Create schema with 100-char field_label | Accepted |
| 2 | Create schema with 101-char field_label | Rejected |
| 3 | Create schema with empty field_label | Rejected — non-empty required |

**Test Data:** field_label of lengths 0, 100, 101

---

### TC-414: Pool config — maxTotalInstances boundaries

| Field | Value |
|-------|-------|
| **ID** | TC-414 |
| **Level** | UT-21 |
| **Priority** | Medium |
| **Type** | Boundary |
| **Requirement** | BR-031 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set maxTotalInstances=0 | Validation error (min 1) |
| 2 | Set maxTotalInstances=1 | Accepted |
| 3 | Set maxTotalInstances=100 | Accepted (max) |
| 4 | Set maxTotalInstances=101 | Validation error (above max) |

**Test Data:** Values: 0, 1, 100, 101

---

## 6. UI/UX Testing

### TC-500: Login page — elements present and functional

| Field | Value |
|-------|-------|
| **ID** | TC-500 |
| **Level** | E2E-UI-02 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.1.5 Login Page |
| **Preconditions** | Navigate to login page |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify Username input is present and focused | Input visible, auto-focused |
| 2 | Verify Password input with show/hide toggle | Password field with toggle icon |
| 3 | Verify Login button is disabled until both fields filled | Button disabled initially |
| 4 | Fill both fields | Login button becomes enabled |
| 5 | Verify SSO button shown (if SSO enabled) | "Login with SSO" button visible |

**Test Data:** N/A (UI verification)

---

### TC-501: Login page — error message display

| Field | Value |
|-------|-------|
| **ID** | TC-501 |
| **Level** | E2E-UI-03 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.1.5 Error Message element |
| **Preconditions** | Login page loaded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Submit login with wrong password | Error alert appears |
| 2 | Verify error message text matches API response | "Invalid username or password" |
| 3 | Wait 5 seconds | Error message auto-dismisses |

**Test Data:** username=john.doe, password=wrong

---

### TC-502: Profile page — bridge token section

| Field | Value |
|-------|-------|
| **ID** | TC-502 |
| **Level** | E2E-UI-04 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.1.5 Profile Bridge Token Section |
| **Preconditions** | User logged in, on profile page |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify token status badge shows current state | "Active (expires: date)" or "No token" |
| 2 | Verify expiry days input with default value | Input shows config default |
| 3 | Click Generate Token button | Confirmation dialog appears (warns about invalidating previous) |
| 4 | Confirm generation | Token displayed in code block |
| 5 | Click Copy button | "Copied!" feedback shown |
| 6 | Wait 60 seconds | Token display auto-hides |

**Test Data:** N/A (UI interaction)

---

### TC-503: Profile page — credential server cards

| Field | Value |
|-------|-------|
| **ID** | TC-503 |
| **Level** | E2E-UI-05 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.3.5 Credentials Section |
| **Preconditions** | User logged in; schemas exist for 2+ servers |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify server cards displayed (one per server with schema) | Cards visible |
| 2 | Verify status badges: check-mark (complete), warning (partial), X (none) | Correct status per server |
| 3 | Click expand on a server card | Credential form expands |
| 4 | Verify dynamic input fields match schema definition | Correct field types and labels |
| 5 | Verify secret fields use type=password | Dots shown for secret input |

**Test Data:** N/A (UI verification)

---

### TC-504: Admin — credential schema management page

| Field | Value |
|-------|-------|
| **ID** | TC-504 |
| **Level** | E2E-UI-06 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.2.5 Admin Schema Management |
| **Preconditions** | Admin logged in |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify server selector dropdown populated from mcp_servers | All servers listed |
| 2 | Select a server | Existing schema fields displayed |
| 3 | Click "Add Field" button | New field row added to list |
| 4 | Fill field_key, field_label, select field_type | Inputs accept values |
| 5 | Click Save Schema | Success feedback shown |
| 6 | Verify field_key auto-slugified from label | "Jira URL" -> "jira_url" suggestion |

**Test Data:** N/A (UI interaction)

---

### TC-505: Admin — delete field confirmation dialog

| Field | Value |
|-------|-------|
| **ID** | TC-505 |
| **Level** | E2E-UI-07 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.2.5 Delete Field Button |
| **Preconditions** | Schema field exists with user data |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click delete icon on a field | Confirmation dialog appears |
| 2 | Verify dialog shows affected user count | "5 users have data for this field" |
| 3 | Click Cancel | Dialog closes, field not deleted |
| 4 | Click delete again, then Confirm | Field removed from list |

**Test Data:** Field with 5 users having data

---

### TC-506: Credential form — save button per server

| Field | Value |
|-------|-------|
| **ID** | TC-506 |
| **Level** | E2E-UI-08 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.3.5 Save Button |
| **Preconditions** | User on profile page, credential form expanded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Fill all required fields for a server | Save button enabled |
| 2 | Click Save | Loading spinner shown |
| 3 | Verify success feedback | "Credentials saved" message |
| 4 | Verify secret fields now show masked values | "****XXXX" format |
| 5 | Verify status badge updates to check-mark | COMPLETE status |

**Test Data:** Valid credential values for all required fields

---

### TC-507: Credential form — clear all button with confirmation

| Field | Value |
|-------|-------|
| **ID** | TC-507 |
| **Level** | E2E-UI-09 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.3.5 Clear Button |
| **Preconditions** | User has saved credentials for a server |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click "Clear All" button | Confirmation dialog appears |
| 2 | Click Cancel | Dialog closes, credentials intact |
| 3 | Click "Clear All" again, then Confirm | All fields cleared |
| 4 | Verify status badge updates to X (none) | NONE status |

**Test Data:** N/A (UI interaction)

---

### TC-508: SSO login button visibility

| Field | Value |
|-------|-------|
| **ID** | TC-508 |
| **Level** | E2E-UI-10 |
| **Priority** | Medium |
| **Type** | UI/UX |
| **Requirement** | FSD 3.1.5 SSO Login Button |
| **Preconditions** | SSO configuration exists |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page with SSO enabled | "Login with SSO" button visible |
| 2 | Disable SSO (admin config) | Button not shown on login page |
| 3 | Re-enable SSO | Button reappears |

**Test Data:** Toggle sso_enabled

---

### TC-509: Admin — pool metrics dashboard

| Field | Value |
|-------|-------|
| **ID** | TC-509 |
| **Level** | SIT-01 |
| **Priority** | Low |
| **Type** | UI/UX |
| **Requirement** | MTO-99 AC#10 |
| **Preconditions** | Admin logged in; pools active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to pool metrics page | Metrics displayed |
| 2 | Verify total processes count shown | Matches API response |
| 3 | Verify per-pool breakdown visible | Server name, active/idle counts |
| 4 | Verify utilization percentage displayed | Correct calculation |
| 5 | Verify visual layout is readable and well-organized | Dark theme, clear typography |

**Test Data:** N/A (visual verification)

---

### TC-510: Login page — dark theme consistency

| Field | Value |
|-------|-------|
| **ID** | TC-510 |
| **Level** | SIT-02 |
| **Priority** | Low |
| **Type** | UI/UX — Visual |
| **Requirement** | FSD UI spec (dark theme) |
| **Preconditions** | Login page loaded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify page uses dark background | Consistent with existing admin portal |
| 2 | Verify text contrast is readable | WCAG AA contrast ratio |
| 3 | Verify input fields have visible borders | Distinguishable from background |
| 4 | Verify button styling matches existing pages | Consistent design system |

**Test Data:** N/A (visual verification)

---

### TC-511: Profile page — responsive layout

| Field | Value |
|-------|-------|
| **ID** | TC-511 |
| **Level** | SIT-03 |
| **Priority** | Low |
| **Type** | UI/UX — Visual |
| **Requirement** | General UI quality |
| **Preconditions** | Profile page loaded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | View at 1920x1080 (desktop) | Layout correct, no overflow |
| 2 | View at 1366x768 (laptop) | Layout adapts, all content visible |
| 3 | View at 768x1024 (tablet) | Cards stack vertically if needed |
| 4 | Verify no horizontal scrollbar at any size | Content fits viewport |

**Test Data:** N/A (visual verification)

---

## 7. Non-Functional Testing

### TC-600: JWT validation throughput > 10,000/sec

| Field | Value |
|-------|-------|
| **ID** | TC-600 |
| **Level** | PBT-06 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD 8 — JWT validation < 5ms |
| **Preconditions** | JwtAuthService initialized with test key |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate 10,000 valid JWT tokens | Tokens ready |
| 2 | Validate all 10,000 tokens sequentially, measure total time | Total < 10 seconds (avg < 1ms) |
| 3 | Validate p95 latency | < 5ms per validation |

**Acceptance Criteria:** > 10,000 validations/sec, p95 < 5ms

---

### TC-601: Credential resolution latency p95 < 15ms

| Field | Value |
|-------|-------|
| **ID** | TC-601 |
| **Level** | IT-18 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD 8 — Credential resolution < 10ms |
| **Preconditions** | User credentials stored; DB warmed up |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Resolve credentials 1000 times for same user/server | All succeed |
| 2 | Measure p50 latency | < 5ms |
| 3 | Measure p95 latency | < 15ms |
| 4 | Measure p99 latency | < 25ms |

**Acceptance Criteria:** p95 < 15ms (DB fetch + decrypt + resolve)

---

### TC-602: Pool warm acquire latency p99 < 200ms

| Field | Value |
|-------|-------|
| **ID** | TC-602 |
| **Level** | IT-19 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD 8 — Pool acquire < 100ms |
| **Preconditions** | Pool with idle processes |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Acquire and release process 1000 times | All succeed |
| 2 | Measure p50 latency | < 10ms |
| 3 | Measure p99 latency | < 200ms |

**Acceptance Criteria:** p99 < 200ms for warm acquisition

---

### TC-603: Pool cold start latency p95 < 5s

| Field | Value |
|-------|-------|
| **ID** | TC-603 |
| **Level** | IT-20 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD 8 — Cold start < 5s |
| **Preconditions** | Mock upstream server script available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Spawn 10 new processes (cold starts) | All succeed |
| 2 | Measure p50 cold start time | < 3s |
| 3 | Measure p95 cold start time | < 5s |

**Acceptance Criteria:** p95 < 5 seconds for process spawn + initialization

---

### TC-604: Login latency p95 < 600ms

| Field | Value |
|-------|-------|
| **ID** | TC-604 |
| **Level** | IT-21 |
| **Priority** | Medium |
| **Type** | Non-Functional — Performance |
| **Requirement** | FSD 8 — Login < 500ms |
| **Preconditions** | User exists with bcrypt hash |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Perform 100 login requests sequentially | All succeed |
| 2 | Measure p50 latency | < 300ms |
| 3 | Measure p95 latency | < 600ms |

**Acceptance Criteria:** p95 < 600ms (bcrypt verify + JWT creation)

---

### TC-605: Concurrent user capacity — 20 users

| Field | Value |
|-------|-------|
| **ID** | TC-605 |
| **Level** | IT-22 |
| **Priority** | Medium |
| **Type** | Non-Functional — Scalability |
| **Requirement** | FSD 8 — 20+ concurrent users |
| **Preconditions** | 20 test users with credentials; pool configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Launch 20 concurrent tool execution requests (different users) | All requests processed |
| 2 | Verify no request fails with 503 | Pool handles load |
| 3 | Verify total processes <= maxTotalInstances | System limit respected |
| 4 | Sustain load for 60 seconds | No degradation |

**Acceptance Criteria:** 20 concurrent users, 50 total processes, sustained 60s

---

### TC-606: AES-256-GCM encryption correctness

| Field | Value |
|-------|-------|
| **ID** | TC-606 |
| **Level** | PBT-07 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | BR-014, FSD 7.3 |
| **Preconditions** | TokenEncryptionService initialized |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Encrypt random credential JSON (property-based, 1000 iterations) | All encrypt successfully |
| 2 | Decrypt each ciphertext | Original plaintext recovered |
| 3 | Verify ciphertext differs for same plaintext (unique IV) | No two ciphertexts identical |
| 4 | Tamper with ciphertext byte and attempt decrypt | Decryption fails (integrity check) |

**Acceptance Criteria:** 100% encrypt/decrypt roundtrip; tamper detection works

---

### TC-607: Bcrypt password hashing — cost factor 12

| Field | Value |
|-------|-------|
| **ID** | TC-607 |
| **Level** | UT-22 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD 7.3 — Bcrypt cost 12 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Hash password "TestPassword123" | Hash generated |
| 2 | Verify hash starts with "$2b$12$" or "$2a$12$" | Cost factor 12 confirmed |
| 3 | Verify same password produces different hash each time | Salt randomization |
| 4 | Verify correct password validates against hash | BCrypt.verify returns true |
| 5 | Verify wrong password fails validation | BCrypt.verify returns false |

**Acceptance Criteria:** Cost factor 12, unique salts, correct verification

---

### TC-608: JWT signature verification — tampered token rejected

| Field | Value |
|-------|-------|
| **ID** | TC-608 |
| **Level** | UT-23 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD 7.3 — HS256/RS256 signing |
| **Preconditions** | Valid JWT available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Take valid JWT and modify one character in payload | Tampered token |
| 2 | Attempt to validate tampered token | Signature verification fails |
| 3 | Take valid JWT and modify signature | Tampered token |
| 4 | Attempt to validate | Signature verification fails |
| 5 | Sign token with different secret | Wrong key |
| 6 | Attempt to validate with correct secret | Verification fails |

**Acceptance Criteria:** All tampered/wrong-key tokens rejected

---

### TC-609: Credential values not in structured logs

| Field | Value |
|-------|-------|
| **ID** | TC-609 |
| **Level** | IT-23 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | BR-022, FSD 7.4 |
| **Preconditions** | Logging configured to capture all levels |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Perform full credential save + resolve flow | Operations complete |
| 2 | Capture all log output | Logs collected |
| 3 | Search for known credential values (API tokens, passwords) | NOT found |
| 4 | Search for JWT token strings | NOT found in logs |
| 5 | Verify only metadata logged (field keys, server names, user IDs) | Metadata only |

**Acceptance Criteria:** Zero credential values in any log output

---

### TC-610: Account lockout after 5 failed attempts

| Field | Value |
|-------|-------|
| **ID** | TC-610 |
| **Level** | E2E-API-35 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD 7.5 — Account lockout |
| **Preconditions** | User "locktest" with 0 failed attempts |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Attempt login with wrong password (attempt 1-4) | HTTP 401 each time |
| 2 | Verify failed_login_attempts increments | Count = 4 |
| 3 | Attempt login with wrong password (attempt 5) | HTTP 401 |
| 4 | Attempt login with CORRECT password (attempt 6) | HTTP 423 ACCOUNT_LOCKED |
| 5 | Verify locked_until is set to now + 15 minutes | Lockout applied |
| 6 | Wait for lockout to expire (or advance time) | Account unlocked |
| 7 | Login with correct password | HTTP 200 — success |

**Acceptance Criteria:** Lock after 5 failures, unlock after 15 min

---

### TC-611: PKCE in SSO flow

| Field | Value |
|-------|-------|
| **ID** | TC-611 |
| **Level** | IT-24 |
| **Priority** | High |
| **Type** | Non-Functional — Security |
| **Requirement** | FSD 7.5 — PKCE for SSO |
| **Preconditions** | SSO configured |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Initiate SSO flow (GET /api/auth/sso/authorize) | Redirect URL contains code_challenge |
| 2 | Verify code_challenge_method=S256 in redirect | PKCE S256 used |
| 3 | Complete callback with valid code | Token exchange includes code_verifier |
| 4 | Attempt callback without matching code_verifier | Token exchange fails |

**Acceptance Criteria:** PKCE prevents authorization code interception

---

### TC-612: Process crash auto-restart

| Field | Value |
|-------|-------|
| **ID** | TC-612 |
| **Level** | IT-25 |
| **Priority** | Medium |
| **Type** | Non-Functional — Availability |
| **Requirement** | BR-034, MTO-99 AC#7 |
| **Preconditions** | Pool with active process |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Kill a pool process externally | Process crashes |
| 2 | Verify HealthMonitor detects crash | Crash detected within health check interval |
| 3 | Verify auto-restart triggered | New process spawned |
| 4 | Verify pool metrics updated | POOL_PROCESS_CRASH audit event |
| 5 | Verify next tool execution succeeds | No user-visible error |

**Acceptance Criteria:** Auto-restart within 5 seconds, no manual intervention

---

### TC-613: Graceful degradation — pool queue before 503

| Field | Value |
|-------|-------|
| **ID** | TC-613 |
| **Level** | IT-26 |
| **Priority** | Medium |
| **Type** | Non-Functional — Availability |
| **Requirement** | FSD 8 — Queue up to 30s |
| **Preconditions** | Pool at max capacity |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Fill pool to max (all BUSY) | Pool exhausted |
| 2 | Send new request | Request queued (not immediately 503) |
| 3 | Release a process within 5 seconds | Queued request served |
| 4 | Send another request, do NOT release any process | Request queued |
| 5 | Wait for acquireTimeoutMs (30s) | HTTP 503 returned after timeout |

**Acceptance Criteria:** Queue before reject; 503 only after timeout

---

### TC-614: Backward compatibility — X-User-Email still works

| Field | Value |
|-------|-------|
| **ID** | TC-614 |
| **Level** | E2E-API-36 |
| **Priority** | High |
| **Type** | Non-Functional — Reliability |
| **Requirement** | BR-006, FSD 8 backward compat |
| **Preconditions** | User exists; deprecated header enabled in config |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send request with X-User-Email header (no JWT) | HTTP 200 — request processed |
| 2 | Verify user context correctly extracted from email | Correct user identified |
| 3 | Check logs for deprecation warning | Warning logged |
| 4 | Verify same endpoint works with JWT Bearer token | Also works (preferred method) |

**Acceptance Criteria:** Both auth methods work; deprecated path logs warning

---

## 8. Integration Testing

### TC-700: End-to-end tool execution with per-user credentials

| Field | Value |
|-------|-------|
| **ID** | TC-700 |
| **Level** | IT-27 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD IT-001, UC-007, UC-008 |
| **Preconditions** | User authenticated; credentials saved; mock upstream server available |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send tool/call request via POST /mcp with Bearer token | Request received |
| 2 | Verify AuthMiddleware extracts user context | User identified |
| 3 | Verify CredentialResolver resolves placeholders | Config resolved |
| 4 | Verify ProcessPoolManager acquires process | Process obtained |
| 5 | Verify tool executes on upstream with resolved credentials | Result returned |
| 6 | Verify response returned to client | JSON-RPC result |

**Test Data:** Bridge token, tool="jira_create_issue", mock upstream
**Postconditions:** Tool execution complete, process released to pool

---

### TC-701: Bridge to orchestrator authenticated flow

| Field | Value |
|-------|-------|
| **ID** | TC-701 |
| **Level** | IT-28 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD 5.3, UC-010 |
| **Preconditions** | Bridge started with --token; orchestrator running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Bridge sends POST /mcp with Authorization header | Orchestrator receives |
| 2 | Verify orchestrator validates JWT | User context established |
| 3 | Verify tool execution proceeds with user identity | Correct user's credentials used |
| 4 | Verify response flows back through bridge | IDE receives result |

**Test Data:** Valid bridge token, tool call request
**Postconditions:** Full round-trip complete

---

### TC-702: SSO full flow with JIT provisioning

| Field | Value |
|-------|-------|
| **ID** | TC-702 |
| **Level** | E2E-UI-11 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD IT-002, UC-011 |
| **Preconditions** | Mock IdP (Keycloak) running; new user in IdP |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Navigate to login page, click SSO | Redirect to IdP |
| 2 | Authenticate at IdP | Auth code issued |
| 3 | Callback processes code exchange | Tokens received |
| 4 | Verify local user created (JIT) | users table has new row |
| 5 | Verify JWT session token issued | User logged in |
| 6 | Verify user can access profile page | Authenticated access |

**Test Data:** IdP user: new.sso@company.com
**Postconditions:** User created with default role, session active

---

### TC-703: Pool scaling under concurrent load

| Field | Value |
|-------|-------|
| **ID** | TC-703 |
| **Level** | IT-29 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD IT-003, UC-009 |
| **Preconditions** | Pool configured with maxInstancesPerServer=3; mock slow server |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send 5 concurrent requests to same server (response time > threshold) | Requests queued/processed |
| 2 | Verify pool scales up from 1 to 2 (then 3) | Scale-up events logged |
| 3 | Stop sending requests, wait for idle timeout | Processes become idle |
| 4 | Verify pool scales down | Idle processes terminated |
| 5 | Verify minimum 1 process maintained | Never scales to 0 |

**Test Data:** 5 concurrent requests, slowResponseThresholdMs=500 (low for testing)
**Postconditions:** Pool returns to minimum size

---

### TC-704: Credential schema change with existing user data

| Field | Value |
|-------|-------|
| **ID** | TC-704 |
| **Level** | E2E-API-37 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD IT-005, BR-012 |
| **Preconditions** | Schema exists; users have saved credentials |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Admin adds new optional field to schema | Schema updated |
| 2 | User fetches their credentials | Existing values intact |
| 3 | New field shows has_value=false | Not yet filled |
| 4 | User fills new field | Merged with existing |
| 5 | Verify all original credentials still accessible | No data loss |

**Test Data:** Add optional field "jira_project" to existing schema
**Postconditions:** Schema extended, user data preserved

---

### TC-705: Bridge reconnection after token expiry

| Field | Value |
|-------|-------|
| **ID** | TC-705 |
| **Level** | SIT-04 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | FSD IT-004 |
| **Preconditions** | Bridge running with token about to expire |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Bridge sends request with expired token | Orchestrator returns 401 |
| 2 | Verify bridge logs "Token expired" message | Clear error logged |
| 3 | User regenerates bridge token from profile | New token issued |
| 4 | Restart bridge with new token | Bridge reconnects |
| 5 | Verify tool execution works again | Full functionality restored |

**Test Data:** Expired bridge token, then new token
**Postconditions:** Bridge operational with new token

---

### TC-706: Multi-user credential isolation

| Field | Value |
|-------|-------|
| **ID** | TC-706 |
| **Level** | IT-30 |
| **Priority** | High |
| **Type** | Integration — Security |
| **Requirement** | FSD 7.5 Credential isolation |
| **Preconditions** | Two users with different credentials for same server |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | User A executes tool on "atlassian" | Uses User A's credentials |
| 2 | User B executes tool on "atlassian" | Uses User B's credentials |
| 3 | Verify User A's process has User A's token in args | Correct isolation |
| 4 | Verify User B's process has User B's token in args | Correct isolation |
| 5 | Verify different pool_keys for A and B | Separate processes |

**Test Data:** User A: jira_token="TOKEN_A", User B: jira_token="TOKEN_B"
**Postconditions:** Complete credential isolation confirmed

---

### TC-707: Audit trail completeness

| Field | Value |
|-------|-------|
| **ID** | TC-707 |
| **Level** | IT-31 |
| **Priority** | Medium |
| **Type** | Integration |
| **Requirement** | FSD 7.4 Audit Trail |
| **Preconditions** | Clean audit log |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Perform login (success) | AUTH_LOGIN_SUCCESS logged |
| 2 | Perform login (failure) | AUTH_LOGIN_FAILED logged |
| 3 | Generate bridge token | AUTH_TOKEN_GENERATED logged |
| 4 | Save credentials | CREDENTIAL_SAVED logged |
| 5 | Clear credentials | CREDENTIAL_CLEARED logged |
| 6 | Modify schema | SCHEMA_UPDATED logged |
| 7 | Verify no credential values in audit entries | Only metadata |

**Test Data:** Various operations to trigger audit events
**Postconditions:** Complete audit trail

---

### TC-708: Database migration — additive only

| Field | Value |
|-------|-------|
| **ID** | TC-708 |
| **Level** | IT-32 |
| **Priority** | High |
| **Type** | Integration |
| **Requirement** | FSD 11.4 Migration Plan |
| **Preconditions** | Existing database with users and mcp_servers data |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run migration scripts on existing database | Migrations succeed |
| 2 | Verify existing users table data intact | No data loss |
| 3 | Verify new columns added with defaults | password_hash=null, auth_mode='local' |
| 4 | Verify new tables created | credential_schemas, user_credentials, bridge_tokens, sso_config |
| 5 | Verify existing application still works | Backward compatible |

**Test Data:** Existing database with production-like data
**Postconditions:** Database upgraded, existing data preserved

---

### TC-709: Encryption key management

| Field | Value |
|-------|-------|
| **ID** | TC-709 |
| **Level** | PBT-08 |
| **Priority** | High |
| **Type** | Integration — Security |
| **Requirement** | FSD 7.3 Encryption |
| **Preconditions** | Encryption key available via env var |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Encrypt credentials with key A | Ciphertext produced |
| 2 | Decrypt with key A | Original recovered |
| 3 | Attempt decrypt with key B (different) | Decryption fails |
| 4 | Verify unique IV per encryption (same plaintext, different ciphertext) | Property holds for 1000 iterations |

**Test Data:** Property-based: random credential maps, random keys

---

## 9. Regression Testing

### TC-800: Existing tool execution without credentials (backward compat)

| Field | Value |
|-------|-------|
| **ID** | TC-800 |
| **Level** | E2E-API-38 |
| **Priority** | High |
| **Type** | Regression |
| **Requirement** | BR-024, BR-035 |
| **Preconditions** | Server "local-tools" has no credential schema; existing tool works |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Execute tool on server without credential schema | Tool executes successfully |
| 2 | Verify no credential resolution attempted | Config used as-is |
| 3 | Verify shared process used (not per-user) | Same process for all users |

**Test Data:** Tool on no-schema server
**Postconditions:** Existing behavior preserved

---

### TC-801: Existing user management API still works

| Field | Value |
|-------|-------|
| **ID** | TC-801 |
| **Level** | E2E-API-39 |
| **Priority** | High |
| **Type** | Regression |
| **Requirement** | Existing functionality |
| **Preconditions** | Admin authenticated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/admin/users | User list returned |
| 2 | POST /api/admin/users (create user) | User created with new auth columns |
| 3 | PUT /api/admin/users/{id} (update user) | User updated |
| 4 | Verify new columns (auth_mode, password_hash) have defaults | Defaults applied |

**Test Data:** Standard user CRUD operations
**Postconditions:** User management works with new schema

---

### TC-802: Existing MCP server configuration unchanged

| Field | Value |
|-------|-------|
| **ID** | TC-802 |
| **Level** | E2E-API-40 |
| **Priority** | High |
| **Type** | Regression |
| **Requirement** | Existing functionality |
| **Preconditions** | mcp_servers table has existing entries |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/admin/servers | Server list returned |
| 2 | Verify existing server configs intact | No modification |
| 3 | Add new server | Works as before |
| 4 | Verify new server can have credential schema added | Integration works |

**Test Data:** Existing server configurations
**Postconditions:** Server management unchanged

---

### TC-803: Health check endpoint still accessible

| Field | Value |
|-------|-------|
| **ID** | TC-803 |
| **Level** | E2E-API-41 |
| **Priority** | High |
| **Type** | Regression |
| **Requirement** | Existing functionality (public endpoint) |
| **Preconditions** | Server running |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /health (no auth header) | HTTP 200 returned |
| 2 | Verify health check not blocked by new AuthMiddleware | Public endpoint still public |

**Test Data:** No authentication
**Postconditions:** None

---

### TC-804: Existing HealthMonitor behavior preserved

| Field | Value |
|-------|-------|
| **ID** | TC-804 |
| **Level** | IT-33 |
| **Priority** | Medium |
| **Type** | Regression |
| **Requirement** | BR-034 |
| **Preconditions** | Process pool with HealthMonitor active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Verify HealthMonitor still checks process health | Health checks running |
| 2 | Kill a process | Crash detected |
| 3 | Verify auto-restart behavior same as before | Process restarted |
| 4 | Verify no regression in restart timing | Within expected bounds |

**Test Data:** Mock upstream server that can be killed
**Postconditions:** HealthMonitor behavior unchanged

---

### TC-805: Existing audit log format preserved

| Field | Value |
|-------|-------|
| **ID** | TC-805 |
| **Level** | IT-34 |
| **Priority** | Medium |
| **Type** | Regression |
| **Requirement** | Existing audit functionality |
| **Preconditions** | Audit log service active |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger existing audit events (user CRUD) | Events logged |
| 2 | Verify existing event format unchanged | Same structure |
| 3 | Verify new event types (AUTH_LOGIN_SUCCESS etc.) follow same format | Consistent |

**Test Data:** Various operations
**Postconditions:** Audit log format consistent

---

### TC-806: Bridge stdio mode still works (no token)

| Field | Value |
|-------|-------|
| **ID** | TC-806 |
| **Level** | SIT-05 |
| **Priority** | Medium |
| **Type** | Regression |
| **Requirement** | BR-040, MTO-100 AC#4 |
| **Preconditions** | Bridge in stdio mode (single-user) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start bridge without --token in stdio mode | Bridge starts with warning |
| 2 | Verify warning logged about missing token | "No token configured" warning |
| 3 | Execute tool through bridge | Tool works (backward compat) |
| 4 | Verify no Authorization header sent | Header absent |

**Test Data:** No token, stdio mode
**Postconditions:** Bridge functional without token

---

### TC-807: Existing encryption service compatibility

| Field | Value |
|-------|-------|
| **ID** | TC-807 |
| **Level** | UT-24 |
| **Priority** | High |
| **Type** | Regression |
| **Requirement** | Existing TokenEncryptionService |
| **Preconditions** | Existing encrypted data in database |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Decrypt existing encrypted tokens (from before changes) | Decryption succeeds |
| 2 | Encrypt new data with same service | Encryption succeeds |
| 3 | Verify no breaking changes to encryption API | Same interface |

**Test Data:** Existing encrypted data from production
**Postconditions:** Encryption service backward compatible

---

### TC-808: Koin DI module registration

| Field | Value |
|-------|-------|
| **ID** | TC-808 |
| **Level** | UT-25 |
| **Priority** | Medium |
| **Type** | Regression |
| **Requirement** | Existing DI setup |
| **Preconditions** | Application startup |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start application with all new modules registered | Startup succeeds |
| 2 | Verify no circular dependency errors | Clean DI graph |
| 3 | Verify existing modules still resolve correctly | No conflicts |
| 4 | Verify new services injectable | All new services available |

**Test Data:** Full application context
**Postconditions:** Application starts cleanly

---

### TC-809: API response format consistency

| Field | Value |
|-------|-------|
| **ID** | TC-809 |
| **Level** | E2E-API-42 |
| **Priority** | Medium |
| **Type** | Regression |
| **Requirement** | FSD 9.2 Error Response Format |
| **Preconditions** | Various error scenarios |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Trigger 401 error | Response has {error, message, timestamp, request_id} |
| 2 | Trigger 400 error | Same format with details field |
| 3 | Trigger 404 error | Same format |
| 4 | Trigger 500 error | Same format (no stack trace exposed) |
| 5 | Verify all errors follow FSD 9.2 JSON structure | Consistent format |

**Test Data:** Various invalid requests
**Postconditions:** None

---

## 10. Requirements Traceability Matrix (RTM)

| Requirement | Source | Test Cases | Coverage |
|-------------|--------|------------|----------|
| UC-001 (Login) | FSD 3.1.2 | TC-001, TC-100, TC-101, TC-200, TC-201, TC-202 | Covered |
| UC-002 (Bridge Token) | FSD 3.1.2 | TC-002, TC-102, TC-203 | Covered |
| UC-003 (JWT Validation) | FSD 3.1.2 | TC-003, TC-103, TC-203, TC-204, TC-205 | Covered |
| UC-004 (Create Schema) | FSD 3.2.2 | TC-004, TC-104, TC-206, TC-207, TC-218 | Covered |
| UC-005 (Delete Schema Field) | FSD 3.2.2 | TC-112 | Covered |
| UC-006 (Save Credentials) | FSD 3.3.2 | TC-005, TC-105, TC-208, TC-209, TC-216 | Covered |
| UC-007 (Resolve Credentials) | FSD 3.4.2 | TC-006, TC-011, TC-106, TC-210 | Covered |
| UC-008 (Pool Acquire) | FSD 3.5.2 | TC-007, TC-008, TC-107, TC-211 | Covered |
| UC-009 (Pool Scale) | FSD 3.5.2 | TC-012, TC-110, TC-311 | Covered |
| UC-010 (Bridge Token) | FSD 3.6.2 | TC-009, TC-108, TC-212 | Covered |
| UC-011 (SSO Login) | FSD 3.7.2 | TC-010, TC-109, TC-213, TC-214 | Covered |
| UC-012 (SSO Config) | FSD 3.7.2 | TC-316, TC-317, TC-318 | Covered |
| BR-001 (Session expiry) | FSD 3.1.3 | TC-300 | Covered |
| BR-002 (Bridge expiry max) | FSD 3.1.3 | TC-301, TC-403 | Covered |
| BR-003 (JWT claims) | FSD 3.1.3 | TC-302 | Covered |
| BR-004 (Token invalidation) | FSD 3.1.3 | TC-303, TC-205, TC-411 | Covered |
| BR-005 (Failed login audit) | FSD 3.1.3 | TC-101, TC-610 | Covered |
| BR-006 (Deprecated header) | FSD 3.1.3 | TC-103, TC-614 | Covered |
| BR-007 (JWT algorithm) | FSD 3.1.3 | TC-608 | Covered |
| BR-008 (field_key format) | FSD 3.2.3 | TC-304, TC-404, TC-218 | Covered |
| BR-009 (field_key unique) | FSD 3.2.3 | TC-207 | Covered |
| BR-010 (Field types) | FSD 3.2.3 | TC-004 | Covered |
| BR-011 (Min 1 field) | FSD 3.2.3 | TC-004 | Covered |
| BR-012 (Schema no invalidate) | FSD 3.2.3 | TC-104, TC-704 | Covered |
| BR-013 (Delete confirmation) | FSD 3.2.3 | TC-112 | Covered |
| BR-014 (AES-256-GCM) | FSD 3.3.3 | TC-305, TC-606 | Covered |
| BR-015 (Masked secrets) | FSD 3.3.3 | TC-306 | Covered |
| BR-016 (Partial merge) | FSD 3.3.3 | TC-105 | Covered |
| BR-017 (10KB limit) | FSD 3.3.3 | TC-405, TC-216 | Covered |
| BR-018 (URL validation) | FSD 3.3.3 | TC-209 | Covered |
| BR-019 (Email validation) | FSD 3.3.3 | TC-209 | Covered |
| BR-020 (Completeness) | FSD 3.3.3 | TC-005, TC-503 | Covered |
| BR-021 (Placeholder format) | FSD 3.4.3 | TC-307 | Covered |
| BR-022 (Never log secrets) | FSD 3.4.3 | TC-308, TC-609 | Covered |
| BR-023 (Per-request) | FSD 3.4.3 | TC-006 | Covered |
| BR-024 (Backward compat) | FSD 3.4.3 | TC-106, TC-800 | Covered |
| BR-025 (Pool key hash) | FSD 3.4.3 | TC-309 | Covered |
| BR-026 (Schema match) | FSD 3.4.3 | TC-307 | Covered |
| BR-027 (Shared credentials) | FSD 3.5.3 | TC-310 | Covered |
| BR-028 (Scale up trigger) | FSD 3.5.3 | TC-311 | Covered |
| BR-029 (Scale down idle) | FSD 3.5.3 | TC-110 | Covered |
| BR-030 (maxInstances 1-20) | FSD 3.5.3 | TC-312, TC-406 | Covered |
| BR-031 (maxTotal 1-100) | FSD 3.5.3 | TC-414 | Covered |
| BR-032 (idleTimeout range) | FSD 3.5.3 | TC-407 | Covered |
| BR-033 (slowResponse range) | FSD 3.5.3 | TC-412 | Covered |
| BR-034 (Auto-restart) | FSD 3.5.3 | TC-612, TC-804 | Covered |
| BR-035 (No-schema shared) | FSD 3.5.3 | TC-313, TC-800 | Covered |
| BR-036 (Token 3-part) | FSD 3.6.3 | TC-314 | Covered |
| BR-037 (CLI precedence) | FSD 3.6.3 | TC-315, TC-108 | Covered |
| BR-038 (Token not logged) | FSD 3.6.3 | TC-009 | Covered |
| BR-039 (Invalid exit 1) | FSD 3.6.3 | TC-212 | Covered |
| BR-040 (No token warning) | FSD 3.6.3 | TC-806 | Covered |
| BR-041 (SSO toggle) | FSD 3.7.3 | TC-316 | Covered |
| BR-042 (Auth mode) | FSD 3.7.3 | TC-100 | Covered |
| BR-043 (JIT provision) | FSD 3.7.3 | TC-109, TC-702 | Covered |
| BR-044 (Bridge token same) | FSD 3.7.3 | TC-002 | Covered |
| BR-045 (HTTPS issuer) | FSD 3.7.3 | TC-317 | Covered |
| BR-046 (openid scope) | FSD 3.7.3 | TC-318 | Covered |
| BR-047 (Fallback local) | FSD 3.7.3 | TC-316 | Covered |

**Coverage Summary:**

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases | 12 | 12 | 100% |
| Business Rules | 47 | 47 | 100% |
| Error Scenarios (FSD 9.1) | 14 | 14 | 100% |
| Non-Functional Requirements | 17 | 15 | 88% |
| **Overall** | **90** | **88** | **98%** |

---

## 11. Appendix

### Test Data Setup Scripts

```sql
-- Pre-seed test users
INSERT INTO users (id, email, name, role, active, password_hash, auth_mode, failed_login_attempts)
VALUES
  ('admin-001', 'admin@company.com', 'Admin User', 'system_owner', true, '$2b$12$LJ3m4sMKfRzlTBhPQOqYxOKGHCzW1234567890abcdef', 'local', 0),
  ('dev-001', 'john.doe@company.com', 'John Doe', 'developer', true, '$2b$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUV', 'local', 0),
  ('dev-002', 'jane.smith@company.com', 'Jane Smith', 'developer', true, '$2b$12$xyzxyzxyzxyzxyzxyzxyzuXYZXYZXYZXYZXYZXYZXYZ', 'local', 0),
  ('disabled-001', 'disabled@company.com', 'Disabled User', 'developer', false, '$2b$12$disabledhashdisabledhash', 'local', 0),
  ('sso-001', 'sso.user@company.com', 'SSO User', 'developer', true, NULL, 'sso', 0),
  ('locked-001', 'locked@company.com', 'Locked User', 'developer', true, '$2b$12$lockedhashlocked', 'local', 6);

-- Pre-seed upstream servers
INSERT INTO mcp_servers (id, name, command, args, transport)
VALUES
  ('srv-001', 'atlassian', 'npx', '["@anthropic/jira-mcp","--url={jira_url}","--email={jira_email}","--token={jira_token}"]', 'stdio'),
  ('srv-002', 'github', 'npx', '["@anthropic/github-mcp","--token={github_token}"]', 'stdio'),
  ('srv-003', 'local-tools', 'node', '["local-server.js"]', 'stdio');

-- Pre-seed credential schemas
INSERT INTO credential_schemas (id, server_name, field_key, field_label, field_type, field_required, display_order)
VALUES
  ('schema-001', 'atlassian', 'jira_url', 'Jira Instance URL', 'url', true, 1),
  ('schema-002', 'atlassian', 'jira_email', 'Jira Email', 'email', true, 2),
  ('schema-003', 'atlassian', 'jira_token', 'Jira API Token', 'secret', true, 3),
  ('schema-004', 'github', 'github_token', 'GitHub Personal Access Token', 'secret', true, 1);
```

### Environment Configuration (Test)

```yaml
auth:
  jwt:
    secret: "test-jwt-secret-256-bit-minimum-length-for-hs256"
    algorithm: HS256
    session-expiry-hours: 4
    bridge-token-default-expiry-days: 30
    bridge-token-max-expiry-days: 90
  lockout:
    max-attempts: 5
    lockout-duration-minutes: 15
  deprecated:
    allow-email-header: true

process-pool:
  max-instances-per-server: 5
  max-total-instances: 20
  idle-timeout-ms: 60000
  slow-response-threshold-ms: 5000
  acquire-timeout-ms: 10000

encryption:
  key-env: MCP_ENCRYPTION_KEY
```
