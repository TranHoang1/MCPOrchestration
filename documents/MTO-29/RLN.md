# Release Notes

## MTO-29: KB Refinery — MarkItDown MCP Integration for OCR

| Field | Value |
|-------|-------|
| **Version** | 1.1.0 |
| **Date** | 2026-05-08 |
| **Type** | Feature |
| **Priority** | High |
| **Epic** | MTO-24 (Knowledge Base Refinery) |

---

## Summary

Added OCR (Optical Character Recognition) capability to the KB Refinery pipeline using MarkItDown MCP server integration. The system can now extract text from image attachments (PNG, JPG, TIFF) in Jira tickets, enabling full-text search and knowledge base indexing of image content.

---

## New Features

### Image Text Extraction via MarkItDown MCP

- **What:** Extract text from image files using MarkItDown's `convert_to_markdown` tool
- **Formats:** PNG, JPEG, TIFF
- **Integration:** Plugs into existing `AttachmentProcessor` → `TextExtractor` pipeline
- **Output:** Markdown-formatted text ready for PII masking and segmentation

### Graceful Degradation

- If MarkItDown MCP server is unavailable, OCR returns empty string (no crash)
- Configurable timeout (default 30s) prevents hanging on large images
- Feature can be disabled via `orchestrator.ocr.enabled = false`

### Koin DI Integration

- New `ocrModule` provides all OCR components via dependency injection
- Clean separation: `OcrService` interface + `OcrServiceImpl` implementation
- `ImageTextExtractor` implements existing `ContentExtractor` strategy pattern

---

## Configuration

New section in `application.yml`:

```yaml
orchestrator:
  ocr:
    enabled: true
    server-name: markitdown
    tool-name: convert_to_markdown
    timeout-seconds: 30
    max-file-size-mb: 20
    supported-formats:
      - image/png
      - image/jpeg
      - image/tiff
```

---

## Files Added

| File | Purpose |
|------|---------|
| `ocr/OcrService.kt` | Public interface |
| `ocr/OcrServiceImpl.kt` | Implementation via MCP tool call |
| `ocr/model/OcrConfig.kt` | Configuration data class |
| `ocr/model/OcrException.kt` | Sealed exception hierarchy |
| `ocr/extractor/ImageTextExtractor.kt` | ContentExtractor for images |
| `ocr/di/OcrModule.kt` | Koin DI module |

---

## Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| MarkItDown MCP Server | Latest | External — requires Python 3.10+ |
| markitdown[all] | Latest | pip package with OCR extras |

No new Kotlin/JVM dependencies added — uses existing `ToolExecutionDispatcher` infrastructure.

---

## Testing

| Level | Count | Pass Rate |
|-------|-------|-----------|
| Property-Based | 2 | 100% |
| Unit | 14 | 100% |
| Integration | 6 | 100% |
| E2E-API | 3 | 100% |
| **Total** | **27** | **100%** |

---

## Breaking Changes

None. This is a purely additive feature.

---

## Known Limitations

1. OCR accuracy depends on MarkItDown server quality (not controlled by this module)
2. No image pre-processing (rotation, deskew) — images must be reasonably oriented
3. Max file size enforced at config level only (no streaming for large files)
4. `runBlocking` used in `ImageTextExtractor.extract()` due to non-suspend `ContentExtractor` interface

---

## Migration Guide

No migration needed. To enable OCR:

1. Install MarkItDown MCP server (`pip install markitdown[all]`)
2. Add `markitdown` entry to `mcp-servers.json`
3. Add `orchestrator.ocr` section to `application.yml`
4. Deploy new JAR

To disable: set `orchestrator.ocr.enabled: false` in config.
