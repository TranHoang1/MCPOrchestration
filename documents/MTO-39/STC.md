# Software Test Cases (STC)

## MCPOrchestration — MTO-39: User Management & Document Approval

---

## 1. Unit Tests

### 1.1 TokenEncryptionService

| ID | Test Case | Input | Expected | Priority |
|----|-----------|-------|----------|----------|
| UT-01 | Encrypt and decrypt roundtrip | "my-secret-token" | Decrypted == original | High |
| UT-02 | Different encryptions produce different ciphertext | Same plaintext twice | ciphertext1 != ciphertext2 (random IV) | High |
| UT-03 | Decrypt with wrong key fails | Valid ciphertext, wrong key | Throws exception | High |
| UT-04 | Missing encryption key env | Env var not set | Throws IllegalStateException | High |

### 1.2 UserServiceImpl

| ID | Test Case | Input | Expected | Priority |
|----|-----------|-------|----------|----------|
| UT-05 | Create user with valid data | Valid CreateUserRequest | User created, token encrypted | High |
| UT-06 | Create user with duplicate email | Existing email | Throws DuplicateEmailException | High |
| UT-07 | Create user with invalid email | "not-an-email" | Throws IllegalArgumentException | Medium |
| UT-08 | Create user with short display name | displayName="A" | Throws IllegalArgumentException | Medium |
| UT-09 | Deactivate user | Active user ID | User.active = false | High |
| UT-10 | Deactivate last system_owner | Only 1 active system_owner | Throws LastAdminException | High |
| UT-11 | Update user role | Valid UUID, new role | User.role updated | Medium |
| UT-12 | Update user with new Jira token | Valid UUID, new token | Token re-encrypted | Medium |

### 1.3 PermissionServiceImpl

| ID | Test Case | Input | Expected | Priority |
|----|-----------|-------|----------|----------|
| UT-13 | BA can approve BRD | role=BA, docType=BRD | PermissionResult.Authorized | High |
| UT-14 | Developer cannot approve BRD | role=DEVELOPER, docType=BRD | PermissionResult.Denied | High |
| UT-15 | User not assigned to project | Valid role, wrong project | PermissionResult.Denied | High |
| UT-16 | Duplicate approval blocked | Same user+ticket+doc+version | PermissionResult.AlreadyApproved | High |
| UT-17 | Leader can approve all types | role=LEADER, any docType | PermissionResult.Authorized | Medium |
| UT-18 | System_owner can approve all | role=SYSTEM_OWNER, any docType | PermissionResult.Authorized | Medium |
| UT-19 | QA can only approve STP_STC | role=QA, docType=TDD | PermissionResult.Denied | Medium |

### 1.4 ApprovalServiceImpl

| ID | Test Case | Input | Expected | Priority |
|----|-----------|-------|----------|----------|
| UT-20 | Approve document successfully | Authorized user, valid request | ApprovalResult(success=true) | High |
| UT-21 | Reject document successfully | Authorized user, decision=reject | ApprovalResult(success=true), comment logged | High |
| UT-22 | Approve with permission denied | Unauthorized user | ApprovalResult(success=false) | High |
| UT-23 | Get approval status - pending | No approvals yet | overallStatus="pending" | Medium |
| UT-24 | Get approval status - approved | All required roles approved | overallStatus="approved" | Medium |
| UT-25 | Get approval status - rejected | One rejection exists | overallStatus="rejected" | Medium |

### 1.5 AdminAuthMiddleware

| ID | Test Case | Input | Expected | Priority |
|----|-----------|-------|----------|----------|
| UT-26 | Valid admin header | X-User-Email of leader | Returns admin user ID | High |
| UT-27 | Missing header | No X-User-Email | Throws PermissionDeniedException | High |
| UT-28 | Non-admin user | X-User-Email of developer | Throws PermissionDeniedException | High |
| UT-29 | Inactive admin | X-User-Email of deactivated leader | Throws PermissionDeniedException | High |

---

## 2. Integration Tests (Testcontainers PostgreSQL)

| ID | Test Case | Setup | Steps | Expected | Priority |
|----|-----------|-------|-------|----------|----------|
| IT-01 | Create and retrieve user | Empty DB | 1. Create user 2. Find by email | User found with correct fields | High |
| IT-02 | Email uniqueness constraint | User exists | 1. Create user with same email | PSQLException (unique violation) | High |
| IT-03 | Soft delete preserves data | Active user | 1. Deactivate 2. Query all | User exists with active=false | High |
| IT-04 | User filter by role | 3 users (BA, QA, DEV) | 1. Filter by role=BA | Returns 1 user | Medium |
| IT-05 | Project assignment CRUD | User exists | 1. Assign 2. List 3. Revoke | Assignment created then removed | High |
| IT-06 | Duplicate project assignment | Assignment exists | 1. Assign same project | PSQLException (unique violation) | High |
| IT-07 | Permission matrix seeding | Empty role_permissions | 1. Call seedDefaults() | 42 records created | High |
| IT-08 | Approval log insert and query | User + permission exists | 1. Insert approval 2. Query by ticket | Entry found | High |
| IT-09 | Duplicate approval check | Approval exists | 1. Check exists() | Returns true | High |
| IT-10 | Migration idempotency | Tables exist | 1. Run migrate() twice | No errors, tables unchanged | Medium |

---

## 3. E2E API Tests (HTTP Endpoints)

| ID | Test Case | Method | Path | Headers | Body | Expected Status | Expected Body | Priority |
|----|-----------|--------|------|---------|------|-----------------|---------------|----------|
| E2E-01 | Create user | POST | /admin/users | X-User-Email: admin@test.com | Valid user JSON | 201 | User object | High |
| E2E-02 | Create user - duplicate | POST | /admin/users | X-User-Email: admin@test.com | Existing email | 409 | Error: DUPLICATE_EMAIL | High |
| E2E-03 | List users | GET | /admin/users | X-User-Email: admin@test.com | — | 200 | Array of users | High |
| E2E-04 | List users filtered | GET | /admin/users?role=BA | X-User-Email: admin@test.com | — | 200 | Filtered array | Medium |
| E2E-05 | Deactivate user | DELETE | /admin/users/{id} | X-User-Email: admin@test.com | — | 200 | User with active=false | High |
| E2E-06 | Assign project | POST | /admin/users/{id}/projects | X-User-Email: admin@test.com | {"projectKey":"MTO"} | 201 | Assignment object | High |
| E2E-07 | Revoke project | DELETE | /admin/users/{id}/projects/MTO | X-User-Email: admin@test.com | — | 200 | Success message | Medium |
| E2E-08 | Assign duplicate project | POST | /admin/users/{id}/projects | X-User-Email: admin@test.com | {"projectKey":"MTO"} | 409 | Error: DUPLICATE_PROJECT | Medium |
| E2E-09 | Get permission matrix | GET | /admin/roles | X-User-Email: admin@test.com | — | 200 | Array of permissions | Medium |
| E2E-10 | Update permissions | PUT | /admin/roles/BA/permissions | X-User-Email: admin@test.com | Permission update JSON | 200 | Updated permissions | Medium |
| E2E-11 | Non-admin access denied | GET | /admin/users | X-User-Email: dev@test.com | — | 403 | Error: PERMISSION_DENIED | High |
| E2E-12 | Missing auth header | GET | /admin/users | (none) | — | 403 | Error: PERMISSION_DENIED | High |
| E2E-13 | Deactivate last system_owner | DELETE | /admin/users/{id} | X-User-Email: admin@test.com | — | 400 | Error: LAST_ADMIN | High |
| E2E-14 | Invalid project key format | POST | /admin/users/{id}/projects | X-User-Email: admin@test.com | {"projectKey":"invalid"} | 400 | Error message | Medium |

---

## 4. MCP Tool Tests

| ID | Test Case | Tool | Arguments | User Context | Expected | Priority |
|----|-----------|------|-----------|-------------|----------|----------|
| MCP-01 | Approve BRD as BA | approve_document | ticket_key=MTO-39, doc_type=BRD, decision=approve | BA user, assigned to MTO | success=true | High |
| MCP-02 | Approve BRD as Developer (denied) | approve_document | ticket_key=MTO-39, doc_type=BRD, decision=approve | Developer user | success=false, permission denied | High |
| MCP-03 | Approve in unassigned project | approve_document | ticket_key=OTHER-1, doc_type=BRD, decision=approve | BA user, not assigned to OTHER | success=false, project denied | High |
| MCP-04 | Reject document | approve_document | ticket_key=MTO-39, doc_type=FSD, decision=reject, comment="Needs work" | Architect user | success=true, rejection logged | High |
| MCP-05 | Get approval status | get_approval_status | ticket_key=MTO-39, doc_type=BRD | Any user | Status with approvals list | Medium |
| MCP-06 | Approve same doc twice | approve_document | Same params as MCP-01 | Same BA user | success=false, already approved | Medium |
| MCP-07 | Missing required param | approve_document | ticket_key only | Any user | Error: missing parameter | Medium |
| MCP-08 | Invalid document type | approve_document | doc_type=INVALID | Any user | Error: invalid document_type | Medium |

---

## 5. Business Rule Verification Matrix

| Rule | Test Cases | Verification Method |
|------|-----------|-------------------|
| BR-01 | UT-26 to UT-29, E2E-11, E2E-12 | Non-admin gets 403 |
| BR-02 | UT-06, IT-02, E2E-02 | Duplicate email rejected |
| BR-03 | UT-09, IT-03, E2E-05 | Soft delete, user still queryable |
| BR-04 | UT-10, E2E-13 | Last system_owner protected |
| BR-05 | UT-05 | Token encrypted before storage |
| BR-06 | IT-01 | created_by field populated |
| BR-07 | E2E-14 | Invalid project key rejected |
| BR-08 | IT-06, E2E-08 | Duplicate assignment rejected |
| BR-09 | UT-09 (inactive user) | Inactive user cannot be assigned |
| BR-10 | UT-15, MCP-03 | Both role AND project required |
| BR-11 | IT-07 | Default matrix seeded |
| BR-12 | E2E-10 | Changes effective immediately |
| BR-13 | E2E-11 | Only admin can modify |
| BR-14 | MCP-08 | Invalid doc type rejected |
| BR-19 | MCP-01, MCP-02, MCP-03 | Both checks enforced |
| BR-20 | MCP-01 | Version unchanged after approval |
| BR-21 | UT-29 | Old attachment deleted |
| BR-22 | UT-30 | Approval logged even if Jira fails |
| BR-23 | UT-16, MCP-06 | Duplicate blocked |
| BR-24 | MCP-04 | Rejection doesn't modify document |
