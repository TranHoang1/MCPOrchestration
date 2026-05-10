# Technical Design Document (TDD)

## MCPOrchestration — MTO-39: User Management & Document Approval

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-39 |
| Title | User Management & Document Approval — Role-based approval với per-user Jira credentials |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |
| Related FSD | FSD-v1-MTO-39.docx |
| Related BRD | BRD-v1-MTO-39.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-10 | SA Agent | Initial technical design |

---

## 1. Architecture Overview

### 1.1 Design Philosophy

The User Management & Document Approval module follows the existing project patterns:
- **Interface/Impl separation** — all services use interface + implementation
- **Koin DI** — module-level DI registration via dedicated `userModule`
- **Package-per-feature** — new `usermanagement` package under `com.orchestrator.mcp`
- **Repository pattern** — database access via repository interfaces
- **Existing infrastructure reuse** — leverages existing `AuditService`, `RoleContextService`, `JiraRestClient`, and `SessionManager`

### 1.2 Module Placement

```
com.orchestrator.mcp.usermanagement/
├── config/
│   └── UserManagementConfig.kt          # Config data class
├── di/
│   └── UserManagementModule.kt          # Koin module
├── model/
│   ├── UserRole.kt                      # Enum: 7 roles
│   ├── DocumentType.kt                  # Enum: 6 doc types
│   ├── ApprovalDecision.kt              # Enum: approve/reject
│   ├── User.kt                          # User domain model
│   ├── UserProject.kt                   # Project assignment model
│   ├── RolePermission.kt                # Permission matrix entry
│   ├── ApprovalLogEntry.kt              # Audit log entry
│   └── ApprovalStatus.kt               # Overall status DTO
├── repository/
│   ├── UserRepository.kt               # Interface
│   ├── UserRepositoryImpl.kt           # JDBC impl
│   ├── UserProjectRepository.kt        # Interface
│   ├── UserProjectRepositoryImpl.kt    # JDBC impl
│   ├── RolePermissionRepository.kt     # Interface
│   ├── RolePermissionRepositoryImpl.kt # JDBC impl
│   ├── ApprovalLogRepository.kt        # Interface
│   └── ApprovalLogRepositoryImpl.kt    # JDBC impl
├── service/
│   ├── UserService.kt                   # Interface — CRUD
│   ├── UserServiceImpl.kt              # Impl
│   ├── ApprovalService.kt              # Interface — approval workflow
│   ├── ApprovalServiceImpl.kt          # Impl
│   ├── PermissionService.kt            # Interface — permission validation
│   ├── PermissionServiceImpl.kt        # Impl
│   └── TokenEncryptionService.kt       # AES-256-GCM encryption
├── routes/
│   ├── AdminRoutes.kt                   # /admin/* HTTP endpoints
│   └── AdminAuthMiddleware.kt          # Role check middleware
├── tools/
│   ├── ApproveDocumentTool.kt          # MCP tool handler
│   ├── ListPendingApprovalsTool.kt     # MCP tool handler
│   └── GetApprovalStatusTool.kt        # MCP tool handler
├── migration/
│   └── UserManagementMigration.kt      # DB schema creation
└── seeder/
    └── PermissionMatrixSeeder.kt        # Default matrix seeding
```

### 1.3 Integration Points

| Component | Integration Type | Purpose |
|-----------|-----------------|---------|
| `AuditService` (MTO-34) | Internal DI | Log approval events |
| `RoleContextService` (MTO-33) | Extend | Resolve user identity from session |
| `JiraRestClient` | Internal DI | Attachment upload/delete per user |
| `SessionManager` | Extend | Store user context in HTTP session |
| `HttpStreamableServer` | Route registration | Admin API endpoints |
| `McpServerFactory` | Tool registration | MCP approval tools |

---

## 2. Data Model Design

### 2.1 Database Schema (PostgreSQL)

#### Table: `users`

```sql
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    jira_token_encrypted TEXT NOT NULL,
    role VARCHAR(20) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_by UUID REFERENCES users(id),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_active ON users(active);
```

#### Table: `user_projects`

```sql
CREATE TABLE IF NOT EXISTS user_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_key VARCHAR(20) NOT NULL,
    granted_by UUID NOT NULL REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, project_key)
);

CREATE INDEX idx_user_projects_user ON user_projects(user_id);
CREATE INDEX idx_user_projects_project ON user_projects(project_key);
```

#### Table: `role_permissions`

```sql
CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(20) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT true,
    can_approve BOOLEAN NOT NULL DEFAULT false,
    UNIQUE(role, document_type)
);

CREATE INDEX idx_role_permissions_role ON role_permissions(role);
```

#### Table: `approval_log`

```sql
CREATE TABLE IF NOT EXISTS approval_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_key VARCHAR(20) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    document_version INTEGER NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    decision VARCHAR(10) NOT NULL,
    comment TEXT,
    jira_synced BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_log_ticket ON approval_log(ticket_key, document_type);
CREATE INDEX idx_approval_log_user ON approval_log(user_id);
CREATE INDEX idx_approval_log_pending ON approval_log(jira_synced) WHERE jira_synced = false;
```

### 2.2 Domain Models

#### UserRole Enum

```kotlin
@Serializable
enum class UserRole(val displayName: String) {
    DEVELOPER("Developer"),
    BA("Business Analyst"),
    ARCHITECT("Architect"),
    QA("QA Engineer"),
    DEVOPS("DevOps Engineer"),
    LEADER("Team Lead"),
    SYSTEM_OWNER("System Owner");

    fun isAdmin(): Boolean = this == LEADER || this == SYSTEM_OWNER
}
```

#### DocumentType Enum

```kotlin
@Serializable
enum class DocumentType(val filePrefix: String) {
    BRD("BRD"),
    FSD("FSD"),
    TDD("TDD"),
    STP_STC("STP"),
    DPG("DPG"),
    UG("UG");
}
```

---

## 3. Component Design

### 3.1 TokenEncryptionService

**Purpose:** Encrypt/decrypt Jira API tokens using AES-256-GCM.

**Design:**
- Encryption key sourced from environment variable `USER_MGMT_ENCRYPTION_KEY`
- Key must be 32 bytes (256 bits), base64-encoded in env var
- Each encryption generates a random 12-byte IV (nonce)
- Stored format: `base64(iv + ciphertext + tag)`
- Thread-safe (stateless operations)

```kotlin
interface TokenEncryptionService {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}
```

**Implementation notes:**
- Uses `javax.crypto.Cipher` with `AES/GCM/NoPadding`
- GCM tag length: 128 bits
- IV: 12 bytes (GCM recommended)
- Throws `ConfigException` if encryption key not configured

### 3.2 UserService

**Purpose:** CRUD operations for user accounts.

```kotlin
interface UserService {
    suspend fun createUser(request: CreateUserRequest, adminId: UUID): User
    suspend fun getUser(id: UUID): User?
    suspend fun getUserByEmail(email: String): User?
    suspend fun listUsers(filter: UserFilter): List<User>
    suspend fun updateUser(id: UUID, request: UpdateUserRequest): User
    suspend fun deactivateUser(id: UUID, adminId: UUID): User
    suspend fun reactivateUser(id: UUID, adminId: UUID): User
    suspend fun validateJiraToken(email: String, token: String): Boolean
}
```

**Business rules enforced:**
- BR-02: Email uniqueness (active + inactive)
- BR-03: Soft delete only
- BR-04: Cannot deactivate last system_owner
- BR-05: Jira token validated before storing
- BR-06: created_by tracks admin

### 3.3 PermissionService

**Purpose:** Permission validation for approval operations.

```kotlin
interface PermissionService {
    suspend fun canApprove(userId: UUID, ticketKey: String, docType: DocumentType): PermissionResult
    suspend fun getPermissionMatrix(): List<RolePermission>
    suspend fun updatePermissions(role: UserRole, permissions: List<PermissionUpdate>): List<RolePermission>
    suspend fun getApproverRoles(docType: DocumentType): List<UserRole>
}
```

**PermissionResult sealed class:**

```kotlin
sealed class PermissionResult {
    data object Authorized : PermissionResult()
    data class Denied(val reason: String, val requiredRoles: List<UserRole> = emptyList()) : PermissionResult()
    data class AlreadyApproved(val approvedAt: Instant) : PermissionResult()
}
```

### 3.4 ApprovalService

**Purpose:** Document approval workflow orchestration.

```kotlin
interface ApprovalService {
    suspend fun approveDocument(request: ApprovalRequest): ApprovalResult
    suspend fun rejectDocument(request: ApprovalRequest): ApprovalResult
    suspend fun listPendingApprovals(userId: UUID): List<PendingApproval>
    suspend fun getApprovalStatus(ticketKey: String, docType: DocumentType): ApprovalStatus
}
```

**ApprovalResult:**

```kotlin
@Serializable
data class ApprovalResult(
    val success: Boolean,
    val message: String,
    val approvalId: String? = null,
    val jiraSynced: Boolean = false
)
```

**Approval flow (approveDocument):**
1. Validate permissions via `PermissionService.canApprove()`
2. Download current document from Jira (via user's JiraRestClient)
3. Update Sign-Off section in DOCX
4. Re-upload modified document to Jira
5. Delete old attachment
6. Log to `approval_log`
7. Log to `AuditService`

**Error handling:**
- Jira API failure → log approval locally, set `jira_synced = false`
- Permission denied → return error immediately, no side effects
- Document not found → return error

### 3.5 AdminRoutes

**Purpose:** HTTP endpoints for Admin UI at `/admin/*`.

**Endpoints:**

| Method | Path | Handler | Auth |
|--------|------|---------|------|
| GET | /admin/users | listUsers | Admin only |
| POST | /admin/users | createUser | Admin only |
| PUT | /admin/users/{id} | updateUser | Admin only |
| DELETE | /admin/users/{id} | deactivateUser | Admin only |
| GET | /admin/users/{id}/projects | listProjects | Admin only |
| POST | /admin/users/{id}/projects | assignProject | Admin only |
| DELETE | /admin/users/{id}/projects/{key} | revokeProject | Admin only |
| GET | /admin/roles | getPermissionMatrix | Admin only |
| PUT | /admin/roles/{role}/permissions | updatePermissions | Admin only |

**Auth middleware:**
- Extract user identity from request header (`X-User-Email`) or session
- Resolve user from DB → check `role.isAdmin()`
- Return 403 if not admin

### 3.6 MCP Tool Handlers

#### approve_document Tool

**Registration in McpToolRegistrar:**

```kotlin
val approveDocumentTool = ToolEntry(
    name = "approve_document",
    description = "Approve or reject a document. Validates role permissions and project assignment.",
    inputSchema = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", buildJsonObject {
            put("ticket_key", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Jira ticket key (e.g., MTO-39)"))
            })
            put("document_type", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray { DocumentType.entries.forEach { add(JsonPrimitive(it.name)) } })
            })
            put("decision", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray { add(JsonPrimitive("approve")); add(JsonPrimitive("reject")) })
            })
            put("comment", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("description", JsonPrimitive("Optional reviewer comment"))
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("ticket_key"))
            add(JsonPrimitive("document_type"))
            add(JsonPrimitive("decision"))
        })
    },
    serverName = "__builtin__"
)
```

---

## 4. Security Design

### 4.1 Token Encryption

- **Algorithm:** AES-256-GCM (authenticated encryption)
- **Key management:** Environment variable `USER_MGMT_ENCRYPTION_KEY` (base64-encoded 32 bytes)
- **IV:** Random 12 bytes per encryption (prepended to ciphertext)
- **Storage format:** `base64(iv[12] || ciphertext || tag[16])`
- **Key rotation:** Not in scope (future enhancement)

### 4.2 Admin Access Control

- All `/admin/*` endpoints require admin role (leader or system_owner)
- Auth check via `AdminAuthMiddleware` — reads user email from request header
- Non-admin requests → 403 Forbidden with JSON error body
- Rate limiting: not required for admin endpoints (internal use only)

### 4.3 Sensitive Data Handling

| Data | Protection | Logging |
|------|-----------|---------|
| Jira API tokens | AES-256-GCM at rest | NEVER logged (masked in all outputs) |
| User emails | Stored plaintext (not sensitive) | Logged in audit events |
| Encryption key | Environment variable only | NEVER logged |
| Approval decisions | Stored in audit log | Logged with full context |

### 4.4 Input Validation

| Input | Validation | Error |
|-------|-----------|-------|
| email | RFC 5322 format, max 255 chars | 400 "Invalid email format" |
| role | Must be valid UserRole enum value | 400 "Invalid role" |
| display_name | 2-100 chars, non-blank | 400 "Display name must be 2-100 characters" |
| project_key | Pattern `[A-Z][A-Z0-9_]+`, max 20 chars | 400 "Invalid project key format" |
| ticket_key | Pattern `[A-Z]+-\d+` | 400 "Invalid ticket key format" |
| document_type | Valid DocumentType enum | 400 "Invalid document type" |
| decision | "approve" or "reject" | 400 "Invalid decision" |

---

## 5. API Design

### 5.1 Admin REST API

All responses use consistent JSON format:

**Success:**
```json
{
  "data": { ... },
  "message": "User created successfully"
}
```

**Error:**
```json
{
  "error": "User with email john@company.com already exists",
  "code": "DUPLICATE_EMAIL"
}
```

### 5.2 Request/Response DTOs

#### CreateUserRequest

```kotlin
@Serializable
data class CreateUserRequest(
    val email: String,
    val jiraToken: String,
    val role: UserRole,
    val displayName: String
)
```

#### UserResponse

```kotlin
@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val role: UserRole,
    val displayName: String,
    val active: Boolean,
    val createdBy: String?,
    val createdAt: String
)
```

#### UpdateUserRequest

```kotlin
@Serializable
data class UpdateUserRequest(
    val role: UserRole? = null,
    val displayName: String? = null,
    val jiraToken: String? = null
)
```

---

## 6. Error Handling

### 6.1 Exception Hierarchy

```kotlin
sealed class UserManagementException(
    message: String,
    val errorCode: String
) : RuntimeException(message) {

    class DuplicateEmailException(email: String) :
        UserManagementException("User with email $email already exists", "DUPLICATE_EMAIL")

    class UserNotFoundException(id: String) :
        UserManagementException("User with id $id not found", "USER_NOT_FOUND")

    class InvalidRoleException(role: String) :
        UserManagementException("Invalid role: $role", "INVALID_ROLE")

    class PermissionDeniedException(reason: String) :
        UserManagementException(reason, "PERMISSION_DENIED")

    class LastAdminException :
        UserManagementException("Cannot deactivate last system_owner", "LAST_ADMIN")

    class TokenValidationException(email: String) :
        UserManagementException("Jira token validation failed for $email", "TOKEN_INVALID")

    class DuplicateApprovalException :
        UserManagementException("Already approved this document version", "DUPLICATE_APPROVAL")

    class DocumentNotFoundException(ticket: String, type: String) :
        UserManagementException("No $type attachment found on $ticket", "DOC_NOT_FOUND")
}
```

### 6.2 Error Response Mapping

| Exception | HTTP Status | MCP Error Code |
|-----------|-------------|----------------|
| DuplicateEmailException | 409 | -32602 |
| UserNotFoundException | 404 | -32602 |
| InvalidRoleException | 400 | -32602 |
| PermissionDeniedException | 403 | -32603 |
| LastAdminException | 400 | -32603 |
| TokenValidationException | 400 | -32602 |
| DuplicateApprovalException | 409 | -32603 |
| DocumentNotFoundException | 404 | -32602 |

---

## 7. Configuration

### 7.1 Config Data Class

```kotlin
@Serializable
data class UserManagementConfig(
    val enabled: Boolean = true,
    val encryptionKeyEnv: String = "USER_MGMT_ENCRYPTION_KEY",
    val jiraTokenValidation: Boolean = true,
    val maxUsersPerProject: Int = 50,
    val adminHeaderName: String = "X-User-Email"
)
```

### 7.2 application.yml Addition

```yaml
orchestrator:
  user_management:
    enabled: true
    encryption_key_env: "USER_MGMT_ENCRYPTION_KEY"
    jira_token_validation: true
    max_users_per_project: 50
    admin_header_name: "X-User-Email"
```

---

## 8. Database Migration

### 8.1 Migration Class

`UserManagementMigration.kt` — creates all 4 tables + indexes on startup.

**Strategy:** Check if tables exist before creating (idempotent).

```kotlin
class UserManagementMigration(private val dataSource: DataSource) {
    fun migrate() {
        dataSource.connection.use { conn ->
            if (!tableExists(conn, "users")) {
                createUsersTable(conn)
            }
            if (!tableExists(conn, "user_projects")) {
                createUserProjectsTable(conn)
            }
            if (!tableExists(conn, "role_permissions")) {
                createRolePermissionsTable(conn)
            }
            if (!tableExists(conn, "approval_log")) {
                createApprovalLogTable(conn)
            }
        }
    }
}
```

### 8.2 Permission Matrix Seeder

Seeds default permission matrix on first startup (when `role_permissions` is empty).

7 roles × 6 document types = 42 records.

---

## 9. Testing Strategy

### 9.1 Unit Tests

| Class | Test File | Focus |
|-------|-----------|-------|
| TokenEncryptionService | TokenEncryptionServiceTest.kt | Encrypt/decrypt roundtrip, invalid key handling |
| UserServiceImpl | UserServiceImplTest.kt | CRUD logic, validation, business rules |
| PermissionServiceImpl | PermissionServiceImplTest.kt | Permission matrix validation |
| ApprovalServiceImpl | ApprovalServiceImplTest.kt | Approval workflow, error paths |
| AdminAuthMiddleware | AdminAuthMiddlewareTest.kt | Auth check, 403 responses |

### 9.2 Integration Tests

| Test | Focus | Infrastructure |
|------|-------|---------------|
| UserRepositoryIntegrationTest | DB CRUD operations | Testcontainers PostgreSQL |
| AdminRoutesIntegrationTest | HTTP endpoint behavior | Ktor TestHost |
| ApprovalFlowIntegrationTest | End-to-end approval | Testcontainers + MockK (Jira) |

### 9.3 Test Data

- Seed test users with known roles for permission testing
- Use in-memory H2 for fast unit tests where possible
- Testcontainers PostgreSQL for integration tests

---

## 10. Implementation Checklist

### Phase 1: Foundation (Models + DB)

| # | File | Description |
|---|------|-------------|
| 1 | `model/UserRole.kt` | Role enum with isAdmin() |
| 2 | `model/DocumentType.kt` | Document type enum |
| 3 | `model/ApprovalDecision.kt` | Approve/reject enum |
| 4 | `model/User.kt` | User domain model |
| 5 | `model/UserProject.kt` | Project assignment model |
| 6 | `model/RolePermission.kt` | Permission entry model |
| 7 | `model/ApprovalLogEntry.kt` | Audit log entry |
| 8 | `model/ApprovalStatus.kt` | Status DTO |
| 9 | `config/UserManagementConfig.kt` | Config data class |
| 10 | `migration/UserManagementMigration.kt` | DB schema creation |
| 11 | `seeder/PermissionMatrixSeeder.kt` | Default matrix |

### Phase 2: Repositories

| # | File | Description |
|---|------|-------------|
| 12 | `repository/UserRepository.kt` | Interface |
| 13 | `repository/UserRepositoryImpl.kt` | JDBC implementation |
| 14 | `repository/UserProjectRepository.kt` | Interface |
| 15 | `repository/UserProjectRepositoryImpl.kt` | JDBC implementation |
| 16 | `repository/RolePermissionRepository.kt` | Interface |
| 17 | `repository/RolePermissionRepositoryImpl.kt` | JDBC implementation |
| 18 | `repository/ApprovalLogRepository.kt` | Interface |
| 19 | `repository/ApprovalLogRepositoryImpl.kt` | JDBC implementation |

### Phase 3: Services

| # | File | Description |
|---|------|-------------|
| 20 | `service/TokenEncryptionService.kt` | Encryption interface + impl |
| 21 | `service/UserService.kt` | Interface |
| 22 | `service/UserServiceImpl.kt` | CRUD implementation |
| 23 | `service/PermissionService.kt` | Interface |
| 24 | `service/PermissionServiceImpl.kt` | Permission validation |
| 25 | `service/ApprovalService.kt` | Interface |
| 26 | `service/ApprovalServiceImpl.kt` | Approval workflow |

### Phase 4: Routes + Tools

| # | File | Description |
|---|------|-------------|
| 27 | `routes/AdminAuthMiddleware.kt` | Auth check |
| 28 | `routes/AdminRoutes.kt` | HTTP endpoints |
| 29 | `tools/ApproveDocumentTool.kt` | MCP tool |
| 30 | `tools/ListPendingApprovalsTool.kt` | MCP tool |
| 31 | `tools/GetApprovalStatusTool.kt` | MCP tool |

### Phase 5: DI + Integration

| # | File | Description |
|---|------|-------------|
| 32 | `di/UserManagementModule.kt` | Koin module |
| 33 | Update `AppModule.kt` | Include userManagementModule |
| 34 | Update `HttpStreamableServer.kt` | Register /admin routes |
| 35 | Update `application.yml` | Add user_management config |

---

## 11. Performance Considerations

| Operation | Target | Approach |
|-----------|--------|----------|
| Permission check | < 200ms | In-memory cache of permission matrix (refresh on update) |
| User CRUD | < 500ms | Direct DB queries with indexes |
| Approval flow | < 10s | Jira API is the bottleneck (network I/O) |
| Token encryption | < 10ms | AES-256-GCM is fast on modern hardware |

### Caching Strategy

- Permission matrix: Cached in-memory, invalidated on update via `PermissionService`
- User lookups by email: No cache (DB indexed, < 1ms)
- Approval log queries: No cache (always fresh data needed)

---

## 12. Deployment Notes

### Environment Variables Required

| Variable | Description | Example |
|----------|-------------|---------|
| `USER_MGMT_ENCRYPTION_KEY` | Base64-encoded 32-byte AES key | `dGhpcyBpcyBhIDMyIGJ5dGUga2V5IGZvciBhZXM=` |

### Database Migration

- Automatic on startup (idempotent)
- No manual migration steps required
- Permission matrix seeded automatically if empty

### Backward Compatibility

- Existing sessions without user identity → fallback to shared account (developer role)
- Existing MCP tools unaffected
- New `/admin/*` routes added alongside existing `/mcp`, `/health`, `/sync/*` routes
</content>
</invoke>