# MCP Bridge Client — Python

Connects to a remote MCP Orchestrator via HTTP Streamable transport and exposes tools locally via stdio MCP server.

## Requirements

- Python 3.10+
- httpx

## Installation

```bash
pip install -e .
```

## Usage

```bash
# Default (connects to localhost:8080)
python -m mcp_bridge

# Custom URL
python -m mcp_bridge --url http://remote:9090

# Disable health check
python -m mcp_bridge --ping-interval 0

# Custom timeout
python -m mcp_bridge --timeout 60000
```

## Configuration

| Flag | Env Var | Default | Description |
|------|---------|---------|-------------|
| --url | ORCHESTRATOR_URL | http://localhost:8080 | Orchestrator URL |
| --timeout | BRIDGE_TIMEOUT | 30000 | Request timeout (ms) |
| --ping-interval | BRIDGE_PING_INTERVAL | 30000 | Health check interval (ms, 0=disabled) |
| --ping-timeout | BRIDGE_PING_TIMEOUT | 5000 | Ping timeout (ms) |
| --no-reconnect | — | false | Disable auto-reconnect |
| --no-local-tools | — | false | Disable local tools |

## MCP Client Configuration

Add to your MCP client config (e.g., Claude Desktop):

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "python",
      "args": ["-m", "mcp_bridge", "--url", "http://localhost:8080"]
    }
  }
}
```
