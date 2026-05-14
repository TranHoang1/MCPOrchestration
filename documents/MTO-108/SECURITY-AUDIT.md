# 🔒 Security Assessment Report — Endpoint Authentication Audit

## Document Information
| Field | Value |
|-------|-------|
| Project | MCP Orchestrator (orchestrator-server + kb-server) |
| Scope | HTTP endpoint authentication coverage audit |
| Date | 2025-01-27 |
| Assessor | Security Agent |
| Ticket | MTO-108 |
| Version | 1.0 |

## Executive Summary

The MCP Orchestrator exposes **multiple HTTP endpoints without authentication protection**. The reported issue — `/sync/graph-viewer` accessible without login — is confirmed and is part of a **systemic pattern** where the Java HttpServer route registration in `HttpStreamableServer.kt` does NOT apply `AuthMiddleware` at the server level.

The architecture uses a **per-handler authentication model** where each route handler is individually responsible for calling `AuthMiddleware.authenticate()`. Several handlers (notably graph API, static files, projects API, and the MCP protocol endpoint) **do not invoke any authentication check**.

**Overall Risk Rating:** 🟠 **High**

| Severity | Count |
|----------|-------|
| 🔴 Critical | 1 |
| 🟠 High | 4 |
| 🟡 Medium | 3 |
| 🔵 Low | 2 |
| ℹ️ Informational | 2 |

---

## Endpoint Authentication Matrix

### Orchestrator Server (port 9180)

| # | Endpoint | Method | Auth Enforced | Handler | Severity |
|---|----------|--------|---------------|---------|----------|
| 1 | `/health` | GET | ❌ None | Inline lambda | ℹ️ Informational (OK — health check) |
| 2 | `/mcp` | POST | ⚠️ Partial | `HttpToolRouter` | 🔴 **Critical** |
| 3 | `/sync/graph/{projectKey}` | GET | ❌ None | `handleGraphRequest()` | 🟠 **High** |
| 4 | `/sync/graph/{projectKey}/{issueKey}` | GET | ❌ None | `handleGraphRequest()` | 🟠 **High** |
| 5 | `/sync/projects` | GET | ❌ None | `handleProjectsRequest()` | 🟠 **High** |
| 6 | `/sync/graph-viewer` | GET | ❌ None | `serveResource()` | 🟠 **High** |
| 7 | `/sync/dashboard` | GET | ❌ None | `serveResource()` | 🟡 Medium |
| 8 | `/static/*` | GET | ❌ None | `handleStaticFile()` | 🟡 Medium |
| 9 | `/login` | GET | ❌ None | `serveResource()` | ℹ️ Informational (OK — login page) |
| 10 | `/profile` | GET | ❌ None | `serveResource()` | 🟡 Medium |
| 11 | `/admin/schemas` | GET | ❌ None (HTML page) | `serveResource()` | 🔵 Low |
| 12 | `/nav-bar.js` | GET | ❌ None | `serveResource()` | ℹ️ Informational (OK — static asset) |
| 13 | `/admin/*` | ALL | ✅ `AdminAuthMiddleware` | `AdminRoutes.handle()` | ✅ Protected |
| 14 | `/api/auth/login` | POST | ❌ None (OK — public) | `AuthRouteHandler` | ✅ OK |
| 15 | `/api/auth/bridge-token` | POST | ✅ JWT required | `AuthRouteHandler` | ✅ Protected |
| 16 | `/api/auth/refresh` | POST | ✅ JWT required | `AuthRouteHandler` | ✅ Protected |
| 17 | `/api/auth/setup-status` | GET | ❌ None (OK — setup) | `AuthRouteHandler` | ✅ OK |
| 18 | `/api/auth/setup` | POST | ⚠️ Conditional | `AuthRouteHandler` | 🔵 Low |
| 19 | `/api/auth/sso/authorize` | GET | ❌ None (OK — SSO flow) | `SsoRoutes.handlePublic()` | ✅ OK |
| 20 | `/api/auth/sso/callback` | GET | ❌ None (OK — SSO flow) | `SsoRoutes.handlePublic()` | ✅ OK |
| 21 | `/api/admin/sso/config` | GET/PUT | ✅ `AdminAuthMiddleware` | `SsoRoutes.handleAdmin()` | ✅ Protected |
| 22 | `/api/credentials/*` | ALL | ✅ `AuthMiddleware` | `UserCredentialRoutes` | ✅ Protected |
| 23 | `/api/admin/credential-schemas/*` | ALL | ✅ `AuthMiddleware` + admin roles | `CredentialSchemaRoutes` | ✅ Protected |

### KB Server (separate port)

| # | Endpoint | Method | Auth Enforced | Handler | Severity |
|---|----------|--------|---------------|---------|----------|
| 1 | `/health` | GET | ❌ None | `handleHealth()` | ℹ️ Informational (OK) |
| 2 | `/mcp` | POST | ❌ None | `handleMcp()` | 🟠 **High** (if exposed) |
| 3 | `/graph/{projectKey}` | GET | ❌ None | `GraphRoutes` | 🟡 Medium |
| 4 | `/sync/graph/{projectKey}` | GET | ❌ None | `GraphRoutes` | 🟡 Medium |
| 5 | `/sync/graph-viewer` | GET | ❌ None | `serveGraphViewer()` | 🟡 Medium |
| 6 | `/static/*` | GET | ❌ None | `serveStatic()` | 🔵 Low |

---

## Detailed Findings

### Finding #1: MCP Protocol Endpoint — No Authentication Enforcement

| Attribute | Value |
|-----------|-------|
| **Severity** | 🔴 Critical |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-306: Missing Authentication for Critical Function |
| **CVSS Score** | 9.1 |
| **Location** | `HttpStreamableServer.kt:91-93` |
| **Status** | Open |

**Description:**
The `/mcp` endpoint accepts POST requests and routes them to `HttpToolRouter` which can execute **any registered tool** (find_tools, execute_dynamic_tool, toggle_tool, reset_tools, manage_auto_approve, agent_log). While `execute_dynamic_tool` attempts to extract user context from headers, it does NOT reject requests without valid authentication — it simply sets `userContext = null` and proceeds.

**Evidence:**
```kotlin
// HttpStreamableServer.kt:91-93
server.createContext("/mcp") { exchange ->
    handleMcpRequest(exchange, router)
}

// McpServerFactory.kt — createDispatcher().callTool()
val userContext = try {
    if (headers.isNotEmpty() && authMiddleware != null) {
        authMiddleware.authenticate(headers)
    } else null  // ← Auth failure is silently ignored!
} catch (e: Exception) {
    logger.debug("No user context from headers: {}", e.message)
    null  // ← Unauthenticated requests proceed!
}
```

**Impact:**
- Any unauthenticated user can invoke MCP tools including `execute_dynamic_tool` which routes to upstream servers
- Tool execution can trigger Jira sync, modify tool configurations, write agent logs
- Potential for data exfiltration via `find_tools` + `execute_dynamic_tool` chain

**Remediation:**
```kotlin
// HttpStreamableServer.kt — Add auth check before routing
server.createContext("/mcp") { exchange ->
    val headers = exchange.requestHeaders.entries.associate { (k, v) ->
        k to (v.firstOrNull() ?: "")
    }
    try {
        val authMiddleware = org.koin.java.KoinJavaComponent.getKoin()
            .get<com.orchestrator.mcp.auth.AuthMiddleware>()
        kotlinx.coroutines.runBlocking { authMiddleware.authenticate(headers) }
        handleMcpRequest(exchange, router)
    } catch (e: com.orchestrator.mcp.auth.model.AuthException) {
        val err = """{"error":"${e.errorCode}","message":"${e.message}"}"""
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(401, err.length.toLong())
        exchange.responseBody.use { it.write(err.toByteArray()) }
    }
}
```

---

### Finding #2: Graph API Exposes Jira Project Data Without Authentication

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟠 High |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-862: Missing Authorization |
| **CVSS Score** | 7.5 |
| **Location** | `HttpStreamableServer.kt:99-113` |
| **Status** | Open |

**Description:**
The `/sync/graph/{projectKey}` and `/sync/graph/{projectKey}/{issueKey}` endpoints return full Jira ticket graph data (nodes, edges, relationships) without any authentication. This exposes project structure, ticket titles, assignees, and dependency relationships.

**Evidence:**
```kotlin
// HttpStreamableServer.kt:99-113
server.createContext("/sync/graph") { exchange ->
    try {
        handleGraphRequest(exchange, graphService)  // ← No auth check!
    } catch (e: Exception) {
        // ...
    }
}
```

**Impact:**
- Unauthenticated users can enumerate all Jira projects and tickets
- Exposes organizational structure, sprint planning, and project dependencies
- Information useful for social engineering or competitive intelligence

**Remediation:**
```kotlin
server.createContext("/sync/graph") { exchange ->
    try {
        val headers = exchange.requestHeaders.entries.associate { (k, v) ->
            k to (v.firstOrNull() ?: "")
        }
        val authMiddleware = org.koin.java.KoinJavaComponent.getKoin()
            .get<com.orchestrator.mcp.auth.AuthMiddleware>()
        kotlinx.coroutines.runBlocking { authMiddleware.authenticate(headers) }
        handleGraphRequest(exchange, graphService)
    } catch (e: com.orchestrator.mcp.auth.model.AuthException) {
        sendAuthError(exchange, e)
    }
}
```

---

### Finding #3: Projects API Exposes Project List Without Authentication

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟠 High |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-862: Missing Authorization |
| **CVSS Score** | 7.5 |
| **Location** | `HttpStreamableServer.kt:117-128` |
| **Status** | Open |

**Description:**
The `/sync/projects` endpoint returns a list of all Jira projects in the system without authentication.

**Evidence:**
```kotlin
// HttpStreamableServer.kt:117-128
server.createContext("/sync/projects") { exchange ->
    try {
        handleProjectsRequest(exchange, ticketCacheRepo)  // ← No auth!
    } catch (e: Exception) { ... }
}
```

**Impact:**
- Exposes all project keys and metadata to unauthenticated users
- Enables enumeration of valid project keys for further graph API exploitation

---

### Finding #4: Graph Viewer HTML Page Accessible Without Login

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟠 High |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-306: Missing Authentication for Critical Function |
| **CVSS Score** | 7.5 |
| **Location** | `HttpStreamableServer.kt:155-157` |
| **Status** | Open |

**Description:**
The `/sync/graph-viewer` page serves an HTML application that visualizes Jira ticket graphs. Since both the HTML page AND the backing API (`/sync/graph/*`) lack authentication, any user with network access can view the full project graph.

**Evidence:**
```kotlin
// HttpStreamableServer.kt:155-157
server.createContext("/sync/graph-viewer") { exchange ->
    serveResource(exchange, "static/graph-viewer.html")  // ← No auth!
}
```

**Impact:**
- This is the exact issue reported by the user
- Full Jira project visualization accessible to anyone on the network
- Combined with unprotected `/sync/graph/*` API, provides complete data access

---

### Finding #5: Sync Dashboard Accessible Without Authentication

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟡 Medium |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-862: Missing Authorization |
| **CVSS Score** | 5.3 |
| **Location** | `HttpStreamableServer.kt:159-161` |
| **Status** | Open |

**Description:**
The `/sync/dashboard` page is served without authentication. If this dashboard displays sync status, progress, or configuration, it leaks operational information.

---

### Finding #6: Profile Page Served Without Auth (Client-Side Only Protection)

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟡 Medium |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-602: Client-Side Enforcement of Server-Side Security |
| **CVSS Score** | 4.3 |
| **Location** | `HttpStreamableServer.kt:165-167` |
| **Status** | Open |

**Description:**
The `/profile` page HTML is served without server-side authentication. While the page likely checks for a JWT token client-side before rendering, the HTML/JS source code is exposed, potentially revealing API endpoint patterns and client-side logic.

---

### Finding #7: CORS Wildcard on Data Endpoints

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟡 Medium |
| **OWASP Category** | A05:2021 — Security Misconfiguration |
| **CWE** | CWE-942: Overly Permissive Cross-domain Whitelist |
| **CVSS Score** | 5.0 |
| **Location** | `HttpStreamableServer.kt:215, 248` |
| **Status** | Open |

**Description:**
Multiple endpoints set `Access-Control-Allow-Origin: *` including data-returning endpoints (`/sync/graph/*`, `/sync/projects`). This allows any website to make cross-origin requests to these APIs.

**Evidence:**
```kotlin
// handleProjectsRequest()
exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")

// handleGraphRequest()
exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")

// serveResource()
exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
```

**Impact:**
- Any malicious website can fetch Jira project data via JavaScript
- Combined with missing auth, enables cross-site data theft

**Remediation:**
```kotlin
// Replace wildcard with specific allowed origins
val allowedOrigins = listOf(
    "http://localhost:9180",
    System.getenv("CORS_ALLOWED_ORIGIN") ?: "http://localhost:9180"
)
val origin = exchange.requestHeaders["Origin"]?.firstOrNull()
if (origin != null && origin in allowedOrigins) {
    exchange.responseHeaders.add("Access-Control-Allow-Origin", origin)
    exchange.responseHeaders.add("Vary", "Origin")
}
```

---

### Finding #8: Hardcoded JWT Secret Fallback

| Attribute | Value |
|-----------|-------|
| **Severity** | 🔵 Low |
| **OWASP Category** | A02:2021 — Cryptographic Failures |
| **CWE** | CWE-798: Use of Hard-coded Credentials |
| **CVSS Score** | 3.7 |
| **Location** | `AuthConfig.kt:30-33` |
| **Status** | Open |

**Description:**
When `JWT_SECRET` environment variable is not set, the system falls back to a hardcoded secret: `"dev-only-insecure-secret-key-change-in-production-32b"`. While logged as a warning, the server still starts and accepts tokens signed with this known secret.

**Evidence:**
```kotlin
private fun resolveSecret(): String {
    val secret = System.getenv("JWT_SECRET")
    if (secret.isNullOrBlank()) {
        logger.warn("JWT_SECRET not set — using insecure default for dev only")
        return "dev-only-insecure-secret-key-change-in-production-32b"
    }
    return secret
}
```

**Remediation:**
```kotlin
private fun resolveSecret(): String {
    val secret = System.getenv("JWT_SECRET")
    if (secret.isNullOrBlank()) {
        if (System.getenv("ENV") == "production") {
            throw IllegalStateException("JWT_SECRET must be set in production")
        }
        logger.warn("JWT_SECRET not set — using insecure default for dev only")
        return "dev-only-insecure-secret-key-change-in-production-32b"
    }
    if (secret.length < 32) {
        throw IllegalStateException("JWT_SECRET must be at least 32 characters")
    }
    return secret
}
```

---

### Finding #9: AdminAuthMiddleware JWT Validation — Manual Base64 Parsing

| Attribute | Value |
|-----------|-------|
| **Severity** | 🔵 Low |
| **OWASP Category** | A07:2021 — Identification and Authentication Failures |
| **CWE** | CWE-347: Improper Verification of Cryptographic Signature |
| **CVSS Score** | 3.1 |
| **Location** | `AdminAuthMiddleware.kt:60-69` |
| **Status** | Open |

**Description:**
`AdminAuthMiddleware.extractEmailFromJwt()` manually decodes the JWT payload using Base64 without verifying the signature. This means a forged JWT with a valid-looking email could bypass admin authentication if the email exists in the database.

**Evidence:**
```kotlin
private fun extractEmailFromJwt(token: String): String? {
    return try {
        val parts = token.split(".")
        if (parts.size < 2) return null
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))
        val emailRegex = """"email"\s*:\s*"([^"]+)"""".toRegex()
        emailRegex.find(payload)?.groupValues?.get(1)
    } catch (e: Exception) { null }
}
```

**Impact:**
- An attacker can craft a JWT with `{"email":"admin@company.com"}` without a valid signature
- The middleware extracts the email and looks up the user — if found with admin role, access is granted
- Mitigated partially by the fact that the user must exist in the database with admin role

**Remediation:**
```kotlin
// Use the shared JwtAuthService for proper signature validation
class AdminAuthMiddleware(
    private val userService: UserService,
    private val jwtAuthService: JwtAuthService,  // ← Add proper JWT validation
    private val headerName: String = "X-User-Email"
) {
    private fun extractEmailFromJwt(token: String): String? {
        return try {
            val claims = jwtAuthService.validateToken(token)  // ← Validates signature!
            claims.email
        } catch (e: Exception) {
            logger.debug("JWT validation failed: {}", e.message)
            null
        }
    }
}
```

---

### Finding #10: KB Server MCP Endpoint — No Authentication

| Attribute | Value |
|-----------|-------|
| **Severity** | 🟠 High (if network-exposed) |
| **OWASP Category** | A01:2021 — Broken Access Control |
| **CWE** | CWE-306: Missing Authentication for Critical Function |
| **CVSS Score** | 7.5 |
| **Location** | `KbHttpTransport.kt:37` |
| **Status** | Open |

**Description:**
The KB server's `/mcp` endpoint accepts tool calls without any authentication. If the KB server port is accessible beyond localhost, any user can execute KB tools (search, ingest, delete knowledge base entries).

**Evidence:**
```kotlin
// KbHttpTransport.kt:37
server.createContext("/mcp") { exchange -> handleMcp(exchange) }
// No auth check anywhere in handleMcp() or routeJsonRpc()
```

**Note:** If KB server is only bound to localhost and accessed exclusively by the orchestrator server, this is lower risk. However, the code binds to `InetSocketAddress(port)` which defaults to all interfaces (0.0.0.0).

---

## Root Cause Analysis

The fundamental issue is an **architectural gap**: the server uses Java's `HttpServer` which does not have a middleware pipeline like Ktor's `install(Authentication)` plugin. Each `createContext()` handler is independently responsible for authentication.

**Current pattern (broken):**
```
HttpServer.createContext("/path") { exchange ->
    // Handler directly processes request — NO auth gate
    handleRequest(exchange)
}
```

**Required pattern:**
```
HttpServer.createContext("/path") { exchange ->
    // Auth gate FIRST
    val userCtx = authMiddleware.authenticate(extractHeaders(exchange))
    // THEN handle request
    handleRequest(exchange, userCtx)
}
```

---

## Recommended Architecture Fix

### Option A: Centralized Auth Filter (Recommended)

Create a wrapper function that enforces auth before delegating:

```kotlin
/**
 * Wraps a handler with authentication enforcement.
 * Use for all endpoints that require login.
 */
private fun authenticatedContext(
    server: HttpServer,
    path: String,
    authMiddleware: AuthMiddleware,
    handler: (HttpExchange, UserContext) -> Unit
) {
    server.createContext(path) { exchange ->
        val headers = exchange.requestHeaders.entries.associate { (k, v) ->
            k to (v.firstOrNull() ?: "")
        }
        try {
            val userCtx = runBlocking { authMiddleware.authenticate(headers) }
            handler(exchange, userCtx)
        } catch (e: AuthException) {
            val err = """{"error":"${e.errorCode}","message":"Authentication required"}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(401, err.length.toLong())
            exchange.responseBody.use { it.write(err.toByteArray()) }
        }
    }
}

// Usage:
authenticatedContext(server, "/sync/graph", authMiddleware) { exchange, userCtx ->
    handleGraphRequest(exchange, graphService)
}

authenticatedContext(server, "/sync/projects", authMiddleware) { exchange, userCtx ->
    handleProjectsRequest(exchange, ticketCacheRepo)
}

authenticatedContext(server, "/mcp", authMiddleware) { exchange, userCtx ->
    handleMcpRequest(exchange, router)
}
```

### Option B: Static Pages — Client-Side Auth Check + API Protection

For HTML pages (`/sync/graph-viewer`, `/sync/dashboard`, `/profile`):
1. The HTML page itself can remain public (it's just a shell)
2. **BUT** the data APIs it calls MUST require authentication
3. The page's JavaScript should redirect to `/login` if no valid token exists

This is acceptable ONLY if the backing APIs are protected. Currently they are NOT.

---

## Remediation Priority

| Priority | Finding | Effort | Impact |
|----------|---------|--------|--------|
| 1 | #1: MCP endpoint no auth | Medium | Critical — full tool execution access |
| 2 | #9: AdminAuthMiddleware JWT bypass | Low | High — admin access with forged JWT |
| 3 | #2: Graph API no auth | Low | High — Jira data exposure |
| 4 | #3: Projects API no auth | Low | High — project enumeration |
| 5 | #4: Graph viewer no auth | Low | High — visual data exposure |
| 6 | #10: KB server MCP no auth | Medium | High — KB manipulation |
| 7 | #7: CORS wildcard | Low | Medium — cross-site data theft |
| 8 | #5: Sync dashboard no auth | Low | Medium — operational info leak |
| 9 | #6: Profile page no auth | Low | Low — source code exposure |
| 10 | #8: Hardcoded JWT secret | Low | Low — dev environment risk |

---

## Recommendations Summary

### Immediate Actions (Critical/High) — Sprint Priority

1. **Add `authenticatedContext()` wrapper** — Create centralized auth enforcement function
2. **Protect `/mcp` endpoint** — Require valid JWT for all MCP tool calls
3. **Protect `/sync/graph/*` endpoints** — Require authentication for graph data
4. **Protect `/sync/projects` endpoint** — Require authentication for project list
5. **Fix `AdminAuthMiddleware`** — Use `JwtAuthService.validateToken()` instead of manual Base64 parsing
6. **Restrict CORS** — Replace `*` with specific allowed origins

### Short-term Improvements (Medium)

1. **Protect `/sync/dashboard`** — Add auth or ensure it only shows non-sensitive info
2. **KB server auth** — Add authentication to KB server if exposed beyond localhost
3. **Bind KB server to localhost** — `InetSocketAddress("127.0.0.1", port)` if internal only
4. **Add security headers** — HSTS, CSP, X-Content-Type-Options to all responses

### Long-term Hardening (Low/Informational)

1. **Fail-closed JWT secret** — Refuse to start in production without `JWT_SECRET`
2. **Audit logging** — Log all authentication failures with source IP
3. **Rate limiting** — Add rate limiting to `/mcp` and `/api/auth/login`
4. **Network segmentation** — Ensure KB server is not reachable from public network

---

## Security Headers Assessment

| Header | Status | Recommendation |
|--------|--------|----------------|
| Strict-Transport-Security | ❌ Missing | Add `max-age=31536000; includeSubDomains` |
| Content-Security-Policy | ❌ Missing | Add restrictive CSP for HTML pages |
| X-Content-Type-Options | ❌ Missing | Add `nosniff` to all responses |
| X-Frame-Options | ❌ Missing | Add `DENY` or `SAMEORIGIN` |
| Referrer-Policy | ❌ Missing | Add `strict-origin-when-cross-origin` |
| Permissions-Policy | ❌ Missing | Add with restricted features |
| Cache-Control | ❌ Missing | Add `no-store` for authenticated responses |
| Server | ⚠️ Default | Suppress server version disclosure |

---

## Appendix

### A. Tools & Methodology
- Static code analysis (manual source code review)
- Route registration analysis in `HttpStreamableServer.kt`
- Handler-level authentication check verification
- Cross-reference between route handlers and `AuthMiddleware` usage

### B. Scope Limitations
- **NOT tested:** Runtime behavior, actual HTTP requests, penetration testing
- **NOT tested:** Network configuration, firewall rules, TLS setup
- **NOT tested:** Frontend JavaScript auth token handling
- **Assumption:** KB server may be internal-only (reduces Finding #10 severity)
- **Assumption:** Static HTML pages may have client-side auth redirects (does not mitigate API exposure)

### C. Files Reviewed
| File | Purpose |
|------|---------|
| `orchestrator-server/.../HttpStreamableServer.kt` | Main route registration |
| `orchestrator-server/.../auth/AuthMiddleware.kt` | JWT authentication logic |
| `orchestrator-server/.../auth/AuthConfig.kt` | Auth configuration |
| `orchestrator-server/.../auth/AuthRouteHandler.kt` | Auth API endpoints |
| `orchestrator-server/.../auth/sso/SsoRoutes.kt` | SSO endpoints |
| `orchestrator-server/.../graph/GraphRoutes.kt` | Graph API (Ktor version) |
| `orchestrator-server/.../usermanagement/routes/AdminRoutes.kt` | Admin endpoints |
| `orchestrator-server/.../usermanagement/routes/AdminAuthMiddleware.kt` | Admin auth |
| `orchestrator-server/.../credentials/UserCredentialRoutes.kt` | Credential endpoints |
| `orchestrator-server/.../credentials/CredentialSchemaRoutes.kt` | Schema endpoints |
| `orchestrator-server/.../protocol/HttpToolRouter.kt` | MCP request routing |
| `orchestrator-server/.../protocol/McpServerFactory.kt` | Tool dispatch + auth |
| `kb-server/.../transport/KbHttpTransport.kt` | KB server routes |
| `kb-server/.../graph/GraphRoutes.kt` | KB graph API |
