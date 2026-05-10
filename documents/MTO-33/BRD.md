# Business Requirements Document (BRD)

## MCPOrchestration — MTO-33: KB Refinery — Business Rules Encryption & Access Control

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-33 |
| Title | KB Refinery — Business Rules Encryption & Access Control |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initial document — auto-generated from Jira ticket MTO-33 |

---

## 1. Introduction

### 1.1 Scope

Implement full D.4 security for Business Rules (BR) content: AES-256-GCM encryption at rest, KMS key management, sensitivity-level-based access control, rate limiting, session-based access tokens, and Data Loss Prevention (DLP) measures. This is the most comprehensive security ticket in Phase 3, building on RLS (MTO-31) and BR masking (MTO-30).

### 1.2 Out of Scope

- PII-specific access control (covered by MTO-32)
- General audit logging service (covered by MTO-34)
- RLS policy creation (covered by MTO-31)
- BR masking logic (covered by MTO-30)
- Key rotation automation (future enhancement)

### 1.3 Preliminary Requirements

| Dependency | Description |
|------------|-------------|
| MTO-26 | Encryption infrastructure exists |
| MTO-30 | BR masking logic exists |
| MTO-31 | RLS policies in place |
| AES-256-GCM | Java Cryptography Extension (JCE) unlimited strength |

---

## 2. Business Requirements

### 2.1 High Level Process Map

Business Rules are encrypted at rest using AES-256-GCM. When a user requests BR content, the system validates their session token, checks their role against the BR's sensitivity level, enforces rate limits, decrypts the content if authorized, and applies DLP headers to prevent caching. Every access is audited.

### 2.2 List of User Stories

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As a security architect, I want BR content encrypted at rest with AES-256-GCM so that data breach exposure is minimized | MUST HAVE | MTO-33 |
| 2 | As a system administrator, I want sensitivity-level-based access control so that highly sensitive BRs require higher authorization | MUST HAVE | MTO-33 |
| 3 | As a BA/Admin, I want session-based access tokens for BR viewing so that access is time-limited and revocable | MUST HAVE | MTO-33 |
| 4 | As a security officer, I want DLP measures on BR responses so that decrypted content cannot be cached or leaked | MUST HAVE | MTO-33 |
| 5 | As a system administrator, I want rate limiting on BR access so that bulk extraction is prevented | SHOULD HAVE | MTO-33 |
| 6 | As a DevOps engineer, I want KMS-managed encryption keys so that key lifecycle is properly managed | SHOULD HAVE | MTO-33 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** User authenticates and receives a session token (valid 30 minutes)

**Step 2:** User requests BR content for a specific issue_key

**Step 3:** BrAccessController validates session token (not expired, not revoked)

**Step 4:** BrAccessController checks user's role against BR sensitivity level

**Step 5:** BrAccessController checks rate limit (per user, per sensitivity level)

**Step 6:** If all checks pass → decrypt BR using AES-256-GCM with KMS-managed key

**Step 7:** Return decrypted BR with DLP headers (Cache-Control: no-store, no-cache)

**Step 8:** Log access event in audit trail

---

#### STORY 1: AES-256-GCM Encryption at Rest

> As a security architect, I want BR content encrypted at rest with AES-256-GCM so that data breach exposure is minimized

**Requirement Details:**

1. BR column in kb_entries is encrypted using AES-256-GCM
2. Each BR entry has a unique IV (Initialization Vector) — never reused
3. Encryption key managed by KMS (Key Management Service)
4. Key ID stored alongside encrypted data for key rotation support
5. Encrypted format: `{key_id}:{iv_base64}:{ciphertext_base64}`

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| business_rules_encrypted | TEXT | Yes | Encrypted BR content | "key1:abc123:encrypted..." |
| br_key_id | VARCHAR(50) | Yes | KMS key identifier | "br-key-2026-05" |
| br_iv | BYTEA | Yes | Initialization vector | (12 bytes random) |
| br_sensitivity_level | INTEGER | Yes | 1=High, 2=Medium, 3=Low | 2 |

**Acceptance Criteria:**

1. GIVEN BR content WHEN stored in database THEN it is encrypted (not plaintext)
2. GIVEN encrypted BR WHEN decrypted with correct key THEN original content is restored exactly
3. GIVEN two BRs encrypted with same key WHEN comparing ciphertext THEN they are different (unique IV)
4. GIVEN a database dump WHEN examined THEN no BR plaintext is visible

---

#### STORY 2: Sensitivity-Level-Based Access Control

> As a system administrator, I want sensitivity-level-based access control so that highly sensitive BRs require higher authorization

**Requirement Details:**

1. Three sensitivity levels: Level 1 (High), Level 2 (Medium), Level 3 (Low)
2. Access matrix:
   - Level 1 (High sensitivity): Head of Department and above only
   - Level 2 (Medium sensitivity): BA/Admin role
   - Level 3 (Low sensitivity): Senior Developer and above
3. Sensitivity level is set per BR entry when content is stored
4. Access denied returns generic "insufficient permissions" (no level leakage)

**Acceptance Criteria:**

1. GIVEN BR with sensitivity_level=1 WHEN accessed by BA/Admin THEN access denied
2. GIVEN BR with sensitivity_level=2 WHEN accessed by BA/Admin THEN access granted
3. GIVEN BR with sensitivity_level=3 WHEN accessed by Senior Developer THEN access granted
4. GIVEN BR with sensitivity_level=3 WHEN accessed by Junior Developer THEN access denied
5. GIVEN access denied WHEN error returned THEN no information about required level is leaked

---

#### STORY 3: Session-Based Access Tokens

> As a BA/Admin, I want session-based access tokens for BR viewing so that access is time-limited and revocable

**Requirement Details:**

1. BR access requires a valid session token
2. Token expires after 30 minutes of inactivity
3. Token can be explicitly revoked (logout, admin action)
4. Token is bound to user identity and role at creation time
5. Expired/revoked token requires re-authentication

**Acceptance Criteria:**

1. GIVEN valid session token WHEN accessing BR THEN access proceeds to role check
2. GIVEN expired token (>30 min) WHEN accessing BR THEN 401 Unauthorized with "session expired"
3. GIVEN revoked token WHEN accessing BR THEN 401 Unauthorized with "session revoked"
4. GIVEN token created with role=admin WHEN role changed to viewer THEN existing token still works until expiry (role at creation time)

---

#### STORY 4: DLP (Data Loss Prevention)

> As a security officer, I want DLP measures on BR responses so that decrypted content cannot be cached or leaked

**Requirement Details:**

1. All API responses containing decrypted BR MUST include: `Cache-Control: no-store, no-cache, must-revalidate`
2. All API responses containing decrypted BR MUST include: `Pragma: no-cache`
3. All API responses containing decrypted BR MUST include: `X-Content-Type-Options: nosniff`
4. Response body MUST NOT be logged by application logging framework
5. Decrypted BR content MUST NOT be stored in any intermediate cache (Redis, CDN, etc.)

**Acceptance Criteria:**

1. GIVEN BR response WHEN inspecting HTTP headers THEN Cache-Control: no-store is present
2. GIVEN BR response WHEN checking application logs THEN decrypted content is NOT in logs
3. GIVEN BR response WHEN checking any cache layer THEN decrypted content is NOT cached
4. GIVEN BR response WHEN browser receives it THEN browser does not cache the response

---

#### STORY 5: Rate Limiting for BR Access

> As a system administrator, I want rate limiting on BR access so that bulk extraction is prevented

**Requirement Details:**

1. Rate limits per sensitivity level:
   - Level 1: 5 access/hour per user
   - Level 2: 15 access/hour per user
   - Level 3: 30 access/hour per user
2. Sliding window rate limiting
3. Rate limit counter persisted in database

**Acceptance Criteria:**

1. GIVEN user accessed 5 Level-1 BRs in last hour WHEN requesting 6th THEN 429 Too Many Requests
2. GIVEN user accessed 15 Level-2 BRs in last hour WHEN requesting 16th THEN 429 Too Many Requests
3. GIVEN rate limit window expired WHEN requesting BR THEN access succeeds

---

#### STORY 6: KMS Key Management

> As a DevOps engineer, I want KMS-managed encryption keys so that key lifecycle is properly managed

**Requirement Details:**

1. Encryption keys stored in KMS (initially file-based KMS for development)
2. Key rotation support: new key for new encryptions, old keys retained for decryption
3. Key ID tracked per encrypted entry
4. Master key encrypted with environment-specific passphrase
5. Key metadata: creation date, expiry date, status (active/retired/revoked)

**Acceptance Criteria:**

1. GIVEN KMS configured WHEN encrypting new BR THEN current active key is used
2. GIVEN key rotated WHEN decrypting old BR THEN old key (by key_id) is used successfully
3. GIVEN key revoked WHEN attempting decrypt with revoked key THEN operation fails with clear error
4. GIVEN KMS unavailable WHEN attempting BR access THEN fail-closed (no access)

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| MTO-26 | System | MTO-26 | Encryption infrastructure |
| MTO-30 | System | MTO-30 | BR masking logic |
| MTO-31 | System | MTO-31 | RLS policies |
| MTO-34 | Feature | MTO-34 | Audit logging integration |
| JCE Unlimited | Infrastructure | — | Java Cryptography Extension |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility |
|------|-------------|----------------|
| Product Owner | Duc Nguyen | Approve requirements, UAT |
| Security Architect | Dev Team | Review encryption design |
| Developer | Dev Team | Implement BrAccessController |
| QA | QA Team | Test security controls |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Key compromise exposes all BR data | Critical | Low | Key rotation, HSM in production |
| Session token theft | High | Medium | Short expiry (30 min), IP binding |
| Performance impact of encryption/decryption | Medium | Medium | Benchmark, consider caching decrypted in memory briefly |
| DLP bypass via browser dev tools | Medium | High | Accept as residual risk, audit trail provides accountability |

### 5.2 Assumptions

- JCE Unlimited Strength is available in JVM 21 (default since JDK 9)
- File-based KMS is acceptable for development/staging; production will use cloud KMS
- Session management is handled at application level (not delegated to external IdP for BR access)

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Security | AES-256-GCM encryption | NIST-approved authenticated encryption |
| Security | Key rotation support | Without re-encrypting all existing data |
| Performance | Decrypt latency < 10ms | Per BR entry |
| Performance | Session validation < 5ms | Token lookup and validation |
| Availability | Fail-closed | If KMS unavailable, deny all BR access |
| Compliance | Audit all access | Every BR access logged (MTO-34) |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-33 | KB Refinery — BR Encryption & Access Control | Docs Review | Story | Main ticket |
| MTO-26 | KB Refinery — Encryption Infrastructure | Done | Story | Prerequisite |
| MTO-30 | KB Refinery — BR Masking | Done | Story | Prerequisite |
| MTO-31 | KB Refinery — PostgreSQL RLS Policies | Docs Review | Story | Prerequisite |
| MTO-34 | KB Refinery — Audit Log & Response Shaping | Docs Review | Story | Integration |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| AES-256-GCM | Advanced Encryption Standard with 256-bit key in Galois/Counter Mode |
| KMS | Key Management Service — manages encryption key lifecycle |
| DLP | Data Loss Prevention — measures to prevent data leakage |
| IV | Initialization Vector — random value ensuring unique ciphertext |
| Sensitivity Level | Classification of BR content criticality (1=High, 2=Medium, 3=Low) |

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case Diagram | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
