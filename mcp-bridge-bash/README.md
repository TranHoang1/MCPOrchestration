# MCP Bridge Client — Bash

Single-file Bash bridge client connecting to MCP Orchestrator via HTTP Streamable transport.

## Requirements

- Bash 4.0+
- curl 7.x+
- jq 1.6+

## Installation

```bash
chmod +x mcp-bridge.sh
cp mcp-bridge.sh /usr/local/bin/
```

## Usage

```bash
# Default (connects to localhost:8080)
./mcp-bridge.sh

# Custom URL
./mcp-bridge.sh --url http://remote:9090

# Disable health check
./mcp-bridge.sh --ping-interval 0

# Custom timeout
./mcp-bridge.sh --timeout 60
```

## Configuration

| Flag | Env Var | Default | Description |
|------|---------|---------|-------------|
| --url | ORCHESTRATOR_URL | http://localhost:8080 | Orchestrator URL |
| --timeout | BRIDGE_TIMEOUT | 30 | Request timeout (seconds) |
| --ping-interval | BRIDGE_PING_INTERVAL | 30 | Health check interval (seconds, 0=disabled) |
| --ping-timeout | BRIDGE_PING_TIMEOUT | 5 | Ping timeout (seconds) |
| --no-reconnect | — | — | Disable auto-reconnect |
| --no-local-tools | — | — | Disable local tools |

## MCP Client Configuration

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "/usr/local/bin/mcp-bridge.sh",
      "args": ["--url", "http://localhost:8080"]
    }
  }
}
```
