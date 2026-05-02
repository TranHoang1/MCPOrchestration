# Release Notes (RLN)

## MCP Orchestration Server — MTO-5: Create MCP Tool Orchestration

---

## Release Information

| Field | Value |
|-------|-------|
| Release Version | 1.0.0 |
| Release Date | 2026-05-03 |
| Jira Ticket | MTO-5 |
| Environment | DEV / UAT / PROD (Developer Local) |
| Author | DevOps Agent |
| Status | Final |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-03 | DevOps Agent | Initiate document — initial release |

---

## 1. What's New

### 1.1 Feature Summary

The **MCP Orchestration Server** is a brand-new Kotlin/Ktor application that acts as an intelligent proxy between the Kiro AI IDE and multiple upstream MCP (Model Context Protocol) Servers. Instead of loading all tool definitions into the AI's context window (which can be 100+ tools), the Orchestrator exposes only **2 tools** — dramatically reducing context window consumption while maintaining full access to all upstream tools.

**Key capabilities:**
- **Semantic Tool Discovery** (`find_tools`): Natural language search across all registered tools using vector embeddings
- **Dynamic Tool Execution** (`execute_dynamic_tool`): Transparent proxy to any upstream MCP server
- **Automatic Tool Indexing**: Scans upstream servers and vectorizes tool metadata on startup
- **Health Monitoring**: Periodic health checks with auto-reconnect for upstream servers
- **Graceful Degradation**: Falls back to keyword search when Vector DB or embedding service is unavailable
- **Hot-Reload Configuration**: Update server config without restart

### 1.2 User-Facing Changes

| # | Change | Description | Impact |
|---|--------|-------------|--------|
| 1 | New MCP Server | MCP Orchestration Server available as a new MCP server for Kiro IDE | High |
| 2 | 2-Tool Interface | AI agents interact via `find_tools` + `execute_dynamic_tool` only | High |
| 3 | Semantic Search | Natural language tool discovery replaces manual tool browsing | Medium |
| 4 | Multi-Server Support | Single entry point to tools across multiple upstream MCP servers | High |
| 5 | FAISS Fallback | Works without external Vector DB (local FAISS index) | Medium |

---

## 2. Technical Changes

### 2.1 API Changes

| Type | Endpoint/Tool | Method | Description |
|------|--------------|--------|-------------|
| New | `find_tools` | MCP `tools/call` | Semantic search for available tools |
| New | `execute_dynamic_tool` | MCP `tools/call` | Proxy execution to upstream MCP server |
| New | `initialize` | MCP protocol | MCP session handshake |
| New | `tools/list` | MCP protocol | Returns 2 tool definitions |
| New | `ping` | MCP protocol | Health check from IDE |

### 2.2 Database Changes

| Type | Object | Description |
|------|--------|-------------|
| New Collection | `mcp_tools` (Qdrant) | Vector collection for tool embeddings (768 dimensions, Cosine distance) |
| New File | `faiss.index` (FAISS) | Local fallback vector index at `~/.mcp-orchestrator/` |
| New File | `faiss-metadata.json` (FAISS) | Companion metadata for FAISS index |

### 2.3 Configuration Changes

| Property | Change Type | Description |
|----------|-----------|-------------|
| `orchestrator.server.port` | New | HTTP server port (default: 8080) |
| `orchestrator.server.transport` | New | Transport mode: `stdio` or `http` |
| `orchestrator.discovery.*` | New | Tool discovery settings (top_k, threshold, fallback) |
| `orchestrator.execution.*` | New | Execution settings (timeout, retries, validation) |
| `orchestrator.embedding.*` | New | Embedding provider config (OpenAI, model, dimensions) |
| `orchestrator.vector_db.*` | New | Vector DB connection config (Qdrant host/port/collection) |
| `orchestrator.health.*` | New | Health monitoring config (interval, reconnect) |
| `orchestrator.upstream_servers` | New | List of upstream MCP server definitions |

### 2.4 Infrastructure Changes

| Component | Change | Description |
|-----------|--------|-------------|
| MCP Orchestrator JAR | New | `mcp-orchestrator-all.jar` — fat JAR (~30-50 MB) |
| Qdrant (optional) | New | Docker container for production vector search |
| FAISS (bundled) | New | Local vector index — no external service needed |

---

## 3. Bug Fixes

> No bug fixes included in this release. This is the initial release (v1.0.0).

---

## 4. Known Issues & Limitations

| # | Issue | Impact | Workaround | Target Fix |
|---|-------|--------|------------|------------|
| 1 | UG only shows YAML config format | Users who prefer JSON config for MCP settings need to manually convert | Convert YAML to JSON manually | v1.1.0 (comment #10046) |
| 2 | stdio transport: single-process model | Cannot horizontally scale in stdio mode | Use HTTP mode for multi-instance | Future |
| 3 | OpenAI API rate limits (3,000 RPM) | Large-scale indexing may be throttled | Batch processing mitigates this | Future |
| 4 | FAISS fallback: no payload filtering | Keyword search less precise than Qdrant semantic search | Use Qdrant for production | Future |
| 5 | No UI dashboard | Tool management only via config files | Edit `application.yml` directly | Future phase |

---

## 5. Dependencies

### 5.1 Pre-requisite Releases

| Release | Version | Status | Required Before |
|---------|---------|--------|-----------------|
| JDK | 21+ | Available | This release |
| Qdrant (optional) | 1.9+ | Available | This release (optional) |

### 5.2 External System Changes

| System | Change Required | Status | Contact |
|--------|----------------|--------|---------|
| OpenAI API | API key provisioned | Required | Developer |
| Upstream MCP Servers | Configured in `application.yml` | Per-deployment | Developer |

---

## 6. Migration Notes

### 6.1 Data Migration

> **Not applicable.** This is the initial release. No data migration required.

### 6.2 Breaking Changes

> **Not applicable.** This is the initial release. No breaking changes.

### 6.3 Backward Compatibility

This is the initial release (v1.0.0). No backward compatibility concerns.

---

## 7. Testing Summary

| Test Level | Total | Passed | Failed | Blocked | Pass Rate |
|-----------|-------|--------|--------|---------|-----------|
| PBT (Property-Based) | 12 | 12 | 0 | 0 | 100% |
| Unit Tests | 42 | 42 | 0 | 0 | 100% |
| Integration Tests | 31 | 31 | 0 | 0 | 100% |
| E2E-API | 21 | 21 | 0 | 0 | 100% |
| FAISS + ToolIndexer | 12 | 12 | 0 | 0 | 100% |
| **Total** | **118** | **118** | **0** | **0** | **100%** |

### Defect Summary

| Severity | Found | Fixed | Open | Deferred |
|----------|-------|-------|------|----------|
| Critical | 0 | 0 | 0 | 0 |
| Major | 0 | 0 | 0 | 0 |
| Minor | 0 | 0 | 0 | 0 |

---

## 8. Deployment Instructions

See: [Deployment Guide](documents/MTO-5/DPG.md)

### Quick Reference

| Step | Action | Estimated Time |
|------|--------|---------------|
| 1 | Build fat JAR (`./gradlew clean build`) | ~2 min |
| 2 | Run tests (`./gradlew test`) | ~2 min |
| 3 | Copy JAR + config to deployment location | < 10s |
| 4 | Set `OPENAI_API_KEY` environment variable | < 10s |
| 5 | Start Qdrant Docker (optional) | < 30s |
| 6 | Start application | < 10s |
| 7 | Verify health + smoke tests | < 2 min |
| **Total** | | **~7 minutes** |

---

## 9. Rollback Plan

See: [Deployment Guide — Section 8](documents/MTO-5/DPG.md#8-rollback-plan)

**Rollback Decision Criteria:**
- Application fails to start after deployment
- Upstream server connectivity broken
- Tool discovery returns incorrect results
- Performance degradation > 50%

**Estimated Rollback Time:** < 3 minutes

---

## 10. Contacts

| Role | Name | Contact | Responsibility |
|------|------|---------|---------------|
| Dev Lead | Duc Nguyen Minh | MTO Project | Technical issues |
| QA Lead | QA Agent | Automated | Testing sign-off |
| DevOps | DevOps Agent | Automated | Deployment execution |
| Business Owner | Duc Nguyen Minh | MTO Project | Business sign-off |

---

## 11. Approval

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Dev Lead | | | ☐ Approved |
| QA Lead | | | ☐ Approved |
| Business Owner | | | ☐ Approved |
| Release Manager | | | ☐ Approved |
