# Software Test Cases (STC)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-18.docx |

---

## 1. PBT — Property-Based Tests

### TC-PBT-001: SHA-256 hash is deterministic and 64 chars hex

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-05, BR-06 |
| Property | For any UTF-8 string, computeHash() returns same 64-char hex string on repeated calls |
| Generator | Arb.string(0..10000) |
| Iterations | 1000 |

### TC-PBT-002: ADF parser never throws for valid ADF JSON

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-04 |
| Property | For any valid ADF document structure, parser returns non-null plain text |
| Generator | Custom Arb generating ADF nodes (paragraph, heading, text, codeBlock) |
| Iterations | 500 |

### TC-PBT-003: Graph edge mapping is symmetric

| Attribute | Value |
|-----------|-------|
| Level | PBT |
| Requirement | BR-08 |
| Property | For any (source, target, linkType), buildEdges produces exactly 2 edges with correct forward/reverse types |
| Generator | Arb.pair(issueKeyArb, issueKeyArb) × Arb.enum<JiraLinkType>() |
| Iterations | 500 |

---

## 2. UT — Unit Tests

### TC-001: ContentFetcher — batch size configurable

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-01, BR-01 |
| Input | batchSize = 5, 20 uncrawled tickets in DB |
| Expected | First batch returns exactly 5 tickets |

### TC-002: ContentFetcher — ADF description converted to plain text

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-04 |
| Input | ADF JSON with paragraphs, headings, code blocks, bullet lists |
| Expected | Plain text output with headings as "# heading", bullets as "- item" |

### TC-003: ContentFetcher — max 50 comments (latest first)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-02 |
| Input | Ticket with 75 comments |
| Expected | Only 50 most recent comments included in content |

### TC-004: ContentFetcher — content truncated at 100KB

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-03 |
| Input | Ticket with 150KB description |
| Expected | Content truncated to 100KB, warning logged |

### TC-005: ContentHasher — SHA-256 produces 64-char hex

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-05, BR-06 |
| Input | "Hello World" |
| Expected | Known SHA-256 hex value, length = 64 |

### TC-006: ContentHasher — NULL hash means always process

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-07 |
| Input | Ticket with contentHash = null |
| Expected | isChanged() returns true regardless of content |

### TC-007: ContentHasher — same content returns UNCHANGED

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | UC-02 |
| Input | Content "abc", existing hash = SHA-256("abc") |
| Expected | isChanged() returns false |

### TC-008: GraphBuilder — bidirectional edges for "blocks" link

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-08 |
| Input | MTO-1 blocks MTO-2 |
| Expected | Two edges: (MTO-1, MTO-2, "blocks") + (MTO-2, MTO-1, "is-blocked-by") |

### TC-009: GraphBuilder — all link type mappings correct

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-08 |
| Input | Each Jira link type from mapping table |
| Expected | Correct forward and reverse edge types per FSD §3.3 |

### TC-010: GraphBuilder — duplicate edges ignored (ON CONFLICT)

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-09 |
| Input | Same edge inserted twice |
| Expected | No error, no duplicate rows |

### TC-011: GraphBuilder — parent field creates parent/child edges

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-10 |
| Input | Ticket MTO-5 with parent = MTO-1 |
| Expected | Edges: (MTO-1, MTO-5, "parent") + (MTO-5, MTO-1, "child") |

### TC-012: KBIngestor — title and tags formatted correctly

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-11, BR-12 |
| Input | issueKey="MTO-18", summary="Ticket Crawler", projectKey="MTO", issueType="Story", labels=["backend"] |
| Expected | Title: "MTO-18: Ticket Crawler", Tags: ["MTO", "Story", "In Progress", "backend"] |

### TC-013: KBIngestor — content > 8000 tokens chunked with overlap

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-13 |
| Input | Content with 20000 tokens |
| Expected | Multiple chunks created, each ≤ 8000 tokens, with overlap between chunks |

### TC-014: KBIngestor — skip ingestion if hash UNCHANGED

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-14 |
| Input | Content with same hash as stored |
| Expected | EmbeddingService NOT called, VectorDbClient NOT called |

### TC-015: KBIngestor — re-ingest if hash CHANGED

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-14 |
| Input | Content with different hash |
| Expected | Old vectors deleted, new embedding generated, new vectors stored |

### TC-016: AttachmentQueuer — new attachment queued with pending status

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-15, BR-16 |
| Input | Attachment: {url: "https://jira/file.pdf", filename: "file.pdf", size: 1024} |
| Expected | Row inserted: status='pending', download_url unique |

### TC-017: AttachmentQueuer — duplicate URL not re-queued

| Attribute | Value |
|-----------|-------|
| Level | UT |
| Requirement | BR-15 |
| Input | Same download_url already in queue |
| Expected | No error, no duplicate row |

---

## 3. IT — Integration Tests

### TC-IT-001: Full crawl cycle with real PostgreSQL

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-01, UC-02 |
| Precondition | Testcontainers PostgreSQL, 5 tickets in cache with NULL content_hash |
| Steps | 1. Mock Jira API responses 2. Call crawl("MTO") 3. Verify content_text and content_hash populated |
| Expected | 5 tickets have content_text, content_hash set, crawled_at updated |

### TC-IT-002: Graph edges persisted in jira_ticket_graph

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-03, BR-08 |
| Precondition | Testcontainers PostgreSQL |
| Steps | 1. Crawl ticket with 3 links 2. Query jira_ticket_graph |
| Expected | 6 edges (3 forward + 3 reverse) in table |

### TC-IT-003: KB ingestion stores vectors in Qdrant

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-04 |
| Precondition | Testcontainers Qdrant + mock EmbeddingService |
| Steps | 1. Ingest ticket content 2. Search Qdrant by embedding |
| Expected | Vector found with correct payload (title, tags) |

### TC-IT-004: Deduplication — second crawl skips unchanged tickets

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-02, BR-14 |
| Precondition | First crawl completed (hashes set) |
| Steps | 1. Call crawl again with same content 2. Check KB ingest count |
| Expected | 0 tickets re-ingested, all skipped |

### TC-IT-005: Attachment queue populated correctly

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | UC-05, BR-15–BR-17 |
| Precondition | Ticket with 3 attachments |
| Steps | 1. Crawl ticket 2. Query jira_attachment_queue |
| Expected | 3 rows with status='pending', correct metadata |

### TC-IT-006: Content update triggers re-ingestion

| Attribute | Value |
|-----------|-------|
| Level | IT |
| Requirement | BR-14 |
| Precondition | First crawl done, then modify mock response (different description) |
| Steps | 1. Second crawl 2. Verify new hash stored 3. Verify KB updated |
| Expected | New hash, new vectors in Qdrant, old vectors removed |

---

## 4. E2E-API — End-to-End Tests

### TC-E2E-001: Full crawl lifecycle — fetch, hash, graph, ingest

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | UC-01 through UC-05 |
| Precondition | Full app context, Testcontainers (PostgreSQL + Qdrant), WireMock Jira |
| Steps | 1. Seed 10 tickets in cache 2. Call crawl("MTO") 3. Verify all outputs |
| Expected | 10 tickets crawled, content stored, graph built, KB ingested, attachments queued |

### TC-E2E-002: Incremental crawl — only processes changed tickets

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | UC-02 |
| Precondition | Previous crawl done, 3 of 10 tickets have new content in Jira |
| Steps | 1. Call crawl("MTO") 2. Check processed count |
| Expected | CrawlResult(processed=3, skipped=7, ingested=3) |

### TC-E2E-003: Large project — 500 tickets crawled in batches

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | NFR throughput |
| Precondition | 500 tickets in cache, batchSize=10 |
| Expected | All 500 processed, throughput ≥ 50 tickets/min |

### TC-E2E-004: Error resilience — Jira API failure for one ticket doesn't stop batch

| Attribute | Value |
|-----------|-------|
| Level | E2E-API |
| Requirement | Error handling |
| Precondition | WireMock: ticket MTO-5 returns 500, others return 200 |
| Expected | 9 of 10 tickets processed, MTO-5 logged as error, crawl continues |

---

## 5. SIT — System Integration Tests (Manual)

### TC-SIT-001: Real Jira project crawl with KB search verification

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Crawl real Jira project 2. Search KB for known ticket content 3. Verify search returns correct results |
| Expected | KB search finds ticket content with correct metadata |

### TC-SIT-002: Graph visualization data matches Jira relationships

| Attribute | Value |
|-----------|-------|
| Level | SIT |
| Steps | 1. Crawl project with known link structure 2. Query graph table 3. Compare with Jira UI |
| Expected | All relationships in Jira reflected in graph table |
