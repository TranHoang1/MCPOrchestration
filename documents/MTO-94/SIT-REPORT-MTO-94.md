# Manual SIT Test Execution Report — MTO-94 (Final Retest)

**Date:** 2026-05-12  
**Environment:** http://localhost:9180  
**Browser:** Playwright (Chromium 147)  
**Executed By:** QA Agent  
**Build:** Post-bugfix build (orchestrator-server) — clean-built JAR  
**Retest Scope:** BUG-001 + BUG-009 verification + regression check

---

## Summary

| Total | Passed | Failed | Blocked | Skipped |
|-------|--------|--------|---------|---------|
| 5 | 2 | 2 | 1 | 0 |

**Pass Rate:** 40% (2/5)

---

## Bug Fix Verification Results

| Bug ID | Severity | Description | Retest Result | Evidence |
|--------|----------|-------------|---------------|----------|
| BUG-001 | Critical | `POST /api/auth/login` returns 500 for existing users | ❌ **NOT FIXED** — still returns 500 | [API test](test-evidence/final-retest-BUG-001-api-test.png), [UI error](test-evidence/final-retest-BUG-001-ui-500-error.png) |
| BUG-009 | Major | Profile page "servers.forEach is not a function" | ✅ **FIXED** (UI code) — but masked by BUG-010 regression | [Profile page](test-evidence/final-retest-BUG-009-FIXED-no-servers.png) |

### New Regression Found

| Bug ID | Severity | Description | Evidence |
|--------|----------|-------------|----------|
| BUG-010 | Major | `/api/credentials/servers` returns 500 (regression of BUG-004 fix) — `AuthException` not caught properly in `UserCredentialRoutes` | Network log: GET /api/credentials/servers → 500 |

---

## Detailed Test Results

### Test 1: BUG-001 — Login API with Existing User

| Field | Value |
|-------|-------|
| **Status** | ❌ **FAIL** |
| **Priority** | Critical |
| **Bug** | BUG-001 |

**Test Steps & Results:**

| Step | Action | Expected | Actual | Status |
|------|--------|----------|--------|--------|
| 1 | POST /api/auth/login `{"username":"admin@company.com","password":"Admin123!"}` | 200 with JWT token | 500 `{"error":"INTERNAL_ERROR","message":"Internal server error"}` | ❌ |
| 2 | POST /api/auth/login `{"username":"admin","password":"Admin123!"}` (non-existent) | 401 Invalid credentials | 401 `{"error":"INVALID_CREDENTIALS","message":"Invalid username or password"}` | ✅ (correct for non-existent) |
| 3 | POST /api/auth/login `{"username":"nonexist@test.com","password":"wrong"}` | 401 Invalid credentials | 401 `{"error":"INVALID_CREDENTIALS","message":"Invalid username or password"}` | ✅ (correct for non-existent) |
| 4 | POST /api/auth/login `{"username":"admin@company.com","password":"wrongpassword"}` | 401 Invalid credentials | 500 Internal server error | ❌ |

**Root Cause Analysis:**
- The 500 occurs ONLY when `findByEmail()` finds a user in the database
- Non-existent users correctly return 401
- The error happens in `PasswordHashQuery.getHash()` — likely the `password_hash` column doesn't exist in the `users` table, or there's a type/schema mismatch
- Code path: `AuthLoginHandler.login()` → `findByEmail()` ✅ → `PasswordHashQuery.getHash()` ❌ THROWS

**Evidence:** [API test](test-evidence/final-retest-BUG-001-api-test.png), [UI error](test-evidence/final-retest-BUG-001-ui-500-error.png)

---

### Test 2: BUG-009 — Profile Page Server Credentials

| Field | Value |
|-------|-------|
| **Status** | ✅ **PASS** (UI code fix verified) |
| **Priority** | Major |
| **Bug** | BUG-009 |

**Test Steps & Results:**

| Step | Action | Expected | Actual | Status |
|------|--------|----------|--------|--------|
| 1 | Inspect profile.html `loadServers()` source code | Uses `data.servers \|\| data \|\| []` | `.then(data => renderServers(data.servers \|\| data \|\| []))` | ✅ FIXED |
| 2 | Inspect `renderServers()` null check | Handles null/empty array | `if (!servers \|\| servers.length === 0)` → shows "No servers configured." | ✅ FIXED |
| 3 | Simulate API response `{"servers":[]}` | Shows "No servers configured." | Correctly renders "No servers configured." | ✅ |
| 4 | Simulate API response with server array | forEach works on array | `Array.isArray()` = true, `forEach` available | ✅ |

**Note:** The UI fix is confirmed working. However, the actual API endpoint `/api/credentials/servers` now returns 500 (BUG-010 regression), so the fix cannot be verified end-to-end in production. The error is gracefully handled by `showError("Failed to load servers")`.

**Evidence:** [Profile with fix](test-evidence/final-retest-BUG-009-FIXED-no-servers.png)

---

### Test 3: Full Login Flow via UI

| Field | Value |
|-------|-------|
| **Status** | ❌ **FAIL** |
| **Priority** | Critical |
| **Blocked By** | BUG-001 |

**Test Steps & Results:**

| Step | Action | Expected | Actual | Status |
|------|--------|----------|--------|--------|
| 1 | Navigate to /static/login.html | Login page loads | Page loaded correctly with form | ✅ |
| 2 | Fill username: admin@company.com | Field populated | Field shows value | ✅ |
| 3 | Fill password: Admin123! | Field populated, Sign In enabled | Button enabled | ✅ |
| 4 | Click "Sign In" | Redirect to profile page | Alert: "Internal server error" | ❌ |
| 5 | Verify redirect to profile | Profile page loads | Stays on login page | ❌ |

**Evidence:** [UI 500 error](test-evidence/final-retest-BUG-001-ui-500-error.png)

---

### Test 4: Profile Page User Info (via injected token)

| Field | Value |
|-------|-------|
| **Status** | ✅ **PASS** |
| **Priority** | High |
| **Workaround** | JWT token injected into localStorage (bypassing broken login) |

**Test Steps & Results:**

| Step | Action | Expected | Actual | Status |
|------|--------|----------|--------|--------|
| 1 | Set auth_token + user_info in localStorage | Profile page accessible | Page loads without redirect | ✅ |
| 2 | Verify Name field | "Admin User" | "Admin User" | ✅ |
| 3 | Verify Email field | "admin@company.com" | "admin@company.com" | ✅ |
| 4 | Verify Role field | "system_owner" | "system_owner" | ✅ |
| 5 | Verify Bridge Token section | "No active bridge token" | Present | ✅ |
| 6 | Verify Server Credentials section | "No servers configured" or error | "Failed to load servers" (API 500) | ⚠️ |

**Note:** User info displays correctly when loaded from localStorage. The Role field correctly reads `user.role` (singular). The `decodeTokenInfo()` fallback correctly maps `payload.roles[0]` → `role`.

**Evidence:** [Profile user info](test-evidence/final-retest-profile-user-info.png)

---

### Test 5: Bridge Token Generation

| Field | Value |
|-------|-------|
| **Status** | 🚫 **BLOCKED** |
| **Priority** | High |
| **Blocked By** | BUG-001 (no valid server-side JWT available) |

**Test Steps & Results:**

| Step | Action | Expected | Actual | Status |
|------|--------|----------|--------|--------|
| 1 | Click "Generate Token" on profile page | Token generated | Redirected to login (401 — injected token not valid server-side) | 🚫 BLOCKED |

**Note:** Cannot test bridge token generation because:
1. Login is broken (BUG-001) → cannot obtain valid server-signed JWT
2. Injected client-side token fails server-side validation
3. This test requires BUG-001 to be fixed first

---

## Regression Analysis

### BUG-010 (NEW): /api/credentials/servers Returns 500

**Severity:** Major  
**Previously:** Fixed in BUG-004 (returned 200 with `{"servers":[]}`)  
**Now:** Returns 500 Internal Server Error

**Root Cause:** The `UserCredentialRoutes.route()` method calls `authMiddleware.authenticate(headers)` which throws `AuthException.InvalidTokenException` when no valid JWT is provided. The catch block in `handle()` only catches `CredentialException` specifically — `AuthException` falls through to the generic `Exception` handler which returns 500.

**Fix Required:** Either:
1. Add `catch (e: AuthException)` block in `UserCredentialRoutes.handle()` to return 401/403
2. Or ensure the endpoint works without auth for the "list servers" case (unlikely intended)

**Impact:** Profile page cannot load server credentials section, showing "Failed to load servers" error.

---

## API Endpoint Status Summary

| Endpoint | Method | Auth Required | Status | Notes |
|----------|--------|---------------|--------|-------|
| `/api/auth/login` (non-existent user) | POST | No | ✅ 401 | Correct behavior |
| `/api/auth/login` (existing user) | POST | No | ❌ 500 | BUG-001 — PasswordHashQuery fails |
| `/api/admin/sso/config` | GET | No | ✅ 200 | Returns `{"enabled":false,"configured":false}` |
| `/api/credentials/servers` | GET | Yes | ❌ 500 | BUG-010 — AuthException not caught |
| `/api/admin/credential-schemas` | GET | Yes | ❌ 500 | Same issue as BUG-010 |
| `/static/login.html` | GET | No | ✅ 200 | Page loads correctly |
| `/static/profile.html` | GET | No | ✅ 200 | Page loads (client-side auth check) |

---

## Outstanding Issues — Must Fix Before Release

### Critical — Blocks All Authenticated Flows

| Bug ID | Severity | Status | Description | Root Cause |
|--------|----------|--------|-------------|------------|
| BUG-001 | Critical | **NOT FIXED** | Login returns 500 for existing users | `PasswordHashQuery.getHash()` fails — likely missing `password_hash` column or schema mismatch in DB |

### Major — Significant Functionality Impact

| Bug ID | Severity | Status | Description | Root Cause |
|--------|----------|--------|-------------|------------|
| BUG-009 | Major | ✅ **FIXED** (UI) | "servers.forEach is not a function" | Code now uses `data.servers \|\| data \|\| []` |
| BUG-010 | Major | **NEW REGRESSION** | `/api/credentials/servers` returns 500 | `AuthException` not caught in `UserCredentialRoutes.handle()` — falls to generic 500 handler |

---

## Recommendations

### Priority 1 — BUG-001 (Critical)

**Investigation needed:**
1. Check if `password_hash` column exists in `users` table: `SELECT column_name FROM information_schema.columns WHERE table_name = 'users'`
2. If column exists, check if admin user has a hash: `SELECT id, email, password_hash FROM users WHERE email = 'admin@company.com'`
3. If column doesn't exist, run migration to add it
4. If hash is NULL, seed the admin user with a bcrypt hash of `Admin123!`

**Likely fix:** Add `password_hash` column to users table + seed admin password hash.

### Priority 2 — BUG-010 (Major Regression)

**Fix in `UserCredentialRoutes.handle()`:**
```kotlin
fun handle(exchange: HttpExchange) {
    if (handleCors(exchange)) return
    try {
        runBlocking { route(exchange) }
    } catch (e: CredentialException) {
        sendError(exchange, e.httpStatus, e.errorCode, e.message ?: "Error")
    } catch (e: AuthException.InvalidTokenException) {
        sendError(exchange, 401, "UNAUTHORIZED", "Authentication required")
    } catch (e: AuthException.InsufficientRoleException) {
        sendError(exchange, 403, "FORBIDDEN", "Insufficient permissions")
    } catch (e: AuthException) {
        sendError(exchange, 401, "UNAUTHORIZED", e.message ?: "Authentication failed")
    } catch (e: Exception) {
        logger.error("User credential route error: {}", e.message, e)
        sendError(exchange, 500, "INTERNAL_ERROR", "Internal server error")
    }
}
```

### Retest Required After Fixes

1. Full login flow: login → redirect → profile → user info → bridge token
2. `/api/credentials/servers` with valid JWT → 200 with servers list
3. Profile page server credentials section renders correctly
4. End-to-end without any token injection workarounds

---

## Test Environment Details

- **Server:** localhost:9180 (Ktor/Netty, clean-built JAR)
- **Database:** PostgreSQL (accessible — queries work for user lookup)
- **Auth:** JWT HS256 with server secret (client-generated tokens rejected)
- **Admin User:** admin-001 / admin@company.com / system_owner role (exists in DB)
- **Browser:** Chromium 147 (Playwright-controlled)
- **OS:** Windows 10/11
