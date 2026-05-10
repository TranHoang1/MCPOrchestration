# Functional Specification Document (FSD)

## MCPOrchestration — MTO-44: PowerShell Bridge Client

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-44 |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Related BRD | BRD-v1-MTO-44.docx |

---

## 1. Functional Requirements

### 1.1 stdio MCP Server

**Tools Exposed:**

| # | Tool | Type | Implementation |
|---|------|------|---------------|
| 1 | find_tools | Proxy | Invoke-RestMethod to Orchestrator |
| 2 | execute_dynamic_tool | Proxy | Invoke-RestMethod to Orchestrator |
| 3 | toggle_tool | Proxy | Invoke-RestMethod |
| 4 | reset_tools | Proxy | Invoke-RestMethod |
| 5 | manage_auto_approve | Proxy | Invoke-RestMethod |
| 6 | agent_log | Proxy | Invoke-RestMethod |
| 7 | stream_write_file | Local | Set-Content / Add-Content |

### 1.2 HTTP Client

```powershell
function Invoke-OrchestratorRequest {
    param([string]$Body)
    
    $headers = @{ "Content-Type" = "application/json" }
    if ($script:SessionId) { $headers["Mcp-Session-Id"] = $script:SessionId }
    
    $response = Invoke-RestMethod -Uri $script:OrchestratorUrl `
        -Method POST -Body $Body -Headers $headers `
        -TimeoutSec ($script:TimeoutMs / 1000)
    
    return $response
}
```

### 1.3 Health Check (Background Job)

```powershell
function Start-HealthCheck {
    $script:HealthCheckJob = Start-Job -ScriptBlock {
        param($Url, $SessionId, $Interval, $StateFile)
        $pingId = 0
        while ($true) {
            Start-Sleep -Milliseconds $Interval
            $pingId++
            try {
                $body = @{ jsonrpc="2.0"; id=$pingId; method="ping" } | ConvertTo-Json
                $headers = @{ "Content-Type"="application/json"; "Mcp-Session-Id"=$SessionId }
                Invoke-RestMethod -Uri $Url -Method POST -Body $body -Headers $headers -TimeoutSec 5
                "CONNECTED" | Set-Content $StateFile
            } catch {
                "DISCONNECTED" | Set-Content $StateFile
            }
        }
    } -ArgumentList $script:OrchestratorUrl, $script:SessionId, $script:PingInterval, $script:StateFile
}
```

### 1.4 Configuration

| Parameter | Function Param | Env Variable | Default |
|-----------|---------------|--------------|---------|
| URL | -Url | ORCHESTRATOR_URL | http://localhost:8080/mcp |
| Timeout | -TimeoutMs | BRIDGE_TIMEOUT | 30000 |
| Ping interval | -PingInterval | PING_INTERVAL | 30000 |

### 1.5 Main Loop

```powershell
function Start-MainLoop {
    while ($line = [Console]::In.ReadLine()) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        
        $request = $line | ConvertFrom-Json
        $response = switch ($request.method) {
            "initialize" { Handle-Initialize $request }
            "tools/list" { Handle-ToolsList $request }
            "tools/call" { Handle-ToolCall $request }
            default { New-JsonRpcError $request.id -32601 "Method not found" }
        }
        
        [Console]::Out.WriteLine(($response | ConvertTo-Json -Compress))
        [Console]::Out.Flush()
    }
}
```

---

## 2. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence | [sequence-tool-call.png](diagrams/sequence-tool-call.png) | [sequence-tool-call.drawio](diagrams/sequence-tool-call.drawio) |
