# Software Test Plan (STP)

## MCPOrchestration — MTO-18: Ticket Crawler – Deep Content Sync & KB Ingestion

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-18 |
| Title | Ticket Crawler – Deep Content Sync & KB Ingestion |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-18.docx |
| Related FSD | FSD-v1-MTO-18.docx |
| Related TDD | TDD-v1-MTO-18.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STP |

---

## 1. Introduction

### 1.1 Purpose

Test plan for the TicketCrawler component — deep content fetching, SHA-256 deduplication, ticket graph building, and KB ingestion.

### 1.2 Test Objectives

- Verify deep content fetch (description + comments) works correctly
- Validate SHA-256 deduplication prevents redundant processing
- Ensure ticket graph edges are built bidirectionally
- Confirm KB ingestion with proper chunking and embedding
- Verify attachment queue population

### 1.3 References

| Document | Location |
|----------|----------|
| BRD | BRD-v1-MTO-18.docx |
| FSD | FSD-v1-MTO-18.docx |
| TDD | TDD-v1-MTO-18.docx |

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Tools | Automation |
|-------|-------|-------|------------|
| PBT | ContentHasher, ADF parser | Kotest Property | 100% |
| UT | ContentFetcher, GraphBuilder, KBIngestor, ContentHasher | Kotest + MockK | 100% |
| IT | DB operations (graph upsert, content update), Qdrant integration | Testcontainers | 100% |
| E2E-API | Full crawl lifecycle with mock Jira + real DB + real Qdrant | Testcontainers | 100% |
| E2E-UI | N/A | — | — |
| SIT | Real Jira + real Qdrant | Manual | 20% automated |

### 2.2 Test Approach

- **Deduplication focus**: Extensive testing of hash comparison logic
- **Graph correctness**: Verify bidirectional edges for all link types
- **KB quality**: Verify chunking, embedding, and retrieval accuracy
- **Testcontainers**: PostgreSQL + Qdrant containers for IT/E2E

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature | Priority | FSD Reference | Test Type |
|---|---------|----------|---------------|-----------|
| 1 | Deep Content Fetch | High | UC-01, BR-01–BR-04 | UT, IT, E2E-API |
| 2 | Content Hash Deduplication | High | UC-02, BR-05–BR-07 | PBT, UT, IT |
| 3 | Ticket Graph Builder | High | UC-03, BR-08–BR-10 | UT, IT, E2E-API |
| 4 | KB Ingestion | High | UC-04, BR-11–BR-14 | UT, IT, E2E-API |
| 5 | Attachment Queue Population | Medium | UC-05, BR-15–BR-17 | UT, IT |

---

## 4. Requirements Traceability Matrix (RTM)

| Requirement ID | Description | Test Case IDs | Coverage |
|----------------|-------------|---------------|----------|
| UC-01 | Deep Content Fetch | TC-001, TC-002, TC-003, TC-004 | ✅ |
| UC-02 | Content Hash Dedup | TC-005, TC-006, TC-007, TC-PBT-001 | ✅ |
| UC-03 | Ticket Graph Builder | TC-008, TC-009, TC-010, TC-011 | ✅ |
| UC-04 | KB Ingestion | TC-012, TC-013, TC-014, TC-015 | ✅ |
| UC-05 | Attachment Queue | TC-016, TC-017 | ✅ |
| BR-01 | Batch size configurable | TC-001 | ✅ |
| BR-02 | Max 50 comments | TC-003 | ✅ |
| BR-03 | Max 100KB content | TC-004 | ✅ |
| BR-04 | ADF → plain text | TC-002, TC-PBT-002 | ✅ |
| BR-05 | SHA-256 UTF-8 | TC-005, TC-PBT-001 | ✅ |
| BR-06 | Hash as 64-char hex | TC-005 | ✅ |
| BR-07 | NULL hash = process | TC-006 | ✅ |
| BR-08 | Bidirectional edges | TC-008, TC-009 | ✅ |
| BR-09 | Upsert ON CONFLICT | TC-010 | ✅ |
| BR-10 | Parent field as edge | TC-011 | ✅ |
| BR-11 | Title format | TC-012 | ✅ |
| BR-12 | Tags from metadata | TC-012 | ✅ |
| BR-13 | Chunk > 8000 tokens | TC-013 | ✅ |
| BR-14 | Only ingest if CHANGED | TC-014 | ✅ |
| BR-15 | Dedup by download_url | TC-016 | ✅ |
| BR-16 | Initial status pending | TC-016 | ✅ |
| BR-17 | Store attachment metadata | TC-017 | ✅ |

---

## 5. Test Summary

| Level | Test Cases | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 3 | 3 | 0 |
| UT | 12 | 12 | 0 |
| IT | 6 | 6 | 0 |
| E2E-API | 4 | 4 | 0 |
| SIT | 2 | 0 | 2 |
| **Total** | **27** | **25** | **2** |

---

## 6. Risk & Mitigation

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | Qdrant container slow to start | Medium | Pre-pull image, health check wait |
| 2 | OpenAI embedding API rate limits | High | Mock embedding service in tests |
| 3 | Large ticket content (>100KB) | Medium | Test truncation logic explicitly |
| 4 | ADF format changes | Low | Pin Jira API version in tests |
