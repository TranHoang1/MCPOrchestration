# Deployment Guide (DPG)

## MTO-13: HTTP Streamable Transport Mode Support

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-13 |
| Version | 1.0 |
| Date | 2026-05-06 |
| Author | DevOps Agent |

---

## 1. Overview

This deployment guide covers the release of MTO-13 features:
- **Part A**: HTTP Streamable Transport (MCP Spec 2025-03-26)
- **Part B**: Hidden Utility Tools (get_drawio_reference, export_drawio)
- **Part F**: Smart Tool Promotion (auto-promote discovered tools)

## 2. Pre-Deployment Checklist

| # | Check | Status |
|---|-------|--------|
| 1 | All unit tests pass (`./gradlew test`) | ✅ |
| 2 | Build fat JAR succeeds (`./gradlew buildFatJar`) | ✅ |
| 3 | No breaking changes to existing stdio transport | ✅ |
| 4 | Configuration backward-compatible (new fields have defaults) | ✅ |
| 5 | Branch pushed to remote | ✅ |

## 3. Build Steps

```bash
# 1. Checkout the MTO-13 branch
git checkout MTO-13

# 2. Build fat JAR
./gradlew buildFatJar

# 3. Verify JAR exists
ls build/libs/mcp-orchestrator-all.jar
```

## 4. Deployment Steps

### 4.1 Stdio Mode (Default — No Changes Required)

Existing stdio deployments require no changes. The new code is backward-compatible.

```bash
java -jar build/libs/mcp-orchestrator-all.jar
```

### 4.2 HTTP Streamable Mode (New)

To enable HTTP Streamable transport:

1. Update `application.yml`:
```yaml
orchestrator:
  server:
    port: 8080
    transport: http-streamable
  http_session:
    max_sessions: 100
    session_ttl_minutes: 30
    event_buffer_size: 1000
    cleanup_interval_seconds: 60
```

2. Start the server:
```bash
java -jar build/libs/mcp-orchestrator-all.jar --config /path/to/application.yml
```

3. Verify health endpoint:
```bash
curl http://localhost:8080/health
# Expected: OK
```

4. Test MCP endpoint:
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

### 4.3 Configuration Changes

| Config Key | Default | Description |
|-----------|---------|-------------|
| `orchestrator.server.transport` | `stdio` | Transport mode: `stdio`, `sse`, `http`, `http-streamable` |
| `orchestrator.http_session.max_sessions` | `100` | Max concurrent HTTP sessions |
| `orchestrator.http_session.session_ttl_minutes` | `30` | Session timeout |
| `orchestrator.http_session.event_buffer_size` | `1000` | SSE event buffer per session |
| `orchestrator.http_session.cleanup_interval_seconds` | `60` | Expired session cleanup interval |

## 5. Rollback Plan

### 5.1 Quick Rollback

If issues are detected after deployment:

1. Stop the server
2. Revert to previous JAR:
```bash
# Switch back to previous branch
git checkout feature/MTO-12-auto-file-proxy
./gradlew buildFatJar
java -jar build/libs/mcp-orchestrator-all.jar
```

### 5.2 Configuration Rollback

If only the HTTP Streamable mode has issues, revert transport config:
```yaml
orchestrator:
  server:
    transport: stdio  # Revert to stdio
```

## 6. Post-Deployment Verification

| # | Check | Command | Expected |
|---|-------|---------|----------|
| 1 | Server starts | Check logs | "MCP Orchestration Server v1.0.0 starting..." |
| 2 | Health endpoint | `curl /health` | "OK" |
| 3 | Stdio mode works | Run via IDE | Tools discoverable |
| 4 | HTTP mode works | `curl POST /mcp` | JSON-RPC response with session ID |
| 5 | Hidden tools discoverable | `find_tools("drawio")` | Returns get_drawio_reference |
| 6 | Existing tests pass | `./gradlew test` | BUILD SUCCESSFUL |

## 7. Monitoring

- Watch logs for `Session created` / `Session terminated` messages
- Monitor active session count (logged periodically)
- Watch for `ServerOverloadedException` if max sessions reached

## 8. Known Limitations

1. HTTP Streamable transport message handler is simplified — full MCP SDK integration pending for Parts C/D
2. Parts C (Gradle multi-module), D (Kotlin Bridge), E (Node.js Bridge) not yet implemented
3. draw.io CLI must be installed for `export_drawio` tool to work
