# Business Requirements Document (BRD)

## MTO-26: KB Refinery — KB Entries Schema 4 Columns + Migration

| Field | Value |
|-------|-------|
| **Ticket** | MTO-26 |
| **Epic** | MTO-24 (Knowledge Base Refinery) |
| **Version** | 1.0 |
| **Status** | Draft |
| **Author** | BA Agent |
| **Created** | 2026-05-08 |

---

## 1. Executive Summary

Thiết kế và implement database schema cho KB entries với 4 columns phân lớp nội dung (public, technical, business_rules, masked_full) cùng bảng pii_mapping encrypted. Schema này là nền tảng cho hệ thống Knowledge Base Refinery, cho phép phân quyền truy cập nội dung theo role và bảo vệ dữ liệu nhạy cảm (PII, business rules) bằng encryption tại application layer.

## 2. Business Context

### 2.1 Problem Statement

Hệ thống KB hiện tại lưu trữ nội dung dạng flat text, không phân biệt mức độ nhạy cảm. Điều này dẫn đến:
- Không thể phân quyền truy cập theo role (Developer vs BA vs Admin)
- Dữ liệu PII (tên, CMND, SĐT, tài khoản ngân hàng) không được bảo vệ
- Business rules (công thức lãi suất, điều kiện duyệt) có thể bị lộ cho unauthorized users
- Không có cơ chế detect thay đổi nội dung (content deduplication)

### 2.2 Business Objectives

| # | Objective | Success Metric |
|---|-----------|----------------|
| BO-1 | Phân lớp nội dung KB theo sensitivity level | 4 content columns hoạt động đúng |
| BO-2 | Bảo vệ PII bằng encryption + masking | AES-256-GCM encryption verified |
| BO-3 | Bảo vệ business rules bằng encryption | BR column encrypted at-rest |
| BO-4 | Hỗ trợ change detection | content_hash detect duplicates |
| BO-5 | Chuẩn bị cho RLS (MTO-31) | Schema compatible với RLS policies |

### 2.3 Stakeholders

| Role | Name/Team | Interest |
|------|-----------|----------|
| Product Owner | KB Refinery Team | Feature completeness |
| Security | Security Team | Encryption standards compliance |
| Developer | Dev Team | Clean API, testable code |
| BA | Business Analysts | Access to business rules |
| DBA | Database Team | Schema performance, indexing |

## 3. Scope

### 3.1 In Scope

- Database schema design cho `kb_entries` (4 content columns)
- Database schema design cho `pii_mapping` (encrypted PII storage)
- Flyway migration scripts (V6, V7)
- Kotlin domain models (data classes, enums)
- Repository interfaces + JDBC implementations
- AES-256-GCM encryption/decryption logic
- Koin DI module cho kbstore package
- Configuration cho encryption keys

### 3.2 Out of Scope

- Row-Level Security (RLS) policies → MTO-31
- KB content ingestion pipeline → MTO-27
- KB search/query API → MTO-28
- UI cho KB management → MTO-29
- Content classification/tagging logic → MTO-30

## 4. User Stories

### STORY-1: Lưu trữ KB Entry với 4 Content Layers

**As a** KB Refinery system  
**I want to** lưu trữ mỗi KB entry với 4 layers nội dung phân biệt  
**So that** hệ thống có thể phân quyền truy cập theo role

**Acceptance Criteria:**
- AC-1.1: Có thể insert KB entry với đầy đủ 4 content columns
- AC-1.2: `public_content` chứa metadata cơ bản (ai cũng thấy)
- AC-1.3: `technical_content` chứa log, code, config (Developer+ mới thấy)
- AC-1.4: `business_rules` chứa encrypted business logic (BA/Admin only)
- AC-1.5: `masked_full` chứa bản đã mask PII + BR cho user thấp
- AC-1.6: `issue_key` là unique constraint (1 entry per Jira ticket)
- AC-1.7: `br_sensitivity_level` phân loại 1=Confidential, 2=Internal, 3=Restricted

### STORY-2: Encrypt/Decrypt Business Rules

**As a** system administrator  
**I want to** business_rules column được encrypt bằng AES-256-GCM  
**So that** dữ liệu nhạy cảm được bảo vệ at-rest

**Acceptance Criteria:**
- AC-2.1: business_rules được encrypt trước khi lưu vào DB
- AC-2.2: business_rules được decrypt khi đọc ra (authorized access)
- AC-2.3: Encryption key lấy từ config/environment variable
- AC-2.4: Key KHÔNG được hardcode trong source code
- AC-2.5: Sử dụng AES-256-GCM (authenticated encryption)

### STORY-3: Quản lý PII Mapping

**As a** KB Refinery system  
**I want to** lưu trữ mapping giữa PII placeholders và original values (encrypted)  
**So that** hệ thống có thể mask/unmask PII theo quyền

**Acceptance Criteria:**
- AC-3.1: Mỗi PII mapping liên kết với 1 KB entry qua issue_key
- AC-3.2: `original_value` được encrypt bằng AES-256-GCM
- AC-3.3: `placeholder` format: [PII_{TYPE}_{NN}] (e.g., [PII_NAME_01])
- AC-3.4: Hỗ trợ mapping types: NAME, ID_CARD, PHONE, BANK_ACCOUNT, EMAIL
- AC-3.5: Có thể query tất cả PII mappings cho 1 issue_key

### STORY-4: Change Detection

**As a** sync service  
**I want to** detect khi nội dung KB entry thay đổi  
**So that** chỉ re-process entries đã thay đổi

**Acceptance Criteria:**
- AC-4.1: `content_hash` được tính từ combined content
- AC-4.2: Có thể query entry by hash để check duplicate
- AC-4.3: `last_synced_at` track thời điểm sync cuối

### STORY-5: Repository CRUD Operations

**As a** developer  
**I want to** có repository interfaces với đầy đủ CRUD operations  
**So that** business logic không phụ thuộc vào implementation details

**Acceptance Criteria:**
- AC-5.1: KbEntryRepository interface với: insert, update, findByIssueKey, findByProject, upsert
- AC-5.2: PiiMappingRepository interface với: insert, findByIssueKey, deleteByIssueKey
- AC-5.3: Implementations dùng JDBC + HikariCP connection pool
- AC-5.4: Tất cả operations là suspend functions (coroutine-friendly)
- AC-5.5: Batch operations cho bulk insert/update

## 5. Business Rules

| ID | Rule | Priority |
|----|------|----------|
| BR-1 | Mỗi issue_key chỉ có 1 KB entry (unique constraint) | Critical |
| BR-2 | business_rules PHẢI encrypted trước khi persist | Critical |
| BR-3 | pii_mapping.original_value PHẢI encrypted trước khi persist | Critical |
| BR-4 | Encryption key KHÔNG được hardcode | Critical |
| BR-5 | br_sensitivity_level chỉ nhận giá trị 1, 2, 3 | High |
| BR-6 | content_hash phải unique per project (detect duplicates) | Medium |
| BR-7 | Khi delete KB entry, cascade delete PII mappings | High |
| BR-8 | Timestamps (created_at, updated_at) tự động set | Medium |

## 6. Non-Functional Requirements

| ID | Category | Requirement | Target |
|----|----------|-------------|--------|
| NFR-1 | Performance | Single entry CRUD < 50ms | p99 < 50ms |
| NFR-2 | Performance | Batch upsert 100 entries < 2s | p99 < 2s |
| NFR-3 | Security | AES-256-GCM encryption | NIST compliant |
| NFR-4 | Security | Key rotation support (future) | Schema compatible |
| NFR-5 | Reliability | Transaction rollback on failure | ACID guaranteed |
| NFR-6 | Maintainability | File ≤ 200 lines, Function ≤ 20 lines | Code standards |
| NFR-7 | Testability | Interface-first design | 100% mockable |
| NFR-8 | Compatibility | PostgreSQL 16+ | Verified |

## 7. Dependencies

| ID | Dependency | Type | Status |
|----|-----------|------|--------|
| DEP-1 | PostgreSQL 16+ database | Infrastructure | Available |
| DEP-2 | HikariCP connection pool | Library | Available (existing) |
| DEP-3 | Flyway migration tool | Library | Available (existing) |
| DEP-4 | Koin DI framework | Library | Available (existing) |
| DEP-5 | kotlinx.serialization | Library | Available (existing) |
| DEP-6 | Java Cryptography (javax.crypto) | JDK | Available (JDK 21) |
| DEP-7 | MTO-31 (RLS policies) | Feature | Planned (future) |

## 8. Constraints

| ID | Constraint | Impact |
|----|-----------|--------|
| CON-1 | Encryption at application layer (not DB-level) | More control, but app must handle encrypt/decrypt |
| CON-2 | Schema must be RLS-ready for MTO-31 | Cannot use patterns incompatible with RLS |
| CON-3 | Kotlin code standards (200 lines/file, 20 lines/function) | More files, cleaner code |
| CON-4 | Existing HikariCP pattern (sync package) | Must follow same JDBC pattern |

## 9. Risks

| ID | Risk | Probability | Impact | Mitigation |
|----|------|-------------|--------|------------|
| R-1 | Encryption key leak | Low | Critical | Env var only, never in code/config files |
| R-2 | Performance degradation from encryption | Medium | Medium | Benchmark encrypt/decrypt overhead |
| R-3 | Schema migration conflicts with other tickets | Low | Medium | Coordinate V6/V7 versioning |
| R-4 | BYTEA column size limits | Low | Low | PostgreSQL BYTEA has no practical limit |

## 10. Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |

---

## Approval

| Role | Name | Date | Status |
|------|------|------|--------|
| Product Owner | | | Pending |
| Tech Lead | | | Pending |
| Security | | | Pending |
