# Deployment Guide (DPG)

## MTO-29: KB Refinery — MarkItDown MCP Integration for OCR

| Field | Value |
|-------|-------|
| **Ticket** | MTO-29 |
| **Version** | 1.0 |
| **Date** | 2026-05-08 |
| **Author** | DevOps Agent |

---

## 1. Overview

This deployment adds OCR capability to the MCP Orchestrator via MarkItDown MCP integration. The change is additive — no existing functionality is modified.

### 1.1 Components Affected

| Component | Change Type | Risk |
|-----------|-------------|------|
| orchestrator-server | New module (ocr/) | Low |
| application.yml | New config section | Low |
| mcp-servers.json | New server entry | Low |

### 1.2 Dependencies

| Dependency | Required | Notes |
|------------|----------|-------|
| MarkItDown MCP Server | Yes | Must be running and accessible |
| Python 3.10+ | Yes | Required by MarkItDown |
| markitdown package | Yes | `pip install markitdown[all]` |

---

## 2. Pre-Deployment Checklist

- [ ] MarkItDown MCP server installed and tested locally
- [ ] `mcp-servers.json` updated with markitdown entry
- [ ] `application.yml` has `orchestrator.ocr` section
- [ ] All 27 automated tests pass (`./gradlew :orchestrator-server:test --tests "com.orchestrator.mcp.ocr.*"`)
- [ ] Shadow JAR built successfully (`./gradlew :orchestrator-server:shadowJar`)
- [ ] Backup of current JAR taken

---

## 3. Deployment Steps

### Step 1: Install MarkItDown MCP Server

```bash
# Install markitdown with all extras (includes OCR support)
pip install markitdown[all]

# Verify installation
python -c "from markitdown import MarkItDown; print('OK')"
```

### Step 2: Configure MCP Servers

Add to `mcp-servers.json`:

```json
{
  "markitdown": {
    "command": "python",
    "args": ["-m", "markitdown.mcp_server"],
    "env": {}
  }
}
```

### Step 3: Configure Application

Add to `application.yml`:

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

### Step 4: Build and Deploy

```bash
# Build shadow JAR
./gradlew :orchestrator-server:shadowJar

# Stop current server
# (depends on deployment method — systemd, docker, etc.)

# Deploy new JAR
cp orchestrator-server/build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/

# Start server
java -jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar
```

### Step 5: Verify Deployment

```bash
# Check server starts without errors
tail -f /var/log/mcp-orchestrator/app.log | grep -i "ocr\|markitdown"

# Verify MarkItDown tool is discovered
curl -s http://localhost:8080/api/tools | jq '.[] | select(.name | contains("markitdown"))'
```

---

## 4. Post-Deployment Verification

| # | Check | Command/Action | Expected |
|---|-------|---------------|----------|
| 1 | Server starts | Check logs | No errors related to OCR module |
| 2 | MarkItDown discovered | List tools API | `markitdown/convert_to_markdown` in tool list |
| 3 | OCR works | Send test image | Extracted text returned |
| 4 | Timeout works | Send large image | Returns empty after 30s, no crash |
| 5 | Disabled mode | Set enabled=false, restart | OCR calls return empty immediately |

---

## 5. Rollback Plan

### 5.1 Quick Rollback (< 5 minutes)

```yaml
# Option A: Disable OCR via config (no restart needed if hot-reload supported)
orchestrator:
  ocr:
    enabled: false

# Option B: Revert to previous JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak /opt/mcp-orchestrator/mcp-orchestrator-all.jar
# Restart server
```

### 5.2 Full Rollback

1. Stop server
2. Restore previous JAR from backup
3. Remove `orchestrator.ocr` section from `application.yml`
4. Remove `markitdown` entry from `mcp-servers.json`
5. Start server
6. Verify no OCR-related errors in logs

### 5.3 Rollback Triggers

| Condition | Action |
|-----------|--------|
| Server fails to start | Full rollback |
| MarkItDown crashes repeatedly | Disable OCR (Option A) |
| Memory leak detected | Disable OCR, investigate |
| Timeout errors > 50% | Increase timeout or disable |

---

## 6. Monitoring

### 6.1 Key Metrics

| Metric | Threshold | Alert |
|--------|-----------|-------|
| OCR success rate | < 80% | Warning |
| OCR avg latency | > 10s | Warning |
| OCR timeout rate | > 20% | Critical |
| MarkItDown process crashes | > 3/hour | Critical |

### 6.2 Log Patterns to Watch

```
# Success
INFO  c.o.mcp.ocr.OcrServiceImpl - OCR completed for URI: ...

# Timeout (expected for large files)
WARN  c.o.mcp.ocr.OcrServiceImpl - OCR timeout for URI: ...

# Server down (needs attention)
WARN  c.o.mcp.ocr.OcrServiceImpl - OCR failed for URI: ... — Connection refused
```

---

## 7. Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `orchestrator.ocr.enabled` | `true` | Feature flag |
| `orchestrator.ocr.server-name` | `markitdown` | MCP server name |
| `orchestrator.ocr.tool-name` | `convert_to_markdown` | MCP tool name |
| `orchestrator.ocr.timeout-seconds` | `30` | Max wait time per image |
| `orchestrator.ocr.max-file-size-mb` | `20` | Max image file size |
| `orchestrator.ocr.supported-formats` | `[png, jpeg, tiff]` | Accepted MIME types |
