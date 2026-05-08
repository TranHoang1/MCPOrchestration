# Software Test Plan (STP)

## MCPOrchestration — MTO-19: Attachment Processor – Background Queue Worker

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-19 |
| Title | Attachment Processor – Background Queue Worker |
| Author | QA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-19.docx |
| Related FSD | FSD-v1-MTO-19.docx |
| Related TDD | TDD-v1-MTO-19.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | QA Agent | Initial STP |

---

## 1. Introduction

### 1.1 Purpose

Test plan for the AttachmentProcessor — background queue worker that downloads Jira attachments, extracts text (PDF/DOCX/TXT), and ingests into KB.

### 1.2 Test Objectives

- Verify queue polling with configurable batch size and backoff
- Validate file download with semaphore-based concurrency
- Ensure text extraction works for PDF, DOCX, and plain text
- Confirm KB ingestion with proper chunking
- Verify error handling, retry logic, and graceful shutdown

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Scope | Tools | Automation |
|-------|-------|-------|------------|
| PBT | TextExtractor edge cases, MIME type routing | Kotest Property | 100% |
| UT | QueueManager, Downloader, TextExtractor, KBIngestor | Kotest + MockK | 100% |
| IT | Queue DB operations, download + extract pipeline | Testcontainers (PostgreSQL + Qdrant) | 100% |
| E2E-API | Full processor lifecycle with mock Jira | Testcontainers | 100% |
| E2E-UI | N/A | — | — |
| SIT | Real Jira attachments, real Qdrant | Manual | 20% |

---

## 3. Test Scope

### 3.1 Features In Scope

| # | Feature | Priority | FSD Reference |
|---|---------|----------|---------------|
| 1 | Background Queue Processing | High | UC-01, BR-01–BR-05 |
| 2 | File Download | High | UC-02, BR-06–BR-10 |
| 3 | Text Extraction (PDF/DOCX/TXT) | High | UC-03, BR-11–BR-16 |
| 4 | KB Ingestion | High | UC-04, BR-17–BR-20 |
| 5 | Graceful Shutdown | Medium | UC-05, BR-21–BR-23 |

---

## 4. Requirements Traceability Matrix (RTM)

| Requirement ID | Description | Test Case IDs | Coverage |
|----------------|-------------|---------------|----------|
| UC-01 | Queue Processing | TC-001, TC-002, TC-003, TC-004 | ✅ |
| UC-02 | File Download | TC-005, TC-006, TC-007, TC-008 | ✅ |
| UC-03 | Text Extraction | TC-009, TC-010, TC-011, TC-012, TC-013 | ✅ |
| UC-04 | KB Ingestion | TC-014, TC-015, TC-016 | ✅ |
| UC-05 | Graceful Shutdown | TC-017, TC-018 | ✅ |
| BR-01 | Batch size configurable | TC-001 | ✅ |
| BR-02 | Poll interval 30s | TC-002 | ✅ |
| BR-03 | Exponential backoff | TC-003 | ✅ |
| BR-04 | Reset backoff on items found | TC-004 | ✅ |
| BR-05 | Mark processing before work | TC-001 | ✅ |
| BR-06 | Max 3 concurrent downloads | TC-005 | ✅ |
| BR-07 | Download timeout 60s | TC-006 | ✅ |
| BR-08 | Max file size 50MB | TC-007 | ✅ |
| BR-09 | Retry 3x with backoff | TC-008 | ✅ |
| BR-10 | No retry on 404 | TC-008 | ✅ |
| BR-11 | PDF all pages | TC-009 | ✅ |
| BR-12 | PDF encrypted → failed | TC-010 | ✅ |
| BR-13 | DOCX paragraphs + tables | TC-011 | ✅ |
| BR-14 | Text UTF-8 fallback | TC-012 | ✅ |
| BR-15 | Empty → skipped | TC-013 | ✅ |
| BR-16 | Max 100KB extracted | TC-013 | ✅ |
| BR-17 | Title format | TC-014 | ✅ |
| BR-18 | Tags | TC-014 | ✅ |
| BR-19 | Metadata | TC-014 | ✅ |
| BR-20 | Chunk >8000 tokens | TC-015 | ✅ |
| BR-21 | Shutdown timeout 60s | TC-017 | ✅ |
| BR-22 | Force stop after timeout | TC-017 | ✅ |
| BR-23 | Reset processing → pending | TC-018 | ✅ |

---

## 5. Test Summary

| Level | Test Cases | Automated | Manual |
|-------|-----------|-----------|--------|
| PBT | 3 | 3 | 0 |
| UT | 13 | 13 | 0 |
| IT | 5 | 5 | 0 |
| E2E-API | 4 | 4 | 0 |
| SIT | 2 | 0 | 2 |
| **Total** | **27** | **25** | **2** |

---

## 6. Risk & Mitigation

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| 1 | PDFBox memory usage for large PDFs | High | Test with 50MB PDF, monitor heap |
| 2 | Qdrant container startup time | Medium | Pre-pull image, health check |
| 3 | Encrypted PDF detection | Medium | Test with known encrypted samples |
| 4 | MIME type detection accuracy | Low | Use file extension as fallback |
