# Functional Specification Document (FSD)

## MCPOrchestration — MTO-42: Python Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-42 |
| Title | Python Bridge Client — MCP Orchestrator Connector |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-42.docx |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the functional behavior of the Python MCP Bridge Client — a stdio-based MCP server that proxies tool calls to the MCP Orchestrator via HTTP Streamable transport.

### 1.2 Scope

- stdio JSON-RPC server implementation
- HTTP Streamable client for Orchestrator communication
- Local tool execution (stream_write_file, embed_images)
- Health check and auto-reconnect (per MTO-46)
- Configuration and packaging

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| stdio | Standard input/output — the MCP transport between IDE and bridge |
| httpx | Modern async HTTP client library for Python |
| pyproject.toml | Python packaging configuration (PEP 621) |
| JSON-RPC 2.0 | Remote procedure call protocol used by MCP |

---

## 2. System Overview

### 2.1 System Context

![System Context](diagrams/system-context.png)

The Python bridge sits between the IDE (stdio) and the Orchestrator (HTTP):
- **Input:** JSON-RPC requests from IDE via stdin
- **Output:** JSON-RPC responses to IDE via stdout
- **Upstream:** HTTP POST requests to Orchestrator at `/mcp`
- **Local:** File operations executed directly on filesystem

---

## 3. Functional Requirements

### 3.1 Feature: stdio MCP Server

**Source:** BRD Story 1

#### 3.1.1 Use Case

**Use Case ID:** UC-1
**Actor:** IDE / AI Agent
**Preconditions:** Bridge process started, Orchestrator reachable
**Postconditions:** Tool result returned to IDE

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | IDE | | Writes JSON-RPC request to bridge's stdin |
| 2 | | Bridge | Parses JSON-RPC request |
| 3 | | Bridge | Identifies tool name from request |
| 4 | | Bridge | Routes to local handler or Orchestrator proxy |
| 5 | | Bridge | Receives result |
| 6 | | Bridge | Writes JSON-RPC response to stdout |
| 7 | IDE | | Reads response from bridge's stdout |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-1 | Tool is local (stream_write_file, embed_images) | Execute locally, skip HTTP call |
| AF-2 | Request is `tools/list` | Return static tool definitions |
| AF-3 | Request is `initialize` | Return server capabilities |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-1 | Bridge disconnected from Orchestrator | Return error: "Bridge is reconnecting" |
| EF-2 | Invalid JSON-RPC request | Return JSON-RPC parse error |
| EF-3 | Orchestrator returns error | Forward error to IDE |

#### 3.1.2 Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-1 | Bridge exposes exactly 8 tools in tools/list | BRD AC-3 |
| BR-2 | find_tools and execute_dynamic_tool proxy to Orchestrator | BRD AC-4 |
| BR-3 | Local tools execute without network | BRD AC-18 |
| BR-4 | All responses are valid JSON-RPC 2.0 | MCP spec |

#### 3.1.3 Tool Definitions

| # | Tool Name | Type | Description |
|---|-----------|------|-------------|
| 1 | find_tools | Proxy | Search for available tools by query |
| 2 | execute_dynamic_tool | Proxy | Execute a tool on upstream server |
| 3 | toggle_tool | Proxy | Enable/disable a tool for session |
| 4 | reset_tools | Proxy | Reset all toggle states |
| 5 | manage_auto_approve | Proxy | Manage auto-approve list |
| 6 | agent_log | Proxy | Write execution log entry |
| 7 | stream_write_file | Local | Write content to file |
| 8 | embed_images | Local | Embed images as base64 in markdown |

---

### 3.2 Feature: HTTP Streamable Client

**Source:** BRD Story 1

#### 3.2.1 Use Case

**Use Case ID:** UC-2
**Actor:** Bridge (internal)
**Preconditions:** Orchestrator URL configured
**Postconditions:** HTTP session established

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | | Bridge | Create httpx AsyncClient |
| 2 | | Bridge | Send initialize request to Orchestrator |
| 3 | | Orchestrator | Returns capabilities + Mcp-Session-Id |
| 4 | | Bridge | Store session ID for subsequent requests |
| 5 | | Bridge | Transition to CONNECTED state |

#### 3.2.2 API Contract

**Request to Orchestrator:**

```
POST {ORCHESTRATOR_URL}
Content-Type: application/json
Mcp-Session-Id: {session_id}  (after init)

{
  "jsonrpc": "2.0",
  "id": N,
  "method": "tools/call",
  "params": {
    "name": "find_tools",
    "arguments": { "query": "..." }
  }
}
```

**Response:**

```json
{
  "jsonrpc": "2.0",
  "id": N,
  "result": {
    "content": [{ "type": "text", "text": "..." }]
  }
}
```

---

### 3.3 Feature: Local Tools

**Source:** BRD Story 4

#### 3.3.1 stream_write_file

**Input:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| file_path | string | Yes | Absolute or relative path |
| content | string | No | Text to write |
| mode | string | No | "write" (default), "append", "create" |
| encoding | string | No | "utf-8" (default) |

**Output:**

```json
{
  "file_path": "/abs/path/to/file",
  "bytes_written": 1234,
  "total_size": 5678,
  "mode": "write"
}
```

#### 3.3.2 embed_images

**Input:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| file_path | string | Yes | Path to markdown file |
| output_path | string | No | Output path (default: overwrite original) |

**Output:**

```json
{
  "file_path": "/abs/path/to/file.md",
  "images_embedded": 3,
  "total_size": 45678
}
```

---

### 3.4 Feature: Health Check & Auto-Reconnect

**Source:** BRD Story 3, MTO-46

Implements the health check protocol defined in MTO-46 FSD:
- Ping every 30s (configurable)
- State machine: CONNECTED ↔ DISCONNECTED ↔ RECONNECTING
- Exponential backoff: 1s, 2s, 4s, 8s, 15s (cap)
- Logging to stderr

---

### 3.5 Feature: Configuration

**Source:** BRD Story 5

#### 3.5.1 Configuration Matrix

| Parameter | CLI Flag | Env Variable | Default |
|-----------|----------|--------------|---------|
| Orchestrator URL | `--url` | `ORCHESTRATOR_URL` | `http://localhost:8080/mcp` |
| Request timeout | `--timeout` | `REQUEST_TIMEOUT` | `30000` (ms) |
| Ping interval | `--ping-interval` | `PING_INTERVAL` | `30000` (ms) |
| Ping timeout | `--ping-timeout` | `PING_TIMEOUT` | `5000` (ms) |
| Reconnect delay | `--reconnect-delay` | `RECONNECT_DELAY` | `1000` (ms) |
| Max reconnect delay | `--max-reconnect-delay` | `MAX_RECONNECT_DELAY` | `15000` (ms) |
| Enable local tools | `--enable-local-tools` | `ENABLE_LOCAL_TOOLS` | `true` |

---

## 4. Data Model

No persistent storage. All state is in-memory:
- Connection state (enum)
- Session ID (string)
- Ping counters (integers)
- Configuration (dataclass)

---

## 5. Processing Logic

### 5.1 Main Loop (asyncio)

```python
async def main():
    config = parse_config()
    http_client = HttpStreamableClient(config)
    health_check = HealthCheckManager(config, http_client)
    bridge = BridgeServer(config, http_client, health_check)
    
    await http_client.connect()
    health_check.start()
    await bridge.run()  # reads stdin forever
```

---

## 6. Testing Considerations

| ID | Scenario | Priority |
|----|----------|----------|
| TC-1 | Bridge starts and connects to Orchestrator | High |
| TC-2 | find_tools proxied correctly | High |
| TC-3 | execute_dynamic_tool proxied correctly | High |
| TC-4 | stream_write_file creates/appends file | High |
| TC-5 | embed_images replaces image refs | Medium |
| TC-6 | Health check detects server down | High |
| TC-7 | Auto-reconnect succeeds after server restart | High |
| TC-8 | Tool call during reconnect returns error | High |
| TC-9 | Invalid config exits with error | Medium |
| TC-10 | pip install creates CLI command | Medium |

---

## 7. Appendix

### Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence — Tool Call | [sequence-tool-call.png](diagrams/sequence-tool-call.png) | [sequence-tool-call.drawio](diagrams/sequence-tool-call.drawio) |
| 3 | State Diagram | [state-bridge.png](diagrams/state-bridge.png) | [state-bridge.drawio](diagrams/state-bridge.drawio) |
