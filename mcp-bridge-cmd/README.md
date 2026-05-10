# MCP Bridge Client — Windows CMD

Minimal Windows CMD bridge client connecting to MCP Orchestrator via HTTP Streamable transport.

## Requirements

- Windows 10+ (curl.exe bundled)
- jq.exe (optional, for full JSON parsing)

## Usage

```cmd
:: Default (connects to localhost:8080)
mcp-bridge.cmd

:: Custom URL
mcp-bridge.cmd --url http://remote:9090

:: Custom timeout
mcp-bridge.cmd --timeout 60
```

## Configuration

| Flag | Env Var | Default | Description |
|------|---------|---------|-------------|
| --url | ORCHESTRATOR_URL | http://localhost:8080 | Orchestrator URL |
| --timeout | BRIDGE_TIMEOUT | 30 | Request timeout (seconds) |
| --ping-interval | BRIDGE_PING_INTERVAL | 30 | Health check interval (seconds) |

## Limitations

- CMD has no native async — health check runs in main loop between requests
- JSON parsing requires jq.exe for full functionality
- Without jq, only basic tool routing works (find_tools, execute_dynamic_tool)
- For full feature set, use PowerShell or Node.js bridge instead

## MCP Client Configuration

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "cmd",
      "args": ["/c", "mcp-bridge.cmd", "--url", "http://localhost:8080"]
    }
  }
}
```
