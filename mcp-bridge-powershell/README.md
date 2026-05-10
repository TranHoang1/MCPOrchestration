# MCP Bridge Client — PowerShell

PowerShell bridge client connecting to MCP Orchestrator via HTTP Streamable transport.

## Requirements

- PowerShell 7+ (cross-platform) or Windows PowerShell 5.1

## Usage

```powershell
# Default (connects to localhost:8080)
pwsh mcp-bridge.ps1

# Custom URL
pwsh mcp-bridge.ps1 -Url http://remote:9090

# Disable health check
pwsh mcp-bridge.ps1 -PingInterval 0

# Custom timeout
pwsh mcp-bridge.ps1 -Timeout 60
```

## Configuration

| Parameter | Env Var | Default | Description |
|-----------|---------|---------|-------------|
| -Url | ORCHESTRATOR_URL | http://localhost:8080 | Orchestrator URL |
| -Timeout | — | 30 | Request timeout (seconds) |
| -PingInterval | — | 30 | Health check interval (seconds, 0=disabled) |
| -PingTimeout | — | 5 | Ping timeout (seconds) |
| -NoReconnect | — | false | Disable auto-reconnect |
| -NoLocalTools | — | false | Disable local tools |

## MCP Client Configuration

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "pwsh",
      "args": ["-File", "mcp-bridge.ps1", "-Url", "http://localhost:8080"]
    }
  }
}
```
