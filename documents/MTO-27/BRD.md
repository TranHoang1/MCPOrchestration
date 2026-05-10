# Business Requirements Document (BRD)

## MCPOrchestration — MTO-27: KB Refinery — PII Masking Engine (Regex-based, VN patterns)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-27 |
| Title | KB Refinery — PII Masking Engine (Regex-based, VN patterns) |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-08 |
| Status | Draft |
| Epic | MTO-24 (Knowledge Base Refinery) |
| Depends On | MTO-26 (KB Entries Schema — KbEntry, PiiMapping models, EncryptionService) |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-08 | BA Agent | Initiate document — auto-generated from Jira ticket MTO-27 |

---

## 1. Introduction

### 1.1 Scope

Xây dựng PII Masking Engine sử dụng regex patterns để phát hiện và mask thông tin cá nhân (PII) trong context tài chính Việt Nam. Engine nhận raw text, phát hiện PII (CMND/CCCD, SĐT, STK ngân hàng, email, tên người), thay thế bằng placeholders, và trả về masked text cùng mapping list. Masked text sẽ được gửi sang LLM (LLM không thấy PII gốc), mapping list sẽ được encrypt và lưu vào pii_mapping table (MTO-26).

### 1.2 Out of Scope

- Database schema và migration (đã implement trong MTO-26)
- Row-Level Security policies (MTO-31)
- KB content ingestion pipeline orchestration (MTO-28)
- KB search/query API (MTO-28)
- NLP-based PII detection (Named Entity Recognition) — chỉ dùng regex + heuristic
- PII detection cho ngôn ngữ khác ngoài tiếng Việt
- UI cho PII management

### 1.3 Preliminary Requirements

| # | Prerequisite | Source | Status |
|---|-------------|--------|--------|
| 1 | KbEntry data class với 4 content columns | MTO-26 | Done |
| 2 | PiiMapping data class (placeholder, original_value, pii_type) | MTO-26 | Done |
| 3 | EncryptionService (AES-256-GCM) | MTO-26 | Done |
| 4 | PiiMappingRepository interface | MTO-26 | Done |

---

## 2. Business Requirements

### 2.1 High Level Process Map

PII Masking Engine là component trung tâm trong KB Refinery pipeline, đảm bảo dữ liệu nhạy cảm không bị lộ khi gửi sang LLM:

1. Raw text (từ Jira ticket, document, hoặc user input) được đưa vào PII Masking Engine
2. Engine scan text bằng regex patterns cho từng loại PII (VN financial context)
3. Mỗi PII match được thay thế bằng placeholder format `[PII_{TYPE}_{NN}]`
4. Engine trả về MaskingResult gồm masked text + list PiiMapping
5. Masked text → gửi sang LLM (an toàn, không chứa PII)
6. PiiMapping list → encrypt bằng EncryptionService → lưu vào DB (MTO-26)

### 2.2 List of User Stories / Use Cases

| # | Story / Use Case | Priority | Source Ticket |
|---|-----------------|----------|---------------|
| 1 | As a KB Refinery system, I want to detect and mask CMND/CCCD numbers in text so that ID numbers are not exposed to LLM | MUST HAVE | MTO-27 |
| 2 | As a KB Refinery system, I want to detect and mask phone numbers (VN format) so that contact info is protected | MUST HAVE | MTO-27 |
| 3 | As a KB Refinery system, I want to detect and mask bank account numbers (with context) so that financial data is protected | MUST HAVE | MTO-27 |
| 4 | As a KB Refinery system, I want to detect and mask email addresses so that email PII is protected | MUST HAVE | MTO-27 |
| 5 | As a KB Refinery system, I want to detect and mask Vietnamese person names (heuristic) so that identity info is protected | SHOULD HAVE | MTO-27 |
| 6 | As a developer, I want a configurable masking pipeline with Strategy pattern so that new PII types can be added easily | MUST HAVE | MTO-27 |
| 7 | As a developer, I want masking results to integrate with MTO-26 PiiMapping model so that mappings can be persisted | MUST HAVE | MTO-27 |

---

### 2.3 Details of User Stories

---

#### Business Flow

**Step 1:** System receives raw text input (String) from KB ingestion pipeline

**Step 2:** PiiMaskingEngine iterates through registered PII detection strategies (ordered by priority)

**Step 3:** Each strategy scans text using its regex pattern(s) and returns matches with positions

**Step 4:** Engine replaces each match with sequential placeholder: `[PII_{TYPE}_{NN}]`

**Step 5:** Engine builds PiiMapping for each replacement (placeholder → original value → pii_type)

**Step 6:** Engine returns MaskingResult(maskedText, mappings)

**Step 7:** Caller uses maskedText for LLM processing, encrypts mappings via EncryptionService, persists to DB

> **Note:** Strategies are applied in priority order. Higher-priority patterns (e.g., bank account with context) are matched first to avoid false positives from lower-priority patterns (e.g., generic number sequences).

---

#### STORY 1: Detect and Mask CMND/CCCD

> As a KB Refinery system, I want to detect and mask CMND/CCCD numbers in text so that ID numbers are not exposed to LLM

**Requirement Details:**

1. Detect 9-digit numbers (CMND cũ) matching pattern `\b\d{9}\b`
2. Detect 12-digit numbers (CCCD mới) matching pattern `\b\d{12}\b`
3. Replace with placeholder format `[PII_ID_01]`, `[PII_ID_02]`, etc.
4. Counter resets per masking session (per text input)

**Acceptance Criteria:**

1. AC-1.1: Text chứa "CMND 123456789" → masked thành "CMND [PII_ID_01]"
2. AC-1.2: Text chứa "CCCD 012345678901" → masked thành "CCCD [PII_ID_01]"
3. AC-1.3: Số 9 chữ số trong context không phải ID (e.g., mã giao dịch) vẫn bị mask (acceptable false positive cho regex approach)
4. AC-1.4: Multiple IDs trong cùng text được đánh số tuần tự: [PII_ID_01], [PII_ID_02]

---

#### STORY 2: Detect and Mask Phone Numbers (VN)

> As a KB Refinery system, I want to detect and mask phone numbers (VN format) so that contact info is protected

**Requirement Details:**

1. Detect 10-digit numbers bắt đầu bằng 0: pattern `\b0\d{9}\b`
2. Covers all VN mobile prefixes (03x, 05x, 07x, 08x, 09x) và landline (02x)
3. Replace with placeholder format `[PII_PHONE_01]`, `[PII_PHONE_02]`, etc.

**Acceptance Criteria:**

1. AC-2.1: Text chứa "SĐT: 0912345678" → masked thành "SĐT: [PII_PHONE_01]"
2. AC-2.2: Text chứa "gọi 0281234567" → masked thành "gọi [PII_PHONE_01]"
3. AC-2.3: Số 10 chữ số không bắt đầu bằng 0 KHÔNG bị mask bởi phone strategy
4. AC-2.4: Phone pattern có priority cao hơn generic ID pattern (tránh 10-digit phone bị match bởi ID pattern)

---

#### STORY 3: Detect and Mask Bank Account Numbers

> As a KB Refinery system, I want to detect and mask bank account numbers (with context) so that financial data is protected

**Requirement Details:**

1. Detect 10-19 digit numbers: pattern `\b\d{10,19}\b`
2. **CHỈ mask khi có context keywords** gần đó: "tài khoản", "STK", "account", "số TK", "bank account"
3. Context window: keyword phải xuất hiện trong cùng câu hoặc trong 50 ký tự trước/sau số
4. Replace with placeholder format `[PII_ACCOUNT_01]`, `[PII_ACCOUNT_02]`, etc.

**Acceptance Criteria:**

1. AC-3.1: Text "STK 1234567890123" → masked thành "STK [PII_ACCOUNT_01]"
2. AC-3.2: Text "tài khoản số 9876543210" → masked thành "tài khoản số [PII_ACCOUNT_01]"
3. AC-3.3: Text "mã giao dịch 1234567890123" (không có context keyword) → KHÔNG mask
4. AC-3.4: Context-aware matching giảm false positives so với pure regex

---

#### STORY 4: Detect and Mask Email Addresses

> As a KB Refinery system, I want to detect and mask email addresses so that email PII is protected

**Requirement Details:**

1. Standard email regex: `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}`
2. Replace with placeholder format `[PII_EMAIL_01]`, `[PII_EMAIL_02]`, etc.

**Acceptance Criteria:**

1. AC-4.1: Text "email: user@example.com" → masked thành "email: [PII_EMAIL_01]"
2. AC-4.2: Text có nhiều email → mỗi email unique placeholder
3. AC-4.3: Invalid email format (thiếu @, thiếu domain) KHÔNG bị mask

---

#### STORY 5: Detect and Mask Vietnamese Person Names (Heuristic)

> As a KB Refinery system, I want to detect and mask Vietnamese person names (heuristic) so that identity info is protected

**Requirement Details:**

1. Dùng heuristic: detect tên sau các prefix indicators: "Ông", "Bà", "Anh", "Chị", "KH", "Khách hàng", "Mr.", "Mrs.", "Ms."
2. Tên = chuỗi 2-4 từ capitalized liên tiếp sau prefix
3. Pattern: `(Ông|Bà|Anh|Chị|KH|Khách hàng|Mr\.|Mrs\.|Ms\.)\s+([A-ZÀ-Ỹ][a-zà-ỹ]+(\s+[A-ZÀ-Ỹ][a-zà-ỹ]+){1,3})`
4. Replace with placeholder format `[PII_NAME_01]`, `[PII_NAME_02]`, etc.

**Acceptance Criteria:**

1. AC-5.1: Text "KH Nguyễn Văn An" → masked thành "KH [PII_NAME_01]"
2. AC-5.2: Text "Bà Trần Thị Bích Ngọc" → masked thành "Bà [PII_NAME_01]"
3. AC-5.3: Text "Ông Lê Minh" → masked thành "Ông [PII_NAME_01]"
4. AC-5.4: Tên không có prefix indicator → KHÔNG bị mask (acceptable miss cho regex approach)
5. AC-5.5: Hỗ trợ Vietnamese diacritics (dấu) trong tên

---

#### STORY 6: Configurable Masking Pipeline with Strategy Pattern

> As a developer, I want a configurable masking pipeline with Strategy pattern so that new PII types can be added easily

**Requirement Details:**

1. Interface `PiiMaskingEngine` với method `mask(text: String): MaskingResult`
2. Mỗi PII type là 1 strategy class implementing `PiiDetectionStrategy`
3. Strategies registered via Koin DI, ordered by priority
4. Config cho enable/disable từng strategy
5. Config cho custom regex patterns (override defaults)

**Data Fields:**

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| maskedText | String | Yes | Text sau khi mask PII | "KH [PII_NAME_01] có SĐT [PII_PHONE_01]" |
| mappings | List<PiiMapping> | Yes | Danh sách mapping placeholder → original | [{placeholder: "[PII_NAME_01]", original: "Nguyễn Văn An", type: NAME}] |

**Acceptance Criteria:**

1. AC-6.1: PiiMaskingEngine interface có method `mask(text: String): MaskingResult`
2. AC-6.2: Mỗi PII type (ID, PHONE, ACCOUNT, EMAIL, NAME) là 1 strategy class riêng
3. AC-6.3: Strategies có thể enable/disable qua MaskingConfig
4. AC-6.4: Thêm PII type mới chỉ cần tạo strategy class mới + register trong DI module
5. AC-6.5: Strategy execution order configurable (priority-based)

---

#### STORY 7: Integration with MTO-26 PiiMapping Model

> As a developer, I want masking results to integrate with MTO-26 PiiMapping model so that mappings can be persisted

**Requirement Details:**

1. MaskingResult.mappings trả về List<PiiMapping> compatible với MTO-26 model
2. PiiMapping fields: issueKey, placeholder, originalValue, piiType, detectedAt
3. Caller có thể pass mappings trực tiếp cho EncryptionService + PiiMappingRepository

**Acceptance Criteria:**

1. AC-7.1: MaskingResult.mappings chứa PiiMapping objects compatible với MTO-26
2. AC-7.2: Mỗi PiiMapping có đầy đủ: placeholder, originalValue, piiType
3. AC-7.3: piiType enum values match MTO-26: NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL
4. AC-7.4: Caller có thể iterate mappings và persist qua PiiMappingRepository

---

## 3. Dependencies

| Dependency | Type | Related Ticket | Description |
|------------|------|----------------|-------------|
| KbEntry model | System | MTO-26 | Data class cho KB entries với 4 content columns |
| PiiMapping model | System | MTO-26 | Data class cho PII placeholder ↔ original value mapping |
| EncryptionService | System | MTO-26 | AES-256-GCM encryption cho PII original values |
| PiiMappingRepository | System | MTO-26 | Repository interface cho persist PII mappings |
| Koin DI | Library | N/A | Dependency injection framework (existing) |
| kotlinx.serialization | Library | N/A | Serialization cho MaskingConfig |
| Kotlin Regex | JDK | N/A | java.util.regex via Kotlin stdlib |

---

## 4. Stakeholders

| Role | Name / Team | Responsibility | Source |
|------|-------------|----------------|--------|
| Product Owner | KB Refinery Team | Feature completeness, acceptance | Reporter |
| Security | Security Team | PII protection compliance | Stakeholder |
| Developer | Dev Team | Implementation | Assignee |
| BA | Business Analysts | VN PII patterns validation | Domain expert |

---

## 5. Risks and Assumptions

### 5.1 Risks

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| False positives (non-PII matched as PII) | Medium | High | Context-aware matching cho bank accounts; accept some FP for ID/phone |
| False negatives (PII missed) | High | Medium | Document known limitations; plan NLP enhancement in future |
| Vietnamese name detection incomplete | Medium | High | Heuristic approach with prefix indicators; document miss rate |
| Regex performance on large texts | Medium | Low | Benchmark; consider compiled regex patterns |
| Overlapping matches between strategies | Medium | Medium | Priority-based execution; first-match-wins for overlapping regions |

### 5.2 Assumptions

- Text input là tiếng Việt hoặc mixed Vietnamese-English trong context tài chính
- CMND/CCCD luôn là 9 hoặc 12 chữ số (không có format khác)
- SĐT Việt Nam luôn 10 số bắt đầu bằng 0
- Bank account numbers trong VN banking là 10-19 chữ số
- Tên người Việt luôn có ít nhất 2 từ (họ + tên)
- MTO-26 models đã stable và không thay đổi interface

---

## 6. Non-Functional Requirements

| Category | Requirement | Details |
|----------|-------------|---------|
| Performance | Mask 1KB text < 10ms | Regex compiled once, reused across calls |
| Performance | Mask 100KB text < 100ms | Linear scaling with text size |
| Reliability | No data loss | Original text recoverable from masked text + mappings |
| Security | PII never logged | Masked text only in logs; original values never in log output |
| Maintainability | Strategy pattern | New PII type = new class, no modification to existing code |
| Maintainability | File ≤ 200 lines | Kotlin code standards |
| Maintainability | Function ≤ 20 lines | Kotlin code standards |
| Testability | 100% strategy coverage | Each strategy independently testable |
| Extensibility | Plugin architecture | New strategies via Koin module registration |

---

## 7. Related Tickets

| Ticket Key | Summary | Status | Type | Relationship |
|------------|---------|--------|------|--------------|
| MTO-27 | KB Refinery — PII Masking Engine | To Do | Story | Main ticket |
| MTO-26 | KB Entries Schema 4 Columns + Migration | Done | Story | Dependency (provides models) |
| MTO-24 | Knowledge Base Refinery | In Progress | Epic | Parent epic |
| MTO-28 | KB Ingestion Pipeline | To Do | Story | Consumer (uses masking engine) |
| MTO-31 | Row-Level Security | To Do | Story | Future (uses masked content) |

---

## 8. Appendix

### Glossary

| Term | Definition |
|------|------------|
| PII | Personally Identifiable Information — dữ liệu có thể identify cá nhân |
| CMND | Chứng Minh Nhân Dân — ID card cũ (9 số) |
| CCCD | Căn Cước Công Dân — ID card mới (12 số) |
| STK | Số Tài Khoản — bank account number |
| SĐT | Số Điện Thoại — phone number |
| Masking | Thay thế PII bằng placeholder, giữ nguyên structure text |
| Strategy Pattern | Design pattern cho phép swap algorithm tại runtime |
| KB Refinery | Hệ thống xử lý và phân loại nội dung Knowledge Base |

### PII Type Reference

| PII Type | Placeholder Format | Regex Pattern | Context Required |
|----------|-------------------|---------------|-----------------|
| ID_CARD | [PII_ID_NN] | `\b\d{9}\b` hoặc `\b\d{12}\b` | No |
| PHONE | [PII_PHONE_NN] | `\b0\d{9}\b` | No |
| BANK_ACCOUNT | [PII_ACCOUNT_NN] | `\b\d{10,19}\b` | Yes (keyword context) |
| EMAIL | [PII_EMAIL_NN] | Standard email regex | No |
| NAME | [PII_NAME_NN] | Prefix + capitalized words | Yes (prefix indicator) |

### Strategy Priority Order

| Priority | Strategy | Rationale |
|----------|----------|-----------|
| 1 (highest) | Email | Most specific pattern, least false positives |
| 2 | Phone | 10-digit starting with 0, fairly specific |
| 3 | Bank Account (context) | Requires keyword context, reduces FP |
| 4 | ID Card | 9 or 12 digits, moderate FP risk |
| 5 (lowest) | Name (heuristic) | Most prone to FP/FN, applied last |

---

## Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
