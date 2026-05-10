# Technical Design Document (TDD)

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
| Related FSD | FSD-v1-MTO-29.docx |

---

## 1. Architecture Overview

The OCR module integrates with the existing MCP orchestrator by calling the MarkItDown MCP tool through the established `ToolExecutionDispatcher`. No new external services or transports are needed.

```
AttachmentProcessor → TextExtractor → ImageTextExtractor → OcrService → ToolExecutionDispatcher → MarkItDown MCP
```

## 2. Package Structure

```
com.orchestrator.mcp.ocr/
├── OcrService.kt                    # Interface (public API)
├── OcrServiceImpl.kt                # Implementation via MCP tool call
├── model/
│   ├── OcrConfig.kt                 # Configuration data class
│   └── OcrException.kt              # Sealed exception hierarchy
├── extractor/
│   └── ImageTextExtractor.kt        # ContentExtractor for image MIME types
└── di/
    └── OcrModule.kt                 # Koin DI module
```

## 3. Class Design

### 3.1 OcrService Interface

```kotlin
package com.orchestrator.mcp.ocr

interface OcrService {
    suspend fun extractText(fileUri: String): String
}
```

### 3.2 OcrServiceImpl

```kotlin
package com.orchestrator.mcp.ocr

class OcrServiceImpl(
    private val dispatcher: ToolExecutionDispatcher,
    private val config: OcrConfig
) : OcrService {

    override suspend fun extractText(fileUri: String): String {
        validateUri(fileUri)
        return callMarkItDown(fileUri)
    }

    private fun validateUri(uri: String) { /* validate format */ }
    private suspend fun callMarkItDown(uri: String): String { /* MCP tool call */ }
}
```

### 3.3 OcrConfig

```kotlin
@Serializable
data class OcrConfig(
    val enabled: Boolean = true,
    val serverName: String = "markitdown",
    val toolName: String = "convert_to_markdown",
    val timeoutSeconds: Int = 30,
    val maxFileSizeMb: Int = 20,
    val supportedFormats: List<String> = listOf("image/png", "image/jpeg", "image/tiff")
)
```

### 3.4 OcrException

```kotlin
sealed class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ServerUnavailableException(server: String, cause: Throwable?) : OcrException(...)
    class TimeoutException(timeoutMs: Long) : OcrException(...)
    class UnsupportedFormatException(format: String) : OcrException(...)
    class FileNotFoundException(uri: String) : OcrException(...)
}
```

### 3.5 ImageTextExtractor

```kotlin
class ImageTextExtractor(
    private val ocrService: OcrService
) : ContentExtractor {
    override suspend fun extract(content: ByteArray, metadata: Map<String, String>): String {
        val uri = metadata["uri"] ?: metadata["file_path"] ?: return ""
        return ocrService.extractText(uri)
    }
}
```

### 3.6 OcrModule (Koin)

```kotlin
val ocrModule = module {
    single { OcrConfig() }
    single<OcrService> { OcrServiceImpl(get(), get()) }
    single { ImageTextExtractor(get()) }
}
```

## 4. Integration Points

### 4.1 ToolExecutionDispatcher Call

```kotlin
private suspend fun callMarkItDown(uri: String): String {
    val request = ExecuteToolRequest(
        serverName = config.serverName,
        toolName = config.toolName,
        arguments = mapOf("uri" to uri)
    )
    val response = dispatcher.execute(request)
    return extractTextFromResponse(response)
}
```

### 4.2 AttachmentProcessor Registration

In `attachmentModule`, add image MIME types to the TextExtractor map:
```kotlin
"image/png" to ImageTextExtractor(get()),
"image/jpeg" to ImageTextExtractor(get()),
"image/tiff" to ImageTextExtractor(get())
```

## 5. Error Handling Strategy

| Scenario | Behavior |
|----------|----------|
| MCP server down | Log WARN, return "" |
| Timeout exceeded | Log WARN, return "" |
| Invalid response format | Log ERROR, return "" |
| Unsupported MIME type | Throw UnsupportedFormatException |
| File URI invalid | Log ERROR, return "" |

All errors except UnsupportedFormatException result in graceful degradation (empty string return). The pipeline continues processing other attachments.

## 6. Security

- File URIs are validated before passing to MCP tool
- No sensitive data in logs (only URI path, not content)
- MCP tool runs in sandboxed process

## 7. Implementation Checklist

### Files to Create

| # | File | Package | Lines (est.) | Priority |
|---|------|---------|-------------|----------|
| 1 | OcrService.kt | ocr | ~10 | P0 |
| 2 | OcrServiceImpl.kt | ocr | ~80 | P0 |
| 3 | OcrConfig.kt | ocr.model | ~20 | P0 |
| 4 | OcrException.kt | ocr.model | ~20 | P0 |
| 5 | ImageTextExtractor.kt | ocr.extractor | ~30 | P0 |
| 6 | OcrModule.kt | ocr.di | ~20 | P0 |

### Files to Modify

| # | File | Change | Priority |
|---|------|--------|----------|
| 1 | AppModule.kt | Include ocrModule | P0 |
| 2 | AttachmentModule.kt | Add image MIME types with ImageTextExtractor | P0 |
| 3 | application.yml | Add ocr config section | P1 |

## 8. Testing Strategy

| Level | What to Test | Technique |
|-------|-------------|-----------|
| Unit | OcrServiceImpl logic, validation | MockK (mock dispatcher) |
| Unit | ImageTextExtractor delegation | MockK (mock OcrService) |
| Integration | Full MCP tool call flow | Ktor TestHost + mock MCP server |
| E2E | Image → text extraction | Real MarkItDown server (if available) |
