# Business Requirements Document (BRD)

## MCPOrchestration — MTO-44: PowerShell Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-44 |
| Title | PowerShell Bridge Client — MCP Orchestrator Connector |
| Author | BA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |

---

## 1. Introduction

### 1.1 Scope

Implement a **PowerShell-based MCP Bridge Client** that connects to the MCP Orchestrator via HTTP Streamable transport and exposes tools via stdio. Supports both PowerShell 7 (cross-platform) and Windows PowerShell 5.1.

1. **PowerShell Module** — `.psm1` format, Import-Module compatible
2. **HTTP Client** — `Invoke-RestMethod` / `Invoke-WebRequest`
3. **stdio MCP Server** — JSON-RPC over stdin/stdout
4. **Auto-Reconnect** — Exponential backoff with PowerShell Jobs
5. **Health Check** — Ping every 30s via background job (per MTO-46)
6. **Configuration** — Function parameters + environment variables

### 1.2 Out of Scope

- Linux/macOS-only features (PowerShell 7 is cross-platform)
- GUI/WPF interface
- Active Directory integration
- Windows Service registration

### 1.3 Preliminary Requirements

1. **MTO-13** — Orchestrator HTTP Streamable endpoint
2. **MTO-46** — Health check protocol
3. PowerShell 7+ (preferred) or Windows PowerShell 5.1
4. No additional modules required (uses built-in cmdlets)

---

## 2. Business Requirements

### 2.1 User Stories

| # | Story | Priority | Source |
|---|-------|----------|--------|
| 1 | As a Windows admin, I want a PowerShell bridge so that I can use MCP tools in automation scripts | MUST HAVE | MTO-44 |
| 2 | As a DevOps engineer, I want module format so that I can Import-Module in any script | MUST HAVE | MTO-44 |
| 3 | As a developer, I want cross-platform support so that the bridge works on Windows, Linux, macOS | SHOULD HAVE | MTO-44 |
| 4 | As a developer, I want auto-reconnect and health check per MTO-46 spec | MUST HAVE | MTO-44 |
| 5 | As a developer, I want local stream_write_file for file operations without network | SHOULD HAVE | MTO-44 |

### 2.2 Acceptance Criteria

1. Bridge runs as PowerShell module (Import-Module McpBridge)
2. Entry point: `Start-McpBridge -Url "http://localhost:8080/mcp"`
3. Reads JSON-RPC from stdin, writes to stdout
4. Proxies find_tools and execute_dynamic_tool via Invoke-RestMethod
5. Local tool: stream_write_file
6. Health check ping every 30s (PowerShell background job)
7. Auto-reconnect with exponential backoff (1s-15s cap)
8. State transitions logged to stderr (Write-Host -NoNewline to stderr)
9. Works on PowerShell 7 (cross-platform) and Windows PowerShell 5.1
10. Config via parameters and env vars (ORCHESTRATOR_URL, PING_INTERVAL)

---

## 3. Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| MTO-13 | System | Orchestrator endpoint |
| MTO-46 | System | Health check protocol |
| PowerShell 7+ / 5.1 | Runtime | Script interpreter |
| Invoke-RestMethod | Built-in | HTTP client cmdlet |

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Tool call overhead < 100ms |
| Startup | Ready in < 3s |
| Compatibility | PowerShell 7 (Win/Linux/macOS) + Windows PowerShell 5.1 |
| Deployment | Module format (.psm1 + .psd1) |

---

## 5. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | Business Flow | [business-flow.png](diagrams/business-flow.png) | [business-flow.drawio](diagrams/business-flow.drawio) |
| 2 | Use Case | [use-case.png](diagrams/use-case.png) | [use-case.drawio](diagrams/use-case.drawio) |
