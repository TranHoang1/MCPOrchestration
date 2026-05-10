# Business Requirements Document (BRD)

## MCPOrchestration — MTO-39: User Management & Document Approval — Role-based approval với per-user Jira credentials

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-39 |
| Title | User Management & Document Approval — Role-based approval với per-user Jira credentials |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |

---

## Author Tracking

| Role | Name - Position | Responsibility |
|------|-----------------|----------------|
| Author | BA Agent – Business Analyst | Create document |
| Peer Reviewer | Duc Nguyen – System Owner | Review document |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-39 |

---

## Sign-Off

| Name | Signature and date |
|------|--------------------|
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |
| | ☐ I agree and confirm all criteria on this BRD as expected requirements |

---

## 1. Introduction

### 1.1 Scope

Implement a centralized User Management system on the MCP Orchestrator Server (HTTP standalone mode) that enables:
- **User identity tracking** — know exactly who reviews/approves each document
- **Per-user Jira credentials** — each user's Jira actions (attach, comment) use their own account instead of a shared service account
- **Role-based document approval** — enforce permission matrix determining which roles can approve which document types
- **Admin UI** — web-based user management interface at `/admin` endpoint
- **Audit trail** — complete logging of all approval decisions

### 1.2 Out of Scope

- OAuth2/SSO integration (future enhancement — current auth uses Jira email + API token)
- Multi-tenant isolation (single organization deployment)
- Document content editing through the approval system (approval is sign-off only)
- Notification system (email/Slack alerts for pending approvals)
- Mobile-responsive Admin UI (desktop-first)

### 1.3 Preliminary Requirement

- MCP Orchestrator Server running in HTTP standalone mode (transport: "sse" or "http-streamable")
- PostgreSQL database (already used for KB store, vector DB, audit logs)
- Existing Jira MCP server connection infrastructure (upstream server management)
- Existing security module (`com.orchestrator.mcp.security`) with RLS support

---

## 2. Business Requirements

### 2.1 High Level Process Map

The system introduces a user-centric approval workflow:

1. **Admin** creates user accounts with roles and project assignments
2. **User** authenticates via Jira email + API token when connecting through bridge/server
3. **Server** resolves user identity → role → permissions
4. **User** reviews documents and invokes `approve_document` MCP tool
5. **Server** validates permissions, updates document, re-attaches to Jira using user's credentials
6. **Audit log** records all approval actions

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|------------------|----------|---------------|
| 1 | As an admin, I want to manage user accounts (CRUD) so that I can control who has access to the system | MUST HAVE | MTO-39 |
| 2 | As an admin, I want to assign users to Jira projects so that users can only approve documents in their assigned projects | MUST HAVE | MTO-39 |
| 3 | As an admin, I want to configure role-permission matrix so that I can control which roles approve which document types | MUST HAVE | MTO-39 |
| 4 | As a user, I want to authenticate with my Jira credentials so that my actions are attributed to me | MUST HAVE | MTO-39 |
| 5 | As a user, I want to approve/reject documents so that the approval workflow is tracked | MUST HAVE | MTO-39 |
| 6 | As a user, I want to see pending approvals so that I know what documents need my review | SHOULD HAVE | MTO-39 |
| 7 | As a user, I want to see approval status of documents so that I know who has already approved | SHOULD HAVE | MTO-39 |
| 8 | As a system, I want to re-attach documents using the approver's Jira account so that Jira shows the correct author | MUST HAVE | MTO-39 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** System Owner/Team Lead accesses Admin UI at `/admin` and creates user accounts with email, role, and display name.

**Step 2:** Admin assigns users to specific Jira projects (e.g., user "john@company.com" → project "MTO").

**Step 3:** When a user connects via bridge (stdio mode), they provide their Jira email and API token for authentication.

**Step 4:** Server looks up the user in the database, resolves their role and project permissions.

**Step 5:** Server spawns a dedicated Jira MCP connection using the user's credentials (not the shared service account).

**Step 6:** User reviews a document and calls `approve_document(ticket_key, document_type, decision)`.

**Step 7:** Server validates: Does the user have the correct role AND project assignment to approve this document type?

**Step 8:** If authorized: Server updates the document's approval section (adds reviewer name + timestamp), re-attaches to Jira using user's account, removes the old attachment, and logs the action.

**Step 9:** If unauthorized: Server returns permission denied error with explanation of what's missing.

> **Note:** The approval does NOT change the document version — it only adds the reviewer's sign-off to the existing version.

---

#### STORY 1: User Account Management (CRUD)

> As an admin, I want to manage user accounts (CRUD) so that I can control who has access to the system.

**Requirement Details:**

1. Admin UI provides a web page at `/admin/users` to list, create, edit, and deactivate user accounts
2. Only users with role `leader` or `system_owner` can access the Admin UI
3. User accounts store: email (unique identifier), encrypted Jira API token, role, display name, active status
4. Supported roles: `developer`, `ba`, `architect`, `qa`, `devops`, `leader`, `system_owner`
5. Deactivation (soft delete) — users are never hard-deleted to preserve audit trail integrity
6. Admin can reset/update a user's Jira token
7. The `created_by` field tracks which admin created each user account

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| id | UUID | Auto | Unique identifier | `550e8400-e29b-41d4-a716-446655440000` |
| email | String | Yes | Jira email (unique) | `john@company.com` |
| jira_token_encrypted | String | Yes | AES-256 encrypted Jira API token | `encrypted_blob` |
| role | Enum | Yes | User role | `ba` |
| display_name | String | Yes | Human-readable name | `John Doe` |
| created_by | UUID | Yes | Admin who created this user | `admin-uuid` |
| active | Boolean | Yes | Account active status | `true` |
| created_at | Timestamp | Auto | Creation timestamp | `2026-05-09T10:00:00Z` |

**Acceptance Criteria:**

1. Admin can create a new user with email, role, display name, and Jira token
2. Admin can list all users with filtering by role and active status
3. Admin can edit user's role, display name, and Jira token
4. Admin can deactivate/reactivate a user account
5. Non-admin users receive 403 Forbidden when accessing `/admin/*` endpoints
6. Email uniqueness is enforced — duplicate email returns clear error message
7. Jira token is stored encrypted at rest (AES-256-GCM)

**Validation Rules:**

- Email must be valid email format
- Role must be one of the 7 defined roles
- Display name: 2-100 characters, non-empty
- Jira token: non-empty string (validated by attempting Jira API call)

**Error Handling:**

- Duplicate email: "User with email {email} already exists"
- Invalid role: "Invalid role '{role}'. Valid roles: developer, ba, architect, qa, devops, leader, system_owner"
- Unauthorized access: "Access denied. Only leader or system_owner can manage users"
- Invalid Jira token: "Jira token validation failed — unable to authenticate with Jira"

---

#### STORY 2: Project Assignment

> As an admin, I want to assign users to Jira projects so that users can only approve documents in their assigned projects.

**Requirement Details:**

1. Admin can assign one or more Jira project keys to a user
2. A user can only approve documents belonging to tickets in their assigned projects
3. Project assignment is independent of role — role determines document type permission, project determines scope
4. Admin can revoke project assignments
5. `granted_by` and `granted_at` fields track who assigned the project and when

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| user_id | UUID | Yes | Reference to user | `user-uuid` |
| project_key | String | Yes | Jira project key | `MTO` |
| granted_by | UUID | Yes | Admin who granted access | `admin-uuid` |
| granted_at | Timestamp | Auto | When access was granted | `2026-05-09T10:00:00Z` |

**Acceptance Criteria:**

1. Admin can assign a project to a user via `/admin/users/{id}/projects`
2. Admin can list all projects assigned to a user
3. Admin can revoke a project assignment
4. User attempting to approve a document in an unassigned project receives clear error
5. Composite unique constraint on (user_id, project_key) prevents duplicate assignments

**Validation Rules:**

- Project key must match pattern `[A-Z][A-Z0-9_]+` (valid Jira project key format)
- User must exist and be active
- Cannot assign same project twice to same user

**Error Handling:**

- User not found: "User with id {id} not found"
- Duplicate assignment: "User already assigned to project {project_key}"
- Invalid project key format: "Invalid project key format. Expected: uppercase letters/numbers (e.g., MTO, SCRUM)"

---

#### STORY 3: Role-Permission Matrix Configuration

> As an admin, I want to configure role-permission matrix so that I can control which roles approve which document types.

**Requirement Details:**

1. System provides a default permission matrix (as defined in Jira ticket)
2. Admin can view the current permission matrix via `/admin/roles`
3. Admin can update permissions for a specific role via `/admin/roles/{role}/permissions`
4. Permission types: `can_view` and `can_approve` per document type
5. Document types: BRD, FSD, TDD, STP/STC, DPG, UG

**Default Permission Matrix:**

| Role | BRD | FSD | TDD | STP/STC | DPG | UG |
|------|-----|-----|-----|---------|-----|-----|
| developer | view | view | view | view | view | view |
| ba | approve | approve | view | view | view | approve |
| architect | view | approve | approve | view | view | view |
| qa | view | view | view | approve | view | view |
| devops | view | view | view | view | approve | view |
| leader | approve | approve | approve | approve | approve | approve |
| system_owner | approve | approve | approve | approve | approve | approve |

**Acceptance Criteria:**

1. GET `/admin/roles` returns the full permission matrix
2. PUT `/admin/roles/{role}/permissions` updates permissions for a role
3. Default matrix is seeded on first system startup
4. Changes to permission matrix take effect immediately (no restart required)
5. Only `leader` and `system_owner` can modify the permission matrix

**Error Handling:**

- Invalid role: "Role '{role}' does not exist"
- Invalid document type: "Document type '{type}' is not recognized. Valid types: BRD, FSD, TDD, STP_STC, DPG, UG"
- Unauthorized: "Only leader or system_owner can modify role permissions"

---

#### STORY 4: User Authentication

> As a user, I want to authenticate with my Jira credentials so that my actions are attributed to me.

**Requirement Details:**

1. When bridge/server starts in stdio mode, user provides Jira email and API token
2. Server looks up user in the database by email
3. If user found and active: resolve role, projects, permissions
4. Server spawns a dedicated Jira MCP connection using the user's decrypted credentials
5. All subsequent Jira actions (attach, comment, transition) use the user's account
6. If user not found or inactive: fall back to shared service account with `developer` (view-only) permissions

**Acceptance Criteria:**

1. User authenticates with email + Jira API token at connection time
2. Server validates credentials by making a test Jira API call (e.g., get current user)
3. Successful auth: user's role and permissions are loaded into session context
4. Failed auth (invalid token): clear error message, connection still works but with view-only permissions
5. User not in DB: connection works with shared account, view-only permissions, warning logged
6. Each authenticated user gets their own Jira MCP connection instance

**Error Handling:**

- Invalid credentials: "Jira authentication failed for {email}. Using shared account with view-only access."
- User inactive: "Account for {email} is deactivated. Contact admin to reactivate."
- Database unavailable: "User database unavailable. Using shared account with view-only access."

---

#### STORY 5: Document Approval

> As a user, I want to approve/reject documents so that the approval workflow is tracked.

**Requirement Details:**

1. User invokes `approve_document` MCP tool with parameters: ticket_key, document_type, decision (approve/reject), optional comment
2. Server validates: user has correct role for document_type AND user is assigned to the ticket's project
3. If authorized and decision = "approve":
   - Read current document from Jira attachment
   - Add reviewer info to document's Sign-Off section (name, date, decision)
   - Re-attach updated document to Jira using user's Jira account
   - Delete the old attachment version from Jira
   - Log approval in audit trail
4. If authorized and decision = "reject":
   - Log rejection in audit trail with comment
   - Add rejection comment to Jira ticket using user's account
   - Do NOT modify the document attachment
5. Document version number does NOT change on approval — only the Sign-Off section is updated

**Data Fields (approve_document input):**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| ticket_key | String | Yes | Jira ticket key | `MTO-39` |
| document_type | Enum | Yes | Document type to approve | `BRD` |
| decision | Enum | Yes | Approve or reject | `approve` |
| comment | String | No | Optional reviewer comment | `"LGTM, requirements are clear"` |

**Acceptance Criteria:**

1. `approve_document` tool validates user permissions before any action
2. Approved document has reviewer name + timestamp added to Sign-Off section
3. Document is re-attached to Jira using the approver's Jira account (not shared account)
4. Old attachment is deleted from Jira after successful re-attach
5. Rejection adds a comment to Jira ticket with rejection reason
6. Audit log records: who, when, what document, what decision, optional comment
7. User cannot approve documents in projects they're not assigned to
8. User cannot approve document types their role doesn't permit

**Error Handling:**

- Permission denied (role): "Role '{role}' cannot approve {document_type}. Required roles: {allowed_roles}"
- Permission denied (project): "User not assigned to project '{project_key}'. Contact admin for access."
- Document not found: "No {document_type} attachment found on ticket {ticket_key}"
- Jira API failure: "Failed to update Jira attachment. Approval logged locally but Jira not updated."
- Already approved by same user: "You have already approved this document version"

---

#### STORY 6: List Pending Approvals

> As a user, I want to see pending approvals so that I know what documents need my review.

**Requirement Details:**

1. `list_pending_approvals` MCP tool returns documents awaiting the current user's approval
2. Filters by: user's role permissions + user's project assignments
3. A document is "pending" if it has been attached to Jira but not yet approved by a user with the required role
4. Results include: ticket key, document type, version, attached date, current approval status

**Acceptance Criteria:**

1. Tool returns only documents the current user CAN approve (based on role + project)
2. Documents already approved by the user are excluded
3. Results are sorted by attached date (oldest first — FIFO)
4. Empty result returns friendly message: "No pending approvals for your role"

---

#### STORY 7: Get Approval Status

> As a user, I want to see approval status of documents so that I know who has already approved.

**Requirement Details:**

1. `get_approval_status` MCP tool returns the approval history for a specific document
2. Input: ticket_key, document_type
3. Output: list of approvals (who, when, decision, comment) + overall status (pending/approved/rejected)

**Acceptance Criteria:**

1. Returns complete approval history for the specified document
2. Shows which roles have approved and which are still pending
3. Overall status: "approved" only when all required roles have approved
4. Any rejection makes overall status "rejected" until document is re-submitted

---

#### STORY 8: Re-attach with User's Jira Account

> As a system, I want to re-attach documents using the approver's Jira account so that Jira shows the correct author.

**Requirement Details:**

1. When a document is approved, the system re-attaches it to Jira using the approver's credentials
2. The old attachment (same document type, same version) is deleted from Jira
3. The new attachment shows the approver as the "uploaded by" user in Jira
4. If re-attach fails (e.g., Jira API error), the approval is still logged locally but marked as "jira_sync_pending"

**Acceptance Criteria:**

1. After approval, Jira shows the approver's name as attachment uploader
2. Old attachment is removed (no duplicate attachments)
3. If Jira API fails, approval is not rolled back — it's queued for retry
4. Attachment filename format preserved: `{DOC}-v{version}-{TICKET}.docx`

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| PostgreSQL Database | Infrastructure | MTO-10 | User tables, approval logs stored in existing PostgreSQL instance |
| Jira REST API | External | — | Attachment upload/delete, comment creation using per-user credentials |
| Existing Security Module | System | MTO-33 | Extend existing RLS and role context for user management |
| Existing Audit Module | System | MTO-34 | Leverage existing audit infrastructure for approval logging |
| Existing Session Module | System | — | Extend session management to include user identity context |
| AES-256-GCM Encryption | System | — | For encrypting Jira API tokens at rest |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| System Owner | Duc Nguyen | Define requirements, approve design | Reporter |
| Development Team | Dev Agent | Implement user management and approval system | — |
| QA Team | QA Agent | Test permission enforcement and approval flows | — |
| End Users | All team members | Use approval workflow for document sign-off | — |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Jira API token exposure | High | Low | AES-256-GCM encryption at rest, never log tokens |
| Permission bypass | High | Low | Server-side validation on every approval request, unit tests for all permission paths |
| Jira API rate limiting | Medium | Medium | Queue re-attach operations, implement retry with backoff |
| Token rotation breaks connections | Medium | Medium | Graceful fallback to shared account, clear error messages |
| Admin accidentally deactivates all admins | High | Low | Prevent deactivation of last system_owner account |

### 5.2 Assumptions

- All team members have individual Jira Cloud accounts with API token access
- The existing PostgreSQL database has capacity for additional tables
- Jira Cloud API supports attachment upload/delete per user (confirmed)
- The MCP Orchestrator Server is deployed in HTTP standalone mode for Admin UI access
- Encryption key for Jira tokens is managed via environment variable (not stored in DB)

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Admin API response time < 500ms | CRUD operations on user table with < 1000 users |
| Performance | Approval validation < 200ms | Permission check should not add noticeable latency |
| Security | Jira tokens encrypted at rest | AES-256-GCM with key from environment variable |
| Security | Admin endpoints protected | Only leader/system_owner roles can access /admin/* |
| Security | No token in logs | Jira tokens must never appear in application logs |
| Availability | Graceful degradation | If user DB unavailable, fall back to shared account with view-only |
| Scalability | Support up to 100 users | Sufficient for team-scale deployment |
| Auditability | Complete audit trail | Every approval/rejection logged with timestamp, user, decision |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-39 | User Management & Document Approval | Docs Review | Story | Main ticket |
| MTO-33 | BR Access Control Module | Done | Story | Provides existing security/RLS infrastructure |
| MTO-34 | Audit Log & Response Shaping | Done | Story | Provides existing audit logging infrastructure |
| MTO-10 | Core MCP Orchestrator | Done | Story | Base system with PostgreSQL, Koin DI, Ktor server |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| MCP | Model Context Protocol — standard for AI tool communication |
| BRD | Business Requirements Document |
| FSD | Functional Specification Document |
| TDD | Technical Design Document |
| STP/STC | Software Test Plan / Software Test Cases |
| DPG | Deployment Guide |
| UG | User Guide |
| RLS | Row-Level Security — PostgreSQL feature for data isolation |
| Bridge | MCP client bridge that connects to orchestrator server via stdio |

### Reference Documents

| Document | Link / Location |
|----------|-----------------|
| MTO-39 Jira Ticket | https://jiraassist.atlassian.net/browse/MTO-39 |
| MTO Workflow | documents/workflows/MTO-workflows.md |
| Project Structure | .analysis/code-intelligence/project-structure.md |
| Existing Security Module | orchestrator-server/src/main/kotlin/com/orchestrator/mcp/security/ |
| Existing Audit Module | orchestrator-server/src/main/kotlin/com/orchestrator/mcp/audit/ |
