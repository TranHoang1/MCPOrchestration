# Functional Specification Document (FSD)

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
| Related BRD | BRD-v1-MTO-29.docx |

---

## 1. Use Cases

### UC-01: Extract Text from Image

| Field | Value |
|-------|-------|
| ID | UC-01 |
| Name | Extract Text from Image |
| Actor | KB Refinery Pipeline (automated) |
| Precondition | Image file URI available, MarkItDown MCP server connected |
| Postcondition | Extracted markdown text returned |

**Main Flow:**
1. Pipeline calls `OcrService.extractText(fileUri)`
2. Service validates file URI and MIME type
3. Service calls MarkItDown MCP tool via ToolExecutionDispatcher
4. MarkItDown processes image and returns markdown
5. Service returns extracted text to caller

**Alternative Flow — Unsupported Format:**
1. At step 2, MIME type is not PNG/JPG/TIFF
2. Service throws `UnsupportedImageFormatException`

**Exception Flow — MCP Tool Unavailable:**
1. At step 3, ToolExecutionDispatcher cannot reach MarkItDown
2. Service logs warning and returns empty string (graceful degradation)

### UC-02: Integrate with AttachmentProcessor

| Field | Value |
|-------|-------|
| ID | UC-02 |
| Name | Process Image Attachment in Pipeline |
| Actor | AttachmentProcessor (automated) |
| Precondition | Image attachment in processing queue |
| Postcondition | Image text extracted and indexed |

**Main Flow:**
1. AttachmentProcessor dequeues attachment with image MIME type
2. TextExtractor selects ImageTextExtractor strategy
3. ImageTextExtractor delegates to OcrService
4. Extracted text is embedded and stored in vector DB

**Alternative Flow — Empty Image:**
1. At step 3, OcrService returns empty string
2. AttachmentProcessor skips indexing for this attachment
3. Logs info: "No text extracted from image"

## 2. Business Rules

| ID | Rule | Source |
|----|------|--------|
| BR-01 | Supported image formats: PNG, JPG/JPEG, TIFF/TIF | BRD BR-01 |
| BR-02 | Max image file size: 20MB | NFR Performance |
| BR-03 | Timeout per OCR request: 30 seconds | NFR Performance |
| BR-04 | On MCP failure: return empty string, do not throw | BRD BR-02 |
| BR-05 | MIME type detection by file extension and content-type header | Implementation |

## 3. Data Specifications

### 3.1 OcrService Interface

```kotlin
interface OcrService {
    suspend fun extractText(fileUri: String): String
}
```

### 3.2 ImageTextExtractor (ContentExtractor implementation)

```kotlin
class ImageTextExtractor(
    private val ocrService: OcrService
) : ContentExtractor {
    override suspend fun extract(content: ByteArray, metadata: Map<String, String>): String
}
```

### 3.3 MCP Tool Call Schema

**Tool:** `convert_to_markdown`
**Server:** `markitdown` (configured in upstream_servers)

**Request:**
```json
{
  "uri": "file:///path/to/image.png"
}
```

**Response:**
```json
{
  "content": [
    { "type": "text", "text": "# Extracted Content\n\nText from image..." }
  ]
}
```

### 3.4 Configuration

```yaml
orchestrator:
  ocr:
    enabled: true
    server-name: "markitdown"
    tool-name: "convert_to_markdown"
    timeout-seconds: 30
    max-file-size-mb: 20
    supported-formats:
      - "image/png"
      - "image/jpeg"
      - "image/tiff"
```

## 4. API Contracts

### 4.1 Internal API: OcrService

| Method | Input | Output | Errors |
|--------|-------|--------|--------|
| `extractText(fileUri: String)` | File URI (file:// or http://) | Markdown string | UnsupportedImageFormatException, OcrTimeoutException |

### 4.2 MCP Tool Invocation

| Parameter | Value |
|-----------|-------|
| Server | Configured via `ocr.server-name` |
| Tool | Configured via `ocr.tool-name` |
| Arguments | `{"uri": "{fileUri}"}` |
| Timeout | `ocr.timeout-seconds` |

## 5. Integration Requirements

### 5.1 ToolExecutionDispatcher Integration

OcrService uses the existing `ToolExecutionDispatcher` to call the MarkItDown MCP tool. No new transport or connection logic needed.

### 5.2 AttachmentProcessor Integration

Register `ImageTextExtractor` in the `TextExtractor` MIME type map for image types:
- `image/png` → ImageTextExtractor
- `image/jpeg` → ImageTextExtractor
- `image/tiff` → ImageTextExtractor

### 5.3 File Proxy Integration

If the image is a local file, use FileProxy to generate a URI accessible by the MCP tool. If already a URI, pass directly.

## 6. Error Handling

| Error | Code | Recovery |
|-------|------|----------|
| MCP server unavailable | OCR_SERVER_UNAVAILABLE | Return empty string, log warning |
| Tool execution timeout | OCR_TIMEOUT | Return empty string, log warning |
| Unsupported format | OCR_UNSUPPORTED_FORMAT | Throw exception (caller decides) |
| File not found | OCR_FILE_NOT_FOUND | Return empty string, log error |
| Response parse error | OCR_PARSE_ERROR | Return empty string, log error |

## 7. Non-Functional Requirements

| # | Category | Metric | Target |
|---|----------|--------|--------|
| 1 | Latency | p95 OCR time | < 30s |
| 2 | Throughput | Concurrent requests | 5 parallel max |
| 3 | Availability | Graceful degradation | 100% (never crash pipeline) |
| 4 | Code Quality | File size | ≤ 200 lines per file |
| 5 | Code Quality | Function size | ≤ 20 lines per function |
