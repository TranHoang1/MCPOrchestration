# Business Requirements Document (BRD)

## MCPOrchestration — MTO-47: Unified Sync Pipeline — Multi-Dimensional Jira Indexing

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-47 |
| Title | Unified Sync Pipeline — Multi-Dimensional Jira Indexing |
| Author | BA Agent + SA + TA |
| Version | 1.0 |
| Date | 2026-05-14 |
| Status | Draft |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-14 | BA Agent | Initial BRD from user requirements discussion |

---

## 1. Introduction

### 1.1 Problem Statement

Hiện tại hệ thống có **2 tool sync Jira** hoạt động song song với logic khác nhau:

| Tool | Module | Pipeline | Vấn đề |
|------|--------|----------|--------|
| `jira_project_sync` | orchestrator-server | ProjectScanner → TicketCrawler → KBIngestor → Vector DB | Comments flatten, không tách per-person, 1 vector entry per ticket |
| `kb_sync_trigger` | kb-server | Queue → SyncTaskHandler | **STUB** — chỉ log, không có logic thực |

**Conflict:** 2 tool cùng mục đích nhưng logic khác nhau, gây confusion cho AI agents và inconsistency trong data.

### 1.2 Scope

Thống nhất 2 tool thành **1 shared pipeline** (Option C) với các yêu cầu mở rộng:

1. Extract crawl logic thành shared service — cả 2 tool cùng gọi
2. Index đầy đủ thông tin ticket theo project, phân biệt loại ticket
3. Comments lưu riêng theo từng người (per-person, per-comment)
4. Tổng hợp theo feature (AI-determined) với liên kết đa chiều
5. Thiết kế extensible — index dimensions cấu hình từ UI, không giới hạn bởi code
6. Source tracking — mọi data biết được từ nguồn nào

### 1.3 Out of Scope

- UI implementation cho dimension configuration (chỉ thiết kế API/schema, REST API = in scope)
- AI feature detection algorithm (chỉ thiết kế interface + hook point)
- Migration data cũ (sẽ full re-sync)
- Attachment content extraction (OCR, PDF parsing) — chỉ metadata

### 1.5 Key Design Decisions (User Confirmed)

| # | Decision | Rationale |
|---|----------|-----------|
| D-01 | **PII Masking = role-based at read time** | Lưu cả original + masked vào DB. Filter theo role khi đọc. Nếu mask trước khi lưu → mất data gốc, không xem lại được. Pattern: store both, unmask on-demand with audit. |
| D-02 | **Existing `kb_search` → migrate sang query `sync.index_entries`** | Option C (migrate) là tối ưu nhất — single source of truth, không dual-write overhead, không query 2 tables. |
| D-03 | **GraphService phải nâng cấp** để đọc từ `sync.index_entries` thay vì `kb.ticket_cache` + `kb.ticket_graph` riêng. |

### 1.4 Dependencies

| # | Dependency | Source | Status |
|---|-----------|--------|--------|
| 1 | JiraRestClient | MTO-16 (orchestrator-server) | ✅ Done |
| 2 | ProjectScanner | MTO-17 (orchestrator-server) | ✅ Done |
| 3 | TicketCrawler | MTO-18 (orchestrator-server) | ✅ Done |
| 4 | KB Server Queue | MTO-38 (kb-server) | ✅ Done |
| 5 | Graph Infrastructure | MTO-20 (kb-server) | ✅ Done |
| 6 | Sync Tools Registration | MTO-20 (orchestrator-server) | ✅ Done |

---

## 2. Business Requirements

### 2.1 User Stories

| # | Story | Priority | Acceptance Criteria |
|---|-------|----------|---------------------|
| 1 | As an AI agent, I want both `jira_project_sync` and `kb_sync_trigger` to produce identical results so that data is consistent regardless of which tool I use | MUST HAVE | Same data in DB after sync via either tool |
| 2 | As an AI agent, I want ticket data indexed per-project with issue type classification so that I can query "all Bugs in project X" | MUST HAVE | Query by project + issueType returns correct results |
| 3 | As an AI agent, I want comments stored per-person so that I can ask "what did user Y say about ticket Z" | MUST HAVE | Each comment = separate record with author identity |
| 4 | As an AI agent, I want to see multi-dimensional relationships (User→Tickets, Ticket→Tickets, Feature→Tickets) so that I can understand context | MUST HAVE | Graph traversal returns correct relationships |
| 5 | As an AI agent, I want features auto-detected from ticket context so that I can group related work | SHOULD HAVE | Feature grouping available after sync |
| 6 | As a system admin, I want to configure index dimensions from UI so that new dimensions can be added without code changes | SHOULD HAVE | Config API accepts new dimension definitions |
| 7 | As an AI agent, I want every piece of data to reference its source so that I can trace provenance | MUST HAVE | Every KB entry has source_ref field |
| 8 | As an AI agent, I want attachments indexed as metadata so that I know what files exist on tickets | SHOULD HAVE | Attachment info queryable per ticket |

### 2.2 Story Details

---

#### STORY 1: Unified Pipeline (Shared Service)

**Description:** Extract crawl + index logic into a shared module that both `jira_project_sync` (orchestrator) and `kb_sync_trigger` (kb-server) invoke identically.

**Current State:**
- orchestrator: ProjectScanner → TicketCrawler → KBIngestor (direct vector DB)
- kb-server: SyncTaskHandler → STUB

**Target State:**
- Shared module: `sync-pipeline` (new Gradle module)
- orchestrator: `jira_project_sync` → shared pipeline
- kb-server: `SyncTaskHandler` → shared pipeline

**Business Rules:**
- BR-01: Both tools MUST produce identical data in all storage layers
- BR-02: Pipeline MUST be idempotent — re-sync same data = no duplicates
- BR-03: Pipeline MUST support incremental sync (only changed tickets)
- BR-04: Pipeline MUST track sync state per project (IDLE/RUNNING/COMPLETED/FAILED)

---

#### STORY 2: Per-Project Ticket Indexing with Type Classification

**Description:** Every ticket indexed with full metadata, classified by issue type.

**Data Requirements:**

| Field | Source | Required |
|-------|--------|----------|
| ticket_key | Jira issue key | Yes |
| project_key | Extracted from key | Yes |
| issue_type | Jira fields.issuetype.name | Yes |
| status | Jira fields.status.name | Yes |
| priority | Jira fields.priority.name | No |
| summary | Jira fields.summary | Yes |
| description | Jira fields.description (ADF→text) | No |
| assignee_id | Jira fields.assignee.accountId | No |
| assignee_name | Jira fields.assignee.displayName | No |
| reporter_id | Jira fields.reporter.accountId | No |
| reporter_name | Jira fields.reporter.displayName | No |
| parent_key | Jira fields.parent.key | No |
| epic_key | Jira fields.customfield_10014 (or epic link) | No |
| labels | Jira fields.labels[] | No |
| components | Jira fields.components[].name | No |
| fix_versions | Jira fields.fixVersions[].name | No |
| story_points | Jira fields.story_points or customfield | No |
| sprint | Jira fields.sprint.name | No |
| created_at | Jira fields.created | Yes |
| updated_at | Jira fields.updated | Yes |
| resolved_at | Jira fields.resolutiondate | No |

**Business Rules:**
- BR-05: Issue types MUST be preserved exactly as Jira returns (Epic, Story, Bug, Task, Sub-task, etc.)
- BR-06: Each ticket MUST be queryable by project_key + issue_type combination
- BR-07: Ticket data MUST be upserted (not duplicated) on re-sync

---

#### STORY 3: Per-Person Comment Storage

**Description:** Each comment stored as individual record with full author identity.

**Data Requirements:**

| Field | Source | Required |
|-------|--------|----------|
| ticket_key | Parent ticket | Yes |
| project_key | From ticket | Yes |
| jira_comment_id | Jira comment.id | Yes |
| author_account_id | Jira comment.author.accountId | Yes |
| author_display_name | Jira comment.author.displayName | Yes |
| body | Jira comment.body (ADF→text) | Yes |
| created_at | Jira comment.created | Yes |
| updated_at | Jira comment.updated | No |
| visibility | Jira comment.visibility (role/group) | No |

**Business Rules:**
- BR-08: Each comment = 1 record, uniquely identified by (ticket_key, jira_comment_id)
- BR-09: Author identity MUST include accountId (stable) + displayName (human-readable)
- BR-10: Comments MUST be queryable by author ("what did user X say?")
- BR-11: Comments MUST be queryable by ticket ("all comments on ticket Y")
- BR-12: Comment updates in Jira MUST be reflected on re-sync (upsert by jira_comment_id)

---

#### STORY 4: Multi-Dimensional Relationship Graph

**Description:** Build relationships across multiple dimensions for context understanding.

**Relationship Types:**

| Dimension | Source → Target | Relation | Source of Truth |
|-----------|----------------|----------|-----------------|
| Ticket→Ticket | MTO-14 → MTO-15 | blocks, relates-to, parent-of, child-of, duplicates | Jira issuelinks + parent |
| User→Ticket | user123 → MTO-14 | assignee, reporter, commenter | Jira fields + comments |
| Feature→Ticket | feature:auth → MTO-14 | contains | AI-determined (Epic + context) |
| Ticket→Attachment | MTO-14 → att_001 | has-attachment | Jira attachments |

**Business Rules:**
- BR-13: All Jira link types MUST be indexed bidirectionally
- BR-14: User→Ticket relations MUST be derived from: assignee, reporter, commenter roles
- BR-15: Feature grouping MUST be AI-determined (not hardcoded logic)
- BR-16: Relationships MUST be traversable in both directions (User→Tickets AND Ticket→Users)

---

#### STORY 5: AI-Determined Feature Detection

**Description:** Features are automatically identified from ticket context, not manually tagged.

**Feature Detection Inputs:**
- Epic hierarchy (tickets under same Epic = likely same feature)
- Shared labels (e.g., `feature:auth`, `module:payment`)
- Shared components (Jira components)
- Semantic similarity of ticket summaries/descriptions
- Link clusters (heavily interconnected tickets = likely same feature)

**Business Rules:**
- BR-17: Feature detection MUST be pluggable — algorithm can be swapped
- BR-18: Feature assignment MUST be stored with confidence score
- BR-19: Features MUST be re-evaluated on each sync (not static)
- BR-20: Initial implementation MAY use Epic-based grouping as baseline

---

#### STORY 6: Config-Driven Index Dimensions (UI-Configurable)

**Description:** Index dimensions are not hardcoded — admins can add/remove/configure dimensions from UI without code changes.

**Dimension Configuration Schema:**

```json
{
  "dimensions": [
    {
      "id": "ticket_metadata",
      "name": "Ticket Metadata",
      "enabled": true,
      "source": "jira_fields",
      "fields": ["summary", "description", "status", "priority", "issue_type"],
      "index_strategy": "per_ticket",
      "vector_enabled": true
    },
    {
      "id": "comments",
      "name": "Comments Per Person",
      "enabled": true,
      "source": "jira_comments",
      "fields": ["body", "author_display_name"],
      "index_strategy": "per_comment",
      "vector_enabled": true
    },
    {
      "id": "attachments",
      "name": "Attachment Metadata",
      "enabled": true,
      "source": "jira_attachments",
      "fields": ["filename", "mime_type", "size"],
      "index_strategy": "per_attachment",
      "vector_enabled": false
    },
    {
      "id": "user_relations",
      "name": "User Relationships",
      "enabled": true,
      "source": "derived",
      "derive_from": ["assignee", "reporter", "commenter"],
      "index_strategy": "per_relation",
      "vector_enabled": false
    },
    {
      "id": "feature_grouping",
      "name": "Feature Auto-Detection",
      "enabled": true,
      "source": "ai_derived",
      "derive_from": ["epic_hierarchy", "labels", "components", "semantic_similarity"],
      "index_strategy": "per_feature",
      "vector_enabled": true
    }
  ]
}
```

**Business Rules:**
- BR-21: Dimensions MUST be enable/disable without code deployment
- BR-22: New dimensions MUST be addable via configuration API
- BR-23: Each dimension defines its own index_strategy (per_ticket, per_comment, per_attachment, per_relation, per_feature)
- BR-24: Vector indexing MUST be optional per dimension (some dimensions are relational only)
- BR-25: Dimension config changes MUST trigger re-index on next sync

---

#### STORY 7: Source Provenance Tracking

**Description:** Every piece of indexed data MUST reference its exact source.

**Source Reference Format:**

```
jira:{project_key}/{ticket_key}                          → ticket metadata
jira:{project_key}/{ticket_key}/comment/{comment_id}     → specific comment
jira:{project_key}/{ticket_key}/attachment/{attachment_id} → attachment
jira:{project_key}/{ticket_key}/link/{link_id}           → relationship
derived:feature/{feature_id}                              → AI-derived feature
```

**Business Rules:**
- BR-26: Every KB entry MUST have a `source_ref` field
- BR-27: Source ref MUST be granular enough to identify exact origin
- BR-28: Source ref MUST include sync timestamp (when was this data fetched)
- BR-29: Multiple sources MAY reference same KB entry (e.g., feature derived from multiple tickets)

---

#### STORY 8: Attachment Metadata Indexing

**Description:** Attachment metadata (not content) indexed per ticket.

**Data Requirements:**

| Field | Source | Required |
|-------|--------|----------|
| ticket_key | Parent ticket | Yes |
| attachment_id | Jira attachment.id | Yes |
| filename | Jira attachment.filename | Yes |
| mime_type | Jira attachment.mimeType | No |
| size_bytes | Jira attachment.size | No |
| author_account_id | Jira attachment.author.accountId | No |
| author_display_name | Jira attachment.author.displayName | No |
| created_at | Jira attachment.created | Yes |
| download_url | Jira attachment.content | Yes (internal use) |

**Business Rules:**
- BR-30: Attachment metadata MUST be queryable by ticket
- BR-31: Attachment metadata MUST be queryable by filename pattern
- BR-32: Download URL MUST NOT be exposed to AI agents (security)
- BR-33: Attachment content extraction (OCR, PDF) is OUT OF SCOPE for this ticket

---

## 3. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | Full sync 100 tickets | < 5 minutes |
| Performance | Incremental sync (10 changed) | < 30 seconds |
| Performance | Single ticket crawl | < 3 seconds |
| Consistency | Both tools produce same data | 100% identical |
| Reliability | Sync survives crash/restart | Resume from last checkpoint |
| Scalability | Support 10,000+ tickets per project | Pagination + batch processing |
| Extensibility | Add new dimension | Config change only, no code |
| Traceability | Every data point has source | 100% coverage |
| Security | PII visible only to authorized roles | Role-based read filtering + audit |

---

## 3.5 PII & Security Requirements

### 3.5.1 Role-Based Content Access

**Pattern:** Store both original + masked content. Filter at read time by caller's role.

| Role | Sees | Can Unmask |
|------|------|------------|
| Developer | publicContent + technicalContent (PII masked) | No |
| BA | publicContent + technicalContent + businessRules | PII with audit |
| Admin | All content | All with audit |
| AI Agent (default) | maskedFull (all PII replaced with placeholders) | Via kb_unmask_pii tool |

**Business Rules:**
- BR-34: Sync pipeline MUST store original content (unmasked) in DB
- BR-35: Sync pipeline MUST also store PII-masked version for low-privilege access
- BR-36: PII masking MUST use existing `PiiMaskingEngine` (email, phone, bank, ID, name)
- BR-37: Read operations MUST filter content by caller's role
- BR-38: Unmask operations MUST be audited with reason + caller identity
- BR-39: PII mappings (placeholder → original) MUST be stored separately for on-demand unmask

### 3.5.2 Storage Strategy for sync_index_entries

```
sync_index_entries.data = {
    "body": "Original comment text with PII",           ← Encrypted/restricted
    "body_masked": "Comment by [NAME_1] about...",      ← For low-privilege roles
    "author_display_name": "Nguyen Van A",              ← Visible to all
    "author_account_id": "5f7c...abc"                   ← Visible to all
}
```

### 3.5.3 Existing kb_search Migration

- BR-40: `kb_search` tool MUST be migrated to query `sync.index_entries` (single source of truth)
- BR-41: During migration, `kb_search` MAY query both old `kb.kb_entries` and new `sync.index_entries`
- BR-42: After full re-sync, old `kb.kb_entries` data becomes read-only archive

### 3.5.4 GraphService Upgrade

- BR-43: `GraphService` MUST be upgraded to read from `sync.index_entries` (dimension = user_relations, ticket_metadata)
- BR-44: Existing `kb.ticket_cache` and `kb.ticket_graph` tables become deprecated after migration
- BR-45: Graph traversal MUST work with new multi-dimensional data (User→Ticket, Ticket→Ticket, Feature→Ticket)

---

## 4. Acceptance Criteria (Summary)

| # | Criterion | Verification |
|---|-----------|--------------|
| AC-01 | `jira_project_sync` and `kb_sync_trigger` produce identical data | Compare DB state after sync via each tool |
| AC-02 | Tickets indexed with full metadata per project | Query by project+type returns correct results |
| AC-03 | Comments stored per-person with author identity | Query "comments by user X" returns correct results |
| AC-04 | Multi-dimensional graph traversable | BFS from user → tickets → related tickets works |
| AC-05 | Feature auto-detection produces groupings | After sync, features exist with ticket assignments |
| AC-06 | Dimension config API accepts new dimensions | POST new dimension → next sync indexes it |
| AC-07 | All data has source_ref | No KB entry without source_ref |
| AC-08 | Attachment metadata queryable | Query attachments by ticket returns correct list |
| AC-09 | Incremental sync only processes changed tickets | Re-sync unchanged project = 0 new ingestions |
| AC-10 | Crash recovery works | Kill during sync → restart → resumes correctly |

---

## 5. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Shared module increases coupling | Medium | Clear interface boundaries, DI |
| AI feature detection inaccurate | Low | Confidence scores + manual override later |
| Large projects slow to sync | Medium | Pagination + concurrent fetch + incremental |
| Jira API rate limiting | Medium | Existing rate limiter + backoff |
| Config-driven dimensions complex | Medium | Start with 5 built-in, extend via config |

---

## 6. Appendix

### 6.1 Related Documents

| Document | Ticket | Relevance |
|----------|--------|-----------|
| ProjectScanner TDD | MTO-17 | Scan + pagination logic |
| TicketCrawler TDD | MTO-18 | Deep content fetch + graph build |
| Sync Tools BRD | MTO-20 | MCP tool registration |
| KB Server FSD | MTO-38 | Queue + ingest pipeline |

### 6.2 Glossary

| Term | Definition |
|------|------------|
| Dimension | A configurable axis of indexing (e.g., comments, users, features) |
| Source Ref | Provenance identifier linking data to its origin |
| Feature | AI-determined grouping of related tickets |
| Shared Pipeline | Common crawl+index logic used by both tools |
