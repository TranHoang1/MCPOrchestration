# User Guide (UG)

## MCPOrchestration — MTO-39: User Management & Document Approval

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-39 |
| Title | User Guide — User Management & Document Approval |
| Author | Dev Agent |
| Version | 1.0 |
| Date | 2026-05-10 |

---

## 1. Overview

The User Management & Document Approval system provides:
- **User account management** — create, update, deactivate user accounts with roles
- **Project assignment** — control which users can approve documents in which projects
- **Role-based approval** — enforce permission matrix for document approvals
- **Audit trail** — complete logging of all approval decisions
- **Per-user Jira credentials** — each user's actions attributed to their own Jira account

---

## 2. Quick Start

### 2.1 Prerequisites

- MCP Orchestrator Server running in HTTP standalone mode
- PostgreSQL database connected
- Environment variable `USER_MGMT_ENCRYPTION_KEY` set (32-byte base64 key)

### 2.2 First-Time Setup

1. Start the server — tables are created automatically
2. Create the first admin user (see §3.1)
3. Assign projects to users (see §3.2)
4. Users can now approve documents via MCP tools

---

## 3. Admin Operations

### 3.1 Managing Users

All admin operations require the `X-User-Email` header with an admin user's email.

#### Create a User

```bash
curl -X POST http://localhost:8080/admin/users \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@company.com" \
  -d '{
    "email": "john@company.com",
    "jiraToken": "ATATT3xFfGF0...",
    "role": "BA",
    "displayName": "John Doe"
  }'
```

**Response (201):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john@company.com",
  "role": "BA",
  "displayName": "John Doe",
  "active": true,
  "createdAt": "2026-05-10T10:00:00Z"
}
```

#### Available Roles

| Role | Can Approve | Admin Access |
|------|-------------|-------------|
| DEVELOPER | None (view only) | No |
| BA | BRD, FSD, UG | No |
| ARCHITECT | FSD, TDD | No |
| QA | STP_STC | No |
| DEVOPS | DPG | No |
| LEADER | All documents | Yes |
| SYSTEM_OWNER | All documents | Yes |

#### List Users

```bash
curl http://localhost:8080/admin/users \
  -H "X-User-Email: admin@company.com"

# Filter by role
curl "http://localhost:8080/admin/users?role=BA" \
  -H "X-User-Email: admin@company.com"

# Filter by active status
curl "http://localhost:8080/admin/users?active=true" \
  -H "X-User-Email: admin@company.com"
```

#### Update a User

```bash
curl -X PUT http://localhost:8080/admin/users/{user-id} \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@company.com" \
  -d '{
    "role": "ARCHITECT",
    "displayName": "John D."
  }'
```

#### Deactivate a User

```bash
curl -X DELETE http://localhost:8080/admin/users/{user-id} \
  -H "X-User-Email: admin@company.com"
```

> **Note:** Users are soft-deleted (deactivated), never permanently removed. This preserves the audit trail.

### 3.2 Managing Project Assignments

#### Assign a Project

```bash
curl -X POST http://localhost:8080/admin/users/{user-id}/projects \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@company.com" \
  -d '{"projectKey": "MTO"}'
```

#### List User's Projects

```bash
curl http://localhost:8080/admin/users/{user-id}/projects \
  -H "X-User-Email: admin@company.com"
```

#### Revoke a Project

```bash
curl -X DELETE http://localhost:8080/admin/users/{user-id}/projects/MTO \
  -H "X-User-Email: admin@company.com"
```

### 3.3 Managing Permission Matrix

#### View Current Matrix

```bash
curl http://localhost:8080/admin/roles \
  -H "X-User-Email: admin@company.com"
```

#### Update Permissions for a Role

```bash
curl -X PUT http://localhost:8080/admin/roles/BA/permissions \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@company.com" \
  -d '{
    "permissions": [
      {"documentType": "TDD", "canView": true, "canApprove": true}
    ]
  }'
```

---

## 4. Document Approval (MCP Tools)

### 4.1 Approve a Document

Use the `approve_document` MCP tool:

```json
{
  "ticket_key": "MTO-39",
  "document_type": "BRD",
  "decision": "approve",
  "comment": "Requirements are clear and complete"
}
```

**Successful response:**
```json
{
  "success": true,
  "message": "Document approved successfully",
  "approvalId": "a1b2c3d4-...",
  "jiraSynced": false
}
```

### 4.2 Reject a Document

```json
{
  "ticket_key": "MTO-39",
  "document_type": "FSD",
  "decision": "reject",
  "comment": "Missing error handling for UC-05"
}
```

### 4.3 Check Approval Status

Use the `get_approval_status` MCP tool:

```json
{
  "ticket_key": "MTO-39",
  "document_type": "BRD"
}
```

**Response:**
```json
{
  "ticketKey": "MTO-39",
  "documentType": "BRD",
  "overallStatus": "pending",
  "approvals": [
    {
      "userId": "...",
      "decision": "APPROVE",
      "comment": "LGTM",
      "createdAt": "2026-05-10T10:00:00Z"
    }
  ],
  "pendingRoles": ["ARCHITECT"]
}
```

### 4.4 Permission Requirements

To approve a document, you need **BOTH**:
1. **Role permission** — your role must have `can_approve = true` for that document type
2. **Project assignment** — you must be assigned to the ticket's project

---

## 5. Error Codes

| Code | HTTP Status | Description | Resolution |
|------|-------------|-------------|-----------|
| DUPLICATE_EMAIL | 409 | Email already registered | Use a different email |
| USER_NOT_FOUND | 404 | User ID doesn't exist | Check the user ID |
| PERMISSION_DENIED | 403 | Insufficient permissions | Contact admin for role/project access |
| LAST_ADMIN | 400 | Cannot deactivate last admin | Create another system_owner first |
| TOKEN_INVALID | 400 | Jira token validation failed | Check Jira API token is correct |
| DUPLICATE_APPROVAL | 409 | Already approved this version | No action needed |
| DOC_NOT_FOUND | 404 | Document not on Jira ticket | Attach document first |
| DUPLICATE_PROJECT | 409 | Project already assigned | No action needed |

---

## 6. Troubleshooting

### 6.1 Common Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| 403 on all admin endpoints | User not in DB or not admin role | Create user with LEADER/SYSTEM_OWNER role |
| "Encryption key env not set" | Missing env var | Set `USER_MGMT_ENCRYPTION_KEY` |
| "Jira token validation failed" | Invalid Jira API token | Generate new token at id.atlassian.com |
| Tables not created | Migration didn't run | Check server startup logs |
| Permission matrix empty | Seeding failed | Restart server, check DB connectivity |

### 6.2 Log Locations

| Log | Location | Content |
|-----|----------|---------|
| Server startup | stdout / logback | Migration status, tool registration |
| Approval actions | stdout / logback | Who approved what, when |
| Errors | stderr / logback | Permission denials, DB errors |

---

## 7. Security Notes

- **Jira tokens** are encrypted at rest using AES-256-GCM
- **Tokens never appear in logs** — only email addresses are logged
- **Admin access** requires LEADER or SYSTEM_OWNER role
- **Soft delete** preserves audit trail integrity
- **The encryption key** must be stored securely (not in code or config files)
