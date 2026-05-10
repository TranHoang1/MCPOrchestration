# Functional Specification Document (FSD)

## MCPOrchestration — MTO-39: User Management & Document Approval

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-39 |
| Title | User Management & Document Approval — Role-based approval với per-user Jira credentials |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-39.docx |
| Related UI Design | [UI-SPEC.md](UI-SPEC.md) — Wireframes + Screen Specifications |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA Agent | Initiate document — auto-generated from BRD and Jira ticket MTO-39 |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the User Management & Document Approval system for the MCP Orchestrator Server. It defines use cases, data models, API contracts, business rules, and integration specifications required for implementation.

### 1.2 Scope

The system adds user identity management, role-based document approval permissions, and per-user Jira credential handling to the existing MCP Orchestrator Server (HTTP standalone mode).

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol |
| RBAC | Role-Based Access Control |
| RLS | Row-Level Security (PostgreSQL) |
| Admin | User with role leader or system_owner |
| Approval Matrix | Configuration mapping roles to document type permissions |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-39.docx |
| MTO Workflow | documents/workflows/MTO-workflows.md |
| Project Structure | .analysis/code-intelligence/project-structure.md |

---

## 2. System Overview

### 2.1 System Context

The User Management module integrates with:
- **Admin UI** (Ktor HTML routes at /admin) — for user CRUD operations
- **MCP Protocol Layer** — exposes approve_document, get_approval_status, list_pending_approvals tools
- **PostgreSQL Database** — stores users, projects, permissions, approval logs
- **Jira REST API** — per-user connections for attachment operations
- **Existing Security Module** — extends RoleContextService for user identity resolution

### 2.2 System Architecture

Components:
1. **UserManagement Package** — CRUD services, repository, models
2. **Approval Package** — approval workflow logic, permission validation
3. **Admin Routes** — Ktor HTTP endpoints for admin UI
4. **MCP Tool Handlers** — approve_document, get_approval_status, list_pending_approvals
5. **Per-User Jira Connection** — spawns dedicated Jira client per authenticated user


---

## 3. Functional Requirements

### 3.1 Feature: User Account Management

**Source:** BRD Story 1

#### 3.1.1 Description

Centralized user account management with CRUD operations accessible via Admin API endpoints. Users are identified by Jira email, assigned a role, and store encrypted Jira API tokens for per-user Jira operations.

#### 3.1.2 Use Case: UC-01 — Create User Account

**Use Case ID:** UC-01
**Actor:** Admin (leader or system_owner)
**Preconditions:** Admin is authenticated and has admin role
**Postconditions:** New user account exists in database with encrypted token

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends POST /admin/users with user data | | Admin provides email, role, display_name, jira_token |
| 2 | | Validates input fields | Check email format, role validity, required fields |
| 3 | | Checks email uniqueness | Query users table for existing email |
| 4 | | Validates Jira token | Makes test API call to Jira with provided credentials |
| 5 | | Encrypts Jira token | AES-256-GCM encryption with server key |
| 6 | | Inserts user record | Stores in users table with created_by = admin's user_id |
| 7 | | Returns created user | Response with user data (token excluded) |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01a | Email already exists | Return 409 Conflict with message |
| AF-01b | Jira token invalid | Return 400 Bad Request, user not created |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01a | Database unavailable | Return 503 Service Unavailable |
| EF-01b | Encryption key not configured | Return 500 Internal Server Error, log critical |

#### 3.1.3 Use Case: UC-02 — List Users

**Use Case ID:** UC-02
**Actor:** Admin
**Preconditions:** Admin is authenticated
**Postconditions:** None (read-only)

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends GET /admin/users | | Optional query params: role, active |
| 2 | | Queries users table | Applies filters if provided |
| 3 | | Returns user list | Array of user objects (tokens excluded) |

#### 3.1.4 Use Case: UC-03 — Update User

**Use Case ID:** UC-03
**Actor:** Admin
**Preconditions:** Target user exists
**Postconditions:** User record updated

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends PUT /admin/users/{id} | | Provides fields to update |
| 2 | | Validates input | Check role validity, display_name constraints |
| 3 | | If token changed, validates new token | Test Jira API call |
| 4 | | Updates user record | Only provided fields are updated |
| 5 | | Returns updated user | Response with updated data |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-03a | User not found | Return 404 Not Found |
| AF-03b | Attempt to change email to existing | Return 409 Conflict |

#### 3.1.5 Use Case: UC-04 — Deactivate User

**Use Case ID:** UC-04
**Actor:** Admin
**Preconditions:** Target user exists and is active
**Postconditions:** User marked inactive, cannot authenticate

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends DELETE /admin/users/{id} | | Soft delete request |
| 2 | | Checks not last system_owner | Prevents lockout |
| 3 | | Sets active = false | Soft delete, preserves audit trail |
| 4 | | Returns confirmation | 200 OK with deactivated user |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-04a | Last system_owner | Return 400 "Cannot deactivate last system_owner" |
| AF-04b | User already inactive | Return 400 "User already deactivated" |

#### 3.1.6 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Only users with role leader or system_owner can access /admin/* endpoints | BRD Story 1 |
| BR-02 | Email must be unique across all users (active and inactive) | BRD Story 1 |
| BR-03 | Users are soft-deleted (active=false), never hard-deleted | BRD Story 1 |
| BR-04 | Cannot deactivate the last active system_owner account | BRD Story 1 |
| BR-05 | Jira token must be validated before storing (test API call) | BRD Story 4 |
| BR-06 | created_by field must reference the admin performing the action | BRD Story 1 |

#### 3.1.7 Data Specifications

**Input Data (Create User):**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| email | String | Yes | Valid email format, max 255 chars | Jira account email |
| jira_token | String | Yes | Non-empty, validated via Jira API | Jira API token (plaintext in request, encrypted at rest) |
| role | String | Yes | One of: developer, ba, architect, qa, devops, leader, system_owner | User role |
| display_name | String | Yes | 2-100 chars, non-blank | Human-readable name |

**Output Data (User Response):**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| email | String | User email |
| role | String | User role |
| display_name | String | Display name |
| active | Boolean | Account status |
| created_by | UUID | Admin who created |
| created_at | String (ISO 8601) | Creation timestamp |

#### 3.1.8 API Contract (Functional View)

**Endpoint:** `POST /admin/users`
**Purpose:** Create a new user account

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| email | String | Yes | BR-02 (unique) | Jira email address |
| jira_token | String | Yes | BR-05 (validated) | Jira API token |
| role | String | Yes | Must be valid role | User role assignment |
| display_name | String | Yes | 2-100 chars | Display name |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Generated user ID |
| email | String | User email |
| role | String | Assigned role |
| display_name | String | Display name |
| active | Boolean | Always true for new users |
| created_at | String | ISO 8601 timestamp |

**Business Error Scenarios:**

| Scenario | User Message | Trigger Condition |
|----------|-------------|-------------------|
| Duplicate email | "User with email {email} already exists" | BR-02 violated |
| Invalid Jira token | "Jira token validation failed — unable to authenticate with Jira" | BR-05 failed |
| Unauthorized | "Access denied. Only leader or system_owner can manage users" | BR-01 violated |
| Invalid role | "Invalid role. Valid: developer, ba, architect, qa, devops, leader, system_owner" | Invalid enum value |

---

**Endpoint:** `GET /admin/users`
**Purpose:** List all user accounts with optional filtering

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| role | String (query) | No | — | Filter by role |
| active | Boolean (query) | No | — | Filter by active status |

---

**Endpoint:** `PUT /admin/users/{id}`
**Purpose:** Update user account fields

---

**Endpoint:** `DELETE /admin/users/{id}`
**Purpose:** Soft-delete (deactivate) a user account


---

### 3.2 Feature: Project Assignment

**Source:** BRD Story 2

#### 3.2.1 Description

Admins assign Jira project keys to users. A user can only approve documents belonging to tickets in their assigned projects. This provides project-level scoping independent of role-based document type permissions.

#### 3.2.2 Use Case: UC-05 — Assign Project to User

**Use Case ID:** UC-05
**Actor:** Admin
**Preconditions:** User exists and is active
**Postconditions:** User has access to approve documents in the assigned project

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends POST /admin/users/{id}/projects | | Provides project_key |
| 2 | | Validates project_key format | Must match [A-Z][A-Z0-9_]+ |
| 3 | | Checks user exists and is active | Query users table |
| 4 | | Checks no duplicate assignment | Query user_projects table |
| 5 | | Inserts assignment record | granted_by = admin's user_id |
| 6 | | Returns assignment confirmation | 201 Created |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-05a | User not found | Return 404 |
| AF-05b | Duplicate assignment | Return 409 "Already assigned" |
| AF-05c | Invalid project key format | Return 400 |

#### 3.2.3 Use Case: UC-06 — Revoke Project Assignment

**Use Case ID:** UC-06
**Actor:** Admin
**Preconditions:** Assignment exists
**Postconditions:** User can no longer approve documents in that project

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends DELETE /admin/users/{id}/projects/{project_key} | | Revoke request |
| 2 | | Validates assignment exists | Query user_projects |
| 3 | | Deletes assignment record | Hard delete (no audit needed for assignment removal) |
| 4 | | Returns confirmation | 200 OK |

#### 3.2.4 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-07 | Project key must match pattern [A-Z][A-Z0-9_]+ | BRD Story 2 |
| BR-08 | Composite unique constraint on (user_id, project_key) | BRD Story 2 |
| BR-09 | User must be active to receive project assignments | BRD Story 2 |
| BR-10 | Approval requires BOTH correct role AND project assignment | BRD Story 5 |

#### 3.2.5 API Contract (Functional View)

**Endpoint:** `POST /admin/users/{id}/projects`
**Purpose:** Assign a Jira project to a user

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID (path) | Yes | User must exist | Target user ID |
| project_key | String (body) | Yes | BR-07 format | Jira project key (e.g., MTO) |

**Business Error Scenarios:**

| Scenario | User Message | Trigger Condition |
|----------|-------------|-------------------|
| User not found | "User with id {id} not found" | Invalid UUID |
| Duplicate | "User already assigned to project {key}" | BR-08 violated |
| Invalid format | "Invalid project key format" | BR-07 violated |

---

**Endpoint:** `GET /admin/users/{id}/projects`
**Purpose:** List all projects assigned to a user

---

**Endpoint:** `DELETE /admin/users/{id}/projects/{project_key}`
**Purpose:** Revoke a project assignment

---

### 3.3 Feature: Role-Permission Matrix

**Source:** BRD Story 3

#### 3.3.1 Description

Configurable permission matrix that maps roles to document type permissions (can_view, can_approve). Seeded with defaults on first startup, modifiable by admins at runtime.

#### 3.3.2 Use Case: UC-07 — View Permission Matrix

**Use Case ID:** UC-07
**Actor:** Admin
**Preconditions:** Admin authenticated
**Postconditions:** None (read-only)

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends GET /admin/roles | | Request full matrix |
| 2 | | Queries role_permissions table | All rows |
| 3 | | Returns matrix | Structured as role → document_type → permissions |

#### 3.3.3 Use Case: UC-08 — Update Role Permissions

**Use Case ID:** UC-08
**Actor:** Admin (system_owner or leader only)
**Preconditions:** Role and document type exist
**Postconditions:** Permission matrix updated, effective immediately

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin sends PUT /admin/roles/{role}/permissions | | Provides permission updates |
| 2 | | Validates role exists | Check against enum |
| 3 | | Validates document types | Check against known types |
| 4 | | Updates permission records | Upsert into role_permissions |
| 5 | | Returns updated permissions | Confirmation with new state |

#### 3.3.4 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-11 | Default matrix seeded on first startup (see BRD §2.3 Story 3) | BRD Story 3 |
| BR-12 | Permission changes take effect immediately (no restart) | BRD Story 3 |
| BR-13 | Only leader and system_owner can modify permissions | BRD Story 3 |
| BR-14 | Document types: BRD, FSD, TDD, STP_STC, DPG, UG | BRD Story 3 |

#### 3.3.5 API Contract (Functional View)

**Endpoint:** `GET /admin/roles`
**Purpose:** Get the full role-permission matrix

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| roles | Array | List of role objects |
| roles[].role | String | Role name |
| roles[].permissions | Array | Document type permissions |
| roles[].permissions[].document_type | String | BRD, FSD, TDD, etc. |
| roles[].permissions[].can_view | Boolean | View permission |
| roles[].permissions[].can_approve | Boolean | Approve permission |

---

**Endpoint:** `PUT /admin/roles/{role}/permissions`
**Purpose:** Update permissions for a specific role

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| role | String (path) | Yes | Must be valid role | Target role |
| permissions | Array (body) | Yes | BR-14 valid types | Permission updates |
| permissions[].document_type | String | Yes | BR-14 | Document type |
| permissions[].can_view | Boolean | Yes | — | View permission |
| permissions[].can_approve | Boolean | Yes | — | Approve permission |


---

### 3.4 Feature: User Authentication

**Source:** BRD Story 4

#### 3.4.1 Description

When a bridge/server connects in stdio mode, the user authenticates with their Jira email and API token. The server resolves the user's identity, role, and permissions, then spawns a dedicated Jira MCP connection using the user's credentials.

#### 3.4.2 Use Case: UC-09 — Authenticate User on Connection

**Use Case ID:** UC-09
**Actor:** User (via bridge/server stdio connection)
**Preconditions:** MCP Orchestrator Server is running
**Postconditions:** User session established with role and permissions loaded

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User provides jira_email + jira_token at connection time | | Via MCP initialize params or auth header |
| 2 | | Looks up user by email in DB | Query users table |
| 3 | | Verifies user is active | Check active = true |
| 4 | | Decrypts stored token, validates match | Compare provided token with stored |
| 5 | | Loads role and project assignments | Join users + user_projects |
| 6 | | Spawns dedicated Jira MCP connection | Using user's credentials |
| 7 | | Stores user context in session | Session now has user identity |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-09a | User not in DB | Log warning, use shared account, view-only permissions |
| AF-09b | User inactive | Return error message, use shared account |
| AF-09c | Token mismatch | Log warning, use shared account, view-only permissions |
| AF-09d | DB unavailable | Use shared account, view-only permissions |

#### 3.4.3 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-15 | Authentication failure does NOT block connection — graceful degradation to shared account | BRD Story 4 |
| BR-16 | Each authenticated user gets their own Jira MCP connection instance | BRD Story 4 |
| BR-17 | Unauthenticated users have developer role (view-only) | BRD Story 4 |
| BR-18 | Token validation uses the stored encrypted token, not a live Jira API call on every connection | Performance |

---

### 3.5 Feature: Document Approval

**Source:** BRD Story 5

#### 3.5.1 Description

Users invoke the `approve_document` MCP tool to approve or reject documents. The system validates permissions (role + project), updates the document's Sign-Off section, re-attaches to Jira using the user's account, and logs the action.

#### 3.5.2 Use Case: UC-10 — Approve Document

**Use Case ID:** UC-10
**Actor:** Authenticated user with appropriate role and project assignment
**Preconditions:** Document attachment exists on Jira ticket, user has approve permission for document type
**Postconditions:** Document Sign-Off updated, re-attached to Jira, old attachment deleted, audit logged

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User calls approve_document(ticket_key, doc_type, "approve", comment) | | MCP tool invocation |
| 2 | | Resolves user from session context | Get user_id, role, projects |
| 3 | | Validates role has can_approve for doc_type | Query role_permissions |
| 4 | | Extracts project_key from ticket_key | Parse "MTO" from "MTO-39" |
| 5 | | Validates user assigned to project | Query user_projects |
| 6 | | Downloads current document from Jira | Using user's Jira connection |
| 7 | | Updates Sign-Off section in document | Add reviewer name, date, decision |
| 8 | | Re-attaches updated document to Jira | Using user's Jira account |
| 9 | | Deletes old attachment from Jira | Remove previous version |
| 10 | | Logs approval in audit trail | Insert into approval_log |
| 11 | | Returns success response | Approval confirmed |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-10a | Decision = "reject" | Skip steps 6-9, add rejection comment to Jira, log rejection |
| AF-10b | Already approved by same user | Return error "Already approved this version" |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-10a | Role permission denied | Return error with required roles list |
| EF-10b | Project not assigned | Return error with project assignment message |
| EF-10c | Document not found on Jira | Return error "No {type} attachment found" |
| EF-10d | Jira API failure on re-attach | Log approval locally, mark as jira_sync_pending |

#### 3.5.3 Use Case: UC-11 — Reject Document

**Use Case ID:** UC-11
**Actor:** Authenticated user with appropriate role
**Preconditions:** Same as UC-10
**Postconditions:** Rejection logged, comment added to Jira ticket

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User calls approve_document(ticket_key, doc_type, "reject", comment) | | MCP tool invocation |
| 2 | | Validates permissions (same as UC-10 steps 2-5) | |
| 3 | | Adds rejection comment to Jira ticket | Using user's Jira account |
| 4 | | Logs rejection in audit trail | Insert into approval_log with decision=reject |
| 5 | | Returns rejection confirmation | |

#### 3.5.4 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-19 | Approval requires BOTH role permission AND project assignment | BRD Story 5 |
| BR-20 | Document version does NOT change on approval | BRD Story 5 |
| BR-21 | Old attachment is deleted after successful re-attach | BRD Story 8 |
| BR-22 | If Jira re-attach fails, approval is still logged locally | BRD Story 8 |
| BR-23 | Same user cannot approve same document version twice | BRD Story 5 |
| BR-24 | Rejection adds comment to Jira but does NOT modify document | BRD Story 5 |

#### 3.5.5 API Contract (MCP Tool)

**Tool:** `approve_document`
**Purpose:** Approve or reject a document with permission enforcement

**Input Parameters:**

| Parameter | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| ticket_key | String | Yes | Valid Jira key format | Target ticket (e.g., MTO-39) |
| document_type | String | Yes | BR-14 valid types | BRD, FSD, TDD, STP_STC, DPG, UG |
| decision | String | Yes | "approve" or "reject" | Approval decision |
| comment | String | No | — | Optional reviewer comment |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| success | Boolean | Whether operation completed |
| message | String | Human-readable result |
| approval_id | UUID | Audit log entry ID |
| jira_synced | Boolean | Whether Jira was updated successfully |

**Business Error Scenarios:**

| Scenario | User Message | Trigger Condition |
|----------|-------------|-------------------|
| Role denied | "Role '{role}' cannot approve {type}. Required: {roles}" | BR-19 role check fails |
| Project denied | "Not assigned to project '{key}'" | BR-19 project check fails |
| Not found | "No {type} attachment found on {ticket}" | Document missing |
| Duplicate | "Already approved this document version" | BR-23 violated |
| Jira failure | "Approval logged. Jira sync pending." | BR-22 fallback |


---

### 3.6 Feature: List Pending Approvals

**Source:** BRD Story 6

#### 3.6.1 Description

MCP tool that returns documents awaiting the current user's approval, filtered by their role permissions and project assignments.

#### 3.6.2 Use Case: UC-12 — List Pending Approvals

**Use Case ID:** UC-12
**Actor:** Authenticated user
**Preconditions:** User is authenticated with known role and projects
**Postconditions:** None (read-only)

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User calls list_pending_approvals() | | No parameters needed |
| 2 | | Resolves user's role and projects | From session context |
| 3 | | Determines approvable document types | From role_permissions where can_approve=true |
| 4 | | Queries Jira for attachments in user's projects | Filter by project keys |
| 5 | | Excludes already-approved documents | Check approval_log |
| 6 | | Returns pending list | Sorted by attached date (oldest first) |

#### 3.6.3 API Contract (MCP Tool)

**Tool:** `list_pending_approvals`
**Purpose:** Show documents awaiting current user's approval

**Input Parameters:** None (uses session context)

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| pending | Array | List of pending documents |
| pending[].ticket_key | String | Jira ticket key |
| pending[].document_type | String | Document type |
| pending[].version | Integer | Document version |
| pending[].attached_at | String | When document was attached |
| pending[].attached_by | String | Who attached the document |
| count | Integer | Total pending count |

---

### 3.7 Feature: Get Approval Status

**Source:** BRD Story 7

#### 3.7.1 Description

MCP tool that returns the approval history and overall status for a specific document.

#### 3.7.2 Use Case: UC-13 — Get Approval Status

**Use Case ID:** UC-13
**Actor:** Any authenticated user
**Preconditions:** User is authenticated
**Postconditions:** None (read-only)

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User calls get_approval_status(ticket_key, document_type) | | |
| 2 | | Queries approval_log for ticket + doc_type | All entries |
| 3 | | Determines required approver roles | From role_permissions |
| 4 | | Calculates overall status | pending/approved/rejected |
| 5 | | Returns status with history | |

#### 3.7.3 API Contract (MCP Tool)

**Tool:** `get_approval_status`
**Purpose:** Get approval history and status for a document

**Input Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| ticket_key | String | Yes | Jira ticket key |
| document_type | String | Yes | Document type |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| ticket_key | String | Ticket key |
| document_type | String | Document type |
| version | Integer | Current document version |
| overall_status | String | "pending", "approved", or "rejected" |
| approvals | Array | List of approval entries |
| approvals[].user_email | String | Approver email |
| approvals[].user_name | String | Approver display name |
| approvals[].role | String | Approver role |
| approvals[].decision | String | "approve" or "reject" |
| approvals[].comment | String | Optional comment |
| approvals[].timestamp | String | ISO 8601 timestamp |
| pending_roles | Array | Roles that haven't approved yet |

---

## 4. Data Model

### 4.1 Logical Entities

#### Entity: users

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Unique identifier |
| email | String(255) | Yes (Unique) | BR-02 | Jira email address |
| jira_token_encrypted | Text | Yes | BR-05 | AES-256-GCM encrypted token |
| role | Enum | Yes | 7 valid roles | User role |
| display_name | String(100) | Yes | 2-100 chars | Human-readable name |
| created_by | UUID | Yes (FK→users) | BR-06 | Admin who created |
| active | Boolean | Yes | Default true | Account status |
| created_at | Timestamp | Yes | Auto | Creation time |
| updated_at | Timestamp | Yes | Auto | Last update time |

#### Entity: user_projects

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Record ID |
| user_id | UUID | Yes (FK→users) | BR-09 | Target user |
| project_key | String(20) | Yes | BR-07 format | Jira project key |
| granted_by | UUID | Yes (FK→users) | — | Admin who granted |
| granted_at | Timestamp | Yes | Auto | Grant timestamp |

**Constraints:** Unique(user_id, project_key)

#### Entity: role_permissions

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Record ID |
| role | Enum | Yes | 7 valid roles | Target role |
| document_type | Enum | Yes | BR-14 | Document type |
| can_view | Boolean | Yes | Default true | View permission |
| can_approve | Boolean | Yes | Default false | Approve permission |

**Constraints:** Unique(role, document_type)

#### Entity: approval_log

| Attribute | Type | Required | Business Rule | Description |
|-----------|------|----------|---------------|-------------|
| id | UUID | Yes (PK) | Auto-generated | Audit entry ID |
| ticket_key | String(20) | Yes | — | Jira ticket key |
| document_type | Enum | Yes | BR-14 | Document type |
| document_version | Integer | Yes | — | Document version at time of approval |
| user_id | UUID | Yes (FK→users) | — | Approver |
| decision | Enum | Yes | approve/reject | Decision |
| comment | Text | No | — | Optional comment |
| jira_synced | Boolean | Yes | BR-22 | Whether Jira was updated |
| created_at | Timestamp | Yes | Auto | Approval timestamp |

**Relationships:**

| From Entity | To Entity | Cardinality | Description |
|-------------|-----------|-------------|-------------|
| users | user_projects | 1:N | User has many project assignments |
| users | approval_log | 1:N | User has many approval actions |
| users | users (created_by) | N:1 | User created by admin |

---

## 5. Integration Specifications

### 5.1 External System: Jira Cloud REST API

| Attribute | Value |
|-----------|-------|
| Purpose | Per-user attachment upload/delete, comment creation |
| Direction | Outbound |
| Data Format | JSON (REST API) + Multipart (file upload) |
| Frequency | On-demand (per approval action) |

**Data Exchange:**

| Our Data | External Data | Direction | Business Rule |
|----------|--------------|-----------|---------------|
| Document file (DOCX) | Jira attachment | Send | BR-21 re-attach |
| Approval comment | Jira issue comment | Send | BR-24 rejection |
| Attachment list | Jira attachments[] | Receive | Find current document |
| Delete attachment | Jira attachment ID | Send | BR-21 delete old |

### 5.2 Internal System: Existing Audit Module (MTO-34)

| Attribute | Value |
|-----------|-------|
| Purpose | Leverage existing audit infrastructure for approval events |
| Direction | Internal (same JVM) |
| Data Format | Kotlin objects |
| Frequency | Per approval action |

### 5.3 Internal System: Existing Security Module (MTO-33)

| Attribute | Value |
|-----------|-------|
| Purpose | Extend RoleContextService to resolve user identity from session |
| Direction | Internal (same JVM) |
| Data Format | Kotlin interfaces |
| Frequency | Per request |


---

## 6. Processing Logic

### 6.1 Approval Permission Validation

**Trigger:** User invokes approve_document tool
**Input:** user_id, ticket_key, document_type
**Output:** Boolean (authorized) + error message if denied

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Resolve user from session → get user_id, role | If no session: return "Not authenticated" |
| 2 | Query role_permissions WHERE role={role} AND document_type={type} | If no record: return "Permission not configured" |
| 3 | Check can_approve = true | If false: return "Role {role} cannot approve {type}" |
| 4 | Extract project_key from ticket_key (split on '-', take first part) | If invalid format: return "Invalid ticket key" |
| 5 | Query user_projects WHERE user_id={id} AND project_key={key} | If no record: return "Not assigned to project {key}" |
| 6 | Check approval_log for duplicate (same user, ticket, type, version) | If exists: return "Already approved" |
| 7 | Return authorized = true | — |

**Pseudocode:**

```
fun validateApprovalPermission(userId, ticketKey, docType, docVersion):
    user = userRepository.findById(userId) ?: throw NotAuthenticated
    
    permission = rolePermissionRepository.find(user.role, docType)
    if (!permission.canApprove):
        throw PermissionDenied("Role ${user.role} cannot approve $docType")
    
    projectKey = ticketKey.substringBefore('-')
    if (!userProjectRepository.exists(userId, projectKey)):
        throw PermissionDenied("Not assigned to project $projectKey")
    
    if (approvalLogRepository.exists(userId, ticketKey, docType, docVersion)):
        throw DuplicateApproval("Already approved this version")
    
    return true
```

### 6.2 Document Re-attachment Process

**Trigger:** Successful approval validation
**Input:** ticket_key, document_type, user's Jira credentials
**Output:** Updated document attached to Jira

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | List attachments on Jira ticket | If Jira unavailable: mark jira_sync_pending |
| 2 | Find attachment matching pattern {DOC}-v{N}-{TICKET}.docx | If not found: return error |
| 3 | Download attachment content | If download fails: return error |
| 4 | Parse DOCX, find Sign-Off section | If section missing: append Sign-Off |
| 5 | Add reviewer entry: name, date, decision | — |
| 6 | Upload modified DOCX as new attachment | If upload fails: mark jira_sync_pending |
| 7 | Delete old attachment by ID | If delete fails: log warning (non-critical) |

### 6.3 Default Permission Matrix Seeding

**Trigger:** First application startup (no role_permissions records exist)
**Schedule:** One-time on startup
**Input:** Hardcoded default matrix from BRD
**Output:** role_permissions table populated

**Processing Steps:**

| Step | Description | Error Handling |
|------|-------------|----------------|
| 1 | Check if role_permissions table is empty | — |
| 2 | If empty: insert default matrix (7 roles × 6 doc types = 42 records) | If insert fails: log error, retry on next startup |
| 3 | If not empty: skip seeding | — |

---

## 7. Security Requirements

### 7.1 Authentication & Authorization

| Role | Permissions | Screens/Features |
|------|-------------|-------------------|
| developer | View all documents | MCP tools (read-only) |
| ba | Approve BRD, FSD, UG | MCP tools (approve specific types) |
| architect | Approve FSD, TDD | MCP tools (approve specific types) |
| qa | Approve STP/STC | MCP tools (approve specific types) |
| devops | Approve DPG | MCP tools (approve specific types) |
| leader | Approve all + Admin UI | /admin/* endpoints + all MCP tools |
| system_owner | Approve all + Admin UI + Permission config | Full access |

### 7.2 Data Sensitivity Classification

| Data Type | Classification | Business Requirement |
|-----------|---------------|---------------------|
| Jira API tokens | Restricted | Must be encrypted at rest (AES-256-GCM) |
| User emails | Internal | PII, not exposed in logs |
| Approval decisions | Internal | Audit trail, retained indefinitely |
| Permission matrix | Internal | Configuration data |

### 7.3 Audit Trail

| Event | Logged Fields | Retention | Business Reason |
|-------|--------------|-----------|-----------------|
| Document approved | user_id, ticket_key, doc_type, version, decision, timestamp | Indefinite | Compliance, traceability |
| Document rejected | user_id, ticket_key, doc_type, version, decision, comment, timestamp | Indefinite | Compliance |
| User created | admin_id, new_user_email, role, timestamp | Indefinite | Admin accountability |
| User deactivated | admin_id, target_user_id, timestamp | Indefinite | Admin accountability |
| Permission changed | admin_id, role, doc_type, old_value, new_value, timestamp | Indefinite | Configuration audit |
| Auth failure | email, reason, timestamp | 90 days | Security monitoring |

---

## 8. Non-Functional Requirements

| Category | Business Requirement | Acceptance Criteria |
|----------|---------------------|---------------------|
| Performance | Admin API responds quickly | All CRUD endpoints < 500ms response time |
| Performance | Approval validation is fast | Permission check < 200ms |
| Performance | Jira re-attach completes reasonably | < 10s for document upload + delete |
| Availability | System degrades gracefully | If user DB down, shared account still works |
| Security | Tokens never in logs | grep -r for token patterns returns 0 matches in log output |
| Security | Admin endpoints protected | Non-admin requests return 403 |
| Scalability | Supports team-scale | Up to 100 users, 1000 approval records |
| Data Integrity | No orphaned records | FK constraints enforced |

---

## 9. Error Handling (User-Facing)

### 9.1 Error Scenarios

| Scenario | Severity | User Message | Expected Behavior |
|----------|----------|-------------|-------------------|
| Auth failure | Warning | "Using shared account with view-only access" | Connection continues, limited permissions |
| Permission denied (role) | Error | "Role '{role}' cannot approve {type}" | Tool returns error, no side effects |
| Permission denied (project) | Error | "Not assigned to project '{key}'" | Tool returns error, no side effects |
| Document not found | Error | "No {type} attachment found on {ticket}" | Tool returns error |
| Jira API failure | Warning | "Approval logged. Jira sync pending." | Approval saved locally, retry later |
| Duplicate approval | Warning | "Already approved this document version" | No action taken |
| Last admin protection | Error | "Cannot deactivate last system_owner" | Deactivation blocked |

### 9.2 Notification Requirements

| Event | Who is Notified | Channel | Timing |
|-------|----------------|---------|--------|
| Document approved | Ticket reporter (future) | Jira comment | Immediate |
| Document rejected | Ticket reporter (future) | Jira comment | Immediate |
| Jira sync failed | System admin | Application log | Immediate |

---

## 10. Testing Considerations

### 10.1 Test Scenarios

| ID | Scenario | Input | Expected Output | Priority |
|----|----------|-------|-----------------|----------|
| TC-01 | Create user with valid data | Valid email, role, token | 201 Created, user in DB | High |
| TC-02 | Create user with duplicate email | Existing email | 409 Conflict | High |
| TC-03 | Create user with invalid Jira token | Bad token | 400 Bad Request | High |
| TC-04 | Non-admin access /admin/users | developer role | 403 Forbidden | High |
| TC-05 | Approve document with correct permissions | BA approves BRD | Success, Jira updated | High |
| TC-06 | Approve document without role permission | developer approves BRD | Permission denied | High |
| TC-07 | Approve document without project assignment | BA approves BRD in unassigned project | Permission denied | High |
| TC-08 | Approve same document twice | Same user, same version | Duplicate error | Medium |
| TC-09 | Reject document | Any role with permission | Rejection logged, Jira comment added | High |
| TC-10 | Deactivate last system_owner | Only 1 system_owner | 400 error, not deactivated | High |
| TC-11 | Auth with invalid credentials | Wrong token | Graceful degradation to shared account | High |
| TC-12 | Jira API failure during re-attach | Jira unavailable | Approval logged, jira_synced=false | Medium |
| TC-13 | List pending approvals | BA with 2 projects | Only documents in assigned projects | Medium |
| TC-14 | Get approval status | Any user | Full history returned | Medium |
| TC-15 | Update permission matrix | system_owner | Permissions updated, effective immediately | Medium |

---

## 11. Appendix

### Open Issues

| # | Issue | Impact | Decision Needed |
|---|-------|--------|-----------------|
| 1 | Should approval require ALL roles with can_approve to sign off, or just ONE? | Affects overall_status calculation | Recommend: configurable per document type |
| 2 | How to handle token rotation (user changes Jira token)? | Existing sessions may break | Recommend: admin updates token, next connection uses new token |
| 3 | Should there be an approval expiry (e.g., approval invalid after 30 days)? | Stale approvals on old versions | Recommend: no expiry, but approval tied to specific version |

### Change Log from BRD

- Added UC-09 (Authentication) with detailed graceful degradation flows
- Added pseudocode for permission validation logic
- Clarified that approval does NOT change document version (BR-20)
- Added jira_synced field to approval_log for retry handling
- Specified default permission matrix seeding process


---

## 12. Related UI Design

### 12.1 UI Specification Document

Full UI design specification: [UI-SPEC.md](UI-SPEC.md)

### 12.2 Screens

| # | Screen | Wireframe | Related Use Cases |
|---|--------|-----------|-------------------|
| 1 | User List Page | [ui-user-list.drawio](diagrams/ui-user-list.drawio) | UC-02, UC-04 |
| 2 | Create/Edit User Modal | [ui-create-user-modal.drawio](diagrams/ui-create-user-modal.drawio) | UC-01, UC-03 |
| 3 | Permission Matrix Page | [ui-permission-matrix.drawio](diagrams/ui-permission-matrix.drawio) | UC-07, UC-08 |

### 12.3 User Flows

| # | Flow | Diagram | Description |
|---|------|---------|-------------|
| 1 | Document Approval Flow | [ui-flow-approval.drawio](diagrams/ui-flow-approval.drawio) | Auth → Validate → Approve → Re-attach |

### 12.4 Design System

- Dark theme: `--bg: #0d1117`, `--surface: #161b22`, `--accent: #58a6ff`
- Font: System font stack (-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto)
- Border radius: 6px (inputs/buttons), 8px (cards/panels)
- Tech: HTML + vanilla JS, no framework, served by Java HttpServer
