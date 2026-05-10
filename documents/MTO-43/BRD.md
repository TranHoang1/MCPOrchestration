# Business Requirements Document (BRD)

## MCPOrchestration — MTO-43: Bash Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-43 |
| Title | Bash Bridge Client — MCP Orchestrator Connector |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |

---

## 1. Introduction

### 1.1 Scope

Implement a **Bash-based MCP Bridge Client** (single shell script) that connects to the MCP Orchestrator via HTTP Streamable transport using `curl`, and exposes tools to IDEs via stdio. The scope covers:

1. **Pure Bash + curl** — No compiled dependencies; runs on any Linux/macOS with Bash 4.0+
2. **jq for JSON** — Required dependency for JSON parsing
3. **stdio MCP Server** — Reads JSON-RPC from stdin, writes to stdout
4. **HTTP Streamable Client** — Uses curl for HTTP POST to Orchestrator
5. **Auto-Reconnect** — Exponential backoff (max 15s) via background process
6. **Health Check** — Ping every 30s in background (per MTO-46)
7. **Single File Deployment** — Entire bridge is one `mcp-bridge.sh` file
8. **Configuration** — CLI args + environment variables

### 1.2 Out of Scope

- Windows native support (use WSL or CMD bridge instead)
- GUI or interactive mode
- File upload/download (binary transfer)
- Python/Node.js dependencies

### 1.3 Preliminary Requirements

1. **MTO-13** — Orchestrator HTTP Streamable endpoint
2. **MTO-46** — Health check protocol
3. Bash 4.0+ runtime
4. `curl` (pre-installed on most systems)
5. `jq` (JSON processor — must be installed)

---

## 2. Business Requirements

### 2.1 User Stories

| # | Story / Use Case | Priority | Source |
|---|------------------|----------|--------|
| 1 | As a DevOps engineer, I want a Bash bridge so that I can use MCP tools in CI/CD pipelines with minimal dependencies | MUST HAVE | MTO-43 |
| 2 | As a sysadmin, I want single-file deployment so that I can copy the bridge to any server via scp | MUST HAVE | MTO-43 |
| 3 | As a developer, I want auto-reconnect so that the bridge recovers from server restarts | MUST HAVE | MTO-43 |
| 4 | As a developer, I want health check so that connection loss is detected proactively | MUST HAVE | MTO-43 |
| 5 | As a developer, I want local stream_write_file so that file writes don't need network | SHOULD HAVE | MTO-43 |

### 2.2 Acceptance Criteria

1. Bridge runs as single Bash script (`mcp-bridge.sh`)
2. Reads JSON-RPC from stdin, writes to stdout
3. Connects to Orchestrator via curl HTTP POST
4. Proxies find_tools and execute_dynamic_tool
5. Local tool: stream_write_file (write/append modes)
6. Health check ping every 30s (background process)
7. Auto-reconnect with exponential backoff (1s-15s cap)
8. State transitions logged to stderr
9. Config via CLI args (--url, --ping-interval) and env vars
10. Works on Linux and macOS (Bash 4.0+)
11. Only dependencies: curl + jq
12. Single file < 500 lines

---

## 3. Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| MTO-13 | System | Orchestrator HTTP Streamable endpoint |
| MTO-46 | System | Health check protocol |
| curl | Runtime | HTTP client (pre-installed) |
| jq | Runtime | JSON parsing (must install) |
| Bash 4.0+ | Runtime | Shell interpreter |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Tool call overhead < 50ms (curl + jq parsing) |
| Startup | Ready in < 1s |
| Memory | < 10MB RSS |
| Compatibility | Linux (Ubuntu, CentOS, Alpine), macOS |
| Deployment | Single file, no installation needed |

---

## 5. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
