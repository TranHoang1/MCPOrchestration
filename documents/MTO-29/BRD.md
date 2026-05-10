# Business Requirements Document (BRD)

## MCPOrchestration — MTO-29: MarkItDown MCP Integration for OCR

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-29 |
| Title | KB Refinery — MarkItDown MCP Integration for OCR |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Approved |

---

## 1. Executive Summary

Integrate the MarkItDown MCP tool (`convert_to_markdown`) for image/attachment OCR within the KB Refinery pipeline. This replaces the need for PaddleOCR/Tesseract by leveraging an existing MCP tool already available in the orchestrator ecosystem.

## 2. Business Objectives

| # | Objective | Success Metric |
|---|-----------|---------------|
| 1 | Extract text from image attachments in Jira tickets | ≥90% accuracy on printed text |
| 2 | Integrate seamlessly with existing AttachmentProcessor | Zero changes to upstream pipeline |
| 3 | Support common image formats | PNG, JPG, TIFF supported |
| 4 | Reduce infrastructure complexity | No new external services needed |

## 3. Stakeholders

| Role | Responsibility |
|------|---------------|
| Product Owner | Approve requirements |
| Solution Architect | Technical design |
| Developer | Implementation |
| QA | Testing |

## 4. Business Requirements

### BR-01: Image Text Extraction

The system SHALL extract text content from image files (PNG, JPG, TIFF) using the MarkItDown MCP tool's `convert_to_markdown` capability.

**Acceptance Criteria:**
- AC-01.1: Given a PNG image with printed text, when OCR is invoked, then extracted text matches ≥90% of source content
- AC-01.2: Given a JPG image with mixed text/graphics, when OCR is invoked, then text portions are extracted correctly
- AC-01.3: Given a TIFF document scan, when OCR is invoked, then full page text is extracted

### BR-02: MCP Tool Integration

The system SHALL call the MarkItDown MCP tool via the existing MCP client infrastructure (ToolExecutionDispatcher).

**Acceptance Criteria:**
- AC-02.1: Given a valid image URI, when OcrService.extractText() is called, then it routes through ToolExecutionDispatcher to MarkItDown
- AC-02.2: Given MarkItDown server is unavailable, when OCR is attempted, then a graceful error is returned (not crash)

### BR-03: AttachmentProcessor Integration

The system SHALL integrate with the existing AttachmentProcessor as a new extraction strategy for image MIME types.

**Acceptance Criteria:**
- AC-03.1: Given an image attachment in the queue, when AttachmentProcessor processes it, then ImageTextExtractor is selected
- AC-03.2: Given a non-image attachment, when processed, then existing extractors (PDF, DOCX) are used unchanged

### BR-04: Output Format

The system SHALL return extracted text as plain markdown string, consistent with other TextExtractor outputs.

**Acceptance Criteria:**
- AC-04.1: Output is a String containing markdown-formatted text
- AC-04.2: Empty images return empty string (not null, not exception)

## 5. User Stories

### STORY-01: As a KB Refinery pipeline, I want to extract text from image attachments so that image content is searchable in the knowledge base.

### STORY-02: As a system administrator, I want OCR to use existing MCP infrastructure so that no additional services need deployment.

### STORY-03: As a developer, I want a clean OcrService interface so that OCR implementation can be swapped without affecting callers.

## 6. Dependencies

| # | Dependency | Type | Status |
|---|-----------|------|--------|
| 1 | MarkItDown MCP server configured in upstream_servers | Runtime | Available |
| 2 | ToolExecutionDispatcher (existing) | Code | Available |
| 3 | AttachmentProcessor (MTO-19) | Code | Available |
| 4 | File Proxy for file URI handling | Code | Available |

## 7. Non-Functional Requirements

| # | Category | Requirement | Target |
|---|----------|-------------|--------|
| 1 | Performance | OCR processing time | < 30s per image |
| 2 | Reliability | Graceful degradation on MCP tool failure | No pipeline crash |
| 3 | Scalability | Concurrent OCR requests | Up to 5 parallel |
| 4 | Maintainability | Interface-first design | OcrService interface |

## 8. Out of Scope

- Handwriting recognition
- Multi-language OCR configuration
- Image pre-processing (rotation, deskew)
- Training custom OCR models

## 9. Risks

| # | Risk | Impact | Mitigation |
|---|------|--------|-----------|
| 1 | MarkItDown quality varies by image type | Medium | Fallback to empty string, log warning |
| 2 | Large images cause timeout | Medium | Configurable timeout, max file size check |
| 3 | MCP server connection issues | Low | Retry with exponential backoff |
