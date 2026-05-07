# User Guide

## Jira REST Client — Direct API Integration (MTO-16)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-16 |
| Title | Jira REST Client — User Guide |
| Author | DEV Agent (SM-generated) |
| Version | 1.0 |
| Date | 2025-07-15 |
| Status | Final |
| Related BRD | BRD-v1-MTO-16.docx |
| Related FSD | FSD-v1-MTO-16.docx |
| Related TDD | TDD-v1-MTO-16.docx |

---

## Table of Contents

1. [Overview](#1-overview)
2. [Quick Start](#2-quick-start)
3. [Configuration Reference](#3-configuration-reference)
4. [API Reference](#4-api-reference)
5. [Error Handling](#5-error-handling)
6. [Rate Limiting & Retry](#6-rate-limiting--retry)
7. [Integration Guide](#7-integration-guide)
8. [Troubleshooting](#8-troubleshooting)
9. [FAQ](#9-faq)

---

## 1. Overview

The Jira REST Client is a Kotlin library module that provides direct, high-throughput access to Jira Cloud/Server REST API v3. It is designed for the background sync job (Epic MTO-14) and offers:

- **4 API methods**: Search issues, get issue details, list attachments, download attachments
- **Token bucket rate limiting**: Configurable requests/second with burst support
- **Exponential backoff retry**: Automatic retry on transient failures (429, 5xx)
- **SSRF protection**: Attachment download URLs validated against configured domain
- **Coroutine-first design**: All operations are non-blocking `suspend` functions
- **Typed exception hierarchy**: Exhaustive error handling via sealed classes

### Architecture

```
┌─────────────────────────────────────────────────┐
│              Sync Job / Caller                    │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│            JiraRestClient (Interface)             │
├─────────────────────────────────────────────────┤
│  searchIssues() │ getIssue() │ getAttachments() │
│                 │ downloadAttachment()           │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│           JiraRestClientImpl                     │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────┐ │
│  │ InputValid. │ │ RateLimiter  │ │  Retry   │ │
│  └─────────────┘ └──────────────┘ └──────────┘ │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│         Ktor HttpClient (CIO Engine)             │
│         Basic Auth + JSON Serialization          │
└──────────────────────┬──────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────┐
│           Jira REST API v3                       │
└─────────────────────────────────────────────────┘
```

---

## 2. Quick Start

### 2.1 Prerequisites

- JDK 21+
- Gradle 8.x
- Valid Jira Cloud/Server API credentials (email + API token)
- Network access to your Jira instance

### 2.2 Environment Setup

Set the required environment variables:

```bash
# Required
export JIRA_BASE_URL="https://your-domain.atlassian.net"
export JIRA_EMAIL="your-email@example.com"
export JIRA_API_TOKEN="your-api-token-here"

# Optional (defaults shown)
export JIRA_RATE_LIMIT=10
export JIRA_MAX_RETRIES=3
export JIRA_TIMEOUT_MS=30000
export JIRA_CONNECT_TIMEOUT_MS=10000
export JIRA_SOCKET_TIMEOUT_MS=30000
```

### 2.3 Build & Run

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application (includes Jira client module)
./gradlew :orchestrator-server:run
```

### 2.4 Minimal Usage Example

```kotlin
import com.orchestrator.mcp.jira.di.jiraModule
import org.koin.core.context.startKoin
import org.koin.core.component.inject

// 1. Start Koin with jiraModule
startKoin {
    modules(jiraModule)
}

// 2. Inject the client
val jiraClient: JiraRestClient by inject()

// 3. Use it (in a coroutine scope)
runBlocking {
    val results = jiraClient.searchIssues(
        jql = "project = MTO AND updated >= -1d",
        fields = listOf("summary", "status", "assignee"),
        maxResults = 50
    )
    println("Found ${results.total} issues")
    results.issues.forEach { issue ->
        println("  ${issue.key}: ${issue.fields?.summary}")
    }
}
```

---

## 3. Configuration Reference

### 3.1 Environment Variables

| Variable | Required | Type | Default | Description |
|----------|----------|------|---------|-------------|
| `JIRA_BASE_URL` | ✅ Yes | String | — | Jira instance base URL (e.g., `https://your-domain.atlassian.net`) |
| `JIRA_EMAIL` | ✅ Yes | String | — | Authentication email address |
| `JIRA_API_TOKEN` | ✅ Yes | String | — | Jira API token ([Generate here](https://id.atlassian.com/manage-profile/security/api-tokens)) |
| `JIRA_RATE_LIMIT` | No | Int | `10` | Maximum requests per second (1–100) |
| `JIRA_MAX_RETRIES` | No | Int | `3` | Maximum retry attempts on transient failures (0–10) |
| `JIRA_TIMEOUT_MS` | No | Long | `30000` | Overall request timeout in milliseconds |
| `JIRA_CONNECT_TIMEOUT_MS` | No | Long | `10000` | TCP connection timeout in milliseconds |
| `JIRA_SOCKET_TIMEOUT_MS` | No | Long | `30000` | Socket read/write timeout in milliseconds |

### 3.2 Validation Rules

| Variable | Rule | Error if Invalid |
|----------|------|------------------|
| `JIRA_BASE_URL` | Must not be blank; must start with `http://` or `https://` | `JiraValidationException` at startup |
| `JIRA_EMAIL` | Must not be blank | `JiraValidationException` at startup |
| `JIRA_API_TOKEN` | Must not be blank | `JiraValidationException` at startup |
| `JIRA_RATE_LIMIT` | Must be 1–100 | `IllegalArgumentException` at startup |
| `JIRA_MAX_RETRIES` | Must be 0–10 | `IllegalArgumentException` at startup |
| `JIRA_TIMEOUT_MS` | Must be > 0 | `IllegalArgumentException` at startup |
| `JIRA_CONNECT_TIMEOUT_MS` | Must be > 0 | `IllegalArgumentException` at startup |
| `JIRA_SOCKET_TIMEOUT_MS` | Must be > 0 | `IllegalArgumentException` at startup |

### 3.3 YAML Configuration (Alternative)

If using `application.yml` instead of environment variables:

```yaml
jira:
  baseUrl: ${JIRA_BASE_URL}
  email: ${JIRA_EMAIL}
  apiToken: ${JIRA_API_TOKEN}
  rateLimit:
    requestsPerSecond: ${JIRA_RATE_LIMIT:10}
  retry:
    maxAttempts: ${JIRA_MAX_RETRIES:3}
    initialDelay: 1000ms
    maxDelay: 30000ms
  timeout:
    connect: ${JIRA_CONNECT_TIMEOUT_MS:10000}
    request: ${JIRA_TIMEOUT_MS:30000}
    socket: ${JIRA_SOCKET_TIMEOUT_MS:30000}
```

### 3.4 Configuration Examples

**Minimal configuration** (production with defaults):

```bash
export JIRA_BASE_URL="https://mycompany.atlassian.net"
export JIRA_EMAIL="sync-bot@mycompany.com"
export JIRA_API_TOKEN="ATATT3xFfGF0..."
```

**High-throughput configuration** (for large sync jobs):

```bash
export JIRA_BASE_URL="https://mycompany.atlassian.net"
export JIRA_EMAIL="sync-bot@mycompany.com"
export JIRA_API_TOKEN="ATATT3xFfGF0..."
export JIRA_RATE_LIMIT=20
export JIRA_MAX_RETRIES=5
export JIRA_TIMEOUT_MS=60000
```

**Conservative configuration** (for shared Jira instances):

```bash
export JIRA_BASE_URL="https://mycompany.atlassian.net"
export JIRA_EMAIL="sync-bot@mycompany.com"
export JIRA_API_TOKEN="ATATT3xFfGF0..."
export JIRA_RATE_LIMIT=3
export JIRA_MAX_RETRIES=2
export JIRA_TIMEOUT_MS=15000
```

---

## 4. API Reference

### 4.1 `searchIssues`

Search Jira issues using JQL (Jira Query Language) with pagination support.

```kotlin
suspend fun searchIssues(
    jql: String,
    fields: List<String> = emptyList(),
    startAt: Int = 0,
    maxResults: Int = 50
): JiraSearchResponse
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `jql` | String | Yes | JQL query string (must not be blank) |
| `fields` | List\<String\> | No | Fields to return (empty = all fields) |
| `startAt` | Int | No | Pagination offset (≥ 0, default: 0) |
| `maxResults` | Int | No | Page size (1–100, default: 50) |

**Returns:** `JiraSearchResponse` containing:
- `issues: List<JiraIssue>` — matching issues for this page
- `total: Int` — total matching issues across all pages
- `startAt: Int` — current offset
- `maxResults: Int` — requested page size

**Example:**

```kotlin
// Search for recently updated issues in project MTO
val response = jiraClient.searchIssues(
    jql = "project = MTO AND updated >= -7d ORDER BY updated DESC",
    fields = listOf("summary", "status", "assignee", "updated"),
    startAt = 0,
    maxResults = 50
)

println("Total: ${response.total}, This page: ${response.issues.size}")

// Paginate through all results
var offset = 0
do {
    val page = jiraClient.searchIssues(jql = "project = MTO", startAt = offset)
    page.issues.forEach { /* process */ }
    offset += page.issues.size
} while (offset < page.total)
```

**Throws:**
- `JiraValidationException` — blank JQL, invalid maxResults/startAt
- `JiraAuthException` — invalid credentials (401) or insufficient permissions (403)
- `RetryExhaustedException` — all retries failed on transient errors

---

### 4.2 `getIssue`

Fetch a single Jira issue with full details.

```kotlin
suspend fun getIssue(
    issueKey: String,
    fields: List<String> = emptyList(),
    expand: List<String> = emptyList()
): JiraIssue
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `issueKey` | String | Yes | Issue key matching `[A-Z]+-\d+` (e.g., "MTO-16") |
| `fields` | List\<String\> | No | Fields to return (empty = all) |
| `expand` | List\<String\> | No | Sections to expand: `changelog`, `renderedFields`, `transitions`, `operations`, `editmeta` |

**Returns:** `JiraIssue` with requested fields and expanded sections.

**Example:**

```kotlin
// Get issue with changelog for sync state tracking
val issue = jiraClient.getIssue(
    issueKey = "MTO-16",
    fields = listOf("summary", "status", "description", "assignee"),
    expand = listOf("changelog")
)

println("${issue.key}: ${issue.fields?.summary}")
println("Status: ${issue.fields?.status?.name}")
```

**Throws:**
- `JiraValidationException` — invalid key format, invalid expand values
- `JiraNotFoundException` — issue does not exist (404)
- `JiraAuthException` — invalid credentials or permissions

---

### 4.3 `getAttachments`

Retrieve attachment metadata for a Jira issue.

```kotlin
suspend fun getAttachments(issueKey: String): List<JiraAttachment>
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `issueKey` | String | Yes | Issue key matching `[A-Z]+-\d+` |

**Returns:** `List<JiraAttachment>` — each containing:
- `id: String` — attachment ID
- `filename: String` — original filename
- `mimeType: String` — MIME type
- `size: Long` — file size in bytes
- `content: String` — download URL
- `author: JiraUser` — uploader info
- `created: String` — ISO 8601 timestamp

**Example:**

```kotlin
val attachments = jiraClient.getAttachments("MTO-16")
attachments.forEach { att ->
    println("${att.filename} (${att.size} bytes) — ${att.mimeType}")
}
```

**Throws:**
- `JiraValidationException` — invalid key format
- `JiraNotFoundException` — issue does not exist

---

### 4.4 `downloadAttachment`

Download attachment binary content from Jira.

```kotlin
suspend fun downloadAttachment(url: String): DownloadResult
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `url` | String | Yes | Full attachment download URL (from `JiraAttachment.content`) |

**Returns:** `DownloadResult` containing binary content.

**Security:** The URL domain is validated against `JIRA_BASE_URL` to prevent SSRF attacks. Only URLs matching the configured Jira domain are allowed.

**Example:**

```kotlin
val attachments = jiraClient.getAttachments("MTO-16")
for (att in attachments) {
    val result = jiraClient.downloadAttachment(att.content)
    // Save to file or process binary content
    File("downloads/${att.filename}").writeBytes(result.content)
}
```

**Throws:**
- `JiraValidationException` — URL domain doesn't match configured base URL (SSRF blocked)
- `JiraNotFoundException` — attachment not found (404)
- `JiraTimeoutException` — download timed out

---

## 5. Error Handling

### 5.1 Exception Hierarchy

All exceptions extend `JiraClientException` (sealed class), enabling exhaustive `when` matching:

```kotlin
try {
    val issue = jiraClient.getIssue("MTO-16")
} catch (e: JiraClientException) {
    when (e) {
        is JiraAuthException -> handleAuth(e.statusCode)
        is JiraNotFoundException -> handleNotFound(e)
        is JiraRateLimitException -> handleRateLimit(e.retryAfterSeconds)
        is JiraServerException -> handleServerError(e.statusCode)
        is JiraTimeoutException -> handleTimeout(e)
        is JiraValidationException -> handleValidation(e)
        is RetryExhaustedException -> handleRetryExhausted(e.attempts)
    }
}
```

### 5.2 Exception Reference

| Exception | HTTP Status | Retryable | Description |
|-----------|-------------|-----------|-------------|
| `JiraAuthException` | 401, 403 | ❌ No | Invalid credentials or insufficient permissions |
| `JiraNotFoundException` | 404 | ❌ No | Issue or attachment does not exist |
| `JiraRateLimitException` | 429 | ✅ Yes | Rate limit exceeded (includes `retryAfterSeconds`) |
| `JiraServerException` | 500, 502, 503 | ✅ Yes | Jira server error |
| `JiraTimeoutException` | — | ✅ Yes | Connection or request timeout |
| `JiraValidationException` | — | ❌ No | Input validation failure (blank JQL, invalid key, SSRF) |
| `RetryExhaustedException` | — | ❌ No | All retry attempts exhausted |

### 5.3 Correlation IDs

Every exception includes a `correlationId` field for log tracing:

```kotlin
catch (e: JiraClientException) {
    logger.error("Jira error [correlationId=${e.correlationId}]: ${e.message}")
}
```

---

## 6. Rate Limiting & Retry

### 6.1 Token Bucket Rate Limiter

The client uses a token bucket algorithm to respect Jira's rate limits:

- **Bucket capacity** = `JIRA_RATE_LIMIT` (default: 10 tokens)
- **Refill rate** = `JIRA_RATE_LIMIT` tokens per second
- **Behavior when empty**: Coroutine suspends (non-blocking) until a token is available
- **429 handling**: When Jira returns 429, ALL requests are paused for `Retry-After` duration

```
Time →  [T1][T2][T3]...[T10] ← burst capacity
         ↓                      
         [wait 100ms] → [T1 refilled] → next request
```

### 6.2 Exponential Backoff Retry

For transient failures (429, 5xx, timeouts):

| Attempt | Delay | With ±20% Jitter |
|---------|-------|-------------------|
| 1st retry | 1,000ms | 800–1,200ms |
| 2nd retry | 2,000ms | 1,600–2,400ms |
| 3rd retry | 4,000ms | 3,200–4,800ms |
| Max cap | 30,000ms | 24,000–36,000ms |

**Non-retryable errors** (immediate failure, no retry):
- 400 Bad Request
- 401 Unauthorized
- 403 Forbidden
- 404 Not Found
- Input validation errors

### 6.3 Retry Flow

```
Request → [Rate Limiter] → [HTTP Call] → Response
                                            │
                              ┌─────────────┼─────────────┐
                              │             │             │
                           200 OK      429/5xx/Timeout   4xx
                              │             │             │
                           Return      [Retry Handler]  Throw
                                            │
                                    ┌───────┴───────┐
                                    │               │
                              Retry < Max      Max Reached
                                    │               │
                              [Backoff Wait]   Throw RetryExhaustedException
                                    │
                              [Next Attempt]
```

---

## 7. Integration Guide

### 7.1 Koin Dependency Injection

The Jira client is registered as a Koin module. Include it in your application:

```kotlin
// In your AppModule.kt or main setup
import com.orchestrator.mcp.jira.di.jiraModule

startKoin {
    modules(
        appModule,
        jiraModule  // ← Add this
    )
}
```

### 7.2 Injecting the Client

```kotlin
// In a Koin component
class SyncJobOrchestrator : KoinComponent {
    private val jiraClient: JiraRestClient by inject()
    
    suspend fun syncProject(projectKey: String) {
        val issues = jiraClient.searchIssues(
            jql = "project = $projectKey AND updated >= -1d"
        )
        // Process issues...
    }
}
```

### 7.3 Testing with MockEngine

For unit tests, create a client with Ktor MockEngine:

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.mock.*

fun createTestClient(handler: MockRequestHandler): JiraRestClient {
    val httpClient = HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json() }
    }
    val config = JiraClientConfig(
        baseUrl = "https://test.atlassian.net",
        email = "test@example.com",
        apiToken = "test-token"
    )
    val rateLimiter = TokenBucketRateLimiter(ratePerSecond = 100, burstCapacity = 100)
    val retryHandler = ExponentialBackoffRetryHandler(maxRetries = 1)
    val responseHandler = JiraResponseHandler(rateLimiter, Json { ignoreUnknownKeys = true })
    return JiraRestClientImpl(httpClient, config, rateLimiter, retryHandler, responseHandler)
}
```

### 7.4 Package Structure

```
com.orchestrator.mcp.jira/
├── JiraRestClient.kt              # Interface
├── JiraRestClientImpl.kt          # Implementation
├── JiraInputValidator.kt          # Input validation
├── JiraResponseHandler.kt         # HTTP response mapping
├── config/
│   └── JiraClientConfig.kt        # Configuration data class
├── di/
│   └── JiraModule.kt              # Koin DI registration
├── exception/
│   └── JiraClientException.kt     # Sealed exception hierarchy
├── model/
│   ├── DownloadResult.kt          # Binary download result
│   ├── JiraAttachment.kt          # Attachment metadata DTO
│   ├── JiraIssue.kt               # Issue DTO
│   ├── JiraSearchRequest.kt       # Search request body
│   └── JiraSearchResponse.kt      # Search response DTO
├── ratelimit/
│   ├── RateLimiter.kt             # Rate limiter interface
│   └── TokenBucketRateLimiter.kt  # Token bucket implementation
└── retry/
    ├── RetryHandler.kt            # Retry handler interface
    └── ExponentialBackoffRetryHandler.kt  # Backoff implementation
```

---

## 8. Troubleshooting

### 8.1 Common Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| `JiraValidationException: Required environment variable 'JIRA_BASE_URL' is not set` | Missing env var | Set `JIRA_BASE_URL` before starting the application |
| `JiraAuthException (401)` | Invalid API token | Regenerate token at [Atlassian API Tokens](https://id.atlassian.com/manage-profile/security/api-tokens) |
| `JiraAuthException (403)` | Insufficient permissions | Ensure the API token user has access to the target project |
| `RetryExhaustedException (attempts=4)` | Jira server consistently failing | Check Jira status page; increase `JIRA_MAX_RETRIES` or `JIRA_TIMEOUT_MS` |
| `JiraRateLimitException` with long pause | Jira enforcing rate limits | Reduce `JIRA_RATE_LIMIT` to a lower value (e.g., 3–5) |
| `JiraTimeoutException` | Slow network or large response | Increase `JIRA_TIMEOUT_MS` and `JIRA_SOCKET_TIMEOUT_MS` |
| `JiraValidationException: SSRF blocked` | Download URL domain mismatch | Ensure attachment URLs come from `getAttachments()` response, not external sources |
| `IllegalArgumentException: rateLimit must be 1..100` | Invalid config value | Check `JIRA_RATE_LIMIT` is between 1 and 100 |

### 8.2 Logging

The client logs all requests with structured fields:

```
INFO  JiraRestClientImpl - [correlationId=abc123] POST /rest/api/3/search → 200 (145ms)
WARN  JiraRestClientImpl - [correlationId=abc123] GET /rest/api/3/issue/MTO-16 → 429, pausing 5s
ERROR JiraRestClientImpl - [correlationId=abc123] Retry exhausted after 4 attempts (total: 7.2s)
```

To adjust log level, configure Logback:

```xml
<!-- logback.xml -->
<logger name="com.orchestrator.mcp.jira" level="DEBUG" />
```

### 8.3 Health Check

Verify the client is working:

```kotlin
// Quick health check — search for 1 issue
try {
    jiraClient.searchIssues(jql = "project = MTO", maxResults = 1)
    println("✅ Jira connection OK")
} catch (e: JiraAuthException) {
    println("❌ Auth failed: ${e.message}")
} catch (e: JiraClientException) {
    println("❌ Connection issue: ${e.message}")
}
```

---

## 9. FAQ

**Q: Can I use this client for Jira Server (on-premise)?**
A: Yes. Set `JIRA_BASE_URL` to your server URL (e.g., `https://jira.mycompany.com`). The client uses REST API v3 which is available on Jira Server 8.x+ and Jira Data Center.

**Q: How do I generate a Jira API token?**
A: For Jira Cloud: Go to https://id.atlassian.com/manage-profile/security/api-tokens → Create API token. For Jira Server: Use a personal access token from your profile settings.

**Q: What happens if Jira is completely down?**
A: The client retries up to `JIRA_MAX_RETRIES` times with exponential backoff. After all retries are exhausted, it throws `RetryExhaustedException` with the attempt count and total elapsed time. The caller should handle this gracefully (e.g., schedule retry later).

**Q: Is the client thread-safe?**
A: Yes. The rate limiter uses atomic operations and the Ktor HttpClient is designed for concurrent coroutine access. You can safely call methods from multiple coroutines simultaneously.

**Q: How do I handle pagination for large result sets?**
A: Use the `startAt` parameter to paginate:

```kotlin
var offset = 0
val pageSize = 50
do {
    val page = jiraClient.searchIssues(jql = myJql, startAt = offset, maxResults = pageSize)
    processIssues(page.issues)
    offset += page.issues.size
} while (offset < page.total)
```

**Q: Can I disable retry completely?**
A: Yes. Set `JIRA_MAX_RETRIES=0`. All transient errors will be thrown immediately without retry.

**Q: What's the maximum attachment size supported?**
A: The client streams downloads without buffering the entire file in memory, so there's no hard limit. However, the request timeout (`JIRA_TIMEOUT_MS`) applies — increase it for very large files.

**Q: How do I monitor rate limiter behavior?**
A: Enable DEBUG logging for `com.orchestrator.mcp.jira.ratelimit`. The rate limiter logs token acquisition, pause events, and resume events.
