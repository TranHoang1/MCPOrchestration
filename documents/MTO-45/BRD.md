# Business Requirements Document (BRD)

## MCPOrchestration — MTO-45: Windows CMD Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-45 |
| Title | Windows CMD Bridge Client — MCP Orchestrator Connector |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |

---

## 1. Introduction

### 1.1 Scope

Implement a **Windows CMD-based MCP Bridge Client** (.cmd/.bat file) that connects to the MCP Orchestrator via HTTP Streamable using `curl.exe` (bundled with Windows 10+). This is the most minimal bridge — designed for legacy Windows environments where PowerShell may be restricted.

1. **Windows CMD compatible** — Pure batch scripting (cmd.exe)
2. **HTTP via curl.exe** — Bundled with Windows 10+ (no install needed)
3. **JSON parsing** — Minimal via jq.exe or string manipulation
4. **stdio MCP Server** — Simplified JSON-RPC (key tools only)
5. **Auto-Reconnect** — Basic retry loop with timeout
6. **Single file** — `mcp-bridge.cmd`
7. **Configuration** — Environment variables only

### 1.2 Out of Scope

- Full MCP protocol support (simplified subset)
- Background health check (CMD has no native background processes)
- Linux/macOS support (Windows-only)
- Complex JSON manipulation (limited by CMD capabilities)

### 1.3 Preliminary Requirements

1. **MTO-13** — Orchestrator HTTP Streamable endpoint
2. Windows 10+ (for bundled curl.exe)
3. Optional: jq.exe for JSON parsing (fallback to basic string parsing)

---

## 2. Business Requirements

### 2.1 User Stories

| # | Story | Priority | Source |
|---|-------|----------|--------|
| 1 | As a Windows user, I want a CMD bridge for environments where PowerShell is restricted | MUST HAVE | MTO-45 |
| 2 | As a developer, I want single-file deployment (.cmd) for easy distribution | MUST HAVE | MTO-45 |
| 3 | As a developer, I want basic auto-reconnect for server restarts | SHOULD HAVE | MTO-45 |
| 4 | As a developer, I want stream_write_file for local file operations | SHOULD HAVE | MTO-45 |

### 2.2 Acceptance Criteria

1. Bridge runs as single .cmd file (mcp-bridge.cmd)
2. Reads JSON-RPC from stdin (set /p or more)
3. Writes JSON-RPC to stdout (echo)
4. Connects to Orchestrator via curl.exe HTTP POST
5. Proxies find_tools and execute_dynamic_tool
6. Local tool: stream_write_file (basic write/append)
7. Basic reconnect on connection failure (retry 3 times with 5s delay)
8. Config via env vars: ORCHESTRATOR_URL, BRIDGE_TIMEOUT
9. Works on Windows 10+ (cmd.exe)
10. Graceful degradation: if jq.exe not available, use basic string parsing

---

## 3. Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| MTO-13 | System | Orchestrator endpoint |
| curl.exe | Runtime | HTTP client (bundled Windows 10+) |
| jq.exe | Optional | JSON parsing (fallback available) |
| cmd.exe | Runtime | Windows command interpreter |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Tool call overhead < 200ms (curl + parsing) |
| Compatibility | Windows 10, 11, Server 2019+ |
| Deployment | Single .cmd file, no installation |
| Limitations | No background health check (CMD limitation) |

---

## 5. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
