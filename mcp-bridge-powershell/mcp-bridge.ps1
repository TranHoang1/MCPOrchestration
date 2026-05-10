#!/usr/bin/env pwsh
<#
.SYNOPSIS
    MCP Bridge Client — PowerShell
    Connects to MCP Orchestrator via HTTP Streamable transport.
    Exposes tools locally via stdio MCP server.
.PARAMETER Url
    Orchestrator URL (default: http://localhost:8080)
.PARAMETER Timeout
    Request timeout in seconds (default: 30)
.PARAMETER PingInterval
    Health check interval in seconds (default: 30, 0=disabled)
.PARAMETER NoReconnect
    Disable auto-reconnect
.PARAMETER NoLocalTools
    Disable local tools (stream_write_file, embed_images)
#>
param(
    [string]$Url = $env:ORCHESTRATOR_URL,
    [int]$Timeout = 30,
    [int]$PingInterval = 30,
    [int]$PingTimeout = 5,
    [switch]$NoReconnect,
    [switch]$NoLocalTools
)

$ErrorActionPreference = "Stop"
$script:Version = "1.0.0"
$script:SessionId = $null
$script:State = "DISCONNECTED"
$script:HealthCheckJob = $null
$script:BaseDelay = 1
$script:MaxDelay = 15

# Default URL
if (-not $Url) { $Url = "http://localhost:8080" }

# === Logging ===
function Write-Log { param([string]$Message) [Console]::Error.WriteLine("[mcp-bridge] $Message") }

# === State Management ===
function Set-BridgeState {
    param([string]$NewState, [string]$Reason = "")
    $oldState = $script:State
    $script:State = $NewState
    Write-Log "State: $oldState -> $NewState (reason: $Reason)"
}

# === HTTP Client ===
function Invoke-OrchestratorPost {
    param([string]$Body, [int]$TimeoutOverride = $Timeout)
    $headers = @{ "Content-Type" = "application/json" }
    if ($script:SessionId) { $headers["Mcp-Session-Id"] = $script:SessionId }
    try {
        $response = Invoke-WebRequest -Uri "$Url/mcp" -Method POST -Body $Body `
            -Headers $headers -TimeoutSec $TimeoutOverride -UseBasicParsing
        return $response
    } catch {
        return $null
    }
}

function Initialize-Session {
    $body = @{
        jsonrpc = "2.0"; id = 1; method = "initialize"
        params = @{
            protocolVersion = "2025-03-26"; capabilities = @{}
            clientInfo = @{ name = "mcp-bridge-powershell"; version = $script:Version }
        }
    } | ConvertTo-Json -Depth 5 -Compress

    $response = Invoke-OrchestratorPost -Body $body
    if ($response -and $response.StatusCode -eq 200) {
        $data = $response.Content | ConvertFrom-Json
        if ($data.result -or $data.error) {
            if ($response.Headers["Mcp-Session-Id"]) {
                $script:SessionId = $response.Headers["Mcp-Session-Id"]
            }
            Set-BridgeState "CONNECTED" "initialized"
            return $true
        }
    }
    return $false
}

# === Health Check ===
function Start-HealthCheck {
    if ($PingInterval -le 0) { Write-Log "Health check disabled (interval=0)"; return }
    Write-Log "Health check started (interval=${PingInterval}s)"

    $script:HealthCheckJob = Start-Job -ScriptBlock {
        param($Url, $Interval, $PingTimeout, $SessionId)
        $pingId = 0
        while ($true) {
            Start-Sleep -Seconds $Interval
            $pingId++
            $body = "{`"jsonrpc`":`"2.0`",`"id`":$pingId,`"method`":`"ping`"}"
            $headers = @{ "Content-Type" = "application/json" }
            if ($SessionId) { $headers["Mcp-Session-Id"] = $SessionId }
            try {
                $null = Invoke-WebRequest -Uri "$Url/mcp" -Method POST -Body $body `
                    -Headers $headers -TimeoutSec $PingTimeout -UseBasicParsing
            } catch {
                # Signal failure by writing to a temp file
                "PING_FAILED" | Out-File "/tmp/mcp-bridge-health-$($PID).status" -Force
                break
            }
        }
    } -ArgumentList $Url, $PingInterval, $PingTimeout, $script:SessionId
}

function Stop-HealthCheck {
    if ($script:HealthCheckJob) {
        Stop-Job -Job $script:HealthCheckJob -ErrorAction SilentlyContinue
        Remove-Job -Job $script:HealthCheckJob -ErrorAction SilentlyContinue
        $script:HealthCheckJob = $null
    }
}

# === Reconnection ===
function Start-ReconnectLoop {
    if ($NoReconnect) { return }
    Set-BridgeState "RECONNECTING" "starting reconnect"
    $attempt = 0
    while ($true) {
        $delay = [Math]::Min($script:BaseDelay * [Math]::Pow(2, $attempt), $script:MaxDelay)
        Write-Log "Reconnecting in ${delay}s (attempt $attempt)"
        Start-Sleep -Seconds $delay
        if (Initialize-Session) { return }
        $attempt++
    }
}

# === JSON-RPC Helpers ===
function Write-JsonResponse { param([string]$Json) [Console]::Out.WriteLine($Json); [Console]::Out.Flush() }

function New-JsonResponse {
    param($Id, $Result)
    return "{`"jsonrpc`":`"2.0`",`"id`":$Id,`"result`":$($Result | ConvertTo-Json -Depth 10 -Compress)}"
}

function New-JsonError {
    param($Id, [int]$Code, [string]$Message)
    return "{`"jsonrpc`":`"2.0`",`"id`":$Id,`"error`":{`"code`":$Code,`"message`":`"$Message`"}}"
}

# === Tool Handlers ===
function Get-ToolDefinitions {
    $tools = @(
        @{ name = "find_tools"; description = "Search for available tools"; inputSchema = @{ type = "object"; properties = @{ query = @{ type = "string" } }; required = @("query") } }
        @{ name = "execute_dynamic_tool"; description = "Execute a tool on an upstream MCP server"; inputSchema = @{ type = "object"; properties = @{ tool_name = @{ type = "string" }; arguments = @{ type = "object" } }; required = @("tool_name") } }
        @{ name = "toggle_tool"; description = "Enable or disable a tool or server"; inputSchema = @{ type = "object"; properties = @{ tool_name = @{ type = "string" }; server_name = @{ type = "string" }; enabled = @{ type = "boolean" } }; required = @("enabled") } }
        @{ name = "reset_tools"; description = "Reset all toggle states"; inputSchema = @{ type = "object"; properties = @{ server_name = @{ type = "string" } }; required = @() } }
        @{ name = "manage_auto_approve"; description = "Manage auto-approve list"; inputSchema = @{ type = "object"; properties = @{ tool_name = @{ type = "string" }; auto_approve = @{ type = "boolean" } }; required = @("auto_approve") } }
        @{ name = "agent_log"; description = "Write execution log entry"; inputSchema = @{ type = "object"; properties = @{ ticket_key = @{ type = "string" }; agent_name = @{ type = "string" }; step = @{ type = "string" }; status = @{ type = "string" }; message = @{ type = "string" } }; required = @("ticket_key","agent_name","step","status","message") } }
    )
    if (-not $NoLocalTools) {
        $tools += @{ name = "stream_write_file"; description = "Write content to a file on disk"; inputSchema = @{ type = "object"; properties = @{ file_path = @{ type = "string" }; content = @{ type = "string" }; mode = @{ type = "string" } }; required = @("file_path") } }
        $tools += @{ name = "embed_images"; description = "Replace local image references with base64 data URIs"; inputSchema = @{ type = "object"; properties = @{ file_path = @{ type = "string" }; output_path = @{ type = "string" } }; required = @("file_path") } }
    }
    return $tools
}

function Invoke-StreamWriteFile {
    param($Id, $Args)
    $filePath = $Args.file_path
    $content = if ($Args.content) { $Args.content } else { "" }
    $mode = if ($Args.mode) { $Args.mode } else { "write" }

    if (-not $filePath) { Write-JsonResponse (New-JsonError $Id -1 "file_path is required"); return }

    $dir = Split-Path $filePath -Parent
    if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    switch ($mode) {
        "write" { $content | Set-Content -Path $filePath -NoNewline -Encoding UTF8 }
        "append" { $content | Add-Content -Path $filePath -Encoding UTF8 }
        "create" {
            if (Test-Path $filePath) { Write-JsonResponse (New-JsonError $Id -1 "File already exists: $filePath"); return }
            $content | Set-Content -Path $filePath -NoNewline -Encoding UTF8
        }
    }
    $result = @{ content = @(@{ type = "text"; text = "{`"status`":`"ok`",`"path`":`"$filePath`"}" }) }
    Write-JsonResponse (New-JsonResponse $Id $result)
}

function Invoke-ProxyToOrchestrator {
    param($Id, [string]$ToolName, $ToolArgs)
    if ($script:State -ne "CONNECTED") {
        Write-JsonResponse (New-JsonError $Id -1 "Bridge is reconnecting")
        return
    }
    $body = @{ jsonrpc = "2.0"; id = 99; method = "tools/call"; params = @{ name = $ToolName; arguments = $ToolArgs } } | ConvertTo-Json -Depth 10 -Compress
    $response = Invoke-OrchestratorPost -Body $body
    if ($response -and $response.Content) {
        $data = $response.Content | ConvertFrom-Json
        if ($data.error) {
            Write-JsonResponse (New-JsonError $Id $data.error.code $data.error.message)
        } else {
            Write-JsonResponse (New-JsonResponse $Id $data.result)
        }
    } else {
        Write-JsonResponse (New-JsonError $Id -1 "$ToolName failed: no response")
    }
}

# === Main Loop ===
function Start-MainLoop {
    Write-Log "Bridge MCP server ready (stdio transport)"
    while ($true) {
        $line = [Console]::In.ReadLine()
        if ($null -eq $line) { break }
        if ([string]::IsNullOrWhiteSpace($line)) { continue }

        try {
            $request = $line | ConvertFrom-Json
            $method = $request.method
            $id = $request.id

            switch ($method) {
                "initialize" {
                    $result = @{ protocolVersion = "2025-03-26"; capabilities = @{ tools = @{ listChanged = $true } }; serverInfo = @{ name = "mcp-bridge-powershell"; version = $script:Version } }
                    Write-JsonResponse (New-JsonResponse $id $result)
                }
                "initialized" { } # notification
                "tools/list" {
                    $result = @{ tools = Get-ToolDefinitions }
                    Write-JsonResponse (New-JsonResponse $id $result)
                }
                "tools/call" {
                    $toolName = $request.params.name
                    $toolArgs = $request.params.arguments
                    if (-not $toolArgs) { $toolArgs = @{} }
                    switch ($toolName) {
                        "stream_write_file" { Invoke-StreamWriteFile $id $toolArgs }
                        default { Invoke-ProxyToOrchestrator $id $toolName $toolArgs }
                    }
                }
                default {
                    if ($id) { Write-JsonResponse (New-JsonError $id -32601 "Method not found: $method") }
                }
            }
        } catch {
            Write-Log "Error: $_"
        }
    }
}

# === Entry Point ===
Write-Log "MCP Bridge Client (PowerShell) v$script:Version starting..."
Write-Log "Connecting to orchestrator at: $Url"

# Initial connection
$connected = $false
for ($i = 1; $i -le 3; $i++) {
    if (Initialize-Session) { $connected = $true; break }
    $delay = $script:BaseDelay * [Math]::Pow(2, $i - 1)
    Write-Log "Connection attempt $i failed, retrying in ${delay}s"
    Start-Sleep -Seconds $delay
}

if (-not $connected) {
    Write-Log "Failed initial connection, will retry in background"
    Start-ReconnectLoop
}

Start-HealthCheck
Start-MainLoop
Stop-HealthCheck
