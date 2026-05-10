# Software Test Cases (STC)

## MTO-29: KB Refinery — MarkItDown MCP Integration for OCR

| Field | Value |
|-------|-------|
| **Ticket** | MTO-29 |
| **Version** | 1.0 |
| **Author** | QA Agent |
| **Created** | 2026-05-10 |
| **Related STP** | STP-v1-MTO-29 |

---

## 1. Property-Based Tests (PBT)

### PBT-01: URI Validation — Non-Blank URIs Accepted

| Field | Value |
|-------|-------|
| **ID** | PBT-01 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-02.1 |
| **Iterations** | 100 |

**Property:** For any non-blank string URI, validateUri does not throw FileNotFoundException.

**Generators:** Arb.string(1..200).filter { it.isNotBlank() }

---

### PBT-02: OcrConfig Serialization Roundtrip

| Field | Value |
|-------|-------|
| **ID** | PBT-02 |
| **Component** | OcrConfig |
| **Requirement** | Configuration integrity |
| **Iterations** | 100 |

**Property:** For any valid OcrConfig, `json.decodeFromString(json.encodeToString(config)) == config`

**Generators:**
- enabled: Arb.boolean()
- serverName: Arb.string(1..50)
- toolName: Arb.string(1..50)
- timeoutSeconds: Arb.int(1..120)
- maxFileSizeMb: Arb.int(1..100)

---

## 2. Unit Tests (UT)

### UT-01: extractText Routes Through Dispatcher

| Field | Value |
|-------|-------|
| **ID** | UT-01 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-02.1 |
| **Priority** | High |

**Steps:**
1. Mock ToolExecutionDispatcher to return text response
2. Create OcrServiceImpl with mocked dispatcher
3. Call extractText("file:///tmp/image.png")
4. Verify dispatcher.execute called with "markitdown/convert_to_markdown"
5. Verify arguments contain {"uri": "file:///tmp/image.png"}

**Expected:** Dispatcher called with correct tool name and arguments

---

### UT-02: Server Unavailable Returns Empty String

| Field | Value |
|-------|-------|
| **ID** | UT-02 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-02.2, NFR-2 |
| **Priority** | High |

**Steps:**
1. Mock dispatcher to throw RuntimeException("Connection refused")
2. Call extractText("file:///tmp/image.png")
3. Verify result == ""
4. Verify no exception propagated

**Expected:** Empty string returned, no crash

---

### UT-03: ImageTextExtractor Delegates to OcrService

| Field | Value |
|-------|-------|
| **ID** | UT-03 |
| **Component** | ImageTextExtractor |
| **Requirement** | AC-03.1 |
| **Priority** | High |

**Steps:**
1. Mock OcrService.extractText to return "extracted text"
2. Mock FileUriResolver to return "file:///tmp/test.png"
3. Create ImageTextExtractor with mocked dependencies
4. Call extract(byteArrayOf(1,2,3))
5. Verify ocrService.extractText called with resolved URI

**Expected:** Delegation works correctly

---

### UT-04: ImageTextExtractor Returns Empty When No URI

| Field | Value |
|-------|-------|
| **ID** | UT-04 |
| **Component** | ImageTextExtractor |
| **Requirement** | AC-03.2 |
| **Priority** | Medium |

**Steps:**
1. Create ImageTextExtractor with fileUriResolver = null
2. Call extract(byteArrayOf(1,2,3))
3. Verify result == ""

**Expected:** Empty string when no URI resolver

---

### UT-05: Response Text Extracted as Markdown

| Field | Value |
|-------|-------|
| **ID** | UT-05 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-04.1 |
| **Priority** | High |

**Steps:**
1. Mock dispatcher to return response with text content: "# Header\nParagraph text"
2. Call extractText
3. Verify result == "# Header\nParagraph text"

**Expected:** Markdown text extracted from response

---

### UT-06: Empty Response Returns Empty String

| Field | Value |
|-------|-------|
| **ID** | UT-06 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-04.2 |
| **Priority** | High |

**Steps:**
1. Mock dispatcher to return response with empty content list
2. Call extractText
3. Verify result == ""

**Expected:** Empty string for empty response

---

### UT-07: MCP Tool Call Format Correct

| Field | Value |
|-------|-------|
| **ID** | UT-07 |
| **Component** | OcrServiceImpl |
| **Requirement** | BR-02 |
| **Priority** | High |

**Steps:**
1. Capture dispatcher.execute arguments
2. Call extractText("file:///path/to/image.png")
3. Verify toolName == "markitdown/convert_to_markdown"
4. Verify arguments is JsonObject with "uri" key

**Expected:** Correct MCP tool call format

---

### UT-08: Timeout Configuration Respected

| Field | Value |
|-------|-------|
| **ID** | UT-08 |
| **Component** | OcrServiceImpl |
| **Requirement** | NFR-1 |
| **Priority** | High |

**Steps:**
1. Create config with timeoutSeconds = 1
2. Mock dispatcher with delay(2000) before response
3. Call extractText
4. Verify result == "" (timeout, graceful)

**Expected:** Timeout fires, returns empty string

---

### UT-09: Exception in Extractor Returns Empty

| Field | Value |
|-------|-------|
| **ID** | UT-09 |
| **Component** | ImageTextExtractor |
| **Requirement** | NFR-2 |
| **Priority** | Medium |

**Steps:**
1. Mock OcrService to throw RuntimeException
2. Call ImageTextExtractor.extract(bytes)
3. Verify result == ""
4. Verify no exception propagated

**Expected:** Graceful degradation in extractor

---

### UT-10: Blank URI Throws FileNotFoundException

| Field | Value |
|-------|-------|
| **ID** | UT-10 |
| **Component** | OcrServiceImpl |
| **Requirement** | Error handling |
| **Priority** | High |

**Steps:**
1. Call extractText("")
2. Verify OcrException.FileNotFoundException thrown

**Expected:** FileNotFoundException for blank URI

---

### UT-11: Disabled Config Returns Empty Immediately

| Field | Value |
|-------|-------|
| **ID** | UT-11 |
| **Component** | OcrServiceImpl |
| **Requirement** | Feature flag |
| **Priority** | Medium |

**Steps:**
1. Create config with enabled = false
2. Call extractText("file:///any.png")
3. Verify dispatcher was NOT called
4. Verify result == ""

**Expected:** Short-circuit when disabled

---

### UT-12: Multiple Text Content Joined

| Field | Value |
|-------|-------|
| **ID** | UT-12 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-04.1 |
| **Priority** | Medium |

**Steps:**
1. Mock dispatcher to return response with 2 text content items: "Line 1" and "Line 2"
2. Call extractText
3. Verify result == "Line 1\nLine 2"

**Expected:** Multiple text items joined with newline

---

### UT-13: Non-Text Content Filtered Out

| Field | Value |
|-------|-------|
| **ID** | UT-13 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-04.1 |
| **Priority** | Medium |

**Steps:**
1. Mock dispatcher to return response with mixed content (text + image types)
2. Call extractText
3. Verify only text-type content included in result

**Expected:** Non-text content filtered

---

### UT-14: OcrConfig Default Values

| Field | Value |
|-------|-------|
| **ID** | UT-14 |
| **Component** | OcrConfig |
| **Requirement** | Configuration |
| **Priority** | Low |

**Steps:**
1. Create OcrConfig() with no arguments
2. Verify enabled == true
3. Verify serverName == "markitdown"
4. Verify toolName == "convert_to_markdown"
5. Verify timeoutSeconds == 30
6. Verify supportedFormats contains "image/png", "image/jpeg", "image/tiff"

**Expected:** All defaults correct

---

## 3. Integration Tests (IT)

### IT-01: PNG Image OCR End-to-End

| Field | Value |
|-------|-------|
| **ID** | IT-01 |
| **Component** | OcrServiceImpl + Dispatcher (mocked) |
| **Requirement** | AC-01.1, AC-02.1, AC-04.1 |
| **Priority** | High |

**Steps:**
1. Set up Koin with OcrModule, mock ToolExecutionDispatcher
2. Mock dispatcher to return markdown text for PNG URI
3. Resolve OcrService from Koin
4. Call extractText("file:///tmp/document.png")
5. Verify non-empty markdown returned
6. Verify dispatcher called with correct arguments

**Expected:** Full flow works with DI

---

### IT-02: JPG Image OCR

| Field | Value |
|-------|-------|
| **ID** | IT-02 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-01.2 |
| **Priority** | High |

**Steps:**
1. Mock dispatcher for JPG URI
2. Call extractText("file:///tmp/photo.jpg")
3. Verify text extracted

**Expected:** JPG format handled

---

### IT-03: TIFF Document OCR

| Field | Value |
|-------|-------|
| **ID** | IT-03 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-01.3 |
| **Priority** | Medium |

**Steps:**
1. Mock dispatcher for TIFF URI
2. Call extractText("file:///tmp/scan.tiff")
3. Verify text extracted

**Expected:** TIFF format handled

---

### IT-04: MCP Server Unavailable — Graceful

| Field | Value |
|-------|-------|
| **ID** | IT-04 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-02.2, NFR-2 |
| **Priority** | High |

**Steps:**
1. Mock dispatcher to throw connection error
2. Call extractText
3. Verify empty string returned
4. Verify no exception propagated to caller
5. Verify warning logged

**Expected:** Graceful degradation

---

### IT-05: Empty Image Response

| Field | Value |
|-------|-------|
| **ID** | IT-05 |
| **Component** | OcrServiceImpl |
| **Requirement** | AC-04.2 |
| **Priority** | Medium |

**Steps:**
1. Mock dispatcher to return empty content
2. Call extractText
3. Verify result == ""

**Expected:** Empty string for empty image

---

### IT-06: Timeout Integration

| Field | Value |
|-------|-------|
| **ID** | IT-06 |
| **Component** | OcrServiceImpl with coroutines |
| **Requirement** | NFR-1 |
| **Priority** | High |

**Steps:**
1. Config timeoutSeconds = 1
2. Mock dispatcher with delay(3000)
3. Call extractText within runTest
4. Verify returns "" within timeout
5. Verify no exception

**Expected:** Timeout handled gracefully

---

## 4. E2E-API Tests

### E2E-01: Full DI Container OCR

| Field | Value |
|-------|-------|
| **ID** | E2E-01 |
| **Component** | OcrModule + all components |
| **Requirement** | AC-01.1 |
| **Priority** | High |

**Steps:**
1. Start Koin with ocrModule (dispatcher mocked)
2. Resolve OcrService
3. Call extractText with valid URI
4. Verify result returned
5. Stop Koin

**Expected:** Full DI wiring works

---

### E2E-02: ImageTextExtractor via DI

| Field | Value |
|-------|-------|
| **ID** | E2E-02 |
| **Component** | ImageTextExtractor + OcrService |
| **Requirement** | AC-03.1 |
| **Priority** | Medium |

**Steps:**
1. Start Koin with ocrModule
2. Resolve ImageTextExtractor
3. Verify it has OcrService injected
4. Stop Koin

**Expected:** Extractor properly wired

---

### E2E-03: Module Wiring Verification

| Field | Value |
|-------|-------|
| **ID** | E2E-03 |
| **Component** | OcrModule |
| **Requirement** | NFR-4 |
| **Priority** | Medium |

**Steps:**
1. Start Koin with ocrModule
2. Verify get<OcrConfig>() resolves
3. Verify get<OcrService>() resolves
4. Verify get<ImageTextExtractor>() resolves
5. Stop Koin

**Expected:** All DI bindings resolve

---

## 5. Test Data

### 5.1 testdata/ocr-responses/valid-text.json

```json
{
  "content": [
    { "type": "text", "text": "# Invoice\n\nDate: 2024-01-15\nAmount: $1,500.00\nVendor: ABC Corp" }
  ]
}
```

### 5.2 testdata/ocr-responses/empty.json

```json
{
  "content": []
}
```

### 5.3 testdata/ocr-responses/mixed-content.json

```json
{
  "content": [
    { "type": "text", "text": "Page 1 content" },
    { "type": "image", "data": "base64..." },
    { "type": "text", "text": "Page 2 content" }
  ]
}
```
