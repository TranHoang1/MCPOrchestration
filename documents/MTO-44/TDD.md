# Technical Design Document (TDD)

## MCPOrchestration — MTO-44: PowerShell Bridge Client

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-44 |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Related BRD | BRD-v1-MTO-44.docx |
| Related FSD | FSD-v1-MTO-44.docx |

---

## 1. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | PowerShell | 7.x (cross-platform) / 5.1 (Windows) |
| HTTP | Invoke-RestMethod | Built-in |
| JSON | ConvertTo-Json / ConvertFrom-Json | Built-in |
| Background | Start-Job / Start-ThreadJob | Built-in |
| Deployment | PowerShell Module (.psm1 + .psd1) | Standard |

---

## 2. Architecture

### 2.1 File Structure

```
mcp-bridge-powershell/
├── McpBridge/
│   ├── McpBridge.psd1          # Module manifest
│   ├── McpBridge.psm1          # Module script (main)
│   ├── Private/
│   │   ├── HttpClient.ps1      # HTTP Streamable client functions
│   │   ├── HealthCheck.ps1     # Health check background job
│   │   ├── Reconnection.ps1   # Reconnection with backoff
│   │   ├── LocalTools.ps1     # stream_write_file implementation
│   │   └── JsonRpc.ps1        # JSON-RPC helpers
│   └── Public/
│       └── Start-McpBridge.ps1 # Entry point function
├── tests/
│   └── McpBridge.Tests.ps1     # Pester tests
└── README.md
```

### 2.2 Module Design

**Public Functions:**
- `Start-McpBridge` — Entry point, starts the bridge

**Private Functions:**
- `Invoke-OrchestratorRequest` — HTTP POST to Orchestrator
- `Initialize-Session` — Send initialize, get session ID
- `Start-HealthCheck` — Launch background job for ping
- `Start-ReconnectLoop` — Exponential backoff reconnection
- `Invoke-StreamWriteFile` — Local file write tool
- `New-JsonRpcResponse` — Format JSON-RPC response
- `New-JsonRpcError` — Format JSON-RPC error

### 2.3 State Management

```powershell
# State stored in script-scope variable + temp file (for background job communication)
$script:State = "DISCONNECTED"
$script:StateFile = Join-Path $env:TEMP "mcp-bridge-$PID.state"

function Set-BridgeState {
    param([string]$NewState, [string]$Reason)
    $oldState = $script:State
    $script:State = $NewState
    $NewState | Set-Content $script:StateFile -NoNewline
    [Console]::Error.WriteLine("[mcp-bridge] State: $oldState → $NewState (reason: $Reason)")
}
```

---

## 3. Implementation Checklist

| # | File | Description |
|---|------|-------------|
| 1 | `McpBridge/McpBridge.psd1` | Module manifest |
| 2 | `McpBridge/McpBridge.psm1` | Module loader (dot-sources Private/*.ps1) |
| 3 | `McpBridge/Public/Start-McpBridge.ps1` | Entry point |
| 4 | `McpBridge/Private/HttpClient.ps1` | HTTP functions |
| 5 | `McpBridge/Private/HealthCheck.ps1` | Health check job |
| 6 | `McpBridge/Private/Reconnection.ps1` | Reconnect logic |
| 7 | `McpBridge/Private/LocalTools.ps1` | stream_write_file |
| 8 | `McpBridge/Private/JsonRpc.ps1` | JSON-RPC helpers |
| 9 | `tests/McpBridge.Tests.ps1` | Pester tests |

---

## 4. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
