# Functional Specification Document (FSD)

## MCPOrchestration — MTO-33: KB Refinery — Business Rules Encryption & Access Control

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-33 |
| Title | KB Refinery — Business Rules Encryption & Access Control |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-33.docx |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the Business Rules (BR) encryption and access control system. It defines how BR content is encrypted at rest, how access is controlled by sensitivity level, how session tokens authorize BR viewing, and how DLP measures prevent data leakage.

### 1.2 Scope

- AES-256-GCM encryption of BR content at rest
- Sensitivity-level-based access control (3 levels)
- Session-based access tokens (30-minute expiry)
- DLP headers on BR responses
- Rate limiting per sensitivity level
- Key rotation support (re-encrypt with new key)

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| BR | Business Rules — sensitive business logic content |
| AES-256-GCM | Authenticated encryption algorithm |
| KMS | Key Management Service |
| DLP | Data Loss Prevention |
| IV | Initialization Vector |

---

## 2. System Overview

### 2.1 System Context

The BR Access Control system wraps the existing `KbEntryRepository` which already stores encrypted BR content. This system adds:
1. Sensitivity-level access control (beyond simple role check)
2. Session-based token management
3. DLP response headers
4. Rate limiting per sensitivity level
5. Key rotation capability

### 2.2 Component Interaction

```
User Request → BrAccessService
                ├── validateSession(token) → BrSessionService
                ├── checkSensitivityAccess(role, level) → access matrix
                ├── checkRateLimit(userId, level) → BrRateLimitService
                ├── decrypt(encryptedBr) → EncryptionService
                ├── applyDlpHeaders() → response headers
                └── logAccess() → AuditService (MTO-34)
```

---

## 3. Functional Requirements

### 3.1 Feature: BR Access with Sensitivity Control

**Source:** BRD Story 1, 2

#### 3.1.1 Use Case: UC-01 — View Business Rules

**Use Case ID:** UC-01
**Actor:** BA/Admin or authorized user
**Preconditions:** Valid session, appropriate role for BR sensitivity level
**Postconditions:** Decrypted BR returned with DLP headers, access logged

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Requests BR content for issue_key |
| 2 | | BrAccessService | Validates session token |
| 3 | | BrAccessService | Retrieves BR entry (encrypted) |
| 4 | | BrAccessService | Checks user role against BR sensitivity level |
| 5 | | BrAccessService | Checks rate limit for this sensitivity level |
| 6 | | EncryptionService | Decrypts BR content |
| 7 | | BrAccessService | Applies DLP headers to response |
| 8 | | AuditService | Logs VIEW_BR event |
| 9 | | BrAccessService | Returns decrypted BR content |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Session expired | Return 401 "Session expired" |
| EF-02 | Insufficient role for sensitivity level | Return 403 "Insufficient permissions" |
| EF-03 | Rate limit exceeded | Return 429 with retry-after |
| EF-04 | KMS unavailable | Return 503 "Service unavailable" (fail-closed) |
| EF-05 | Decryption failure | Return 500, log error, alert ops |

#### 3.1.2 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | BR encrypted at rest with AES-256-GCM, unique IV per entry | BRD Story 1 |
| BR-02 | Sensitivity Level 1: Head of Department only (mapped to special admin) | BRD Story 2 |
| BR-03 | Sensitivity Level 2: BA_ADMIN role | BRD Story 2 |
| BR-04 | Sensitivity Level 3: DEVELOPER and above | BRD Story 2 |
| BR-05 | Session token expires after 30 min inactivity | BRD Story 3 |
| BR-06 | DLP: Cache-Control: no-store on all BR responses | BRD Story 4 |
| BR-07 | DLP: BR content MUST NOT appear in application logs | BRD Story 4 |
| BR-08 | DLP: BR content MUST NOT be sent to external LLM | BRD Story 4 |
| BR-09 | Rate limit per sensitivity: L1=5/hr, L2=15/hr, L3=30/hr | BRD Story 5 |
| BR-10 | Key rotation: new key for new encryptions, old keys for decryption | BRD Story 6 |
| BR-11 | Fail-closed: if KMS unavailable, deny all BR access | BRD NFR |

#### 3.1.3 Sensitivity Level Access Matrix

| Sensitivity Level | Description | Allowed Roles | Rate Limit |
|-------------------|-------------|---------------|------------|
| 1 (High) | Critical business logic | BA_ADMIN (head-level) | 5/hour |
| 2 (Medium) | Standard business rules | BA_ADMIN | 15/hour |
| 3 (Low) | General guidelines | BA_ADMIN, DEVELOPER | 30/hour |

#### 3.1.4 API Contract (Functional View)

**Tool Name:** `view_business_rules`

**Input Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| issue_key | String | Yes | Target ticket |
| session_token | String | Yes | Valid BR access session |

**Output Data:**

| Field | Type | Description |
|-------|------|-------------|
| status | String | "success" / "denied" / "rate_limited" |
| content | String? | Decrypted BR content (only on success) |
| sensitivity_level | Int? | BR sensitivity level |
| dlp_headers | Map? | DLP response headers to apply |
| reason | String? | Denial reason |

---

### 3.2 Feature: Key Rotation

**Source:** BRD Story 6

#### 3.2.1 Use Case: UC-02 — Rotate Encryption Key

**Use Case ID:** UC-02
**Actor:** System Administrator
**Preconditions:** New key generated and stored in KMS
**Postconditions:** All BR entries re-encrypted with new key

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin | | Triggers key rotation |
| 2 | | BrKeyRotationService | Loads new key from KMS |
| 3 | | BrKeyRotationService | Iterates all BR entries |
| 4 | | BrKeyRotationService | Decrypts with old key |
| 5 | | BrKeyRotationService | Re-encrypts with new key |
| 6 | | BrKeyRotationService | Updates key_id reference |
| 7 | | BrKeyRotationService | Reports rotation result |

**Processing Logic:**

```
fun rotateKey(newKeyId: String, newKey: SecretKey): RotationResult {
    val entries = kbEntryRepository.findAllWithBr()
    var success = 0; var failed = 0
    
    entries.forEach { entry ->
        try {
            val decrypted = oldEncryption.decrypt(entry.businessRules)
            val reEncrypted = newEncryption.encrypt(decrypted)
            kbEntryRepository.updateBrEncryption(entry.id, reEncrypted, newKeyId)
            success++
        } catch (e: Exception) {
            failed++
            log.error("Rotation failed for entry={}: {}", entry.id, e.message)
        }
    }
    
    return RotationResult(total = entries.size, success = success, failed = failed)
}
```

---

### 3.3 Feature: DLP Enforcement

**Source:** BRD Story 4

#### 3.3.1 DLP Headers

All responses containing decrypted BR content MUST include:

| Header | Value | Purpose |
|--------|-------|---------|
| Cache-Control | no-store, no-cache, must-revalidate | Prevent browser/proxy caching |
| Pragma | no-cache | HTTP/1.0 compatibility |
| X-Content-Type-Options | nosniff | Prevent MIME sniffing |
| X-BR-DLP | enforced | Signal that DLP is active |

#### 3.3.2 Logging Restrictions

- BR content MUST be redacted from all log statements
- Log only: "BR accessed for issue_key={}, sensitivity_level={}"
- NEVER log: actual BR text content

---

## 4. Data Model

### 4.1 Logical Entities

#### Entity: br_access_session

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| token | UUID | Yes | Session token PK |
| user_id | VARCHAR(100) | Yes | Bound user |
| role | VARCHAR(20) | Yes | Role at creation |
| created_at | TIMESTAMPTZ | Yes | Creation time |
| expires_at | TIMESTAMPTZ | Yes | Expiry time |
| revoked | BOOLEAN | Yes | Revocation flag |

#### Existing Entity: kb_entries (modified columns)

| Attribute | Type | Description |
|-----------|------|-------------|
| business_rules | BYTEA | AES-256-GCM encrypted BR content |
| br_sensitivity_level | INTEGER | 1=High, 2=Medium, 3=Low (default 2) |

---

## 5. Security Requirements

### 5.1 Encryption Specification

| Property | Value |
|----------|-------|
| Algorithm | AES-256-GCM |
| Key size | 256 bits (32 bytes) |
| IV size | 12 bytes (96 bits) |
| Tag size | 128 bits |
| Key source | Environment variable (BR_ENCRYPTION_KEY) |
| Key format | Base64-encoded |

### 5.2 Fail-Closed Behavior

| Failure | Response |
|---------|----------|
| KMS/key unavailable | Deny all BR access |
| Decryption error | Deny access, alert ops |
| Audit service down | Still allow BR access (audit is async) |
| Session service down | Deny all BR access |

---

## 6. Non-Functional Requirements

| Category | Requirement | Acceptance Criteria |
|----------|-------------|---------------------|
| Performance | Decrypt < 10ms per entry | Measured under load |
| Performance | Session validation < 5ms | In-memory lookup |
| Security | No BR in logs | Log audit confirms |
| Security | No BR in cache | Cache-Control headers verified |
| Availability | Fail-closed on KMS failure | Integration test confirms |

---

## 7. Testing Considerations

| ID | Scenario | Expected | Priority |
|----|----------|----------|----------|
| TC-01 | Admin views Level 2 BR | Success, content returned | High |
| TC-02 | Developer views Level 3 BR | Success | High |
| TC-03 | Developer views Level 2 BR | Denied | High |
| TC-04 | DLP headers present on BR response | All 4 headers present | High |
| TC-05 | Key rotation re-encrypts all entries | All entries readable with new key | High |
| TC-06 | Rate limit per sensitivity level | L1: 5/hr, L2: 15/hr, L3: 30/hr | Medium |
| TC-07 | BR content not in application logs | Grep logs, no BR text | High |

---

## 8. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence — BR Access | [sequence-br-access.png](diagrams/sequence-br-access.png) | [sequence-br-access.drawio](diagrams/sequence-br-access.drawio) |
| 3 | State — Key Rotation | [state-key-rotation.png](diagrams/state-key-rotation.png) | [state-key-rotation.drawio](diagrams/state-key-rotation.drawio) |
