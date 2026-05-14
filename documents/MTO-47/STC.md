# Software Test Cases (STC)

## MCPOrchestration — MTO-47: Unified Sync Pipeline — Multi-Dimensional Jira Indexing

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-47 |
| Title | Unified Sync Pipeline — Multi-Dimensional Jira Indexing |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-14 |
| Status | Draft |
| Related STP | STP-v1-MTO-47.docx |
| Related FSD | FSD-v1-MTO-47.docx |
| Related TDD | TDD-v1-MTO-47.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-14 | QA Agent | Initiate document — auto-generated from BRD, FSD, and TDD |

---

## Test Case Summary

| Level | ID Range | Count | Automated |
|-------|----------|-------|-----------|
| PBT — Property-Based Testing | PBT-001 to PBT-012 | 12 | 12 |
| UT — Unit Testing | UT-001 to UT-045 | 45 | 45 |
| IT — Integration Testing | IT-001 to IT-028 | 28 | 28 |
| E2E-API — End-to-End API | E2E-API-001 to E2E-API-018 | 18 | 18 |
| E2E-UI — End-to-End UI | E2E-UI-001 to E2E-UI-008 | 8 | 6 |
| SIT — System Integration | SIT-001 to SIT-010 | 10 | 7 |
| **Total** | | **121** | **116** |

---

## 1. Property-Based Testing (PBT)

### PBT-001: ContentHasher Determinism

| Field | Value |
|-------|-------|
| **ID** | PBT-001 |
| **Priority** | High |
| **Requirement** | BR-02 (Idempotent), FSD §7.2 |
| **Property** | For any input string S, `ContentHasher.hash(S)` always returns the same SHA-256 value |

**Generator:** Random strings (0 to 50000 chars, including Unicode, empty, whitespace-only)
**Property assertion:** `hash(s) == hash(s)` for all generated `s`
**Shrink:** Minimal failing input

---

### PBT-002: IndexEntry ID Determinism

| Field | Value |
|-------|-------|
| **ID** | PBT-002 |
| **Priority** | High |
| **Requirement** | BR-02, BR-07 (Upsert) |
| **Property** | `deterministicId(key)` produces same UUID for same key, different UUID for different keys |

**Generator:** Random entry keys (ticket_key + dimension + suffix combinations)
**Property assertion:** `deterministicId(k1) == deterministicId(k1)` AND `k1 != k2 → deterministicId(k1) != deterministicId(k2)`

---

### PBT-003: DimensionProcessor Purity (No Side Effects)

| Field | Value |
|-------|-------|
| **ID** | PBT-003 |
| **Priority** | High |
| **Requirement** | UC-02 |
| **Property** | For any CrawledTicket T, `dimensionProcessor.process(T)` returns same entries regardless of call order |

**Generator:** Random CrawledTicket instances (varying comments count 0-50, links 0-20, attachments 0-10)
**Property assertion:** `process(t).sortedBy { it.entryKey } == process(t).sortedBy { it.entryKey }`

---

### PBT-004: SourceRef Path Format Validity

| Field | Value |
|-------|-------|
| **ID** | PBT-004 |
| **Priority** | Medium |
| **Requirement** | BR-26, BR-27 |
| **Property** | All generated SourceRef paths match pattern `^(jira|derived):[A-Z]+/[A-Z]+-\d+(/\w+/\w+)?$` |

**Generator:** Random project keys, ticket keys, comment IDs, attachment IDs
**Property assertion:** Every IndexEntry produced by any dimension has valid source_path format

---

### PBT-005: Batch Write Atomicity

| Field | Value |
|-------|-------|
| **ID** | PBT-005 |
| **Priority** | High |
| **Requirement** | TDD §4.2 |
| **Property** | BatchIndexWriter with buffer size N flushes exactly when buffer reaches N entries |

**Generator:** Random sequences of IndexEntry (1-500 entries), random buffer sizes (1-100)
**Property assertion:** Total DB writes = ceil(entries.size / bufferSize)

---

### PBT-006: Comment Extraction Completeness

| Field | Value |
|-------|-------|
| **ID** | PBT-006 |
| **Priority** | High |
| **Requirement** | BR-08 |
| **Property** | CommentDimension.extract(ticket) produces exactly ticket.comments.size entries |

**Generator:** CrawledTicket with 0-100 random comments
**Property assertion:** `extract(ticket).size == ticket.comments.size`

---

### PBT-007: UserRelation Uniqueness

| Field | Value |
|-------|-------|
| **ID** | PBT-007 |
| **Priority** | High |
| **Requirement** | BR-14 |
| **Property** | UserRelationDimension never produces duplicate (user_id, ticket_key, role) combinations |

**Generator:** CrawledTicket with overlapping commenters (same user comments multiple times)
**Property assertion:** All entry_keys in result are unique

---

### PBT-008: JQL Builder Safety

| Field | Value |
|-------|-------|
| **ID** | PBT-008 |
| **Priority** | Medium |
| **Requirement** | UC-01 Step 5 |
| **Property** | buildJql never produces SQL injection or invalid JQL regardless of input |

**Generator:** Random project keys including special chars, SQL injection attempts
**Property assertion:** Output matches safe JQL pattern, no unescaped quotes

---

### PBT-009: Incremental Sync Hash Skip Correctness

| Field | Value |
|-------|-------|
| **ID** | PBT-009 |
| **Priority** | High |
| **Requirement** | AC-09 |
| **Property** | If ticket content unchanged (same hash), it is skipped; if changed, it is processed |

**Generator:** Pairs of (old_hash, new_content) — some matching, some different
**Property assertion:** `shouldProcess(ticket) == (storedHash != computedHash)`

---

### PBT-010: DimensionConfig Serialization Roundtrip

| Field | Value |
|-------|-------|
| **ID** | PBT-010 |
| **Priority** | Medium |
| **Requirement** | BR-21 |
| **Property** | Any DimensionConfig serialized to JSON and deserialized back equals original |

**Generator:** Random DimensionConfig instances
**Property assertion:** `deserialize(serialize(config)) == config`

---

### PBT-011: SyncState Transitions Valid

| Field | Value |
|-------|-------|
| **ID** | PBT-011 |
| **Priority** | High |
| **Requirement** | BR-04 |
| **Property** | State machine only allows valid transitions: IDLE→RUNNING, RUNNING→COMPLETED, RUNNING→FAILED |

**Generator:** Random sequences of state transition attempts
**Property assertion:** Invalid transitions throw IllegalStateException

---

### PBT-012: Vector Text Truncation Safety

| Field | Value |
|-------|-------|
| **ID** | PBT-012 |
| **Priority** | Medium |
| **Requirement** | FSD §9.1 content_max_length |
| **Property** | vectorText never exceeds configured max length |

**Generator:** CrawledTickets with very long descriptions/comments (up to 100K chars)
**Property assertion:** All vectorText fields in output ≤ config.pipeline.contentMaxLength

---

## 2. Unit Testing (UT)

### UT-001: ContentHasher — SHA-256 Correct Output

| Field | Value |
|-------|-------|
| **ID** | UT-001 |
| **Priority** | High |
| **Requirement** | FSD §7.2 |
| **Preconditions** | None |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `ContentHasher.hash("Hello World")` | Returns known SHA-256: `a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e` |
| 2 | Call `ContentHasher.hash("")` | Returns SHA-256 of empty string |
| 3 | Call `ContentHasher.hash(null)` | Returns null or empty hash |

---

### UT-002: AdfParser — Simple Paragraph

| Field | Value |
|-------|-------|
| **ID** | UT-002 |
| **Priority** | High |
| **Requirement** | TDD §2.1 crawl/AdfParser |
| **Preconditions** | ADF JSON fixture loaded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Parse ADF `{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hello"}]}]}` | Returns "Hello" |
| 2 | Parse ADF with multiple paragraphs | Returns paragraphs joined by newline |
| 3 | Parse ADF with inline code, bold, italic | Returns plain text without formatting |

---

### UT-003: AdfParser — Complex Structures

| Field | Value |
|-------|-------|
| **ID** | UT-003 |
| **Priority** | High |
| **Requirement** | TDD §2.1 |
| **Preconditions** | Complex ADF fixtures |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Parse ADF with bullet list | Returns list items as lines |
| 2 | Parse ADF with table | Returns table content as text |
| 3 | Parse ADF with code block | Returns code content |
| 4 | Parse ADF with mentions | Returns mention display name |
| 5 | Parse null/empty ADF | Returns empty string |

---

### UT-004: TicketMetadataDimension — Extract Full Metadata

| Field | Value |
|-------|-------|
| **ID** | UT-004 |
| **Priority** | High |
| **Requirement** | UC-02, BR-05 to BR-07 |
| **Preconditions** | CrawledTicket fixture with all fields populated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(fullTicket, config)` | Returns 1 IndexEntry |
| 2 | Verify entry.dimensionId | Equals "ticket_metadata" |
| 3 | Verify entry.data contains all required fields | summary, description, issue_type, status, priority, assignee, reporter, labels, components present |
| 4 | Verify entry.sourceRef.path | Matches `jira:{project}/{key}` |
| 5 | Verify entry.vectorText | Contains summary + description snippet |

---

### UT-005: TicketMetadataDimension — Minimal Ticket (Optional Fields Null)

| Field | Value |
|-------|-------|
| **ID** | UT-005 |
| **Priority** | High |
| **Requirement** | BR-06 |
| **Preconditions** | CrawledTicket with only required fields |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(minimalTicket, config)` | Returns 1 IndexEntry without error |
| 2 | Verify optional fields in data | Null values stored as null (not missing keys) |

---

### UT-006: CommentDimension — Multiple Comments

| Field | Value |
|-------|-------|
| **ID** | UT-006 |
| **Priority** | High |
| **Requirement** | UC-03, BR-08, BR-09 |
| **Preconditions** | CrawledTicket with 3 comments from 2 different authors |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Returns 3 IndexEntries |
| 2 | Verify each entry has unique entryKey | Format: `{ticketKey}:{commentId}` |
| 3 | Verify author_account_id present in each | Stable account ID stored |
| 4 | Verify author_display_name present | Human-readable name stored |
| 5 | Verify vectorText format | "Comment by {name} on {key}: {body_snippet}" |

---

### UT-007: CommentDimension — Zero Comments

| Field | Value |
|-------|-------|
| **ID** | UT-007 |
| **Priority** | Medium |
| **Requirement** | BR-08 |
| **Preconditions** | CrawledTicket with empty comments list |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticketNoComments, config)` | Returns empty list |

---

### UT-008: AttachmentDimension — Extract Metadata

| Field | Value |
|-------|-------|
| **ID** | UT-008 |
| **Priority** | Medium |
| **Requirement** | UC-08, BR-30, BR-32 |
| **Preconditions** | CrawledTicket with 2 attachments |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Returns 2 IndexEntries |
| 2 | Verify data contains filename, mime_type, size_bytes | All metadata present |
| 3 | Verify download_url NOT in data | Security: URL not exposed |
| 4 | Verify vectorText is null | Attachments don't support vector |

---

### UT-009: UserRelationDimension — All Roles Extracted

| Field | Value |
|-------|-------|
| **ID** | UT-009 |
| **Priority** | High |
| **Requirement** | UC-04, BR-14 |
| **Preconditions** | CrawledTicket with assignee, reporter, and 2 unique commenters |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Returns 4 entries (assignee + reporter + 2 commenters) |
| 2 | Verify relation_type values | "assignee", "reporter", "commenter" |
| 3 | Verify no duplicate user+ticket+role | All entry_keys unique |

---

### UT-010: UserRelationDimension — Same User Multiple Roles

| Field | Value |
|-------|-------|
| **ID** | UT-010 |
| **Priority** | High |
| **Requirement** | BR-14 |
| **Preconditions** | CrawledTicket where assignee is also a commenter |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Returns separate entries for each role |
| 2 | Verify same user has both "assignee" and "commenter" entries | Both present with different entry_keys |

---

### UT-011: UserRelationDimension — Duplicate Commenter Dedup

| Field | Value |
|-------|-------|
| **ID** | UT-011 |
| **Priority** | High |
| **Requirement** | BR-14 |
| **Preconditions** | CrawledTicket where same user commented 5 times |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Only 1 "commenter" entry for that user (deduped by accountId) |

---

### UT-012: FeatureDetectionDimension — Extract Returns Empty

| Field | Value |
|-------|-------|
| **ID** | UT-012 |
| **Priority** | Medium |
| **Requirement** | TDD §4.5 |
| **Preconditions** | Any CrawledTicket |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Returns empty list (feature detection is post-sync only) |

---

### UT-013: DimensionRegistry — Load Enabled Dimensions

| Field | Value |
|-------|-------|
| **ID** | UT-013 |
| **Priority** | High |
| **Requirement** | BR-21 |
| **Preconditions** | 5 dimension configs in DB, 1 disabled |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `registry.getEnabled(null)` | Returns 4 dimensions (excludes disabled) |
| 2 | Call `registry.getEnabled(listOf("comments", "attachments"))` | Returns only 2 specified dimensions |

---

### UT-014: DimensionRegistry — Unknown Dimension ID

| Field | Value |
|-------|-------|
| **ID** | UT-014 |
| **Priority** | Medium |
| **Requirement** | BR-22 |
| **Preconditions** | Registry loaded |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `registry.getEnabled(listOf("nonexistent"))` | Returns empty list (graceful, no exception) |

---

### UT-015: SyncPipelineConfig — Load from YAML

| Field | Value |
|-------|-------|
| **ID** | UT-015 |
| **Priority** | High |
| **Requirement** | FSD §9.1 |
| **Preconditions** | Valid YAML config file |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Load config from test YAML | All fields populated correctly |
| 2 | Verify defaults applied for missing fields | batch_size=50, max_concurrent=5, etc. |
| 3 | Verify env variable substitution | `${JIRA_URL}` resolved |

---

### UT-016: SyncPipelineConfig — Invalid Config

| Field | Value |
|-------|-------|
| **ID** | UT-016 |
| **Priority** | Medium |
| **Requirement** | FSD §9.1 |
| **Preconditions** | Invalid YAML (missing required fields) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Load config with missing jira.base_url | Throws ConfigurationException with clear message |
| 2 | Load config with batch_size = 0 | Throws validation error |
| 3 | Load config with unknown provider | Throws validation error |

---

### UT-017: SyncStateTracker — State Transitions

| Field | Value |
|-------|-------|
| **ID** | UT-017 |
| **Priority** | High |
| **Requirement** | BR-04 |
| **Preconditions** | Mock DB |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | markRunning("MTO") when IDLE | State = RUNNING, started_at set |
| 2 | markRunning("MTO") when already RUNNING | Throws IllegalStateException |
| 3 | markCompleted("MTO") when RUNNING | State = COMPLETED, last_sync_at set |
| 4 | markFailed("MTO", "error") when RUNNING | State = FAILED, error_message set |

---

### UT-018: SyncStateTracker — Stale Detection

| Field | Value |
|-------|-------|
| **ID** | UT-018 |
| **Priority** | High |
| **Requirement** | FSD §9.1 stale_timeout_minutes |
| **Preconditions** | State = RUNNING, updated_at = 40 minutes ago |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call markRunning("MTO") | Detects stale RUNNING state, resets to IDLE, then marks RUNNING |

---

### UT-019: AiProviderFactory — Create Ollama Model

| Field | Value |
|-------|-------|
| **ID** | UT-019 |
| **Priority** | Medium |
| **Requirement** | TDD §6 |
| **Preconditions** | Config with provider="ollama" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `createChatModel(ollamaConfig)` | Returns OllamaChatModel instance |
| 2 | Verify baseUrl, model, temperature set | Matches config values |

---

### UT-020: AiProviderFactory — Unsupported Provider

| Field | Value |
|-------|-------|
| **ID** | UT-020 |
| **Priority** | Medium |
| **Requirement** | TDD §6 |
| **Preconditions** | Config with provider="unknown" |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `createChatModel(unknownConfig)` | Throws IllegalArgumentException with supported providers listed |

---

### UT-021: AiAnalysisService — Epic-Based Grouping

| Field | Value |
|-------|-------|
| **ID** | UT-021 |
| **Priority** | High |
| **Requirement** | BR-20 |
| **Preconditions** | 5 TicketSummary instances, 3 under same epic |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `detectFeatures(tickets)` with AI unavailable | Returns 1 FeatureGroup with 3 tickets |
| 2 | Verify featureId format | "epic-{epicKey}" |
| 3 | Verify confidence | 1.0 (epic-based = certain) |
| 4 | Verify detectionMethod | "epic_hierarchy" |

---

### UT-022: AiAnalysisService — No Epics

| Field | Value |
|-------|-------|
| **ID** | UT-022 |
| **Priority** | Medium |
| **Requirement** | BR-17 |
| **Preconditions** | Tickets with no epic links |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `detectFeatures(tickets)` | Returns empty list (no features detected) |

---

### UT-023: GenericFieldDimension — Extract Configured Fields

| Field | Value |
|-------|-------|
| **ID** | UT-023 |
| **Priority** | Medium |
| **Requirement** | FSD §5.1, BR-22 |
| **Preconditions** | DimensionConfig with fields=["sprint", "story_points"] |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call `extract(ticket, config)` | Returns 1 entry with data containing only sprint + story_points |
| 2 | Verify missing fields | Fields not in ticket data stored as null |

---

### UT-024: BatchIndexWriter — Buffer and Flush

| Field | Value |
|-------|-------|
| **ID** | UT-024 |
| **Priority** | High |
| **Requirement** | TDD §4.2 |
| **Preconditions** | Mock PostgresIndexWriter, buffer_size=10 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Write 9 entries | No flush to DB yet |
| 2 | Write 1 more entry (total 10) | Auto-flush: DB receives batch of 10 |
| 3 | Write 5 more, then call flush() | DB receives batch of 5 |

---

### UT-025: VectorIndexWriter — Queue and Batch Embed

| Field | Value |
|-------|-------|
| **ID** | UT-025 |
| **Priority** | High |
| **Requirement** | TDD §4.7 |
| **Preconditions** | Mock EmbeddingService, batch_size=5 |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Queue 4 entries with vectorText | No embedding call yet |
| 2 | Queue 1 more (total 5) | EmbeddingService called with 5 texts, VectorDB upsert called |
| 3 | Verify vector points have correct payload | dimension_id, project_key, source_path present |

---

### UT-026 to UT-045: Additional Unit Tests

| ID | Test | Requirement |
|----|------|-------------|
| UT-026 | JiraCrawlService — buildJql full sync | UC-01 Step 5 |
| UT-027 | JiraCrawlService — buildJql incremental | UC-01 Step 5 |
| UT-028 | TicketFetcher — Map Jira response to CrawledTicket | UC-01 Step 7a |
| UT-029 | TicketFetcher — Handle missing optional fields | UC-01 |
| UT-030 | DimensionProcessor — Parallel execution | UC-02 |
| UT-031 | DimensionProcessor — Single dimension failure isolated | UC-02 |
| UT-032 | PostgresIndexWriter — Upsert ON CONFLICT | BR-07 |
| UT-033 | PostgresIndexWriter — Batch INSERT SQL generation | TDD §4.2 |
| UT-034 | SyncOrchestratorImpl — validateProjectKey | UC-01 Step 1 |
| UT-035 | SyncOrchestratorImpl — Cancel running sync | FSD §4.2 |
| UT-036 | SyncOrchestratorImpl — getProgress returns current stats | FSD §4.2 |
| UT-037 | SourceRef — Path construction for each type | BR-27 |
| UT-038 | IndexEntry — deterministicId from entryKey | BR-02 |
| UT-039 | CrawledTicket — contentHash computation | FSD §7.2 |
| UT-040 | SyncPipelineModule — All bindings resolve | TDD §5.3 |
| UT-041 | DimensionConfig — JSON serialization | BR-21 |
| UT-042 | SyncOptions — Default values | FSD §4.2 |
| UT-043 | SyncResult — entriesCreated aggregation | FSD §4.2 |
| UT-044 | AdfParser — Nested structures (table in list) | TDD §2.1 |
| UT-045 | CommentDimension — Long comment truncation for vectorText | FSD §9.1 |

---

## 3. Integration Testing (IT)

### IT-001: Full Sync — 10 Tickets End-to-End (Testcontainers)

| Field | Value |
|-------|-------|
| **ID** | IT-001 |
| **Priority** | Critical |
| **Requirement** | UC-01, AC-01 |
| **Preconditions** | PostgreSQL Testcontainer running, mock Jira API returning 10 tickets |
| **Technique** | Testcontainers (PostgreSQL), MockK (Jira HTTP), real DimensionProcessor |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start PostgreSQL Testcontainer, run migration SQL | Schema `sync` created with all tables |
| 2 | Configure mock Jira to return 10 tickets (3 types, 20 comments) | Mock responds to search + fetch |
| 3 | Call `syncOrchestrator.sync("TEST", SyncOptions(fullSync=true))` | Returns SyncResult with processedTickets=10 |
| 4 | Query `sync.index_entries WHERE project_key='TEST'` | Entries exist for all 5 dimensions |
| 5 | Verify ticket_metadata entries | 10 entries, one per ticket |
| 6 | Verify comment entries | 20 entries, one per comment |
| 7 | Verify user_relations entries | Entries for all assignees + reporters + commenters |
| 8 | Verify sync.state | status=COMPLETED, synced_issues=10 |

**Test Data:** `src/test/resources/fixtures/jira-10-tickets.json`

---

### IT-002: Incremental Sync — Skip Unchanged Tickets

| Field | Value |
|-------|-------|
| **ID** | IT-002 |
| **Priority** | High |
| **Requirement** | AC-09, FSD §7.2 |
| **Preconditions** | Previous full sync completed, 10 tickets in DB |
| **Technique** | Testcontainers, content hash comparison |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run full sync (10 tickets) | All 10 processed |
| 2 | Modify 2 tickets in mock (change description) | Mock returns updated content |
| 3 | Run incremental sync | Only 2 tickets processed (8 skipped via hash) |
| 4 | Verify updated entries in DB | 2 entries have new updated_at, 8 unchanged |

---

### IT-003: Crash Recovery — Resume from Checkpoint

| Field | Value |
|-------|-------|
| **ID** | IT-003 |
| **Priority** | High |
| **Requirement** | AC-10, FSD UC-01 Error Handling |
| **Preconditions** | Testcontainer, mock Jira with 50 tickets |
| **Technique** | Testcontainers, simulated crash via CancellationException |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start sync, cancel after 20 tickets processed | State = FAILED, last_offset = 20 |
| 2 | Restart sync (fullSync=false) | Resumes from offset 20 |
| 3 | Verify all 50 tickets eventually processed | Total entries = expected count |

---

### IT-004: PostgresIndexWriter — Upsert Idempotency

| Field | Value |
|-------|-------|
| **ID** | IT-004 |
| **Priority** | High |
| **Requirement** | BR-02, BR-07 |
| **Preconditions** | Testcontainer with sync schema |
| **Technique** | Testcontainers (PostgreSQL) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Write 10 IndexEntries to DB | 10 rows in sync.index_entries |
| 2 | Write same 10 entries again (same entry_keys) | Still 10 rows (upsert, not duplicate) |
| 3 | Verify updated_at changed | Timestamps updated on second write |

---

### IT-005: PostgresIndexWriter — Batch Performance

| Field | Value |
|-------|-------|
| **ID** | IT-005 |
| **Priority** | High |
| **Requirement** | FSD §7.4 (100 entries < 500ms) |
| **Preconditions** | Testcontainer |
| **Technique** | Testcontainers, timing assertion |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Generate 100 IndexEntries | Entries ready |
| 2 | Call writeBatch(entries) | Completes in < 500ms |
| 3 | Verify all 100 in DB | Count = 100 |

---

### IT-006: VectorIndexWriter — Batch Embedding Integration

| Field | Value |
|-------|-------|
| **ID** | IT-006 |
| **Priority** | High |
| **Requirement** | TDD §4.7, FSD §7.4 |
| **Preconditions** | Mock EmbeddingService (returns fixed vectors), mock VectorDbClient |
| **Technique** | MockK for external services, verify batch calls |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Queue 20 entries with vectorText | Buffer fills |
| 2 | Flush | EmbeddingService.generateEmbeddings called with 20 texts |
| 3 | Verify VectorDbClient.upsert called | 20 VectorPoints with correct payloads |

---

### IT-007: DimensionProcessor — Concurrent Execution

| Field | Value |
|-------|-------|
| **ID** | IT-007 |
| **Priority** | High |
| **Requirement** | FSD §7.2 Dimension parallelism |
| **Preconditions** | 5 dimensions registered, each with artificial 100ms delay |
| **Technique** | Coroutine timing, verify parallel execution |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Process 1 ticket through all 5 dimensions | Total time < 200ms (parallel, not 500ms sequential) |
| 2 | Verify all 5 dimensions produced entries | Entries from each dimension present |

---

### IT-008: DimensionProcessor — Single Dimension Failure Isolation

| Field | Value |
|-------|-------|
| **ID** | IT-008 |
| **Priority** | High |
| **Requirement** | UC-02 |
| **Preconditions** | 5 dimensions, 1 configured to throw exception |
| **Technique** | MockK (one dimension throws), verify others succeed |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Process ticket | 4 dimensions succeed, 1 fails |
| 2 | Verify entries from 4 successful dimensions | All present |
| 3 | Verify warning logged for failed dimension | Log contains dimension ID + error |

---

### IT-009: JiraCrawlService — Pagination

| Field | Value |
|-------|-------|
| **ID** | IT-009 |
| **Priority** | High |
| **Requirement** | UC-01 Step 6 |
| **Preconditions** | Mock Jira returns 120 tickets across 3 pages (batch_size=50) |
| **Technique** | MockK (Jira client), Flow collection |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call crawlProject("TEST", null, SyncOptions(batchSize=50)) | Flow emits all 120 tickets |
| 2 | Verify Jira search called 3 times | startAt=0, 50, 100 |
| 3 | Verify all tickets have full content | Comments, links, attachments populated |

---

### IT-010: JiraCrawlService — Concurrent Fetch with Semaphore

| Field | Value |
|-------|-------|
| **ID** | IT-010 |
| **Priority** | High |
| **Requirement** | FSD §7.1, §7.2 |
| **Preconditions** | Mock Jira with 10 tickets per page, maxConcurrentFetches=3 |
| **Technique** | AtomicInteger counter to verify max concurrency |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Crawl with maxConcurrentFetches=3 | Never more than 3 simultaneous fetch calls |
| 2 | Verify all tickets fetched | 10 tickets emitted |
| 3 | Verify total time < sequential time | Parallel execution confirmed |

---

### IT-011: SyncOrchestrator — Full Pipeline (Crawl → Process → Write)

| Field | Value |
|-------|-------|
| **ID** | IT-011 |
| **Priority** | Critical |
| **Requirement** | UC-01 full flow |
| **Preconditions** | Testcontainer, mock Jira (10 tickets), all dimensions enabled |
| **Technique** | Testcontainers, full pipeline execution |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call sync("TEST", SyncOptions(fullSync=true)) | SyncResult returned |
| 2 | Verify result.processedTickets | 10 |
| 3 | Verify result.entriesCreated | Map with counts per dimension |
| 4 | Verify result.status | COMPLETED |
| 5 | Verify DB state | sync.state = COMPLETED, entries in all dimension tables |

---

### IT-012: SyncOrchestrator — Jira API 401 Handling

| Field | Value |
|-------|-------|
| **ID** | IT-012 |
| **Priority** | High |
| **Requirement** | FSD UC-01 Error Handling |
| **Preconditions** | Mock Jira returns 401 |
| **Technique** | MockK, exception handling verification |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call sync("TEST") with Jira returning 401 | Throws exception |
| 2 | Verify sync.state | status=FAILED, error_message contains "auth" |

---

### IT-013: SyncOrchestrator — Jira API 429 Retry

| Field | Value |
|-------|-------|
| **ID** | IT-013 |
| **Priority** | High |
| **Requirement** | FSD UC-01 Error Handling |
| **Preconditions** | Mock Jira returns 429 twice, then 200 |
| **Technique** | MockK with answer sequence |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call sync("TEST") | Retries after 429, eventually succeeds |
| 2 | Verify Jira called 3 times for first page | 2 retries + 1 success |
| 3 | Verify final state | COMPLETED |

---

### IT-014: SyncOrchestrator — Single Ticket Failure Skipped

| Field | Value |
|-------|-------|
| **ID** | IT-014 |
| **Priority** | High |
| **Requirement** | FSD UC-01 Error Handling |
| **Preconditions** | Mock Jira: 10 tickets, ticket #5 fetch throws exception |
| **Technique** | MockK, verify skip behavior |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call sync("TEST") | Completes without throwing |
| 2 | Verify result.processedTickets | 9 (1 skipped) |
| 3 | Verify result.skippedTickets | 1 |
| 4 | Verify warning logged | Contains skipped ticket key |

---

### IT-015: FeatureDetection — Post-Sync with AI Mock

| Field | Value |
|-------|-------|
| **ID** | IT-015 |
| **Priority** | Medium |
| **Requirement** | UC-05, BR-17 to BR-20 |
| **Preconditions** | Testcontainer with 10 tickets (3 under epic), mock AI service |
| **Technique** | Testcontainers, MockK (AiAnalysisService) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run full sync | Tickets indexed |
| 2 | Verify postProcess called | FeatureDetectionDimension.postProcess invoked |
| 3 | Verify feature entries in DB | At least 1 feature entry with ticket_keys |
| 4 | Verify feature entry sourceRef | type="ai_derived", derivedFrom contains ticket paths |

---

### IT-016: FeatureDetection — AI Unavailable Graceful Degradation

| Field | Value |
|-------|-------|
| **ID** | IT-016 |
| **Priority** | Medium |
| **Requirement** | FSD §8.4 |
| **Preconditions** | AI service throws on isHealthy() |
| **Technique** | MockK |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run sync with AI unavailable | Sync completes without error |
| 2 | Verify feature entries | Epic-based grouping only (no AI enrichment) |
| 3 | Verify warning logged | "AI feature detection failed, using epic-only" |

---

### IT-017: Dimension Config — CRUD via Repository

| Field | Value |
|-------|-------|
| **ID** | IT-017 |
| **Priority** | Medium |
| **Requirement** | UC-06, BR-21 to BR-25 |
| **Preconditions** | Testcontainer with seed data |
| **Technique** | Testcontainers |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | List all configs | Returns 5 default dimensions |
| 2 | Add new dimension config | Inserted, returns with ID |
| 3 | Update existing config (disable) | enabled=false |
| 4 | Delete config | Soft delete (or removed) |
| 5 | Verify next sync skips disabled dimension | No entries for disabled dimension |

---

### IT-018: PII Masking — Comment Body Masked

| Field | Value |
|-------|-------|
| **ID** | IT-018 |
| **Priority** | High |
| **Requirement** | BR-34 to BR-36 |
| **Preconditions** | Comment with email "test@company.com" and name "Nguyen Van A" |
| **Technique** | Real PiiMaskingEngine, Testcontainers |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Sync ticket with PII in comments | Entry stored |
| 2 | Verify data["body"] | Contains original text with PII |
| 3 | Verify data["body_masked"] | PII replaced with [NAME_1], [EMAIL_1] |
| 4 | Verify PII mappings stored | Separate table/field with placeholder→original mapping |

---

### IT-019: Source Provenance — All Entries Have SourceRef

| Field | Value |
|-------|-------|
| **ID** | IT-019 |
| **Priority** | High |
| **Requirement** | AC-07, BR-26 |
| **Preconditions** | Full sync completed |
| **Technique** | Testcontainers, DB query |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query all entries: `SELECT * FROM sync.index_entries WHERE source_path IS NULL` | Returns 0 rows |
| 2 | Query all entries: `SELECT * FROM sync.index_entries WHERE source_type IS NULL` | Returns 0 rows |
| 3 | Verify source_path format per dimension | Matches expected patterns from FSD §2.7 |

---

### IT-020: Sync Users Registry — Auto-Populated

| Field | Value |
|-------|-------|
| **ID** | IT-020 |
| **Priority** | Medium |
| **Requirement** | TDD §3.1 sync.users |
| **Preconditions** | Sync with tickets having 5 unique users |
| **Technique** | Testcontainers |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run full sync | Users extracted |
| 2 | Query sync.users | 5 rows with account_id, display_name |
| 3 | Verify total_tickets count | Correct per user |
| 4 | Verify total_comments count | Correct per user |

---

### IT-021 to IT-028: Additional Integration Tests

| ID | Test | Requirement | Technique |
|----|------|-------------|-----------|
| IT-021 | Koin DI — SyncPipelineModule resolves all bindings | TDD §5.3 | Koin test (checkModules) |
| IT-022 | DB Migration — All tables created correctly | TDD §3.1 | Testcontainers + Flyway |
| IT-023 | DB Migration — Seed data inserted | TDD §3.1 | Testcontainers |
| IT-024 | Streaming pipeline — Memory bounded (100 tickets < 200MB) | FSD §7.4 | Testcontainers + memory profiling |
| IT-025 | Concurrent sync prevention — Second sync rejected | BR-04 | Testcontainers |
| IT-026 | GenericFieldDimension — Custom fields extracted | FSD §5.1 | Testcontainers |
| IT-027 | Attachment dimension — Metadata without download URL | BR-32 | Testcontainers |
| IT-028 | Vector graceful degradation — Embedding fails, relational still works | FSD §8.4 | MockK + Testcontainers |

---

## 4. E2E API Testing (E2E-API)

### E2E-API-001: jira_project_sync Tool — Full Sync via MCP

| Field | Value |
|-------|-------|
| **ID** | E2E-API-001 |
| **Priority** | Critical |
| **Requirement** | AC-01, UC-01 |
| **Preconditions** | orchestrator-server running (Ktor testApplication), PostgreSQL, mock Jira |
| **Technique** | Ktor testApplication, real DB, mock Jira HTTP |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send MCP tools/call: `jira_project_sync` with `{projectKey: "TEST", fullSync: true}` | Returns success response with sync started message |
| 2 | Poll `jira_sync_status` tool until completed | Status = COMPLETED |
| 3 | Query DB: sync.index_entries count | > 0 entries across all dimensions |
| 4 | Query DB: sync.state | status=COMPLETED, synced_issues matches expected |

---

### E2E-API-002: kb_sync_trigger Tool — Full Sync via MCP

| Field | Value |
|-------|-------|
| **ID** | E2E-API-002 |
| **Priority** | Critical |
| **Requirement** | AC-01 |
| **Preconditions** | kb-server running, same PostgreSQL, mock Jira |
| **Technique** | Ktor testApplication (kb-server), real DB |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Send MCP tools/call: `kb_sync_trigger` with `{project_key: "TEST", full_sync: true}` | Task queued |
| 2 | Wait for queue processing | Task completed |
| 3 | Query DB: sync.index_entries | Same data as E2E-API-001 |

---

### E2E-API-003: Both Tools Produce Identical Data (AC-01)

| Field | Value |
|-------|-------|
| **ID** | E2E-API-003 |
| **Priority** | Critical |
| **Requirement** | AC-01 |
| **Preconditions** | Both servers configured with same DB, same mock Jira |
| **Technique** | Sequential execution + DB comparison |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Sync via orchestrator-server (jira_project_sync) | Completes |
| 2 | Record all entries: `SELECT * FROM sync.index_entries ORDER BY entry_key` | Snapshot A |
| 3 | Truncate sync.index_entries + sync.state | Clean slate |
| 4 | Sync via kb-server (kb_sync_trigger) | Completes |
| 5 | Record all entries: same query | Snapshot B |
| 6 | Compare Snapshot A vs B (excluding synced_at timestamps) | Identical |

---

### E2E-API-004: Query Tickets by Project + Type

| Field | Value |
|-------|-------|
| **ID** | E2E-API-004 |
| **Priority** | High |
| **Requirement** | AC-02, BR-06 |
| **Preconditions** | Sync completed with 3 Bugs, 5 Stories, 2 Tasks |
| **Technique** | Direct DB query via test |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query: dimension=ticket_metadata, project=TEST, data->>'issue_type'='Bug' | Returns 3 entries |
| 2 | Query: dimension=ticket_metadata, project=TEST, data->>'issue_type'='Story' | Returns 5 entries |
| 3 | Verify each entry has correct metadata | All required fields present |

---

### E2E-API-005: Query Comments by Author

| Field | Value |
|-------|-------|
| **ID** | E2E-API-005 |
| **Priority** | High |
| **Requirement** | AC-03, BR-10 |
| **Preconditions** | Sync completed, user "user-123" has 5 comments across 3 tickets |
| **Technique** | DB query |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query: dimension=comments, data->>'author_account_id'='user-123' | Returns 5 entries |
| 2 | Verify entries span 3 different ticket_keys | Correct ticket distribution |
| 3 | Verify each has body, created_at, author_display_name | All fields present |

---

### E2E-API-006: Graph Traversal — User to Tickets

| Field | Value |
|-------|-------|
| **ID** | E2E-API-006 |
| **Priority** | High |
| **Requirement** | AC-04, BR-16 |
| **Preconditions** | Sync completed, user assigned to 3 tickets |
| **Technique** | DB query on user_relations dimension |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query: dimension=user_relations, data->>'user_account_id'='user-123' | Returns entries with relation types |
| 2 | Verify relation_types | "assignee" for 3, "commenter" for 2 |
| 3 | Verify bidirectional: from ticket, find users | Query by ticket_key returns user entries |

---

### E2E-API-007: Dimension Config API — List

| Field | Value |
|-------|-------|
| **ID** | E2E-API-007 |
| **Priority** | Medium |
| **Requirement** | UC-06, FSD §2.6 |
| **Preconditions** | kb-server HTTP mode running |
| **Technique** | HTTP GET request |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /api/sync/dimensions | Returns 5 default dimensions |
| 2 | Verify each has id, display_name, enabled, source_type | All fields present |

---

### E2E-API-008: Dimension Config API — Add Custom

| Field | Value |
|-------|-------|
| **ID** | E2E-API-008 |
| **Priority** | Medium |
| **Requirement** | UC-06, BR-22 |
| **Preconditions** | kb-server HTTP mode |
| **Technique** | HTTP POST |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | POST /api/sync/dimensions with new dimension JSON | 201 Created |
| 2 | GET /api/sync/dimensions | Returns 6 dimensions (5 default + 1 new) |
| 3 | Run sync | New dimension processed (GenericFieldDimension) |
| 4 | Verify entries for new dimension in DB | Entries exist |

---

### E2E-API-009: Dimension Config API — Disable

| Field | Value |
|-------|-------|
| **ID** | E2E-API-009 |
| **Priority** | Medium |
| **Requirement** | BR-21 |
| **Preconditions** | 5 dimensions enabled |
| **Technique** | HTTP PUT |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | PUT /api/sync/dimensions/attachments with enabled=false | 200 OK |
| 2 | Run sync | Only 4 dimensions processed |
| 3 | Verify no new attachment entries | Existing preserved, no new ones |

---

### E2E-API-010: Incremental Sync Performance

| Field | Value |
|-------|-------|
| **ID** | E2E-API-010 |
| **Priority** | High |
| **Requirement** | FSD §7.4 (incremental < 20s) |
| **Preconditions** | 100 tickets synced, 10 modified |
| **Technique** | Timing assertion |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run full sync (100 tickets) | Baseline established |
| 2 | Modify 10 tickets in mock | Updated content |
| 3 | Run incremental sync, measure time | Completes in < 20 seconds |
| 4 | Verify only 10 processed | result.processedTickets = 10, skipped = 90 |

---

### E2E-API-011: Full Sync Performance — 100 Tickets

| Field | Value |
|-------|-------|
| **ID** | E2E-API-011 |
| **Priority** | High |
| **Requirement** | FSD §7.4 (100 tickets < 3 min) |
| **Preconditions** | Mock Jira with 100 tickets (realistic data) |
| **Technique** | Timing assertion |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run full sync with 100 tickets | Completes |
| 2 | Measure total duration | < 180 seconds (3 minutes) |
| 3 | Verify all entries created | ~500+ entries across dimensions |

---

### E2E-API-012: Source Provenance — Every Entry Has Valid SourceRef

| Field | Value |
|-------|-------|
| **ID** | E2E-API-012 |
| **Priority** | High |
| **Requirement** | AC-07 |
| **Preconditions** | Full sync completed |
| **Technique** | DB validation query |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query: entries with NULL source_path | 0 rows |
| 2 | Query: entries with NULL source_type | 0 rows |
| 3 | Validate path format per source_type | All match expected patterns |
| 4 | Verify synced_at is recent | Within last hour |

---

### E2E-API-013: Attachment Metadata — No Download URL Exposed

| Field | Value |
|-------|-------|
| **ID** | E2E-API-013 |
| **Priority** | High |
| **Requirement** | AC-08, BR-32 |
| **Preconditions** | Sync with tickets having attachments |
| **Technique** | DB query + assertion |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query attachment entries | Entries exist |
| 2 | Check data JSONB for "download_url" key | NOT present in any entry |
| 3 | Verify filename, mime_type, size present | All metadata fields present |

---

### E2E-API-014: PII Role-Based Access

| Field | Value |
|-------|-------|
| **ID** | E2E-API-014 |
| **Priority** | High |
| **Requirement** | BR-37 |
| **Preconditions** | Comments with PII synced |
| **Technique** | Query with different role contexts |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query as Developer role | Returns body_masked (PII replaced) |
| 2 | Query as Admin role | Returns body (original with PII) |
| 3 | Verify masking placeholders | [NAME_1], [EMAIL_1] format |

---

### E2E-API-015: Sync Cancel

| Field | Value |
|-------|-------|
| **ID** | E2E-API-015 |
| **Priority** | Medium |
| **Requirement** | FSD §4.2 |
| **Preconditions** | Sync running (large project) |
| **Technique** | Async cancel call |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start sync (100 tickets, with delay) | Sync running |
| 2 | Call cancel("TEST") | Returns true |
| 3 | Verify state | FAILED with "Cancelled" message |
| 4 | Verify partial data preserved | Some entries exist (not rolled back) |

---

### E2E-API-016: Sync Progress Reporting

| Field | Value |
|-------|-------|
| **ID** | E2E-API-016 |
| **Priority** | Medium |
| **Requirement** | FSD §4.2 |
| **Preconditions** | Sync running |
| **Technique** | Poll getProgress during sync |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start sync (50 tickets) | Running |
| 2 | Call getProgress("TEST") multiple times | Returns increasing synced_issues |
| 3 | Verify progress.total | Matches expected total |

---

### E2E-API-017: MCP Tool sync_dimension_config

| Field | Value |
|-------|-------|
| **ID** | E2E-API-017 |
| **Priority** | Medium |
| **Requirement** | FSD §2.6 |
| **Preconditions** | Server running |
| **Technique** | MCP tools/call |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call sync_dimension_config with action="list" | Returns all dimension configs |
| 2 | Call with action="update", dimension_id="comments", config={vector_enabled: false} | Updated |
| 3 | Verify change persisted | GET returns updated config |

---

### E2E-API-018: Feature Detection Results Queryable

| Field | Value |
|-------|-------|
| **ID** | E2E-API-018 |
| **Priority** | Medium |
| **Requirement** | AC-05 |
| **Preconditions** | Sync completed with epic-based tickets |
| **Technique** | DB query |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Query: dimension=feature_grouping, project=TEST | Returns feature entries |
| 2 | Verify feature has ticket_keys | List of related ticket keys |
| 3 | Verify confidence score | Between 0.0 and 1.0 |
| 4 | Verify detection_method | "epic_hierarchy" or "ai_hybrid" |

---

## 5. E2E UI Testing (E2E-UI)

### E2E-UI-001: Sync Dashboard — Real-Time Progress via SSE

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-001 |
| **Priority** | High |
| **Requirement** | Sync Dashboard feature |
| **Preconditions** | orchestrator-server running, dashboard accessible at /sync-dashboard.html |
| **Technique** | Playwright |

**Gherkin:**
```gherkin
Feature: Sync Dashboard Real-Time Progress
  Scenario: User sees sync progress update in real-time
    Given the sync dashboard is open at "/sync-dashboard.html"
    And no sync is currently running
    When I click the "Start Sync" button
    And I enter project key "TEST"
    Then the progress bar should appear
    And the progress percentage should increase over time
    And the status card should show "RUNNING"
    When the sync completes
    Then the status card should show "COMPLETED"
    And the synced issues count should be > 0
```

---

### E2E-UI-002: Sync Dashboard — Start/Stop Controls

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-002 |
| **Priority** | High |
| **Requirement** | Dashboard controls |
| **Preconditions** | Dashboard loaded |
| **Technique** | Playwright |

**Gherkin:**
```gherkin
Feature: Sync Dashboard Controls
  Scenario: User can start and stop a sync
    Given the sync dashboard is open
    When I click "Start Sync" with project "TEST"
    Then the "Stop Sync" button should become enabled
    And the "Start Sync" button should become disabled
    When I click "Stop Sync"
    Then the status should change to "FAILED" with message "Cancelled"
    And the "Start Sync" button should become enabled again
```

---

### E2E-UI-003: Sync Dashboard — Event Log Updates

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-003 |
| **Priority** | Medium |
| **Requirement** | Dashboard event log |
| **Preconditions** | Sync running |
| **Technique** | Playwright |

**Gherkin:**
```gherkin
Feature: Sync Dashboard Event Log
  Scenario: Event log shows sync events in real-time
    Given a sync is running for project "TEST"
    When I observe the event log panel
    Then new events should appear as sync progresses
    And events should show timestamp, type, and message
    And the log should not exceed 50 entries (oldest removed)
```

---

### E2E-UI-004: Graph Viewer — Data Refresh After Sync

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-004 |
| **Priority** | High |
| **Requirement** | Graph Viewer integration |
| **Preconditions** | Sync completed, graph-viewer accessible |
| **Technique** | Playwright |

**Gherkin:**
```gherkin
Feature: Graph Viewer Shows Synced Data
  Scenario: Graph displays tickets after sync
    Given a sync has completed for project "TEST" with 10 tickets
    When I open the graph viewer at "/sync/graph-viewer?project=TEST"
    Then the graph should render with 10 nodes
    And nodes should be colored by issue type (hierarchy view)
    And edges should represent ticket relationships
```

---

### E2E-UI-005: Graph Viewer — View Mode Switch

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-005 |
| **Priority** | Medium |
| **Requirement** | Graph view modes |
| **Preconditions** | Graph loaded with data |
| **Technique** | Playwright |

**Gherkin:**
```gherkin
Feature: Graph View Mode Switching
  Scenario: User switches between view modes
    Given the graph viewer is showing hierarchy view
    When I select "Dependency" view mode
    Then nodes should be recolored by status
    And the legend should update to show status colors
    When I select "Team" view mode
    Then nodes should be recolored by assignee
```

---

### E2E-UI-006: Graph Viewer — Node Click Details

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-006 |
| **Priority** | Medium |
| **Requirement** | Graph interaction |
| **Preconditions** | Graph loaded |
| **Technique** | Playwright |

**Gherkin:**
```gherkin
Feature: Graph Node Details
  Scenario: Clicking a node shows ticket details
    Given the graph viewer is loaded with tickets
    When I click on a node representing "TEST-1"
    Then a details panel should appear
    And it should show: summary, issue type, status, priority
    And it should show connected nodes count
```

---

### E2E-UI-007: Sync Dashboard — SSE Reconnection (Manual)

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-007 |
| **Priority** | Low |
| **Requirement** | Dashboard reliability |
| **Preconditions** | Dashboard connected via SSE |
| **Technique** | Manual |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open dashboard, verify SSE connected (green indicator) | Connected |
| 2 | Temporarily stop server (kill process) | Connection indicator turns red |
| 3 | Restart server | Dashboard auto-reconnects within 10s |
| 4 | Verify data refreshes | Current status displayed correctly |

---

### E2E-UI-008: Graph Viewer — Large Dataset Rendering (Manual)

| Field | Value |
|-------|-------|
| **ID** | E2E-UI-008 |
| **Priority** | Low |
| **Requirement** | Performance |
| **Preconditions** | 500+ tickets synced |
| **Technique** | Manual visual verification |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open graph viewer with 500 nodes | Renders within 5 seconds |
| 2 | Rotate/zoom the 3D graph | Smooth interaction (>30 FPS) |
| 3 | Search for a specific ticket | Camera flies to node |
| 4 | Verify no browser crash or freeze | Stable |

---

## 6. System Integration Testing (SIT)

### SIT-001: Cross-Server Data Consistency

| Field | Value |
|-------|-------|
| **ID** | SIT-001 |
| **Priority** | Critical |
| **Requirement** | AC-01 |
| **Preconditions** | Both servers deployed, shared PostgreSQL, real Jira project (or realistic mock) |
| **Technique** | Automated comparison script |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Sync via orchestrator-server: call jira_project_sync | Completes |
| 2 | Export sync.index_entries to JSON (sorted by entry_key) | Snapshot A |
| 3 | Reset: TRUNCATE sync.index_entries, sync.state | Clean |
| 4 | Sync via kb-server: trigger kb_sync_trigger | Completes |
| 5 | Export sync.index_entries to JSON (sorted by entry_key) | Snapshot B |
| 6 | Diff A vs B (ignoring synced_at, id) | No differences |

---

### SIT-002: Concurrent Sync Prevention

| Field | Value |
|-------|-------|
| **ID** | SIT-002 |
| **Priority** | High |
| **Requirement** | BR-04 |
| **Preconditions** | Both servers running |
| **Technique** | Automated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Start sync via orchestrator-server | Running |
| 2 | Immediately trigger sync via kb-server (same project) | Rejected (state = RUNNING) |
| 3 | Wait for first sync to complete | Completed |
| 4 | Trigger via kb-server again | Succeeds |

---

### SIT-003: kb_search Migration — Query New Schema

| Field | Value |
|-------|-------|
| **ID** | SIT-003 |
| **Priority** | High |
| **Requirement** | BR-40, FSD §6.3 |
| **Preconditions** | Sync completed, kb_search updated to query sync.index_entries |
| **Technique** | Automated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call kb_search with query "authentication" | Returns relevant entries from sync.index_entries |
| 2 | Verify results include ticket metadata + comments | Multi-dimensional results |
| 3 | Verify role-based filtering | Developer sees masked content |

---

### SIT-004: GraphService — Read from New Schema

| Field | Value |
|-------|-------|
| **ID** | SIT-004 |
| **Priority** | High |
| **Requirement** | BR-43, FSD §6.4 |
| **Preconditions** | Sync completed, GraphService updated |
| **Technique** | Automated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | GET /sync/graph/TEST | Returns graph with nodes + edges |
| 2 | Verify nodes include user_relations data | User nodes present |
| 3 | Verify feature grouping in graph | Feature nodes connected to tickets |
| 4 | Compare with old graph output | Same ticket relationships preserved |

---

### SIT-005: End-to-End Sync Lifecycle

| Field | Value |
|-------|-------|
| **ID** | SIT-005 |
| **Priority** | High |
| **Requirement** | Full lifecycle |
| **Technique** | Automated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Full sync (10 tickets) | All dimensions indexed |
| 2 | Add 2 new tickets to mock Jira | Available for next sync |
| 3 | Incremental sync | Only 2 new tickets processed |
| 4 | Modify 1 existing ticket | Content changed |
| 5 | Incremental sync | 1 ticket updated (hash changed) |
| 6 | Verify final state | 12 ticket_metadata entries, correct comments count |

---

### SIT-006: Dimension Enable/Disable Lifecycle

| Field | Value |
|-------|-------|
| **ID** | SIT-006 |
| **Priority** | Medium |
| **Requirement** | BR-21, BR-25 |
| **Technique** | Automated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Full sync (all 5 dimensions) | All entries created |
| 2 | Disable "attachments" dimension via API | Config updated |
| 3 | Add new ticket, run incremental sync | New ticket indexed for 4 dimensions only |
| 4 | Verify old attachment entries preserved | Still in DB |
| 5 | Re-enable "attachments" | Config updated |
| 6 | Run sync | New ticket's attachments now indexed |

---

### SIT-007: Graceful Degradation — AI Provider Down

| Field | Value |
|-------|-------|
| **ID** | SIT-007 |
| **Priority** | Medium |
| **Requirement** | FSD §8.4 |
| **Technique** | Automated (stop Ollama) |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Stop Ollama service | AI unavailable |
| 2 | Run full sync | Completes without error |
| 3 | Verify ticket_metadata, comments, attachments, user_relations | All present |
| 4 | Verify feature_grouping | Epic-based only (no AI enrichment) |
| 5 | Verify vector_indexed = false for new entries | Embedding skipped |
| 6 | Start Ollama, run sync again | Vector entries now indexed |

---

### SIT-008: Graceful Degradation — Vector DB Down (Manual)

| Field | Value |
|-------|-------|
| **ID** | SIT-008 |
| **Priority** | Medium |
| **Requirement** | FSD §8.4 |
| **Technique** | Manual |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Misconfigure vector DB connection | Connection fails |
| 2 | Run sync | Completes (relational data written) |
| 3 | Verify sync.index_entries populated | All entries present |
| 4 | Verify vector_indexed = false | Vector indexing skipped |
| 5 | Fix vector config, run sync | Pending entries now vector-indexed |

---

### SIT-009: Performance — 100 Tickets Full Sync (Manual Verification)

| Field | Value |
|-------|-------|
| **ID** | SIT-009 |
| **Priority** | High |
| **Requirement** | FSD §7.4 |
| **Technique** | Manual timing |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Prepare 100-ticket mock project | Realistic data (comments, links, attachments) |
| 2 | Run full sync, time it | < 3 minutes |
| 3 | Verify memory usage | < 200MB peak |
| 4 | Verify no connection pool exhaustion | All connections returned |

---

### SIT-010: Regression — Existing jira_project_sync Behavior Preserved

| Field | Value |
|-------|-------|
| **ID** | SIT-010 |
| **Priority** | High |
| **Requirement** | Backward compatibility |
| **Technique** | Automated |

**Test Steps:**

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Call jira_project_sync with same params as before migration | Returns success |
| 2 | Call jira_sync_status | Returns valid status |
| 3 | Call jira_ticket_graph | Returns graph data |
| 4 | Verify all 3 tools still registered in MCP | tools/list includes them |

---

## 7. Requirements Traceability Matrix (RTM)

### Use Cases Coverage

| Requirement | Source | Test Cases | Coverage |
|-------------|--------|------------|----------|
| UC-01: Unified Sync Execution | FSD §2.1 | IT-001, IT-011, IT-012, IT-013, IT-014, E2E-API-001, E2E-API-002, E2E-API-003, SIT-001, SIT-005 | Covered |
| UC-02: Dimension Processing | FSD §2.2 | PBT-003, PBT-006, PBT-007, UT-004 to UT-014, IT-007, IT-008 | Covered |
| UC-03: Comment Extraction | FSD §2.3 | PBT-006, UT-006, UT-007, IT-018, E2E-API-005 | Covered |
| UC-04: User Relation Extraction | FSD §2.4 | PBT-007, UT-009, UT-010, UT-011, E2E-API-006 | Covered |
| UC-05: Feature Auto-Detection | FSD §2.5 | UT-012, UT-021, UT-022, IT-015, IT-016, E2E-API-018 | Covered |
| UC-06: Dimension Configuration API | FSD §2.6 | UT-013, UT-014, IT-017, E2E-API-007, E2E-API-008, E2E-API-009, E2E-API-017, SIT-006 | Covered |
| UC-07: Source Provenance Tracking | FSD §2.7 | PBT-004, UT-037, IT-019, E2E-API-012 | Covered |

### Business Rules Coverage

| Requirement | Test Cases | Coverage |
|-------------|------------|----------|
| BR-01: Both tools identical data | E2E-API-003, SIT-001 | Covered |
| BR-02: Idempotent (no duplicates) | PBT-001, PBT-002, IT-004 | Covered |
| BR-03: Incremental sync | IT-002, E2E-API-010, SIT-005 | Covered |
| BR-04: Sync state tracking | PBT-011, UT-017, UT-018, IT-025, SIT-002 | Covered |
| BR-05: Issue types preserved | UT-004, E2E-API-004 | Covered |
| BR-06: Query by project+type | E2E-API-004 | Covered |
| BR-07: Upsert not duplicate | PBT-002, IT-004 | Covered |
| BR-08: Each comment = 1 record | PBT-006, UT-006, UT-007 | Covered |
| BR-09: Author identity | UT-006 | Covered |
| BR-10: Query by author | E2E-API-005 | Covered |
| BR-11: Query by ticket | E2E-API-005 | Covered |
| BR-12: Comment upsert | IT-002 | Covered |
| BR-13: Bidirectional links | E2E-API-006 | Covered |
| BR-14: User relations derived | PBT-007, UT-009, UT-010, UT-011 | Covered |
| BR-15: AI-determined features | UT-021, IT-015 | Covered |
| BR-16: Bidirectional traversal | E2E-API-006 | Covered |
| BR-17: Pluggable detection | UT-021, IT-016 | Covered |
| BR-18: Confidence score | E2E-API-018 | Covered |
| BR-19: Re-evaluated each sync | SIT-005 | Covered |
| BR-20: Epic-based baseline | UT-021 | Covered |
| BR-21: Enable/disable no deploy | UT-013, IT-017, E2E-API-009, SIT-006 | Covered |
| BR-22: Add via config API | UT-014, E2E-API-008 | Covered |
| BR-23: Index strategy per dimension | UT-004, UT-006, UT-008, UT-009 | Covered |
| BR-24: Vector optional per dimension | UT-008, UT-025 | Covered |
| BR-25: Config change triggers re-index | SIT-006 | Covered |
| BR-26: Every entry has source_ref | IT-019, E2E-API-012 | Covered |
| BR-27: Granular source ref | PBT-004, UT-037 | Covered |
| BR-28: Sync timestamp | IT-019 | Covered |
| BR-29: Multiple sources | IT-015 | Covered |
| BR-30: Attachment queryable by ticket | UT-008, E2E-API-013 | Covered |
| BR-31: Attachment queryable by filename | UT-008 | Covered |
| BR-32: Download URL not exposed | UT-008, E2E-API-013 | Covered |
| BR-33: Content extraction out of scope | N/A | N/A |
| BR-34: Store original content | IT-018 | Covered |
| BR-35: Store masked version | IT-018 | Covered |
| BR-36: Use PiiMaskingEngine | IT-018 | Covered |
| BR-37: Role-based read filtering | E2E-API-014 | Covered |
| BR-38: Unmask audited | E2E-API-014 | Covered |
| BR-39: PII mappings stored | IT-018 | Covered |
| BR-40: kb_search migrated | SIT-003 | Covered |
| BR-41: Dual query during migration | SIT-003 | Covered |
| BR-42: Old table read-only | SIT-003 | Covered |
| BR-43: GraphService upgraded | SIT-004 | Covered |
| BR-44: Old tables deprecated | SIT-004 | Covered |
| BR-45: Multi-dimensional graph | SIT-004, E2E-API-006 | Covered |

### Acceptance Criteria Coverage

| Criterion | Test Cases | Coverage |
|-----------|------------|----------|
| AC-01: Identical data from both tools | E2E-API-003, SIT-001 | Covered |
| AC-02: Tickets indexed per project | E2E-API-004 | Covered |
| AC-03: Comments per-person | E2E-API-005 | Covered |
| AC-04: Multi-dimensional graph | E2E-API-006, SIT-004 | Covered |
| AC-05: Feature auto-detection | E2E-API-018, IT-015 | Covered |
| AC-06: Dimension config API | E2E-API-007, E2E-API-008 | Covered |
| AC-07: All data has source_ref | E2E-API-012, IT-019 | Covered |
| AC-08: Attachment metadata queryable | E2E-API-013 | Covered |
| AC-09: Incremental sync efficiency | IT-002, E2E-API-010 | Covered |
| AC-10: Crash recovery | IT-003 | Covered |

### Non-Functional Requirements Coverage

| NFR | Target | Test Cases | Coverage |
|-----|--------|------------|----------|
| Full sync 100 tickets | < 3 min | E2E-API-011, SIT-009 | Covered |
| Incremental sync 10 changed | < 20 sec | E2E-API-010 | Covered |
| Single ticket crawl | < 2 sec | IT-010 | Covered |
| Both tools identical | 100% | SIT-001, E2E-API-003 | Covered |
| Crash recovery | Resume from checkpoint | IT-003 | Covered |
| Batch write 100 entries | < 500ms | IT-005 | Covered |
| Memory per 100 tickets | < 200MB | IT-024, SIT-009 | Covered |
| PII role-based access | Filtered by role | E2E-API-014, IT-018 | Covered |
| Source provenance | 100% coverage | E2E-API-012, IT-019 | Covered |

### Coverage Summary

| Category | Total | Covered | Coverage % |
|----------|-------|---------|------------|
| Use Cases (UC-01 to UC-07) | 7 | 7 | 100% |
| Business Rules (BR-01 to BR-45) | 45 | 44 | 98% (BR-33 N/A) |
| Acceptance Criteria (AC-01 to AC-10) | 10 | 10 | 100% |
| Non-Functional Requirements | 9 | 9 | 100% |
| **Overall** | **71** | **70** | **99%** |

---

## 8. Test Data Files

| File | Purpose | Location |
|------|---------|----------|
| jira-10-tickets.json | Small project fixture (10 tickets, 20 comments) | src/test/resources/fixtures/ |
| jira-100-tickets.json | Medium project fixture (performance testing) | src/test/resources/fixtures/ |
| dimension-configs-seed.sql | Default dimension configurations | src/test/resources/sql/ |
| sync-schema-migration.sql | Full schema creation | src/main/resources/db/migration/ |
| ai-feature-response.json | Mock AI feature detection response | src/test/resources/fixtures/ |
| pii-comments.json | Comments with PII for masking tests | src/test/resources/fixtures/ |

---

## 9. Appendix

### Test Execution Order

```
1. PBT (property-based) — run first, fastest feedback
2. UT (unit tests) — isolated, no external deps
3. IT (integration) — requires Testcontainers
4. E2E-API — requires running server + DB
5. E2E-UI — requires browser + running server
6. SIT — requires both servers + shared DB
```

### Gradle Test Configuration

```kotlin
// Run by level
tasks.test { useJUnitPlatform { includeTags("unit") } }
tasks.register<Test>("integrationTest") { useJUnitPlatform { includeTags("integration") } }
tasks.register<Test>("e2eTest") { useJUnitPlatform { includeTags("e2e") } }
tasks.register<Test>("sitTest") { useJUnitPlatform { includeTags("sit") } }
```

### Test Tags

| Tag | Level | Requires |
|-----|-------|----------|
| `unit` | PBT + UT | Nothing (pure logic) |
| `integration` | IT | Testcontainers (Docker) |
| `e2e` | E2E-API + E2E-UI | Running server + DB |
| `sit` | SIT | Both servers + shared DB |
