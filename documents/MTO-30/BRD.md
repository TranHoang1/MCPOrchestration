# Business Requirements Document (BRD)

## MCPOrchestration — MTO-30: Business Rules Masking (AI-based)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-30 |
| Title | KB Refinery — Business Rules Masking (AI-based) |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Approved |

---

## 1. Executive Summary

Implement Layer 2 masking for Business Rules content. After LLM-based content segmentation (MTO-28) identifies business rules, this module uses AI to identify specific BR boundaries, categorize them, and replace with placeholders. Original BR content is encrypted (AES-256-GCM) before storage.

## 2. Business Objectives

| # | Objective | Success Metric |
|---|-----------|---------------|
| 1 | Protect sensitive business rules from unauthorized access | 100% BR content masked before KB storage |
| 2 | Maintain traceability via placeholder references | Each placeholder maps to encrypted original |
| 3 | Support selective unmasking for authorized roles | BA/Admin can unmask specific BRs |
| 4 | Categorize BR types for audit and compliance | All BRs tagged with category |

## 3. Business Requirements

### BR-01: AI-based BR Identification

The system SHALL use LLM (LangChain4j) to identify individual business rule boundaries within the `businessRules` content from SegmentationResult.

**Acceptance Criteria:**
- AC-01.1: Given text containing 3 distinct business rules, when masking is applied, then 3 separate placeholders are generated
- AC-01.2: Given text with no business rules, when masking is applied, then original text is returned unchanged

### BR-02: BR Categorization

The system SHALL categorize each identified BR into one of: RATE, APPROVAL, THRESHOLD, PROCESS, COMMISSION.

**Acceptance Criteria:**
- AC-02.1: "Lãi suất 12.5%/năm" → category RATE
- AC-02.2: "Duyệt khi điểm tín dụng ≥ 700" → category APPROVAL
- AC-02.3: "Ngưỡng rủi ro cao: NPL > 5%" → category THRESHOLD
- AC-02.4: "Quy trình xét duyệt 3 cấp" → category PROCESS
- AC-02.5: "Hoa hồng đại lý: 2.5% giá trị hợp đồng" → category COMMISSION

### BR-03: Placeholder Generation

The system SHALL replace each BR with a placeholder in format: `[BR_{CATEGORY}_{NN}]`

**Acceptance Criteria:**
- AC-03.1: First RATE rule → `[BR_RATE_01]`
- AC-03.2: Second RATE rule → `[BR_RATE_02]`
- AC-03.3: Placeholders are unique within a single masking operation

### BR-04: Encryption of Original BR

The system SHALL encrypt original BR content using AES-256-GCM before storage.

**Acceptance Criteria:**
- AC-04.1: Original BR text is never stored in plaintext
- AC-04.2: Encryption key is configurable via environment variable
- AC-04.3: Each BR has unique IV (initialization vector)

### BR-05: Authorized Unmasking

The system SHALL allow BA/Admin roles to unmask specific BRs by placeholder ID.

**Acceptance Criteria:**
- AC-05.1: Given valid placeholder and authorized role, when unmask is called, then original BR text is returned
- AC-05.2: Given unauthorized role, when unmask is attempted, then access denied error is returned

## 4. User Stories

### STORY-01: As a KB Refinery pipeline, I want to mask business rules so that sensitive financial data is protected in the knowledge base.

### STORY-02: As a Business Analyst, I want to unmask specific business rules so that I can review original content when needed.

### STORY-03: As a compliance officer, I want BR categories tracked so that I can audit what types of rules are being processed.

## 5. Dependencies

| # | Dependency | Type | Status |
|---|-----------|------|--------|
| 1 | MTO-28 (SegmentationResult.businessRules) | Data | In Progress |
| 2 | LangChain4j (shared with MTO-28) | Library | Available |
| 3 | Java Cryptography (AES-256-GCM) | JDK | Available |

## 6. Non-Functional Requirements

| # | Category | Requirement | Target |
|---|----------|-------------|--------|
| 1 | Security | Encryption algorithm | AES-256-GCM |
| 2 | Security | Key management | Environment variable |
| 3 | Performance | Masking latency | < 5s per document |
| 4 | Auditability | Category tracking | All BRs categorized |

## 7. Out of Scope

- Key rotation mechanism
- Multi-tenant key management
- BR versioning/history
- UI for BR management
