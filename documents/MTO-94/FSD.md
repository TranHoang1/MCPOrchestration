# Functional Specification Document (FSD)

## MCP Orchestrator — MTO-94: Per-User Credentials + Scalable Process Pool

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-94 |
| Title | Per-User Credentials + Scalable Process Pool for MCP Orchestrator |
| Author | TA Agent (Technical Architect) |
| Version | 1.0 |
| Date | 2026-07-06 |
| Status | Draft |
| Related BRD | documents/MTO-94/BRD.md |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-07-06 | TA Agent | Full FSD creation — technical specification from BRD MTO-94 |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional and technical design for implementing per-user credential management and a scalable process pool in the MCP Orchestrator system. It provides complete API contracts, database schemas, integration specifications, and pseudocode sufficient for developers to implement without additional clarification.

[Implements: MTO-94 Epic]

### 1.2 Scope

**In Scope:**
- JWT-based authentication replacing `X-User-Email` header (MTO-95)
- Admin-configurable credential schemas per upstream MCP server (MTO-96)
- Per-user encrypted credential storage and management (MTO-97)
- Runtime credential placeholder resolution (MTO-98)
- Scalable process pool with dynamic scaling (MTO-99)
- Bridge client `--token` CLI argument and Authorization header (MTO-100)
- OAuth2/OIDC SSO integration (MTO-101)

**Out of Scope:**
- Credential rotation automation
- Multi-factor authentication (MFA)
- Rate limiting per user
- User self-registration
- Mobile client support

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| JWT | JSON Web Token — compact, URL-safe token for transmitting claims |
| Bridge Token | Long-lived JWT (configurable up to 365 days) for MCP Bridge client authentication |
| Session Token | Short-lived JWT (1-4 hours) for Admin Portal web sessions |
| Credential Schema | Admin-defined field set describing required credentials per upstream server |
| Pool Key | SHA-256 hash of `serverName + resolvedCredentials` for process sharing |
| Placeholder | `{field_key}` pattern in server config resolved with user credentials at runtime |
| OIDC | OpenID Connect — identity layer on OAuth 2.0 |
| JIT Provisioning | Just-In-Time user creation on first SSO login |
| IdP | Identity Provider (Azure AD, Okta, Keycloak, etc.) |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD | documents/MTO-94/BRD.md |
| Project Structure | .analysis/code-intelligence/project-structure.md |
| Orchestrator Server Module | .analysis/code-intelligence/modules/orchestrator-server.md |
| Orchestrator Client Module | .analysis/code-intelligence/modules/orchestrator-client.md |
| Orchestrator Bridge Module | .analysis/code-intelligence/modules/orchestrator-bridge.md |
| Existing UserService | orchestrator-server/src/.../usermanagement/service/UserService.kt |
| Existing TokenEncryptionService | orchestrator-server/src/.../usermanagement/service/TokenEncryptionService.kt |
| Existing UpstreamServerManager | orchestrator-client/src/.../upstream/UpstreamServerManager.kt |
| Existing AdminAuthMiddleware | orchestrator-server/src/.../usermanagement/routes/AdminAuthMiddleware.kt |

---

## 2. System Overview

### 2.1 System Context Diagram

![System Context Diagram](diagrams/system-context-per-user-credentials.png)
*[Edit in draw.io](diagrams/system-context-per-user-credentials.drawio)*

### 2.2 System Architecture

The system extends the existing MCP Orchestrator with three new cross-cutting concerns:

**1. Authentication Layer** (orchestrator-server)
- `JwtAuthService` — JWT creation, validation, claims extraction
- `AuthMiddleware` — Ktor route interceptor replacing `AdminAuthMiddleware`
- `SsoService` — OAuth2/OIDC integration with external IdPs

**2. Credential Management Layer** (orchestrator-server)
- `CredentialSchemaService` — Admin CRUD for credential field definitions
- `UserCredentialService` — User CRUD for encrypted credential values
- `CredentialResolver` — Runtime placeholder resolution

**3. Process Pool Layer** (orchestrator-client)
- `ProcessPoolManager` — Wraps `UpstreamServerManager` with pooling
- `PooledConnection` — Connection wrapper with usage tracking
- `PoolScaler` — Auto-scale logic (up on slow response, down on idle)

**Module Placement:**

| Component | Module | Package |
|-----------|--------|---------|
| JwtAuthService | orchestrator-server | `com.orchestrator.mcp.auth` |
| AuthMiddleware | orchestrator-server | `com.orchestrator.mcp.auth` |
| SsoService | orchestrator-server | `com.orchestrator.mcp.auth.sso` |
| CredentialSchemaService | orchestrator-server | `com.orchestrator.mcp.credentials` |
| CredentialSchemaRepository | orchestrator-server | `com.orchestrator.mcp.credentials` |
| UserCredentialService | orchestrator-server | `com.orchestrator.mcp.credentials` |
| UserCredentialRepository | orchestrator-server | `com.orchestrator.mcp.credentials` |
| CredentialResolver | orchestrator-server | `com.orchestrator.mcp.credentials` |
| ProcessPoolManager | orchestrator-client | `com.orchestrator.mcp.client.pool` |
| PooledConnection | orchestrator-client | `com.orchestrator.mcp.client.pool` |
| PoolScaler | orchestrator-client | `com.orchestrator.mcp.client.pool` |
| BridgeConfig (token) | orchestrator-bridge | `com.orchestrator.mcp.bridge` |

---

## 3. Functional Requirements

### 3.1 Feature: JWT Authentication & Login API

**Source:** MTO-95 — JWT Auth Middleware + Login API + Bridge Token Header
[Implements: Story #1, Story #2]

#### 3.1.1 Description

Replace the existing `X-User-Email` header-based authentication with JWT-based authentication. Provide login endpoint for username/password authentication and bridge token generation for long-lived IDE client authentication.

#### 3.1.2 Use Cases

**Use Case ID:** UC-001
**Actor:** User (Admin Portal)
**Preconditions:** User account exists in `users` table with active status and bcrypt-hashed password
**Postconditions:** User receives a valid JWT session token

**Main Flow — Login:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User submits username + password | | POST /api/auth/login |
| 2 | | Validate username exists | Query users table |
| 3 | | Verify bcrypt hash | Compare password against stored hash |
| 4 | | Generate JWT session token | Include user_id, email, roles, exp claims |
| 5 | | Return token + user info | HTTP 200 with token |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | User has auth_mode=sso | Return HTTP 400 with redirect URL to IdP |
| AF-2 | Account locked (>5 failed attempts) | Return HTTP 423 with lockout duration |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Invalid username | Return HTTP 401 INVALID_CREDENTIALS (same message as wrong password) |
| EF-2 | Invalid password | Return HTTP 401 INVALID_CREDENTIALS |
| EF-3 | Account disabled | Return HTTP 403 ACCOUNT_DISABLED |
| EF-4 | Database unavailable | Return HTTP 503 SERVICE_UNAVAILABLE |

---

**Use Case ID:** UC-002
**Actor:** Authenticated User
**Preconditions:** User has valid session token (logged in)
**Postconditions:** User receives a long-lived bridge token

**Main Flow — Generate Bridge Token:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User requests bridge token generation | | POST /api/auth/bridge-token |
| 2 | | Validate session JWT | Extract user_id from token |
| 3 | | Generate bridge token | Long-lived JWT (configurable up to 365 days) |
| 4 | | Invalidate previous bridge token | Mark old token as revoked |
| 5 | | Return bridge token | HTTP 200 with token + expiry |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | Custom expiry requested | Validate within 1-365 days range, use requested value |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Session token expired | Return HTTP 401 TOKEN_EXPIRED |
| EF-2 | User account deactivated since login | Return HTTP 403 ACCOUNT_DISABLED |

---

**Use Case ID:** UC-003
**Actor:** System (AuthMiddleware)
**Preconditions:** HTTP request arrives at protected endpoint
**Postconditions:** Request proceeds with authenticated user context OR is rejected

**Main Flow — JWT Validation:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Extract Authorization header | `Bearer <token>` |
| 2 | | Verify JWT signature | HS256 or RS256 based on config |
| 3 | | Check token expiry | Reject if expired |
| 4 | | Extract claims (user_id, email, roles) | Build UserContext |
| 5 | | Attach UserContext to request | Available to downstream handlers |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | No Authorization header, X-User-Email present | Use deprecated header path, log warning |
| AF-2 | No auth headers at all, endpoint is public | Allow through (health, login endpoints) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Malformed JWT | Return HTTP 401 INVALID_TOKEN |
| EF-2 | Expired JWT | Return HTTP 401 TOKEN_EXPIRED |
| EF-3 | Signature verification failed | Return HTTP 401 INVALID_TOKEN |
| EF-4 | User not found for token subject | Return HTTP 401 INVALID_TOKEN |

#### 3.1.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-001 | Session token expires in 1-4 hours (configurable) | MTO-95 AC |
| BR-002 | Bridge token expires in configurable period (max 365 days) | MTO-95 AC |
| BR-003 | JWT must contain: sub (user_id), email, roles, iat, exp | MTO-95 Validation |
| BR-004 | Generating new bridge token invalidates previous one | MTO-95 Req #4 |
| BR-005 | Failed login attempts logged to audit trail | MTO-95 AC #8 |
| BR-006 | X-User-Email header deprecated but still functional | MTO-95 AC #7 |
| BR-007 | JWT signing algorithm configurable: HS256 (default) or RS256 | MTO-95 AC #6 |

#### 3.1.4 Data Specifications

**Input Data — Login:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| username | String | Yes | Non-empty, alphanumeric + dots/underscores, max 100 | Login identifier |
| password | String | Yes | Non-empty, min 8 chars | User password |

**Output Data — Login Response:**

| Field | Type | Description |
|-------|------|-------------|
| token | String | JWT session token |
| expires_at | ISO-8601 | Token expiry timestamp |
| user.id | UUID | User identifier |
| user.email | String | User email |
| user.name | String | User display name |
| user.roles | String[] | User roles |

**Input Data — Bridge Token Generation:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| expiry_days | Int | No | 1-90, default from config | Token validity in days |

**Output Data — Bridge Token Response:**

| Field | Type | Description |
|-------|------|-------------|
| bridge_token | String | Long-lived JWT for bridge client |
| expires_at | ISO-8601 | Token expiry timestamp |
| token_id | UUID | Unique token identifier (for revocation) |

#### 3.1.5 UI Specifications

**Screen: Login Page**

| No. | Element | Type | Required | Behavior | Validation |
|-----|---------|------|----------|----------|------------|
| 1 | Username | Input (text) | Yes | Focus on page load | Non-empty |
| 2 | Password | Input (password) | Yes | Show/hide toggle | Non-empty |
| 3 | Login Button | Button | — | Submit form, show spinner | Disabled until both fields filled |
| 4 | SSO Login Button | Button | — | Redirect to IdP | Only shown if SSO enabled |
| 5 | Error Message | Alert | — | Show on failed login | Auto-dismiss after 5s |

**Screen: Profile — Bridge Token Section**

| No. | Element | Type | Required | Behavior | Validation |
|-----|---------|------|----------|----------|------------|
| 1 | Current Token Status | Badge | — | Shows "Active (expires: date)" or "No token" | — |
| 2 | Expiry Days | Input (number) | No | Default from config | 1-90 |
| 3 | Generate Token Button | Button | — | Generate new token, show confirmation | Warns about invalidating previous |
| 4 | Token Display | Code block | — | Shows token once after generation | Copy button, auto-hide after 60s |
| 5 | Copy Button | Button | — | Copy token to clipboard | Shows "Copied!" feedback |

#### 3.1.6 API Contract (Functional View)

<!-- TA enrichment -->

**Endpoint:** `POST /api/auth/login`
**Purpose:** Authenticate user with username/password and issue JWT session token

**Input Parameters:**

| Parameter | Location | Type | Required | Validation | Description |
|-----------|----------|------|----------|------------|-------------|
| username | body | String | Yes | `^[a-zA-Z0-9._]{1,100}$` | Login identifier |
| password | body | String | Yes | min 8 chars, non-empty | User password |

**Request Body:**
```json
{
  "username": "john.doe",
  "password": "securePassword123"
}
```

**Success Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_at": "2026-07-06T18:00:00Z",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "john.doe@company.com",
    "name": "John Doe",
    "roles": ["developer"]
  }
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 401 | INVALID_CREDENTIALS | Invalid username or password | Wrong username or password |
| 403 | ACCOUNT_DISABLED | Account is disabled. Contact administrator | user.active = false |
| 423 | ACCOUNT_LOCKED | Account locked. Try again in {minutes} minutes | >5 failed attempts in 15 min |
| 503 | SERVICE_UNAVAILABLE | Authentication service temporarily unavailable | DB connection failure |

---

**Endpoint:** `POST /api/auth/bridge-token`
**Purpose:** Generate long-lived JWT for bridge client authentication
**Auth Required:** Yes (session token)

**Input Parameters:**

| Parameter | Location | Type | Required | Validation | Description |
|-----------|----------|------|----------|------------|-------------|
| Authorization | header | String | Yes | Bearer <jwt> | Session token |
| expiry_days | body | Int | No | 1-90, default: config value | Token validity |

**Request Body:**
```json
{
  "expiry_days": 30
}
```

**Success Response (200):**
```json
{
  "bridge_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_at": "2026-08-05T14:00:00Z",
  "token_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 401 | TOKEN_EXPIRED | Session expired. Please login again | Session JWT expired |
| 401 | INVALID_TOKEN | Invalid session token | Malformed/tampered JWT |
| 403 | ACCOUNT_DISABLED | Account disabled | User deactivated after login |

---

**Endpoint:** `POST /api/auth/refresh`
**Purpose:** Refresh an expiring session token (within last 30 min of validity)
**Auth Required:** Yes (session token)

**Success Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_at": "2026-07-06T22:00:00Z"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 401 | TOKEN_EXPIRED | Token already expired. Please login again | Token past expiry |
| 400 | TOKEN_NOT_REFRESHABLE | Token not yet eligible for refresh | More than 30 min until expiry |

---

### 3.2 Feature: Credential Schema CRUD — Admin API

**Source:** MTO-96 — Credential Schema CRUD — Admin API + UI
[Implements: Story #3]

#### 3.2.1 Description

Admin-configurable credential field definitions per upstream MCP server. Admins define what credentials users need to provide (e.g., Jira needs: URL, email, API token). Schema drives the dynamic form on user's profile page.

#### 3.2.2 Use Cases

**Use Case ID:** UC-004
**Actor:** Admin
**Preconditions:** Admin is authenticated with admin role; target server exists in `mcp_servers` table
**Postconditions:** Credential schema is created/updated for the specified server

**Main Flow — Create/Update Schema:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin selects upstream server | | From dropdown populated by mcp_servers |
| 2 | Admin defines fields (key, label, type, required) | | Dynamic form builder |
| 3 | Admin clicks Save | | POST /api/admin/credential-schemas |
| 4 | | Validate field definitions | Check uniqueness, format |
| 5 | | Persist schema to database | Insert/update credential_schemas |
| 6 | | Return success | HTTP 200 with saved schema |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | Schema already exists for server | Update existing fields, add new ones |
| AF-2 | Admin reorders fields | Update display_order values |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Server not found in mcp_servers | Return HTTP 404 SERVER_NOT_FOUND |
| EF-2 | Duplicate field_key for same server | Return HTTP 409 DUPLICATE_FIELD_KEY |
| EF-3 | Invalid field_key format | Return HTTP 400 INVALID_FIELD_KEY |

---

**Use Case ID:** UC-005
**Actor:** Admin
**Preconditions:** Schema field exists; admin is authenticated
**Postconditions:** Field is removed from schema

**Main Flow — Delete Schema Field:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin clicks delete on a field | | DELETE /api/admin/credential-schemas/{server}/{fieldKey} |
| 2 | | Check if users have data for this field | Count affected users |
| 3 | | Return confirmation with affected count | If count > 0, include warning |
| 4 | Admin confirms deletion | | With `?confirm=true` parameter |
| 5 | | Remove field from schema | Delete from credential_schemas |
| 6 | | Remove field from user credentials | Clean up user_credentials JSONB |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Field is the last required field | Return HTTP 400 CANNOT_DELETE_LAST_FIELD |

#### 3.2.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-008 | field_key must be lowercase alphanumeric + underscores, max 50 chars | MTO-96 Validation |
| BR-009 | field_key must be unique per server_name | MTO-96 Validation |
| BR-010 | Supported field types: url, email, secret, text, number | MTO-96 Req #3 |
| BR-011 | At least one field required per schema | MTO-96 Validation |
| BR-012 | Schema changes do NOT invalidate existing user credentials | MTO-96 Req #6 |
| BR-013 | Removing a field with user data requires explicit confirmation | MTO-96 Req #7 |

#### 3.2.4 Data Specifications

**Input Data — Create/Update Schema:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| server_name | String | Yes | Must exist in mcp_servers | Target server |
| fields | Array | Yes | Min 1 element | Field definitions |
| fields[].field_key | String | Yes | `^[a-z][a-z0-9_]{0,49}$` | Unique field identifier |
| fields[].field_label | String | Yes | Non-empty, max 100 chars | Display label |
| fields[].field_type | Enum | Yes | url/email/secret/text/number | Input type |
| fields[].field_required | Boolean | Yes | — | Whether mandatory |
| fields[].field_description | String | No | Max 500 chars | Help text |
| fields[].field_placeholder | String | No | Max 200 chars | Input placeholder |
| fields[].display_order | Int | No | >= 0 | Sort order |

**Output Data — Schema Response:**

| Field | Type | Description |
|-------|------|-------------|
| server_name | String | Server identifier |
| fields | Array | Field definitions with IDs |
| fields[].id | UUID | Field entry ID |
| fields[].field_key | String | Field key |
| fields[].field_label | String | Display label |
| fields[].field_type | String | Input type |
| fields[].field_required | Boolean | Required flag |
| fields[].field_description | String? | Help text |
| fields[].field_placeholder | String? | Placeholder |
| fields[].display_order | Int | Sort order |
| created_at | ISO-8601 | Creation timestamp |
| updated_at | ISO-8601 | Last update timestamp |

#### 3.2.5 UI Specifications

**Screen: Admin — Credential Schema Management**

| No. | Element | Type | Required | Behavior | Validation |
|-----|---------|------|----------|----------|------------|
| 1 | Server Selector | Dropdown | Yes | Populated from mcp_servers, shows schema status | — |
| 2 | Field List | Dynamic list | — | Shows existing fields with edit/delete | Sortable via drag |
| 3 | Add Field Button | Button | — | Adds new field row to list | — |
| 4 | Field Key Input | Input (text) | Yes | Auto-slugified from label | `^[a-z][a-z0-9_]{0,49}$` |
| 5 | Field Label Input | Input (text) | Yes | Human-readable name | Non-empty, max 100 |
| 6 | Field Type Dropdown | Select | Yes | url/email/secret/text/number | — |
| 7 | Required Toggle | Checkbox | Yes | Default: true | — |
| 8 | Description Input | Input (text) | No | Help text for users | Max 500 |
| 9 | Placeholder Input | Input (text) | No | Example value | Max 200 |
| 10 | Save Schema Button | Button | — | Persist all changes | Validates all fields |
| 11 | Delete Field Button | Icon button | — | Remove field (with confirmation) | Shows affected user count |

#### 3.2.6 API Contract (Functional View)

<!-- TA enrichment -->

**Endpoint:** `GET /api/admin/credential-schemas`
**Purpose:** List all credential schemas (all servers)
**Auth Required:** Yes (admin role)

**Success Response (200):**
```json
{
  "schemas": [
    {
      "server_name": "atlassian",
      "field_count": 3,
      "users_configured": 5,
      "updated_at": "2026-07-01T10:00:00Z"
    }
  ]
}
```

---

**Endpoint:** `GET /api/admin/credential-schemas/{serverName}`
**Purpose:** Get credential schema for a specific server
**Auth Required:** Yes (admin role)

**Success Response (200):**
```json
{
  "server_name": "atlassian",
  "fields": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "field_key": "jira_url",
      "field_label": "Jira Instance URL",
      "field_type": "url",
      "field_required": true,
      "field_description": "Your Jira Cloud or Server URL",
      "field_placeholder": "https://your-domain.atlassian.net",
      "display_order": 1
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "field_key": "jira_email",
      "field_label": "Jira Email",
      "field_type": "email",
      "field_required": true,
      "field_description": "Email associated with your Atlassian account",
      "field_placeholder": "you@company.com",
      "display_order": 2
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "field_key": "jira_token",
      "field_label": "Jira API Token",
      "field_type": "secret",
      "field_required": true,
      "field_description": "Generate from Atlassian account settings → Security → API tokens",
      "field_placeholder": "ATATT3x...",
      "display_order": 3
    }
  ],
  "created_at": "2026-07-01T10:00:00Z",
  "updated_at": "2026-07-01T10:00:00Z"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 404 | SCHEMA_NOT_FOUND | No credential schema defined for server '{name}' | Server has no schema |
| 404 | SERVER_NOT_FOUND | Upstream server '{name}' not registered | Server not in mcp_servers |

---

**Endpoint:** `PUT /api/admin/credential-schemas/{serverName}`
**Purpose:** Create or update credential schema for a server
**Auth Required:** Yes (admin role)

**Request Body:**
```json
{
  "fields": [
    {
      "field_key": "jira_url",
      "field_label": "Jira Instance URL",
      "field_type": "url",
      "field_required": true,
      "field_description": "Your Jira Cloud or Server URL",
      "field_placeholder": "https://your-domain.atlassian.net",
      "display_order": 1
    },
    {
      "field_key": "jira_email",
      "field_label": "Jira Email",
      "field_type": "email",
      "field_required": true,
      "display_order": 2
    },
    {
      "field_key": "jira_token",
      "field_label": "Jira API Token",
      "field_type": "secret",
      "field_required": true,
      "field_description": "Generate from Atlassian account settings",
      "display_order": 3
    }
  ]
}
```

**Success Response (200):**
```json
{
  "server_name": "atlassian",
  "fields": [ /* ... full schema with generated IDs ... */ ],
  "created_at": "2026-07-06T14:00:00Z",
  "updated_at": "2026-07-06T14:00:00Z"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 404 | SERVER_NOT_FOUND | Upstream server '{name}' not registered | serverName not in mcp_servers |
| 409 | DUPLICATE_FIELD_KEY | Field key '{key}' already exists for server '{name}' | Duplicate key in request |
| 400 | INVALID_FIELD_KEY | Field key must be lowercase alphanumeric + underscores | Invalid format |
| 400 | EMPTY_FIELDS | At least one field is required | Empty fields array |

---

**Endpoint:** `DELETE /api/admin/credential-schemas/{serverName}/{fieldKey}`
**Purpose:** Delete a specific field from a server's credential schema
**Auth Required:** Yes (admin role)

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| confirm | Boolean | No | Set to true to confirm deletion when users have data |

**Success Response (200) — No user data:**
```json
{
  "deleted": true,
  "field_key": "jira_token",
  "server_name": "atlassian"
}
```

**Success Response (200) — Users have data, no confirm:**
```json
{
  "deleted": false,
  "warning": "FIELD_HAS_DATA",
  "affected_users": 5,
  "message": "5 users have data for this field. Add ?confirm=true to proceed."
}
```

---

### 3.3 Feature: User Credential CRUD — Profile API

**Source:** MTO-97 — User Credential CRUD — Profile API + UI
[Implements: Story #4]

#### 3.3.1 Description

Users manage their personal credentials for each upstream server via their profile page. Credentials are encrypted at rest using AES-256-GCM (existing `TokenEncryptionService`). The UI dynamically renders forms based on admin-defined schemas.

#### 3.3.2 Use Cases

**Use Case ID:** UC-006
**Actor:** Authenticated User
**Preconditions:** User is logged in; at least one server has a credential schema defined
**Postconditions:** User's credentials are encrypted and stored

**Main Flow — Save Credentials:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User navigates to Profile page | | GET /api/credentials/servers |
| 2 | | Return servers with schemas + user's completion status | List with ✅/⚠️/❌ |
| 3 | User selects a server | | GET /api/credentials/{serverName} |
| 4 | | Return schema fields + masked current values | Secret fields show ****XXXX |
| 5 | User fills/updates credential values | | Client-side validation |
| 6 | User clicks Save | | PUT /api/credentials/{serverName} |
| 7 | | Validate required fields | Check field_required flags |
| 8 | | Encrypt credential values | AES-256-GCM via TokenEncryptionService |
| 9 | | Store encrypted JSONB | Upsert user_credentials |
| 10 | | Return success with masked values | HTTP 200 |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | User updates only some fields | Merge with existing values, re-encrypt full JSONB |
| AF-2 | User clears all credentials for a server | DELETE /api/credentials/{serverName} |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Required field missing | Return HTTP 400 MISSING_REQUIRED_FIELD |
| EF-2 | URL field invalid format | Return HTTP 400 INVALID_FIELD_FORMAT |
| EF-3 | Encryption failure | Return HTTP 500 ENCRYPTION_ERROR |
| EF-4 | Payload exceeds 10KB | Return HTTP 400 PAYLOAD_TOO_LARGE |

#### 3.3.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-014 | Credentials encrypted with AES-256-GCM before storage | MTO-97 AC #3 |
| BR-015 | Secret fields masked in responses (last 4 chars visible) | MTO-97 AC #4 |
| BR-016 | Partial updates merge with existing values | MTO-97 AC #5 |
| BR-017 | Max 10KB per server per user for credential JSONB | MTO-97 Validation |
| BR-018 | URL fields validated for URL format | MTO-97 Validation |
| BR-019 | Email fields validated for email format | MTO-97 Validation |
| BR-020 | Completeness: ✅ all required filled, ⚠️ partial, ❌ none | MTO-97 AC #7 |

#### 3.3.4 Data Specifications

**Input Data — Save Credentials:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| credentials | Map<String, String> | Yes | Keys must match schema field_keys | Key-value pairs |
| credentials[field_key] | String | Per schema | Type-specific validation | Credential value |

**Output Data — Credential Status:**

| Field | Type | Description |
|-------|------|-------------|
| server_name | String | Server identifier |
| status | Enum | COMPLETE / PARTIAL / NONE |
| fields | Array | Field values (masked for secrets) |
| fields[].field_key | String | Field identifier |
| fields[].has_value | Boolean | Whether user has filled this field |
| fields[].masked_value | String? | Masked value for secrets, full for non-secrets |
| updated_at | ISO-8601? | Last update timestamp |

#### 3.3.5 UI Specifications

**Screen: Profile — Credentials Section**

| No. | Element | Type | Required | Behavior | Validation |
|-----|---------|------|----------|----------|------------|
| 1 | Server Card | Card | — | One per server with schema | Shows name + status badge |
| 2 | Status Badge | Badge | — | ✅ Complete / ⚠️ Partial / ❌ None | Color-coded |
| 3 | Expand/Collapse | Toggle | — | Show/hide credential form | Default collapsed |
| 4 | Dynamic Input Fields | Input (varies) | Per schema | type=password for secrets | Per field_type |
| 5 | Field Help Text | Tooltip/text | — | Shows field_description | Below input |
| 6 | Save Button | Button | — | Save credentials for this server | Per-server |
| 7 | Clear All Button | Button | — | Clear all credentials | Confirmation dialog |
| 8 | Last Updated | Text | — | Shows updated_at timestamp | — |

#### 3.3.6 API Contract (Functional View)

<!-- TA enrichment -->

**Endpoint:** `GET /api/credentials/servers`
**Purpose:** List all servers with credential schemas and user's completion status
**Auth Required:** Yes (any authenticated user)

**Success Response (200):**
```json
{
  "servers": [
    {
      "server_name": "atlassian",
      "server_label": "Atlassian (Jira/Confluence)",
      "status": "COMPLETE",
      "required_fields": 3,
      "filled_fields": 3,
      "updated_at": "2026-07-05T10:00:00Z"
    },
    {
      "server_name": "github",
      "server_label": "GitHub",
      "status": "NONE",
      "required_fields": 2,
      "filled_fields": 0,
      "updated_at": null
    }
  ]
}
```

---

**Endpoint:** `GET /api/credentials/{serverName}`
**Purpose:** Get credential form for a specific server (schema + masked current values)
**Auth Required:** Yes (any authenticated user)

**Success Response (200):**
```json
{
  "server_name": "atlassian",
  "status": "COMPLETE",
  "fields": [
    {
      "field_key": "jira_url",
      "field_label": "Jira Instance URL",
      "field_type": "url",
      "field_required": true,
      "field_description": "Your Jira Cloud or Server URL",
      "field_placeholder": "https://your-domain.atlassian.net",
      "has_value": true,
      "masked_value": "https://mycompany.atlassian.net"
    },
    {
      "field_key": "jira_token",
      "field_label": "Jira API Token",
      "field_type": "secret",
      "field_required": true,
      "has_value": true,
      "masked_value": "****3xFg"
    }
  ],
  "updated_at": "2026-07-05T10:00:00Z"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 404 | SCHEMA_NOT_FOUND | No credential schema for server '{name}' | Server has no schema |

---

**Endpoint:** `PUT /api/credentials/{serverName}`
**Purpose:** Save/update user's credentials for a specific server
**Auth Required:** Yes (any authenticated user)

**Request Body:**
```json
{
  "credentials": {
    "jira_url": "https://mycompany.atlassian.net",
    "jira_email": "john@company.com",
    "jira_token": "ATATT3xFgA..."
  }
}
```

**Success Response (200):**
```json
{
  "server_name": "atlassian",
  "status": "COMPLETE",
  "fields": [
    {
      "field_key": "jira_url",
      "has_value": true,
      "masked_value": "https://mycompany.atlassian.net"
    },
    {
      "field_key": "jira_email",
      "has_value": true,
      "masked_value": "john@company.com"
    },
    {
      "field_key": "jira_token",
      "has_value": true,
      "masked_value": "****gA..."
    }
  ],
  "updated_at": "2026-07-06T14:30:00Z"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 400 | MISSING_REQUIRED_FIELD | Required field '{key}' is missing | Required field not provided |
| 400 | INVALID_FIELD_FORMAT | Field '{key}' must be a valid {type} | URL/email format invalid |
| 400 | UNKNOWN_FIELD | Field '{key}' is not defined in schema | Key not in schema |
| 400 | PAYLOAD_TOO_LARGE | Credential data exceeds maximum size (10KB) | JSONB > 10KB |
| 500 | ENCRYPTION_ERROR | Failed to encrypt credentials. Contact administrator | Encryption key issue |

---

**Endpoint:** `DELETE /api/credentials/{serverName}`
**Purpose:** Clear all user's credentials for a specific server
**Auth Required:** Yes (any authenticated user)

**Success Response (200):**
```json
{
  "deleted": true,
  "server_name": "atlassian"
}
```

---

### 3.4 Feature: Credential Resolver — Placeholder Resolution

**Source:** MTO-98 — Credential Resolver — Placeholder Resolution
[Implements: Story #5]

#### 3.4.1 Description

Runtime resolution of `{placeholder}` patterns in upstream server configurations using the requesting user's decrypted credentials. This is the core mechanism that enables per-user tool execution. The resolver operates transiently — decrypted values exist only in memory for the duration of the request.

#### 3.4.2 Use Cases

**Use Case ID:** UC-007
**Actor:** System (CredentialResolver)
**Preconditions:** User is authenticated; tool execution requested; target server has placeholders in config
**Postconditions:** Server config has all placeholders resolved with user's credential values; pool key computed

**Main Flow — Resolve Credentials:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Identify target upstream server | From tool → server mapping |
| 2 | | Extract placeholders from server config | Regex `\{[a-z_]+\}` in command, args, env |
| 3 | | Fetch user's encrypted credentials | Query user_credentials for user_id + server_name |
| 4 | | Decrypt credentials | AES-256-GCM via TokenEncryptionService |
| 5 | | Resolve each placeholder | Replace `{key}` with decrypted value |
| 6 | | Compute pool key | SHA-256(serverName + sorted resolved values) |
| 7 | | Return resolved config + pool key | To ProcessPoolManager |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | Server has no placeholders | Skip resolution, use config as-is, pool key = serverName |
| AF-2 | Server has no credential schema | Backward compatible — shared process mode |
| AF-3 | Optional placeholder not filled | Leave placeholder unresolved (only for non-required fields) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Required placeholder not filled by user | Return HTTP 400 MISSING_CREDENTIAL with field list |
| EF-2 | Decryption failure | Return HTTP 500 DECRYPTION_ERROR |
| EF-3 | User has no credentials for server | Return HTTP 400 MISSING_CREDENTIAL with all required fields |

#### 3.4.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-021 | Placeholder format: `{[a-z_]+}` — lowercase + underscores only | MTO-98 Validation |
| BR-022 | Resolved values NEVER logged or persisted | MTO-98 AC #4 |
| BR-023 | Resolution is per-request — no caching of decrypted values | MTO-98 AC #5 |
| BR-024 | Servers without placeholders work unchanged (backward compatible) | MTO-98 AC #6 |
| BR-025 | Pool key = SHA-256(serverName + sorted resolved credential values) | MTO-98 AC #7 |
| BR-026 | All placeholders must have corresponding schema fields | MTO-98 Validation |

#### 3.4.4 Data Specifications

**Input Data:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| server_name | String | Yes | Must exist in mcp_servers | Target server |
| user_id | UUID | Yes | From JWT claims | Requesting user |
| server_config.command | String | Yes | May contain `{placeholders}` | Server launch command |
| server_config.args | String[] | No | May contain `{placeholders}` | Command arguments |
| server_config.env | Map<String,String> | No | May contain `{placeholders}` | Environment variables |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| resolved_command | String | Command with placeholders replaced |
| resolved_args | String[] | Args with placeholders replaced |
| resolved_env | Map<String,String> | Env vars with placeholders replaced |
| pool_key | String | SHA-256 hash for process pool lookup |
| has_credentials | Boolean | Whether user has any credentials for this server |

#### 3.4.6 API Contract (Functional View)

<!-- TA enrichment -->

> **Note:** The Credential Resolver is an internal service — not exposed as a public API endpoint. It is invoked internally by the tool execution pipeline. However, a validation endpoint is provided for users to check their credential completeness.

**Endpoint:** `GET /api/credentials/{serverName}/validate`
**Purpose:** Check if user's credentials are sufficient for a server's placeholders
**Auth Required:** Yes (any authenticated user)

**Success Response (200):**
```json
{
  "server_name": "atlassian",
  "valid": true,
  "resolved_placeholders": ["jira_url", "jira_email", "jira_token"],
  "missing_placeholders": []
}
```

**Validation Failure Response (200):**
```json
{
  "server_name": "atlassian",
  "valid": false,
  "resolved_placeholders": ["jira_url"],
  "missing_placeholders": ["jira_email", "jira_token"]
}
```

---

### 3.5 Feature: Process Pool Manager — Scalable Process Pool

**Source:** MTO-99 — Process Pool Manager — Scalable Process Pool
[Implements: Story #6]

#### 3.5.1 Description

Dynamic process pool replacing the current single-process-per-server model. Processes are keyed by `pool_key` (hash of server + resolved credentials), enabling credential-based isolation while allowing sharing between users with identical credentials. The pool auto-scales based on response time and idle timeout.

#### 3.5.2 Use Cases

**Use Case ID:** UC-008
**Actor:** System (ProcessPoolManager)
**Preconditions:** Tool execution requested; credentials resolved; pool key computed
**Postconditions:** A process instance is acquired for tool execution

**Main Flow — Acquire Process (Warm):**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Lookup pool by pool_key | ConcurrentHashMap lookup |
| 2 | | Find idle process in pool | First available with state=IDLE |
| 3 | | Mark process as BUSY | Atomic state transition |
| 4 | | Return process connection | < 100ms |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | No idle process, pool below max | Spawn new process (cold start < 5s) |
| AF-2 | Pool at max for this key, other keys have idle | Queue request, wait for release |
| AF-3 | Server has no credential schema | Use shared pool (pool_key = serverName, max 1) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Pool exhausted (all keys at max) + total at max | Queue with timeout, return 503 if timeout exceeded |
| EF-2 | Process spawn failure | Retry 3x with exponential backoff, then 503 |
| EF-3 | Process crashes during execution | Auto-restart, retry tool execution once |

---

**Use Case ID:** UC-009
**Actor:** System (PoolScaler)
**Preconditions:** Pool is running; metrics are being collected
**Postconditions:** Pool size adjusted based on demand

**Main Flow — Scale Up:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Monitor average response time per pool_key | Rolling 1-minute window |
| 2 | | Detect response time > slowResponseThresholdMs | Default 10s |
| 3 | | Check pool size < maxInstancesPerServer | And total < maxTotalInstances |
| 4 | | Spawn additional process | With same resolved credentials |
| 5 | | Add to pool | Available for next request |

**Main Flow — Scale Down:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Monitor idle time per process | Coroutine-based timer |
| 2 | | Detect idle > idleTimeoutMs | Default 5 min |
| 3 | | Gracefully terminate process | Send shutdown signal, wait 5s |
| 4 | | Remove from pool | Free resources |
| 5 | | Maintain minimum 1 process per active pool_key | Never scale to 0 while key is active |

#### 3.5.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-027 | Pool key = SHA-256(serverName + sorted resolved credentials) | MTO-99 Req #2 |
| BR-028 | Scale up when avg response > slowResponseThresholdMs AND below max | MTO-99 Req #3 |
| BR-029 | Scale down when idle > idleTimeoutMs | MTO-99 Req #4 |
| BR-030 | maxInstancesPerServer: 1-20 (default 5) | MTO-99 Validation |
| BR-031 | maxTotalInstances: 1-100 (default 50) | MTO-99 Validation |
| BR-032 | idleTimeoutMs: 30000-3600000 (default 300000) | MTO-99 Validation |
| BR-033 | slowResponseThresholdMs: 1000-60000 (default 10000) | MTO-99 Validation |
| BR-034 | Process crash → auto-restart (preserve HealthMonitor behavior) | MTO-99 Req #7 |
| BR-035 | Backward compatible: no-schema servers use shared pool (size 1) | MTO-99 AC #9 |

#### 3.5.4 Data Specifications

**Configuration Data:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| maxInstancesPerServer | Int | Yes | 1-20 | Max processes per server type |
| maxTotalInstances | Int | Yes | 1-100 | System-wide process limit |
| idleTimeoutMs | Long | Yes | 30000-3600000 | Idle process kill threshold |
| slowResponseThresholdMs | Long | Yes | 1000-60000 | Scale-up trigger threshold |

**Pool Metrics Output:**

| Field | Type | Description |
|-------|------|-------------|
| total_processes | Int | Total active processes across all pools |
| pools | Array | Per-pool metrics |
| pools[].pool_key | String | Pool identifier (truncated hash) |
| pools[].server_name | String | Server name |
| pools[].active_count | Int | Busy processes |
| pools[].idle_count | Int | Idle processes |
| pools[].avg_response_ms | Long | Average response time (1-min window) |
| pools[].total_requests | Long | Total requests served |

#### 3.5.6 API Contract (Functional View)

<!-- TA enrichment -->

**Endpoint:** `GET /api/admin/pool/metrics`
**Purpose:** Get process pool metrics for monitoring
**Auth Required:** Yes (admin role)

**Success Response (200):**
```json
{
  "total_processes": 12,
  "max_total_instances": 50,
  "utilization_percent": 24.0,
  "pools": [
    {
      "pool_key": "a1b2c3d4...",
      "server_name": "atlassian",
      "active_count": 2,
      "idle_count": 1,
      "max_instances": 5,
      "avg_response_ms": 3200,
      "total_requests": 1547,
      "last_scale_event": "2026-07-06T14:20:00Z"
    }
  ]
}
```

---

**Endpoint:** `POST /api/admin/pool/scale`
**Purpose:** Manually trigger scale up/down for a specific pool (admin override)
**Auth Required:** Yes (admin role)

**Request Body:**
```json
{
  "server_name": "atlassian",
  "pool_key": "a1b2c3d4...",
  "action": "scale_up",
  "target_count": 3
}
```

**Success Response (200):**
```json
{
  "action": "scale_up",
  "previous_count": 2,
  "new_count": 3,
  "server_name": "atlassian"
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 400 | EXCEEDS_MAX | Cannot scale beyond maxInstancesPerServer ({max}) | target > max |
| 400 | EXCEEDS_TOTAL | Cannot scale: total instances would exceed {max} | total would exceed limit |
| 404 | POOL_NOT_FOUND | Pool not found for key '{key}' | Invalid pool_key |

---

### 3.6 Feature: Bridge Client Update — Token Support

**Source:** MTO-100 — Bridge Client Update — --token + Authorization header
[Implements: Story #7]

#### 3.6.1 Description

Update the MCP Bridge client (`orchestrator-bridge`) to accept a `--token` CLI argument and include it as an `Authorization: Bearer` header in all HTTP requests to the orchestrator. This enables the orchestrator to identify which user is making requests through the bridge.

#### 3.6.2 Use Cases

**Use Case ID:** UC-010
**Actor:** User (IDE via Bridge)
**Preconditions:** User has generated a bridge token from profile page
**Postconditions:** Bridge sends authenticated requests to orchestrator

**Main Flow — Bridge Startup with Token:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User starts bridge with `--token <jwt>` | | CLI argument |
| 2 | | Validate token format | 3-part base64url JWT structure |
| 3 | | Store token in memory | Never written to disk/logs |
| 4 | | Initialize connection to orchestrator | POST /mcp with Authorization header |
| 5 | | Orchestrator validates JWT | Returns session or 401 |
| 6 | | Bridge ready for IDE connections | stdio server starts |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | Token provided via MCP_BRIDGE_TOKEN env var | Use env var (CLI takes precedence) |
| AF-2 | No token provided | Log warning, proceed without auth (backward compatible) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Invalid token format | Log error, exit with code 1 |
| EF-2 | Orchestrator returns 401 | Log "Token expired/invalid", propagate error to IDE |
| EF-3 | Orchestrator unreachable | Existing reconnection logic applies |

#### 3.6.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-036 | Token format: three base64url segments separated by dots | MTO-100 Validation |
| BR-037 | CLI `--token` takes precedence over env var MCP_BRIDGE_TOKEN | MTO-100 AC #6 |
| BR-038 | Token never logged or written to any file | MTO-100 AC #3 |
| BR-039 | Invalid format at startup → exit code 1 | MTO-100 AC #5 |
| BR-040 | Missing token in multi-user mode → warning, proceed without auth | MTO-100 AC #4 |

#### 3.6.4 Data Specifications

**Configuration:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| --token | CLI arg | No | JWT format (xxx.yyy.zzz) | Bridge token |
| MCP_BRIDGE_TOKEN | Env var | No | JWT format | Fallback token source |

#### 3.6.6 API Contract (Functional View)

<!-- TA enrichment -->

> **Note:** No new API endpoints. The bridge modifies its HTTP client to include the Authorization header in existing requests.

**Modified Request Pattern:**

```http
POST /mcp HTTP/1.1
Host: orchestrator.local:8080
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Mcp-Session-Id: session-uuid

{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {...}}
```

---

### 3.7 Feature: SSO Integration — OAuth2/OIDC

**Source:** MTO-101 — SSO Integration — OAuth2/OIDC
[Implements: Story #8]

#### 3.7.1 Description

OAuth2/OIDC integration enabling users to authenticate via corporate identity providers (Azure AD, Google Workspace, Okta, Keycloak). Supports JIT (Just-In-Time) user provisioning on first login and claims mapping to local user fields.

#### 3.7.2 Use Cases

**Use Case ID:** UC-011
**Actor:** User (SSO-enabled)
**Preconditions:** SSO is configured by admin; user has account in IdP
**Postconditions:** User is authenticated and has local user record

**Main Flow — SSO Login:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User clicks "Login with SSO" | | GET /api/auth/sso/authorize |
| 2 | | Generate state + PKCE verifier | Store in session |
| 3 | | Redirect to IdP authorization URL | With client_id, scopes, redirect_uri |
| 4 | User authenticates at IdP | | IdP login page |
| 5 | IdP redirects to callback | | GET /api/auth/sso/callback?code=xxx&state=yyy |
| 6 | | Validate state parameter | Prevent CSRF |
| 7 | | Exchange auth code for tokens | POST to IdP token endpoint |
| 8 | | Extract claims from ID token | email, name, roles |
| 9 | | Find or create local user | JIT provisioning |
| 10 | | Issue JWT session token | Same as local login |
| 11 | | Redirect to Admin Portal | With session token |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | User already exists locally | Update user fields from IdP claims |
| AF-2 | Claims mapping configured | Apply custom mapping rules |
| AF-3 | User has auth_mode=local but SSO configured | Show both login options |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | IdP unreachable | Return HTTP 503 SSO_PROVIDER_UNAVAILABLE |
| EF-2 | Invalid auth code | Return HTTP 401 SSO_AUTH_FAILED |
| EF-3 | State mismatch (CSRF) | Return HTTP 400 INVALID_STATE |
| EF-4 | User not authorized in IdP | Return HTTP 403 SSO_UNAUTHORIZED |
| EF-5 | Claims mapping failure | Log warning, use email from token as fallback |

---

**Use Case ID:** UC-012
**Actor:** Admin
**Preconditions:** Admin is authenticated with admin role
**Postconditions:** SSO provider is configured

**Main Flow — Configure SSO:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin navigates to SSO settings | | GET /api/admin/sso/config |
| 2 | Admin fills provider settings | | issuer_url, client_id, client_secret, scopes |
| 3 | Admin configures claims mapping | | Map IdP claims to local fields |
| 4 | Admin clicks Save | | PUT /api/admin/sso/config |
| 5 | | Validate issuer URL (OIDC discovery) | Fetch .well-known/openid-configuration |
| 6 | | Store encrypted config | Encrypt client_secret |
| 7 | | Return success | SSO enabled |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Invalid issuer URL | Return HTTP 400 INVALID_ISSUER |
| EF-2 | OIDC discovery fails | Return HTTP 400 DISCOVERY_FAILED |
| EF-3 | Client ID/secret invalid | Detected at first login attempt |

#### 3.7.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-041 | SSO is globally toggleable (sso_enabled config) | MTO-101 Req #1 |
| BR-042 | Auth mode per user: local or sso | MTO-101 Req #2 |
| BR-043 | JIT provisioning: first SSO login auto-creates user with default role | MTO-101 AC #5 |
| BR-044 | Bridge token generation works same regardless of auth mode | MTO-101 AC #4 |
| BR-045 | Issuer URL must be HTTPS | MTO-101 Validation |
| BR-046 | Scopes must include "openid" | MTO-101 Validation |
| BR-047 | Admin can switch user from SSO to local as fallback | MTO-101 AC #8 |

#### 3.7.6 API Contract (Functional View)

<!-- TA enrichment -->

**Endpoint:** `GET /api/auth/sso/authorize`
**Purpose:** Initiate SSO login flow — redirect to IdP
**Auth Required:** No

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| redirect_uri | String | No | Where to redirect after login (default: portal home) |

**Response:** HTTP 302 redirect to IdP authorization URL

---

**Endpoint:** `GET /api/auth/sso/callback`
**Purpose:** OAuth2 callback — exchange code for tokens, create session
**Auth Required:** No

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| code | String | Yes | Authorization code from IdP |
| state | String | Yes | CSRF state parameter |

**Success Response:** HTTP 302 redirect to portal with session cookie/token

**Error Response (401):**
```json
{
  "error": "SSO_AUTH_FAILED",
  "message": "Authentication failed. Please try again."
}
```

---

**Endpoint:** `GET /api/admin/sso/config`
**Purpose:** Get current SSO configuration
**Auth Required:** Yes (admin role)

**Success Response (200):**
```json
{
  "sso_enabled": true,
  "issuer_url": "https://login.microsoftonline.com/{tenant}/v2.0",
  "client_id": "app-client-id",
  "client_secret_configured": true,
  "scopes": "openid profile email",
  "callback_url": "https://orchestrator.local/api/auth/sso/callback",
  "claims_mapping": {
    "email": "preferred_username",
    "name": "name",
    "roles": "groups"
  },
  "default_role": "developer",
  "jit_provisioning": true
}
```

---

**Endpoint:** `PUT /api/admin/sso/config`
**Purpose:** Create or update SSO configuration
**Auth Required:** Yes (admin role)

**Request Body:**
```json
{
  "sso_enabled": true,
  "issuer_url": "https://login.microsoftonline.com/{tenant}/v2.0",
  "client_id": "app-client-id",
  "client_secret": "app-secret-value",
  "scopes": "openid profile email",
  "callback_url": "https://orchestrator.local/api/auth/sso/callback",
  "claims_mapping": {
    "email": "preferred_username",
    "name": "name",
    "roles": "groups"
  },
  "default_role": "developer",
  "jit_provisioning": true
}
```

**Business Error Scenarios:**

| HTTP | Error Code | User Message | Trigger Condition |
|------|-----------|--------------|-------------------|
| 400 | INVALID_ISSUER | Issuer URL must be a valid HTTPS URL | Non-HTTPS or invalid URL |
| 400 | DISCOVERY_FAILED | Cannot reach OIDC discovery endpoint | .well-known fetch fails |
| 400 | MISSING_OPENID_SCOPE | Scopes must include 'openid' | openid not in scopes |

---

## 4. Data Model

### 4.1 Entity Relationship Diagram

![Entity Relationship Diagram](diagrams/er-diagram-credential-management.png)
*[Edit in draw.io](diagrams/er-diagram-credential-management.drawio)*

### 4.2 Logical Entities

#### Entity: users (MODIFIED — existing table)

New columns added to existing `users` table:

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | — | User identifier (existing) |
| email | VARCHAR(255) | Yes | Unique | User email (existing) |
| name | VARCHAR(255) | Yes | — | Display name (existing) |
| role | VARCHAR(50) | Yes | — | User role (existing) |
| active | BOOLEAN | Yes | Default true | Account status (existing) |
| password_hash | VARCHAR(255) | No | Bcrypt cost 12 | **NEW** — Hashed password for local auth |
| auth_mode | VARCHAR(10) | Yes | Default 'local' | **NEW** — 'local' or 'sso' |
| failed_login_attempts | INT | Yes | Default 0, reset on success | **NEW** — Failed login counter |
| locked_until | TIMESTAMP | No | Set when attempts > 5 | **NEW** — Account lockout expiry |
| created_at | TIMESTAMP | Yes | Auto | Creation time (existing) |
| updated_at | TIMESTAMP | Yes | Auto-update | Last modification (existing) |

#### Entity: credential_schemas (NEW)

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Schema entry identifier |
| server_name | VARCHAR(100) | Yes (FK) | References mcp_servers.name | Target upstream server |
| field_key | VARCHAR(50) | Yes | Unique per server, `^[a-z][a-z0-9_]*$` | Credential field key |
| field_label | VARCHAR(100) | Yes | Non-empty | Human-readable label |
| field_type | VARCHAR(20) | Yes | Enum: url/email/secret/text/number | Input type |
| field_required | BOOLEAN | Yes | Default true | Whether mandatory |
| field_description | TEXT | No | Max 500 chars | Help text for users |
| field_placeholder | VARCHAR(200) | No | — | Input placeholder text |
| display_order | INT | Yes | Default 0 | Field display order |
| created_at | TIMESTAMP | Yes | Auto | Creation time |
| updated_at | TIMESTAMP | Yes | Auto-update | Last modification |

**Constraints:**
- UNIQUE(server_name, field_key)
- FK server_name → mcp_servers(name) ON DELETE CASCADE
- CHECK field_type IN ('url', 'email', 'secret', 'text', 'number')

#### Entity: user_credentials (NEW)

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Credential entry identifier |
| user_id | UUID | Yes (FK) | References users.id | Owning user |
| server_name | VARCHAR(100) | Yes | References mcp_servers.name | Target server |
| credentials_encrypted | TEXT | Yes | AES-256-GCM encrypted | Encrypted JSON of credential values |
| created_at | TIMESTAMP | Yes | Auto | Creation time |
| updated_at | TIMESTAMP | Yes | Auto-update | Last modification |

**Constraints:**
- UNIQUE(user_id, server_name)
- FK user_id → users(id) ON DELETE CASCADE
- FK server_name → mcp_servers(name) ON DELETE CASCADE

#### Entity: bridge_tokens (NEW)

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Token identifier |
| user_id | UUID | Yes (FK) | References users.id | Token owner |
| token_hash | VARCHAR(64) | Yes | SHA-256 of token | For revocation lookup |
| expires_at | TIMESTAMP | Yes | 1-365 days from creation | Token expiry |
| revoked | BOOLEAN | Yes | Default false | Revocation flag |
| created_at | TIMESTAMP | Yes | Auto | Creation time |

**Constraints:**
- FK user_id → users(id) ON DELETE CASCADE
- INDEX on token_hash for fast lookup
- INDEX on user_id for listing user's tokens

#### Entity: sso_config (NEW)

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Singleton row | Config identifier |
| sso_enabled | BOOLEAN | Yes | Default false | Global SSO toggle |
| issuer_url | TEXT | Conditional | Required if enabled, HTTPS | OIDC issuer URL |
| client_id | VARCHAR(255) | Conditional | Required if enabled | OAuth2 client ID |
| client_secret_encrypted | TEXT | Conditional | AES-256-GCM encrypted | OAuth2 client secret |
| scopes | VARCHAR(500) | Conditional | Must include 'openid' | Requested scopes |
| callback_url | TEXT | Conditional | Must be valid URL | OAuth2 redirect URI |
| claims_mapping | JSONB | No | Default standard mapping | IdP → local field mapping |
| default_role | VARCHAR(50) | Yes | Default 'developer' | Role for JIT-provisioned users |
| jit_provisioning | BOOLEAN | Yes | Default true | Auto-create users on first SSO login |
| updated_at | TIMESTAMP | Yes | Auto-update | Last modification |

#### Entity: audit_log (EXISTING — new event types)

New audit event types added:

| Event Type | Description |
|------------|-------------|
| AUTH_LOGIN_SUCCESS | Successful login (local or SSO) |
| AUTH_LOGIN_FAILED | Failed login attempt |
| AUTH_TOKEN_GENERATED | Bridge token generated |
| AUTH_TOKEN_REVOKED | Bridge token revoked |
| CREDENTIAL_SAVED | User saved credentials for a server |
| CREDENTIAL_CLEARED | User cleared credentials |
| SCHEMA_CREATED | Admin created credential schema |
| SCHEMA_UPDATED | Admin updated credential schema |
| SCHEMA_FIELD_DELETED | Admin deleted a schema field |
| SSO_CONFIG_UPDATED | Admin updated SSO configuration |
| POOL_SCALE_UP | Process pool scaled up |
| POOL_SCALE_DOWN | Process pool scaled down |
| POOL_PROCESS_CRASH | Process crashed and was restarted |

### 4.3 Database DDL

```sql
-- ============================================================
-- Migration: Add auth columns to existing users table
-- ============================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255),
    ADD COLUMN IF NOT EXISTS auth_mode VARCHAR(10) NOT NULL DEFAULT 'local',
    ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

-- Add check constraint for auth_mode
ALTER TABLE users
    ADD CONSTRAINT chk_users_auth_mode CHECK (auth_mode IN ('local', 'sso'));

-- ============================================================
-- Table: credential_schemas
-- ============================================================
CREATE TABLE IF NOT EXISTS credential_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_name VARCHAR(100) NOT NULL,
    field_key VARCHAR(50) NOT NULL,
    field_label VARCHAR(100) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    field_required BOOLEAN NOT NULL DEFAULT true,
    field_description TEXT,
    field_placeholder VARCHAR(200),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_credential_schemas_server
        FOREIGN KEY (server_name) REFERENCES mcp_servers(name) ON DELETE CASCADE,
    CONSTRAINT uq_credential_schemas_server_key
        UNIQUE (server_name, field_key),
    CONSTRAINT chk_credential_schemas_field_type
        CHECK (field_type IN ('url', 'email', 'secret', 'text', 'number')),
    CONSTRAINT chk_credential_schemas_field_key_format
        CHECK (field_key ~ '^[a-z][a-z0-9_]*$')
);

CREATE INDEX idx_credential_schemas_server ON credential_schemas(server_name);

-- ============================================================
-- Table: user_credentials
-- ============================================================
CREATE TABLE IF NOT EXISTS user_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    server_name VARCHAR(100) NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_user_credentials_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_credentials_server
        FOREIGN KEY (server_name) REFERENCES mcp_servers(name) ON DELETE CASCADE,
    CONSTRAINT uq_user_credentials_user_server
        UNIQUE (user_id, server_name)
);

CREATE INDEX idx_user_credentials_user ON user_credentials(user_id);
CREATE INDEX idx_user_credentials_server ON user_credentials(server_name);

-- ============================================================
-- Table: bridge_tokens
-- ============================================================
CREATE TABLE IF NOT EXISTS bridge_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_bridge_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_bridge_tokens_hash ON bridge_tokens(token_hash);
CREATE INDEX idx_bridge_tokens_user ON bridge_tokens(user_id);
CREATE INDEX idx_bridge_tokens_active ON bridge_tokens(user_id, revoked, expires_at)
    WHERE revoked = false;

-- ============================================================
-- Table: sso_config (singleton)
-- ============================================================
CREATE TABLE IF NOT EXISTS sso_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sso_enabled BOOLEAN NOT NULL DEFAULT false,
    issuer_url TEXT,
    client_id VARCHAR(255),
    client_secret_encrypted TEXT,
    scopes VARCHAR(500) DEFAULT 'openid profile email',
    callback_url TEXT,
    claims_mapping JSONB DEFAULT '{"email": "email", "name": "name", "roles": "groups"}',
    default_role VARCHAR(50) NOT NULL DEFAULT 'developer',
    jit_provisioning BOOLEAN NOT NULL DEFAULT true,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Ensure singleton (only one row)
CREATE UNIQUE INDEX idx_sso_config_singleton ON sso_config((true));

-- ============================================================
-- Trigger: auto-update updated_at
-- ============================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_credential_schemas_updated
    BEFORE UPDATE ON credential_schemas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_user_credentials_updated
    BEFORE UPDATE ON user_credentials
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

**Relationships:**

| From Entity | To Entity | Cardinality | Description |
|-------------|-----------|-------------|-------------|
| users | user_credentials | 1:N | User has credentials for multiple servers |
| users | bridge_tokens | 1:N | User can have multiple tokens (only latest active) |
| mcp_servers | credential_schemas | 1:N | Server has multiple credential fields |
| mcp_servers | user_credentials | 1:N | Server has credentials from multiple users |

---

## 5. Integration Specifications

### 5.1 External System: Identity Provider (OAuth2/OIDC)

| Attribute | Value |
|-----------|-------|
| Purpose | User authentication via corporate SSO |
| Direction | Bidirectional |
| Data Format | JSON (OAuth2/OIDC standard) |
| Frequency | On-demand (per login) |
| Protocol | HTTPS |
| Auth | OAuth2 Authorization Code + PKCE |

**Data Exchange:**

| Our Data | External Data | Direction | Business Rule |
|----------|--------------|-----------|---------------|
| client_id + redirect_uri | Authorization code | Receive | Standard OAuth2 flow |
| Authorization code + PKCE verifier | Access token + ID token | Receive | Token exchange |
| — | User claims (email, name, groups) | Receive | Claims mapping applied |

**Integration Flow:**

```mermaid
sequenceDiagram
    participant User as User Browser
    participant Orch as Orchestrator
    participant IdP as Identity Provider

    User->>Orch: GET /api/auth/sso/authorize
    Orch->>Orch: Generate state + PKCE code_verifier
    Orch->>User: 302 Redirect to IdP
    User->>IdP: Authorization request
    IdP->>User: Login page
    User->>IdP: Credentials
    IdP->>User: 302 Redirect to callback
    User->>Orch: GET /api/auth/sso/callback?code=xxx&state=yyy
    Orch->>IdP: POST /token (code + code_verifier)
    IdP->>Orch: Access token + ID token
    Orch->>Orch: Validate ID token, extract claims
    Orch->>Orch: Find/create local user (JIT)
    Orch->>Orch: Issue JWT session token
    Orch->>User: 302 Redirect to portal + set token
```

### 5.2 External System: Upstream MCP Servers (with Per-User Credentials)

| Attribute | Value |
|-----------|-------|
| Purpose | Tool execution with user-specific credentials |
| Direction | Outbound |
| Data Format | JSON-RPC 2.0 (MCP protocol) |
| Frequency | Real-time (per tool invocation) |
| Protocol | stdio (process) or HTTP Streamable |
| Auth | Per-user credentials resolved from placeholders |

**Data Exchange:**

| Our Data | External Data | Direction | Business Rule |
|----------|--------------|-----------|---------------|
| Resolved command + args + env | Process handle | Send | Spawn with user's credentials |
| JSON-RPC tool/call request | Tool execution result | Bidirectional | MCP protocol |
| — | Process health status | Receive | HealthMonitor checks |

**Integration Flow — Tool Execution with Credential Resolution:**

```mermaid
sequenceDiagram
    participant Bridge as MCP Bridge
    participant Auth as Auth Middleware
    participant Exec as Execution Dispatcher
    participant Resolver as Credential Resolver
    participant Pool as Process Pool
    participant Upstream as Upstream Server

    Bridge->>Auth: POST /mcp [Authorization: Bearer jwt]
    Auth->>Auth: Validate JWT, extract user_id
    Auth->>Exec: Forward with UserContext
    Exec->>Exec: Identify target server from tool
    Exec->>Resolver: Resolve credentials(user_id, server_name)
    Resolver->>Resolver: Fetch encrypted credentials from DB
    Resolver->>Resolver: Decrypt (AES-256-GCM)
    Resolver->>Resolver: Replace {placeholders} in config
    Resolver->>Resolver: Compute pool_key = SHA-256(server + creds)
    Resolver-->>Exec: ResolvedConfig + pool_key
    Exec->>Pool: Acquire process(pool_key, resolved_config)
    alt Warm process available
        Pool-->>Exec: Existing process
    else Cold start
        Pool->>Upstream: Spawn(resolved_command, resolved_args, resolved_env)
        Upstream-->>Pool: Process ready
        Pool-->>Exec: New process
    end
    Exec->>Upstream: tools/call (JSON-RPC)
    Upstream-->>Exec: Result
    Exec->>Pool: Release process (mark idle)
    Exec-->>Bridge: MCP response
```

### 5.3 Internal Integration: Bridge ↔ Orchestrator (JWT Auth)

| Attribute | Value |
|-----------|-------|
| Purpose | Authenticated tool execution from IDE |
| Direction | Bidirectional |
| Data Format | JSON-RPC 2.0 over HTTP |
| Frequency | Real-time (per tool invocation) |
| Protocol | HTTP Streamable |
| Auth | JWT Bearer token in Authorization header |

**Modified HTTP Request Pattern:**

```http
POST /mcp HTTP/1.1
Host: orchestrator.local:8080
Content-Type: application/json
Accept: application/json, text/event-stream
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Mcp-Session-Id: 550e8400-e29b-41d4-a716-446655440000

{
  "jsonrpc": "2.0",
  "id": 42,
  "method": "tools/call",
  "params": {
    "name": "jira_create_issue",
    "arguments": { "project": "MTO", "summary": "Test issue" }
  }
}
```

---

## 6. Processing Logic

### 6.1 Credential Resolution Process

**Trigger:** Tool execution request arrives with authenticated user context
**Input:** user_id (from JWT), server_name (from tool → server mapping), server_config (from mcp_servers)
**Output:** ResolvedConfig (command, args, env with placeholders replaced) + pool_key

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Extract placeholders from server config using regex `\{([a-z_]+)\}` | If no placeholders found, return config as-is |
| 2 | Fetch user's encrypted credentials from user_credentials table | If not found, check if any placeholders are required |
| 3 | Decrypt credentials using TokenEncryptionService | On failure: return DECRYPTION_ERROR |
| 4 | For each placeholder, lookup value in decrypted credentials map | Collect missing required fields |
| 5 | If any required placeholders missing, return error with field list | MISSING_CREDENTIAL error |
| 6 | Replace all placeholders in command, args, and env | String replacement |
| 7 | Compute pool_key = SHA-256(serverName + sorted credential values) | Deterministic hash |
| 8 | Return ResolvedConfig + pool_key | Clear decrypted values from scope |

**Pseudocode:**

```kotlin
// CredentialResolver.kt — com.orchestrator.mcp.credentials
// [Implements: MTO-98]

data class ResolvedConfig(
    val command: String,
    val args: List<String>,
    val env: Map<String, String>,
    val poolKey: String
)

suspend fun resolveCredentials(
    userId: UUID,
    serverName: String,
    serverConfig: ServerConfig
): ResolvedConfig {
    // Step 1: Extract placeholders
    val placeholderRegex = """\{([a-z_]+)\}""".toRegex()
    val allText = serverConfig.command + serverConfig.args.joinToString(" ") +
                  serverConfig.env.values.joinToString(" ")
    val requiredPlaceholders = placeholderRegex.findAll(allText)
        .map { it.groupValues[1] }
        .toSet()

    // Step 2: No placeholders → return as-is (backward compatible)
    if (requiredPlaceholders.isEmpty()) {
        return ResolvedConfig(
            command = serverConfig.command,
            args = serverConfig.args,
            env = serverConfig.env,
            poolKey = sha256(serverName)
        )
    }

    // Step 3: Fetch and decrypt user credentials
    val encrypted = userCredentialRepository.findByUserAndServer(userId, serverName)
        ?: throw MissingCredentialException(serverName, requiredPlaceholders.toList())

    val credentialsJson = try {
        encryptionService.decrypt(encrypted.credentialsEncrypted)
    } catch (e: Exception) {
        throw DecryptionException(serverName, e)
    }

    val credentials: Map<String, String> = json.decodeFromString(credentialsJson)

    // Step 4: Check for missing required fields
    val schemaFields = credentialSchemaRepository.findByServer(serverName)
    val requiredFields = schemaFields.filter { it.fieldRequired }.map { it.fieldKey }
    val missingFields = requiredFields.filter { it in requiredPlaceholders && it !in credentials }

    if (missingFields.isNotEmpty()) {
        throw MissingCredentialException(serverName, missingFields)
    }

    // Step 5: Resolve placeholders
    fun resolve(input: String): String {
        return placeholderRegex.replace(input) { match ->
            val key = match.groupValues[1]
            credentials[key] ?: match.value // Leave unresolved if optional
        }
    }

    val resolvedCommand = resolve(serverConfig.command)
    val resolvedArgs = serverConfig.args.map { resolve(it) }
    val resolvedEnv = serverConfig.env.mapValues { (_, v) -> resolve(v) }

    // Step 6: Compute pool key (deterministic)
    val sortedValues = credentials.toSortedMap().values.joinToString("|")
    val poolKey = sha256("$serverName|$sortedValues")

    return ResolvedConfig(resolvedCommand, resolvedArgs, resolvedEnv, poolKey)
}
```

### 6.2 Process Pool Management

**Trigger:** Process acquisition request from tool execution pipeline
**Input:** pool_key, resolved_config
**Output:** Active McpConnection ready for tool execution

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Lookup pool by pool_key in ConcurrentHashMap | If not found, create new pool entry |
| 2 | Try to acquire idle process from pool | Atomic CAS on process state |
| 3 | If idle process found, mark as BUSY and return | < 100ms |
| 4 | If no idle process and pool below max, spawn new | Cold start < 5s |
| 5 | If pool at max, check total instances | If total below max, spawn |
| 6 | If total at max, queue request with timeout | Return 503 if timeout exceeded |
| 7 | After tool execution, release process (mark IDLE) | Reset idle timer |

**Pseudocode:**

```kotlin
// ProcessPoolManager.kt — com.orchestrator.mcp.client.pool
// [Implements: MTO-99]

class ProcessPoolManager(
    private val upstreamServerManager: UpstreamServerManager,
    private val config: PoolConfig,
    private val healthMonitor: HealthMonitor
) {
    private val pools = ConcurrentHashMap<String, ProcessPool>()
    private val totalProcessCount = AtomicInteger(0)

    suspend fun acquireProcess(poolKey: String, resolvedConfig: ResolvedConfig): PooledConnection {
        val pool = pools.getOrPut(poolKey) { ProcessPool(poolKey, resolvedConfig) }

        // Try warm acquisition first (< 100ms target)
        pool.tryAcquireIdle()?.let { return it }

        // Cold start if below limits
        if (pool.size() < config.maxInstancesPerServer &&
            totalProcessCount.get() < config.maxTotalInstances) {
            return pool.spawnAndAcquire(resolvedConfig)
        }

        // Queue with timeout
        return pool.waitForAvailable(config.acquireTimeoutMs)
            ?: throw PoolExhaustedException(poolKey)
    }

    suspend fun releaseProcess(poolKey: String, connection: PooledConnection) {
        val pool = pools[poolKey] ?: return
        connection.markIdle()
        pool.release(connection)
    }

    // Background coroutine: scale-down idle processes
    private suspend fun idleReaper() {
        while (isActive) {
            delay(30_000) // Check every 30s
            pools.values.forEach { pool ->
                pool.reapIdle(config.idleTimeoutMs)
            }
        }
    }

    // Background coroutine: scale-up on slow response
    private suspend fun responseTimeMonitor() {
        while (isActive) {
            delay(10_000) // Check every 10s
            pools.values.forEach { pool ->
                if (pool.avgResponseTimeMs() > config.slowResponseThresholdMs &&
                    pool.size() < config.maxInstancesPerServer &&
                    totalProcessCount.get() < config.maxTotalInstances) {
                    pool.scaleUp()
                }
            }
        }
    }
}
```

### 6.3 JWT Token Lifecycle

**Trigger:** Various auth events
**Input:** User credentials or existing token
**Output:** JWT token or validation result

**Processing Steps — Token Creation:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Validate user credentials (bcrypt verify) | Return INVALID_CREDENTIALS |
| 2 | Check account status (active, not locked) | Return ACCOUNT_DISABLED or ACCOUNT_LOCKED |
| 3 | Build JWT claims (sub, email, roles, iat, exp) | — |
| 4 | Sign with configured algorithm (HS256/RS256) | Key must be available |
| 5 | Record login event in audit log | — |
| 6 | Return signed JWT | — |

**Pseudocode:**

```kotlin
// JwtAuthService.kt — com.orchestrator.mcp.auth
// [Implements: MTO-95]

data class JwtClaims(
    val userId: UUID,
    val email: String,
    val roles: List<String>,
    val tokenType: TokenType, // SESSION or BRIDGE
    val tokenId: UUID
)

enum class TokenType { SESSION, BRIDGE }

fun createSessionToken(user: User): String {
    val now = Instant.now()
    val claims = JwtClaims(
        userId = user.id,
        email = user.email,
        roles = listOf(user.role),
        tokenType = TokenType.SESSION,
        tokenId = UUID.randomUUID()
    )
    return JWT.create()
        .withSubject(user.id.toString())
        .withClaim("email", user.email)
        .withClaim("roles", claims.roles)
        .withClaim("type", "session")
        .withClaim("jti", claims.tokenId.toString())
        .withIssuedAt(now)
        .withExpiresAt(now.plus(config.sessionTokenExpiryHours, ChronoUnit.HOURS))
        .sign(algorithm) // HS256 or RS256
}

fun createBridgeToken(user: User, expiryDays: Int): String {
    val now = Instant.now()
    val tokenId = UUID.randomUUID()

    // Store token hash for revocation tracking
    val tokenHash = sha256(tokenId.toString())
    bridgeTokenRepository.save(BridgeToken(
        id = tokenId,
        userId = user.id,
        tokenHash = tokenHash,
        expiresAt = now.plus(expiryDays.toLong(), ChronoUnit.DAYS),
        revoked = false
    ))

    // Revoke previous tokens
    bridgeTokenRepository.revokeAllForUser(user.id, excludeId = tokenId)

    return JWT.create()
        .withSubject(user.id.toString())
        .withClaim("email", user.email)
        .withClaim("roles", listOf(user.role))
        .withClaim("type", "bridge")
        .withClaim("jti", tokenId.toString())
        .withIssuedAt(now)
        .withExpiresAt(now.plus(expiryDays.toLong(), ChronoUnit.DAYS))
        .sign(algorithm)
}

fun validateToken(token: String): JwtClaims {
    val verifier = JWT.require(algorithm)
        .build()
    val decoded = verifier.verify(token) // Throws on invalid/expired

    // Check if bridge token is revoked
    val tokenType = decoded.getClaim("type").asString()
    if (tokenType == "bridge") {
        val tokenId = UUID.fromString(decoded.getClaim("jti").asString())
        val bridgeToken = bridgeTokenRepository.findById(tokenId)
        if (bridgeToken?.revoked == true) {
            throw TokenRevokedException()
        }
    }

    return JwtClaims(
        userId = UUID.fromString(decoded.subject),
        email = decoded.getClaim("email").asString(),
        roles = decoded.getClaim("roles").asList(String::class.java),
        tokenType = if (tokenType == "bridge") TokenType.BRIDGE else TokenType.SESSION,
        tokenId = UUID.fromString(decoded.getClaim("jti").asString())
    )
}
```

**Activity Diagram — Authentication Flow:**

![Authentication Flow — Activity Diagram](diagrams/authentication-flow.png)
*[Edit in draw.io](diagrams/authentication-flow.drawio)*

---

## 7. Security Requirements

### 7.1 Authentication & Authorization

| Role | Permissions | Screens/Features |
|------|-------------|-------------------|
| system_owner | Full admin access | All admin pages, SSO config, credential schemas, user management, pool management |
| leader | Admin access (no SSO config) | Credential schemas, user management, pool metrics |
| developer | Standard user | Profile, own credentials, bridge token generation, tool execution |
| viewer | Read-only | Profile (view only), tool execution (read-only tools) |

**Authentication Flow:**

| Method | Token Type | Expiry | Use Case |
|--------|-----------|--------|----------|
| Local login | Session JWT | 1-4 hours | Admin Portal web sessions |
| SSO login | Session JWT | 1-4 hours | Admin Portal via IdP |
| Bridge token | Bridge JWT | 1-365 days (configurable) | IDE bridge client |
| Deprecated | X-User-Email header | N/A | Legacy compatibility (logged as warning) |

**JWT Claims Structure:**

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@company.com",
  "roles": ["developer"],
  "type": "session",
  "jti": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "iat": 1720281600,
  "exp": 1720296000
}
```

**Endpoint Authorization Matrix:**

| Endpoint | Roles Required | Token Types |
|----------|---------------|-------------|
| POST /api/auth/login | None (public) | — |
| POST /api/auth/bridge-token | Any authenticated | Session |
| GET /api/auth/sso/* | None (public) | — |
| GET/PUT /api/admin/credential-schemas/* | system_owner, leader | Session |
| GET/PUT/DELETE /api/credentials/* | Any authenticated | Session, Bridge |
| GET/POST /api/admin/pool/* | system_owner, leader | Session |
| GET/PUT /api/admin/sso/config | system_owner | Session |
| POST /mcp | Any authenticated | Session, Bridge |

### 7.2 Data Sensitivity Classification

| Data Type | Classification | Business Requirement |
|-----------|---------------|---------------------|
| User passwords | Restricted | Bcrypt hashed (cost 12), never stored in plain text |
| User credentials (API tokens, secrets) | Restricted | AES-256-GCM encrypted at rest, decrypted only in memory per-request |
| JWT signing key | Restricted | Environment variable only, never in config files or logs |
| SSO client secret | Restricted | AES-256-GCM encrypted in database |
| Bridge tokens | Confidential | SHA-256 hash stored for revocation, full token only in memory |
| User email/name | Internal | Standard PII handling |
| Credential schemas | Internal | No sensitive data (field definitions only) |
| Pool metrics | Internal | No credential data exposed |
| Audit logs | Confidential | Retained for compliance, no credential values logged |

### 7.3 Encryption Specifications

| Purpose | Algorithm | Key Size | Implementation |
|---------|-----------|----------|----------------|
| Credential at-rest encryption | AES-256-GCM | 256-bit | Existing TokenEncryptionService |
| Password hashing | Bcrypt | Cost factor 12 | New — BCrypt library |
| JWT signing (default) | HMAC-SHA256 | 256-bit | Auth0 java-jwt |
| JWT signing (enterprise) | RSA-SHA256 | 2048-bit | Auth0 java-jwt |
| Token hash (revocation) | SHA-256 | — | java.security.MessageDigest |
| Pool key computation | SHA-256 | — | java.security.MessageDigest |

### 7.4 Audit Trail

| Event | Logged Fields | Retention | Business Reason |
|-------|--------------|-----------|-----------------|
| Login success | user_id, email, auth_mode, ip_address, timestamp | 90 days | Security monitoring |
| Login failure | username_attempted, ip_address, reason, timestamp | 90 days | Brute force detection |
| Bridge token generated | user_id, token_id, expiry, timestamp | 90 days | Token lifecycle tracking |
| Bridge token revoked | user_id, token_id, reason, timestamp | 90 days | Security audit |
| Credential saved | user_id, server_name, fields_updated (keys only), timestamp | 90 days | Change tracking |
| Credential cleared | user_id, server_name, timestamp | 90 days | Change tracking |
| Schema modified | admin_id, server_name, action, field_keys, timestamp | 90 days | Admin activity |
| SSO config changed | admin_id, fields_changed (no secrets), timestamp | 90 days | Admin activity |
| Pool scale event | server_name, pool_key, action, old_count, new_count | 30 days | Capacity planning |
| Process crash | server_name, pool_key, error_summary, timestamp | 30 days | Reliability monitoring |

> **CRITICAL:** Audit logs NEVER contain: decrypted credentials, password values, JWT tokens, encryption keys, or any secret material. Only metadata (field keys, server names, user IDs) is logged.

### 7.5 Security Controls

| Control | Implementation | Threat Mitigated |
|---------|---------------|------------------|
| Account lockout | Lock after 5 failed attempts for 15 minutes | Brute force attacks |
| Token revocation | Bridge token revocation on new generation | Stolen token usage |
| PKCE for SSO | code_verifier + code_challenge in OAuth2 flow | Authorization code interception |
| State parameter | Random state in SSO flow, validated on callback | CSRF attacks |
| Credential isolation | Per-user encryption, per-request decryption | Cross-user data leakage |
| Memory-only secrets | Decrypted credentials never persisted beyond request | Data at rest exposure |
| Structured logging exclusion | Credential values excluded from all log outputs | Log-based credential exposure |
| Token format validation | Bridge validates JWT structure before sending | Malformed token injection |

---

## 8. Non-Functional Requirements

| Category | Business Requirement | Acceptance Criteria |
|----------|---------------------|---------------------|
| Performance — JWT validation | Every request must be authenticated quickly | < 5ms per JWT validation (signature + claims extraction) |
| Performance — Credential resolution | Credential lookup and decryption must not add significant latency | < 10ms per request (DB fetch + AES-256-GCM decrypt + placeholder resolve) |
| Performance — Pool acquire (warm) | Users should not notice pool management overhead | < 100ms to acquire an existing idle process |
| Performance — Pool acquire (cold) | New process spawn should be fast enough for acceptable UX | < 5 seconds for cold start (process spawn + initialization) |
| Performance — Login | Login should feel instant | < 500ms for local auth (bcrypt verify + JWT creation) |
| Performance — SSO | SSO should complete within acceptable OAuth2 timeouts | < 3 seconds total (redirect + token exchange + user creation) |
| Scalability — Concurrent users | System must support team-sized deployments | 20+ concurrent users with configurable pool limits |
| Scalability — Process pool | Pool must handle multiple servers × multiple users | maxInstancesPerServer: 1-20 (default 5), maxTotalInstances: 1-100 (default 50) |
| Scalability — Credential storage | Must handle credentials for all users × all servers | Up to 1000 user_credentials rows (20 users × 50 servers) |
| Availability — Process recovery | Crashed processes must auto-recover | Auto-restart within 5 seconds, no manual intervention |
| Availability — Token expiry | Users must get clear feedback on expired tokens | Clear error message with action guidance |
| Availability — Graceful degradation | Pool exhaustion should queue, not immediately fail | Queue requests up to 30s before returning 503 |
| Security — Encryption | All secrets encrypted at rest | AES-256-GCM with unique IV per encryption |
| Security — Password | Passwords must resist offline attacks | Bcrypt cost factor 12 (≈250ms per hash) |
| Security — Token | Tokens must be cryptographically secure | HS256/RS256 signing, 256-bit minimum key |
| Reliability — Backward compat | Existing integrations must not break | X-User-Email header still works (deprecated), no-schema servers unchanged |
| Observability — Metrics | Pool health must be monitorable | Metrics API with active/idle counts, response times, utilization |

---

## 9. Error Handling (User-Facing)

### 9.1 Error Scenarios

| Scenario | Severity | Error Code | User Message | Expected Behavior |
|----------|----------|-----------|--------------|-------------------|
| Invalid login credentials | Warning | INVALID_CREDENTIALS | Invalid username or password | Show error, allow retry |
| Expired session token | Warning | TOKEN_EXPIRED | Session expired. Please login again | Redirect to login page |
| Expired bridge token | Warning | TOKEN_EXPIRED | Bridge token expired. Regenerate from profile page | Bridge logs message, IDE shows error |
| Malformed JWT | Critical | INVALID_TOKEN | Token is malformed or signature verification failed | Reject request, log security event |
| Account disabled | Critical | ACCOUNT_DISABLED | Account is disabled. Contact administrator | Block all access |
| Account locked | Warning | ACCOUNT_LOCKED | Account locked for {minutes} minutes due to failed attempts | Wait or contact admin |
| Missing credential | Warning | MISSING_CREDENTIAL | Credential '{field}' not configured for server '{name}' | User fills credential in profile |
| Decryption failure | Critical | DECRYPTION_ERROR | Failed to decrypt credentials. Please re-enter | User re-saves credentials |
| Pool exhausted | Warning | POOL_EXHAUSTED | All process instances are busy. Please retry | Auto-retry after delay |
| Process spawn failure | Critical | SPAWN_FAILED | Failed to start server process. Contact administrator | Admin checks server config |
| SSO provider unavailable | Warning | SSO_PROVIDER_UNAVAILABLE | Identity provider not responding. Try again or use local login | Retry or switch auth mode |
| SSO auth failed | Warning | SSO_AUTH_FAILED | Authentication failed. Please try again | Retry SSO flow |
| Invalid bridge token format | Critical | INVALID_TOKEN_FORMAT | Invalid token format. Expected JWT (header.payload.signature) | Bridge exits, user regenerates token |
| Payload too large | Warning | PAYLOAD_TOO_LARGE | Credential data exceeds maximum size (10KB) | User reduces data |

### 9.2 Error Response Format

All API errors follow a consistent JSON structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message for the user",
  "details": {
    "server_name": "atlassian",
    "missing_fields": ["jira_token", "jira_email"]
  },
  "timestamp": "2026-07-06T14:30:00Z",
  "request_id": "req-550e8400"
}
```

### 9.3 Notification Requirements

| Event | Who is Notified | Channel | Timing |
|-------|----------------|---------|--------|
| Account locked | User (on next attempt) | HTTP response | Immediate |
| Bridge token expiring (< 3 days) | User | Admin Portal banner | On login |
| Pool utilization > 80% | Admin | Structured log (WARN) | Real-time |
| Process crash (repeated) | Admin | Structured log (ERROR) | Real-time |
| SSO config invalid | Admin | Admin Portal alert | On detection |

---

## 10. Testing Considerations

### 10.1 Test Scenarios

| ID | Scenario | Input | Expected Output | Priority |
|----|----------|-------|-----------------|----------|
| TC-001 | Login with valid credentials | username=john, password=valid | 200 + JWT token | High |
| TC-002 | Login with invalid password | username=john, password=wrong | 401 INVALID_CREDENTIALS | High |
| TC-003 | Login with disabled account | username=disabled_user | 403 ACCOUNT_DISABLED | High |
| TC-004 | Login with locked account | username=locked_user | 423 ACCOUNT_LOCKED | High |
| TC-005 | Generate bridge token | Valid session token | 200 + bridge token | High |
| TC-006 | Generate bridge token (expired session) | Expired session token | 401 TOKEN_EXPIRED | High |
| TC-007 | Validate valid JWT | Valid Bearer token | UserContext extracted | High |
| TC-008 | Validate expired JWT | Expired token | 401 TOKEN_EXPIRED | High |
| TC-009 | Validate revoked bridge token | Revoked token | 401 INVALID_TOKEN | High |
| TC-010 | Deprecated X-User-Email header | Valid email header | Works + deprecation warning logged | Medium |
| TC-011 | Create credential schema | Admin + valid fields | 200 + schema saved | High |
| TC-012 | Create schema — duplicate field_key | Same key twice | 409 DUPLICATE_FIELD_KEY | Medium |
| TC-013 | Create schema — invalid field_key | "Invalid Key!" | 400 INVALID_FIELD_KEY | Medium |
| TC-014 | Delete schema field with user data | Field has 5 users | 200 + warning (affected_users=5) | Medium |
| TC-015 | Save user credentials | Valid credential values | 200 + encrypted storage | High |
| TC-016 | Save credentials — missing required | Required field empty | 400 MISSING_REQUIRED_FIELD | High |
| TC-017 | Save credentials — invalid URL | url field = "not-a-url" | 400 INVALID_FIELD_FORMAT | Medium |
| TC-018 | Get credentials — masked secrets | After save | Secret shows ****XXXX | High |
| TC-019 | Resolve credentials — all present | User has all fields | ResolvedConfig returned | High |
| TC-020 | Resolve credentials — missing field | User missing jira_token | 400 MISSING_CREDENTIAL | High |
| TC-021 | Resolve credentials — no placeholders | Server has no {placeholders} | Config returned as-is | High |
| TC-022 | Pool acquire — warm process | Idle process exists | Connection in < 100ms | High |
| TC-023 | Pool acquire — cold start | No idle process | Connection in < 5s | High |
| TC-024 | Pool acquire — exhausted | All at max | 503 after timeout | Medium |
| TC-025 | Pool scale up | Avg response > threshold | New process spawned | Medium |
| TC-026 | Pool scale down | Process idle > timeout | Process terminated | Medium |
| TC-027 | Pool — shared credentials | 2 users, same creds | Share same process | High |
| TC-028 | Pool — different credentials | 2 users, different creds | Separate processes | High |
| TC-029 | Bridge --token valid format | 3-part JWT | Bridge starts successfully | High |
| TC-030 | Bridge --token invalid format | "not-a-jwt" | Bridge exits code 1 | High |
| TC-031 | Bridge sends Authorization header | Token configured | Header present in requests | High |
| TC-032 | Bridge no token (backward compat) | No --token | Warning logged, requests proceed | Medium |
| TC-033 | SSO login flow | Valid IdP credentials | Local user created + JWT issued | High |
| TC-034 | SSO JIT provisioning | First-time SSO user | User auto-created with default role | High |
| TC-035 | SSO — IdP unreachable | IdP down | 503 SSO_PROVIDER_UNAVAILABLE | Medium |
| TC-036 | SSO — invalid state (CSRF) | Tampered state param | 400 INVALID_STATE | High |
| TC-037 | Concurrent tool execution | 10 users, same server | All get processes, no conflicts | High |
| TC-038 | Credential encryption roundtrip | Save → fetch → decrypt | Original values recovered | High |
| TC-039 | Account lockout after 5 failures | 5 wrong passwords | Account locked 15 min | Medium |
| TC-040 | Token refresh within window | Token expiring in 20 min | New token issued | Medium |

### 10.2 Integration Test Scenarios

| ID | Scenario | Components | Expected Behavior |
|----|----------|-----------|-------------------|
| IT-001 | End-to-end tool execution with credentials | Bridge → Auth → Resolver → Pool → Upstream | Tool executes with user's credentials |
| IT-002 | SSO full flow with JIT provisioning | Browser → Orchestrator → IdP → DB | User created, JWT issued |
| IT-003 | Pool scaling under load | Multiple concurrent requests | Pool scales up, then down after idle |
| IT-004 | Bridge reconnection after token expiry | Bridge → Orchestrator (401) → User regenerates | Bridge resumes after new token |
| IT-005 | Credential schema change with existing users | Admin modifies schema → User's form updates | No data loss, new fields shown |

### 10.3 Performance Test Targets

| Metric | Target | Test Method |
|--------|--------|-------------|
| JWT validation throughput | > 10,000 validations/sec | JMH benchmark |
| Credential resolution latency (p95) | < 15ms | Load test with 20 concurrent users |
| Pool warm acquire latency (p99) | < 200ms | Load test |
| Pool cold start latency (p95) | < 5s | Spawn test with various server types |
| Login latency (p95) | < 600ms | Bcrypt + JWT creation benchmark |
| Concurrent user capacity | 20 users, 50 total processes | Sustained load test (10 min) |

---

## 11. Appendix

### 11.1 Configuration Specification

All new configuration is added to the existing `application.yml` structure:

```yaml
# Authentication configuration
auth:
  jwt:
    secret: ${JWT_SECRET}                    # HS256 key (env var)
    algorithm: HS256                          # HS256 or RS256
    session-expiry-hours: 4                   # Session token TTL
    bridge-token-default-expiry-days: 30      # Default bridge token TTL
    bridge-token-max-expiry-days: 365          # Maximum allowed (1 year)
  lockout:
    max-attempts: 5                           # Failed attempts before lock
    lockout-duration-minutes: 15              # Lock duration
  deprecated:
    allow-email-header: true                  # Allow X-User-Email (deprecated)
    email-header-name: X-User-Email           # Header name

# SSO configuration (can also be in DB via sso_config table)
sso:
  enabled: false
  issuer-url: ""
  client-id: ""
  client-secret: ${SSO_CLIENT_SECRET}
  scopes: "openid profile email"
  callback-url: "http://localhost:8080/api/auth/sso/callback"
  claims-mapping:
    email: "email"
    name: "name"
    roles: "groups"
  default-role: "developer"
  jit-provisioning: true

# Process pool configuration
process-pool:
  max-instances-per-server: 5                 # Per server type limit
  max-total-instances: 50                     # System-wide limit
  idle-timeout-ms: 300000                     # 5 minutes
  slow-response-threshold-ms: 10000           # 10 seconds
  acquire-timeout-ms: 30000                   # 30 seconds (queue wait)
  health-check-interval-ms: 30000             # 30 seconds
  scale-check-interval-ms: 10000              # 10 seconds

# Credential encryption (existing, reused)
encryption:
  key-env: MCP_ENCRYPTION_KEY                 # AES-256 key env var name
```

### 11.2 New Dependencies

| Library | Version | Purpose | Module |
|---------|---------|---------|--------|
| com.auth0:java-jwt | 4.4.0 | JWT creation and verification | orchestrator-server |
| at.favre.lib:bcrypt | 0.10.2 | Password hashing | orchestrator-server |
| io.ktor:ktor-client-auth | 3.4.0 | OAuth2 client support | orchestrator-server |

### 11.3 API Endpoint Summary

| Method | Path | Auth | Role | Story |
|--------|------|------|------|-------|
| POST | /api/auth/login | No | — | MTO-95 |
| POST | /api/auth/bridge-token | Session | Any | MTO-95 |
| POST | /api/auth/refresh | Session | Any | MTO-95 |
| GET | /api/auth/sso/authorize | No | — | MTO-101 |
| GET | /api/auth/sso/callback | No | — | MTO-101 |
| GET | /api/admin/credential-schemas | Session | Admin | MTO-96 |
| GET | /api/admin/credential-schemas/{server} | Session | Admin | MTO-96 |
| PUT | /api/admin/credential-schemas/{server} | Session | Admin | MTO-96 |
| DELETE | /api/admin/credential-schemas/{server}/{field} | Session | Admin | MTO-96 |
| GET | /api/credentials/servers | Session/Bridge | Any | MTO-97 |
| GET | /api/credentials/{server} | Session/Bridge | Any | MTO-97 |
| PUT | /api/credentials/{server} | Session/Bridge | Any | MTO-97 |
| DELETE | /api/credentials/{server} | Session/Bridge | Any | MTO-97 |
| GET | /api/credentials/{server}/validate | Session/Bridge | Any | MTO-98 |
| GET | /api/admin/pool/metrics | Session | Admin | MTO-99 |
| POST | /api/admin/pool/scale | Session | Admin | MTO-99 |
| GET | /api/admin/sso/config | Session | system_owner | MTO-101 |
| PUT | /api/admin/sso/config | Session | system_owner | MTO-101 |

### 11.4 Data Migration Plan

**Migration Strategy:** Additive only — no existing data is modified or deleted.

| Step | Action | Rollback |
|------|--------|----------|
| 1 | Add new columns to `users` table (password_hash, auth_mode, etc.) | DROP COLUMN |
| 2 | Create `credential_schemas` table | DROP TABLE |
| 3 | Create `user_credentials` table | DROP TABLE |
| 4 | Create `bridge_tokens` table | DROP TABLE |
| 5 | Create `sso_config` table | DROP TABLE |
| 6 | Set default password for existing users (admin must reset) | N/A |
| 7 | Set auth_mode='local' for all existing users | N/A |

**Rollback Strategy:**
- All new tables can be dropped without affecting existing functionality
- New columns on `users` table can be dropped (nullable, with defaults)
- Application code checks for feature flags before using new auth

### 11.5 Open Issues

| # | Issue | Owner | Target Date | Status |
|---|-------|-------|-------------|--------|
| 1 | JWT key rotation strategy — how to rotate without invalidating all tokens | TA / Security | Sprint +1 | Open |
| 2 | Rate limiting per user on upstream servers — deferred to future iteration | Product Owner | Backlog | Deferred |
| 3 | Credential schema versioning — how to handle schema changes with existing data | TA | Sprint +1 | Open |
| 4 | Process pool memory monitoring — need OS-level metrics for process RAM usage | DevOps | Sprint +1 | Open |
| 5 | SSO group-to-role mapping — complex mapping rules beyond simple claim extraction | BA / TA | Sprint +2 | Open |
| 6 | Bridge token refresh — should bridge auto-refresh before expiry? | Product Owner | Sprint +1 | Open |

### 11.6 Data Flow Diagram

![Data Flow Diagram — Credential Resolution](diagrams/data-flow-credential-resolution.png)
*[Edit in draw.io](diagrams/data-flow-credential-resolution.drawio)*

### 11.7 Sample Payloads

**Example: Complete tool execution flow with per-user credentials**

1. Bridge sends authenticated request:
```json
{
  "jsonrpc": "2.0",
  "id": 42,
  "method": "tools/call",
  "params": {
    "name": "jira_create_issue",
    "arguments": {
      "project": "MTO",
      "summary": "New feature request",
      "type": "Story"
    }
  }
}
```

2. Server config with placeholders (from mcp_servers table):
```json
{
  "name": "atlassian",
  "command": "npx",
  "args": ["@anthropic/jira-mcp", "--url={jira_url}", "--email={jira_email}", "--token={jira_token}"],
  "env": {},
  "transport": "stdio"
}
```

3. User's stored credentials (after decryption):
```json
{
  "jira_url": "https://mycompany.atlassian.net",
  "jira_email": "john@company.com",
  "jira_token": "ATATT3xFgA0123456789"
}
```

4. Resolved config (sent to process pool):
```json
{
  "command": "npx",
  "args": ["@anthropic/jira-mcp", "--url=https://mycompany.atlassian.net", "--email=john@company.com", "--token=ATATT3xFgA0123456789"],
  "env": {},
  "pool_key": "sha256:a1b2c3d4e5f6..."
}
```

### 11.8 Change Log from BRD

| Section | Change | Reason |
|---------|--------|--------|
| Auth endpoints | Added /api/auth/refresh endpoint | Token refresh needed for long portal sessions |
| Pool management | Added /api/admin/pool/scale endpoint | Admin override for manual scaling |
| Credential validation | Added /api/credentials/{server}/validate endpoint | Users need to verify completeness before tool use |
| Account lockout | Added lockout mechanism (5 attempts / 15 min) | Security best practice not in original BRD |
| Bridge token revocation | Added bridge_tokens table for revocation tracking | BRD mentioned invalidation but no mechanism specified |
| SSO PKCE | Added PKCE to OAuth2 flow | Security best practice for public clients |
| Error response format | Standardized JSON error format with request_id | Developer experience improvement |

---

*End of Document*
