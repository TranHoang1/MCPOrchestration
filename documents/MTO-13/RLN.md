# Release Notes

## MTO-13: HTTP Streamable Transport Mode Support

**Version:** 1.1.0
**Date:** 2026-05-06
**Branch:** MTO-13

---

## Summary

This release adds HTTP Streamable transport support (MCP Spec 2025-03-26), hidden utility tools for draw.io diagram management, and smart tool promotion for improved tool discovery UX.

---

## New Features

### Part A: HTTP Streamable Transport

- **POST /mcp endpoint** — Full JSON-RPC 2.0 over HTTP with session management
- **Session management** — UUID-based sessions with configurable TTL (default 30 min)
- **SSE event buffering** — Stream resumption via `Last-Event-ID` header
- **Health endpoint** — GET /health for monitoring
- **Max session limit** — Configurable cap with 503 + Retry-After response

### Part B: Hidden Utility Tools

- **get_drawio_reference** — Returns draw.io XML reference documentation
- **export_drawio** — Exports .drawio files to PNG/SVG/PDF via CLI
- Hidden from `tools/list` but discoverable via `find_tools`

### Part F: Smart Tool Promotion

- **Auto-promotion** — Discovered tools promoted to top-level for direct access
- **LRU cache** — Configurable max promoted tools (default 50)
- **TTL expiry** — Promoted tools expire after 5 minutes of inactivity
- **Compact schemas** — Descriptions truncated, optional params stripped

---

## Configuration Changes

New configuration sections (all optional with sensible defaults):

```yaml
orchestrator:
  server:
    transport: http-streamable  # New transport mode
  http_session:
    max_sessions: 100
    session_ttl_minutes: 30
    event_buffer_size: 1000
    cleanup_interval_seconds: 60
```

---

## Breaking Changes

None. All changes are additive. Existing stdio and SSE transports work unchanged.

---

## Files Changed

### New Files (14)
- `src/main/kotlin/com/orchestrator/mcp/session/` — 5 files (session management)
- `src/main/kotlin/com/orchestrator/mcp/promotion/` — 6 files (smart promotion)
- `src/main/kotlin/com/orchestrator/mcp/protocol/HiddenToolRegistrar.kt`
- `src/main/kotlin/com/orchestrator/mcp/protocol/DrawioExportExecutor.kt`
- `src/main/kotlin/com/orchestrator/mcp/HttpStreamableServer.kt`
- `src/main/kotlin/com/orchestrator/mcp/transport/HttpStreamableTransport.kt`

### Modified Files (4)
- `src/main/kotlin/com/orchestrator/mcp/Application.kt` — Added http-streamable transport case
- `src/main/kotlin/com/orchestrator/mcp/config/OrchestratorConfig.kt` — Added httpSession config
- `src/main/kotlin/com/orchestrator/mcp/model/ErrorCodes.kt` — Added session error codes
- `src/main/kotlin/com/orchestrator/mcp/model/Exceptions.kt` — Added session exceptions

### New Tests (3 files, 14 test cases)
- `SessionManagerImplTest.kt` — 6 tests
- `PromotionCacheTest.kt` — 5 tests
- `CompactSchemaGeneratorTest.kt` — 3 tests

---

## Test Results

- All existing tests: **PASSED**
- New tests: **14 PASSED**
- Build: **SUCCESSFUL**
- Integration test (Testcontainers): SKIPPED (Docker not available in CI)

---

## Known Issues

1. HTTP Streamable message handler uses simplified pass-through — full MCP SDK wiring for HTTP mode pending Part C/D implementation
2. Parts C (Gradle multi-module), D (Kotlin Bridge), E (Node.js Bridge) deferred to next sprint
3. `export_drawio` requires draw.io Desktop installed on the host machine

---

## Upgrade Path

1. Pull branch MTO-13
2. Build: `./gradlew buildFatJar`
3. Deploy JAR (no config changes needed for stdio mode)
4. To enable HTTP mode: update `application.yml` with `transport: http-streamable`
