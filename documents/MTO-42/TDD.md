# Technical Design Document (TDD)

## MCPOrchestration — MTO-42: Python Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-42 |
| Title | Python Bridge Client — MCP Orchestrator Connector |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-42.docx |
| Related FSD | FSD-v1-MTO-42.docx |

---

## 1. Introduction

### 1.1 Purpose

Technical design for the Python MCP Bridge Client — specifying package structure, class design, async patterns, and implementation details.

### 1.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Python | 3.10+ |
| HTTP Client | httpx | 0.27+ |
| Async | asyncio | stdlib |
| Packaging | pyproject.toml | PEP 621 |
| CLI | argparse | stdlib |
| Testing | pytest + pytest-asyncio | latest |

### 1.3 Design Principles

- Mirror Node.js bridge behavior exactly (cross-client consistency per MTO-46)
- Use asyncio for non-blocking I/O (stdin + HTTP concurrently)
- Minimal dependencies (httpx only, rest is stdlib)
- Type hints throughout (mypy compatible)

---

## 2. System Architecture

### 2.1 Architecture Overview

![Architecture Diagram](diagrams/architecture.png)

```
mcp-bridge-python/
├── src/
│   └── mcp_bridge/
│       ├── __init__.py
│       ├── __main__.py          # Entry point (python -m mcp_bridge)
│       ├── bridge_server.py     # stdio MCP server (asyncio)
│       ├── http_client.py       # HTTP Streamable client (httpx)
│       ├── health_check.py      # Health check manager (MTO-46)
│       ├── reconnection.py      # Reconnection with backoff
│       ├── local_tools.py       # stream_write_file, embed_images
│       ├── config.py            # Configuration parsing
│       └── models.py            # Dataclasses, enums
├── tests/
│   ├── test_bridge_server.py
│   ├── test_http_client.py
│   ├── test_health_check.py
│   ├── test_local_tools.py
│   └── test_config.py
├── pyproject.toml
├── README.md
└── .gitignore
```

### 2.2 Component Diagram

![Component Diagram](diagrams/component.png)

| Component | File | Responsibility |
|-----------|------|---------------|
| BridgeServer | bridge_server.py | Read stdin, route requests, write stdout |
| HttpStreamableClient | http_client.py | HTTP POST to Orchestrator, session management |
| HealthCheckManager | health_check.py | Periodic ping, state transitions |
| ReconnectionManager | reconnection.py | Exponential backoff reconnect loop |
| LocalTools | local_tools.py | stream_write_file, embed_images |
| Config | config.py | CLI args + env vars + defaults |
| Models | models.py | BridgeState enum, dataclasses |

---

## 3. Class Design

### 3.1 BridgeServer

```python
class BridgeServer:
    """stdio MCP server — reads JSON-RPC from stdin, writes to stdout."""
    
    def __init__(self, config: BridgeConfig, http_client: HttpStreamableClient,
                 health_check: HealthCheckManager):
        self._config = config
        self._http_client = http_client
        self._health_check = health_check
        self._workspace_root: str | None = None
    
    async def run(self) -> None:
        """Main loop: read stdin line by line, process, write stdout."""
    
    async def _handle_request(self, request: dict) -> dict:
        """Route request to appropriate handler."""
    
    async def _handle_initialize(self, request: dict) -> dict:
        """Handle MCP initialize request."""
    
    async def _handle_tools_list(self) -> dict:
        """Return tool definitions."""
    
    async def _handle_tool_call(self, name: str, args: dict) -> dict:
        """Route tool call to local or proxy handler."""
    
    def _get_tool_definitions(self) -> list[dict]:
        """Return list of 8 tool definitions."""
```

### 3.2 HttpStreamableClient

```python
class HttpStreamableClient:
    """Async HTTP client for Orchestrator communication."""
    
    def __init__(self, config: BridgeConfig):
        self._config = config
        self._client: httpx.AsyncClient | None = None
        self._session_id: str | None = None
        self._connected = False
    
    async def initialize(self) -> bool:
        """Send initialize request, store session ID."""
    
    async def call_tool(self, name: str, arguments: dict) -> dict:
        """Send tools/call request to Orchestrator."""
    
    async def send_ping(self) -> bool:
        """Send ping request for health check."""
    
    def reset_session(self) -> None:
        """Clear session ID for reconnection."""
    
    async def close(self) -> None:
        """Close HTTP client."""
    
    @property
    def is_connected(self) -> bool:
        return self._connected
```

### 3.3 HealthCheckManager

```python
class HealthCheckManager:
    """Periodic ping + state management (per MTO-46 spec)."""
    
    def __init__(self, config: BridgeConfig, http_client: HttpStreamableClient,
                 reconnection: ReconnectionManager):
        self._config = config
        self._http_client = http_client
        self._reconnection = reconnection
        self._state = BridgeState.DISCONNECTED
        self._consecutive_failures = 0
        self._task: asyncio.Task | None = None
    
    def start(self) -> None:
        """Start health check asyncio task."""
    
    def stop(self) -> None:
        """Cancel health check task."""
    
    async def _ping_loop(self) -> None:
        """Main ping loop: sleep → ping → check."""
    
    def _on_ping_success(self) -> None:
        """Reset failure counter."""
    
    def _on_ping_failure(self) -> None:
        """Increment failures, maybe trigger reconnect."""
    
    @property
    def state(self) -> BridgeState:
        return self._state
```

### 3.4 Models

```python
from enum import Enum
from dataclasses import dataclass

class BridgeState(Enum):
    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    CONNECTED = "CONNECTED"
    RECONNECTING = "RECONNECTING"

@dataclass
class BridgeConfig:
    orchestrator_url: str = "http://localhost:8080/mcp"
    request_timeout_ms: int = 30000
    ping_interval_ms: int = 30000
    ping_timeout_ms: int = 5000
    base_reconnect_delay_ms: int = 1000
    max_reconnect_delay_ms: int = 15000
    enable_local_tools: bool = True
    workspace_root: str | None = None
```

---

## 4. Configuration

### 4.1 pyproject.toml

```toml
[project]
name = "mcp-bridge-python"
version = "1.0.0"
description = "MCP Bridge Client for Python — connects to MCP Orchestrator via HTTP Streamable"
requires-python = ">=3.10"
dependencies = ["httpx>=0.27"]

[project.scripts]
mcp-bridge-python = "mcp_bridge.__main__:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"
```

### 4.2 CLI Parsing

```python
def parse_config() -> BridgeConfig:
    parser = argparse.ArgumentParser(description="MCP Bridge Client (Python)")
    parser.add_argument("--url", default=None, help="Orchestrator URL")
    parser.add_argument("--timeout", type=int, default=None, help="Request timeout (ms)")
    parser.add_argument("--ping-interval", type=int, default=None, help="Ping interval (ms)")
    parser.add_argument("--ping-timeout", type=int, default=None, help="Ping timeout (ms)")
    args = parser.parse_args()
    
    # Precedence: CLI > env > default
    url = args.url or os.environ.get("ORCHESTRATOR_URL", "http://localhost:8080/mcp")
    timeout = args.timeout or int(os.environ.get("REQUEST_TIMEOUT", "30000"))
    ping_interval = args.ping_interval or int(os.environ.get("PING_INTERVAL", "30000"))
    
    return BridgeConfig(orchestrator_url=url, request_timeout_ms=timeout, ...)
```

---

## 5. Async Architecture

### 5.1 Event Loop Design

```python
async def main():
    config = parse_config()
    
    async with httpx.AsyncClient(timeout=config.request_timeout_ms/1000) as client:
        http_client = HttpStreamableClient(config, client)
        reconnection = ReconnectionManager(config, http_client)
        health_check = HealthCheckManager(config, http_client, reconnection)
        bridge = BridgeServer(config, http_client, health_check)
        
        # Connect to Orchestrator
        connected = await reconnection.connect_with_retry()
        if connected:
            health_check.start()
        
        # Run stdio server (blocks until stdin closes)
        await bridge.run()
```

### 5.2 stdin/stdout Handling

```python
async def _read_stdin(self) -> AsyncIterator[str]:
    """Read lines from stdin asynchronously."""
    loop = asyncio.get_event_loop()
    reader = asyncio.StreamReader()
    protocol = asyncio.StreamReaderProtocol(reader)
    await loop.connect_read_pipe(lambda: protocol, sys.stdin.buffer)
    
    while True:
        line = await reader.readline()
        if not line:
            break
        yield line.decode("utf-8").strip()
```

---

## 6. Implementation Checklist

### Files to Create

| # | File | Description |
|---|------|-------------|
| 1 | `mcp-bridge-python/src/mcp_bridge/__init__.py` | Package init |
| 2 | `mcp-bridge-python/src/mcp_bridge/__main__.py` | Entry point |
| 3 | `mcp-bridge-python/src/mcp_bridge/bridge_server.py` | stdio MCP server |
| 4 | `mcp-bridge-python/src/mcp_bridge/http_client.py` | HTTP Streamable client |
| 5 | `mcp-bridge-python/src/mcp_bridge/health_check.py` | Health check (MTO-46) |
| 6 | `mcp-bridge-python/src/mcp_bridge/reconnection.py` | Reconnection manager |
| 7 | `mcp-bridge-python/src/mcp_bridge/local_tools.py` | Local tools |
| 8 | `mcp-bridge-python/src/mcp_bridge/config.py` | Configuration |
| 9 | `mcp-bridge-python/src/mcp_bridge/models.py` | Dataclasses, enums |
| 10 | `mcp-bridge-python/pyproject.toml` | Package metadata |
| 11 | `mcp-bridge-python/tests/test_*.py` | Test files |

---

## 7. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
| 3 | Class Diagram | [class-diagram.png](diagrams/class-diagram.png) | [class-diagram.drawio](diagrams/class-diagram.drawio) |
