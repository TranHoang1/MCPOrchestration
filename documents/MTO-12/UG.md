# User Guide (UG)

## MCP Orchestration Server — MTO-12: Auto File Proxy (Input + Output)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-12 |
| Title | Auto File Proxy — Transparent file I/O for upstream MCP tools |
| Author | DEV Agent |
| Version | 1.0 |
| Date | 2026-05-05 |
| Status | Draft |
| Related BRD | BRD-v1.0-MTO-12.docx |
| Related FSD | FSD-v1.0-MTO-12.docx |
| Related TDD | TDD-v1.0-MTO-12.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-05 | DEV Agent | Initial document — covers Input Proxy, Output Proxy, Configuration, and Troubleshooting |

---

## 1. Introduction

### 1.1 What is Auto File Proxy?

The **Auto File Proxy** is a transparent interception layer in the MCP Orchestrator that handles file I/O on behalf of AI agents. It solves two problems:

1. **Input:** AI agents no longer need to load file content into their context window. Instead of passing base64-encoded file data, agents simply provide a `file_path` — the proxy reads, encodes, and forwards automatically.

2. **Output:** When upstream tools return file content (paths or base64), agents can specify an `output_path` — the proxy saves the file to disk and returns a clean confirmation.

### 1.2 Key Benefits

- **Zero Context Pollution** — File content never enters the AI agent's context window
- **Fully Transparent** — Wrapper tools have the same name as originals; no agent behavior change needed
- **Auto-Detection** — No manual configuration required; the proxy detects file parameters via schema heuristics
- **Graceful Degradation** — If proxy fails, original tools remain accessible

---

## 2. How It Works

### 2.1 Input Proxy Flow (STDIO Mode)

```
Agent calls:  convert_pdf(file_path: "/tmp/report.pdf")
    ↓
Proxy reads file → encodes to base64
    ↓
Upstream receives: convert_pdf(content: "JVBERi0xLjQ...")
    ↓
Response returned to agent (no file content in context)
```

### 2.2 Input Proxy Flow (HTTP/SSE Mode)

```
Agent calls:  upload_file(file_path: "/tmp/report.pdf")
    → Returns: { file_id: "550e8400-..." }

Agent calls:  convert_pdf(file_id: "550e8400-...")
    ↓
Proxy resolves file_id → reads temp file → encodes to base64
    ↓
Upstream receives: convert_pdf(content: "JVBERi0xLjQ...")
    ↓
Response returned to agent
```

### 2.3 Output Proxy Flow

```
Agent calls:  export_report(report_id: "123", output_path: "/tmp/output/result.pdf")
    ↓
Proxy strips output_path, forwards: export_report(report_id: "123")
    ↓
Upstream returns: { artifacts: [{ path: "/tmp/upstream/report.pdf" }] }
    ↓
Proxy copies file to output_path
    ↓
Agent receives: { saved_to: "/tmp/output/result.pdf", bytes_written: 15234 }
```

---

## 3. Configuration

### 3.1 Default Configuration

The file proxy is enabled by default. Configuration lives in `application.yml`:

```yaml
orchestrator:
  file-proxy:
    enabled: true
    max-size-mb: 50
    temp-directory: "/tmp/mcp-file-proxy"
    ttl-minutes: 60
    cleanup-interval-minutes: 15
    shutdown-timeout-seconds: 30
    input-proxy-enabled: true
    output-proxy-enabled: true
    runtime-detection-enabled: true
    servers: {}
```

### 3.2 Configuration Reference

| Field | Default | Description |
|-------|---------|-------------|
| `enabled` | `true` | Master switch for the entire file proxy feature |
| `max-size-mb` | `50` | Maximum file size allowed (global default) |
| `temp-directory` | `/tmp/mcp-file-proxy` | Directory for temporary file storage (HTTP/SSE uploads) |
| `ttl-minutes` | `60` | Time-to-live for uploaded files before auto-cleanup |
| `cleanup-interval-minutes` | `15` | Background cleanup job interval |
| `shutdown-timeout-seconds` | `30` | Max time for cleanup during graceful shutdown |
| `input-proxy-enabled` | `true` | Enable/disable input file proxying |
| `output-proxy-enabled` | `true` | Enable/disable output file proxying |
| `runtime-detection-enabled` | `true` | Enable runtime detection of output tools on first call |
| `servers` | `{}` | Per-server configuration overrides |

### 3.3 Per-Server Size Override

Override the max file size for specific upstream servers:

```yaml
orchestrator:
  file-proxy:
    max-size-mb: 50          # Global default
    servers:
      pdf-tools:
        max-size-mb: 100     # Allow 100MB for pdf-tools server
      image-processor:
        max-size-mb: 200     # Allow 200MB for image-processor
```

### 3.4 Disabling the Feature

To completely disable file proxy (original tools remain visible as-is):

```yaml
orchestrator:
  file-proxy:
    enabled: false
```

---

## 4. Usage

### 4.1 Input Proxy — STDIO Mode

When the orchestrator detects an upstream tool with a base64 parameter, it automatically creates a wrapper that accepts `file_path` instead.

**Example: Converting a PDF**

Original upstream tool expects base64 content. With file proxy, you simply provide the path:

```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "convert_pdf",
    "arguments": {
      "file_path": "/home/user/documents/report.pdf",
      "output_format": "markdown"
    }
  }
}
```

The proxy handles reading the file and encoding it. Non-file parameters (`output_format`) pass through unchanged.

### 4.2 Input Proxy — HTTP/SSE Mode

In HTTP/SSE mode, files are uploaded first, then referenced by ID:

**Step 1: Upload the file**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "upload_file",
    "arguments": {
      "file_path": "/home/user/documents/report.pdf"
    }
  }
}
```

**Response:**
```json
{
  "file_id": "550e8400-e29b-41d4-a716-446655440000",
  "file_name": "report.pdf",
  "file_size": 15234,
  "expires_in": "60 minutes"
}
```

**Step 2: Use the file_id**
```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "convert_pdf",
    "arguments": {
      "file_id": "550e8400-e29b-41d4-a716-446655440000",
      "output_format": "markdown"
    }
  }
}
```

### 4.3 Output Proxy

When an upstream tool returns file content, add `output_path` to save it:

```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "export_report",
    "arguments": {
      "report_id": "RPT-2026-001",
      "output_path": "/home/user/downloads/report.pdf"
    }
  }
}
```

**Response:**
```json
{
  "saved_to": "/home/user/downloads/report.pdf",
  "bytes_written": 52428,
  "source_type": "FILE_PATH"
}
```

**Without `output_path`:** The upstream response passes through unchanged (passthrough mode).

### 4.4 Combined Input + Output

Some tools accept file input AND produce file output. Both can be proxied in a single call:

```json
{
  "name": "execute_dynamic_tool",
  "arguments": {
    "tool_name": "convert_image",
    "arguments": {
      "file_path": "/home/user/photos/input.png",
      "format": "webp",
      "output_path": "/home/user/photos/output.webp"
    }
  }
}
```

---

## 5. Auto-Detection

### 5.1 How Detection Works

The proxy automatically identifies file parameters using three heuristics:

| Method | Confidence | Example |
|--------|-----------|---------|
| Schema type (`contentEncoding: base64`) | 0.95 | `{"type": "string", "contentEncoding": "base64"}` |
| Parameter name pattern | 0.90 | `content`, `file_content`, `image_data`, `base64` |
| Description keywords | 0.80 | "base64-encoded file content", "binary data" |

### 5.2 Output Detection

Output tools are detected via:
- **Static:** Tool name patterns (`export_*`, `generate_*`, `convert_*`, `render_*`)
- **Static:** Output schema declaring file type
- **Runtime:** First call returns `artifacts[].path` or long base64 string (>1000 chars)

### 5.3 Re-Detection

Detection runs automatically:
- On server startup (all connected upstream servers)
- On upstream server reconnect (only that server's tools)

No manual intervention needed.

---

## 6. File Path Requirements

### 6.1 Input Paths (`file_path`)

| Rule | Example |
|------|---------|
| Must be absolute | ✅ `/home/user/file.pdf` — ❌ `./file.pdf` |
| No path traversal (`../`) | ✅ `/tmp/safe/file.pdf` — ❌ `/tmp/../etc/passwd` |
| File must exist and be readable | Permission check before processing |
| File size ≤ configured max | Default 50MB, configurable per-server |

### 6.2 Output Paths (`output_path`)

| Rule | Example |
|------|---------|
| Must be absolute | ✅ `/home/user/output.pdf` — ❌ `output.pdf` |
| No path traversal (`../`) | Blocked for security |
| Parent directory must exist | `/home/user/` must exist |
| Parent directory must be writable | Permission check before save |
| Existing files are overwritten | Default behavior |

---

## 7. Lifecycle & Cleanup

### 7.1 Session Management

Each server startup generates a unique **session ID** (UUID). All file proxy operations are tagged with this session ID for cleanup scoping.

### 7.2 Cleanup Strategies

| Strategy | When | What |
|----------|------|------|
| **Startup** | Server boot | Deletes all records from previous sessions + orphan temp files |
| **Per-Request** | After each proxy call | Deletes the registry record for that operation |
| **Background TTL** | Every 15 minutes | Deletes records older than TTL (60 min default) |
| **Shutdown** | Server stop (SIGTERM) | Deletes all current session records + temp files |

### 7.3 Graceful Degradation

If PostgreSQL is unavailable:
- File proxy continues to operate (reads/writes files normally)
- Registry operations are skipped with a warning log
- No cleanup guarantee until DB is restored

---

## 8. Error Codes

| Error Code | Meaning | Resolution |
|------------|---------|------------|
| `FILE_NOT_FOUND` | File does not exist at specified path | Verify the file path is correct |
| `FILE_TOO_LARGE` | File exceeds max size limit | Use a smaller file or increase `max-size-mb` |
| `FILE_NOT_READABLE` | Permission denied reading file | Check file permissions |
| `INVALID_PATH` | Path traversal or non-absolute path | Use absolute paths without `../` |
| `INVALID_FILE_ID` | file_id is not a valid UUID | Use the UUID returned from `upload_file` |
| `FILE_ID_NOT_FOUND` | file_id not in registry | File may have expired; re-upload |
| `FILE_EXPIRED` | Uploaded file exceeded TTL | Re-upload the file |
| `OUTPUT_DIR_NOT_FOUND` | Parent directory of output_path doesn't exist | Create the directory first |
| `OUTPUT_NOT_WRITABLE` | Cannot write to output directory | Check directory permissions |

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Tool still shows base64 param | Detection didn't match | Check upstream tool schema has `contentEncoding: base64` or matching name pattern |
| `FILE_TOO_LARGE` error | File exceeds 50MB default | Add per-server override in config |
| `FILE_EXPIRED` after upload | TTL exceeded (default 60 min) | Re-upload, or increase `ttl-minutes` |
| Proxy not working after restart | Feature disabled in config | Verify `file-proxy.enabled: true` |
| Orphan files in temp directory | Abnormal shutdown | Files cleaned on next startup automatically |
| `INVALID_PATH` on valid path | Path contains `../` | Use canonical absolute path without traversal |
| Output file not saved | No `output_path` provided | Add `output_path` parameter to the call |
| Original tool visible (no wrapper) | Wrapper generation failed | Check logs for `[FileProxy] Wrapper creation failed` |

---

## 10. Monitoring & Logs

### 10.1 Key Log Messages

| Log | Level | Meaning |
|-----|-------|---------|
| `[FileProxy] Detected: tool=X, param=Y, method=Z` | INFO | File parameter detected in upstream tool |
| `[FileProxy] Wrapper created: tool=X` | INFO | Wrapper tool generated successfully |
| `[FileProxy] INPUT proxy: tool=X, file_size=N` | INFO | Input proxy processing a file |
| `[FileProxy] OUTPUT proxy: saved N bytes to path` | INFO | Output proxy saved file |
| `[FileProxy] Registry unavailable — degraded mode` | WARN | PostgreSQL connection lost |
| `[FileProxy] Cleanup: records=N, files=N` | INFO | Cleanup completed |
| `[FileProxy] Feature disabled — skipping` | INFO | Feature turned off in config |

### 10.2 Health Indicators

- **Temp directory size:** Should stay near zero in steady state
- **Registry record count:** Should be < 10 during normal operation (transient records)
- **Cleanup frequency:** Background job runs every 15 minutes

---

## 11. Appendix

### 11.1 Glossary

| Term | Definition |
|------|------------|
| File Proxy | Transparent wrapper handling file I/O for AI agents |
| Input Proxy | Converts file_path/file_id to base64 for upstream tools |
| Output Proxy | Saves upstream file responses to agent-specified output_path |
| Wrapper Tool | Proxy tool replacing original in discovery (same name) |
| Session ID | UUID generated per server startup, scopes registry records |
| TTL | Time-To-Live for uploaded files before auto-cleanup |

### 11.2 Related Documents

| Document | Location |
|----------|----------|
| BRD | documents/MTO-12/BRD.md |
| FSD | documents/MTO-12/FSD.md |
| TDD | documents/MTO-12/TDD.md |
| STP | documents/MTO-12/STP.md |
| STC | documents/MTO-12/STC.md |
| MTO-10 User Guide | documents/MTO-10/UG.md |
