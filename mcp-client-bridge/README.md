# MCP Client Bridge (Node.js)

A TypeScript/Node.js MCP Client Bridge that connects to a remote MCP Orchestrator via HTTP Streamable transport and exposes tools locally via stdio.

## Requirements

- Node.js 20+
- MCP Orchestrator running with HTTP Streamable transport

## Installation

```bash
npm install
npm run build
```

## Usage

### Via npx (recommended)

```bash
npx @orchestrator/mcp-bridge --url http://localhost:8080
```

### Via environment variable

```bash
export ORCHESTRATOR_URL=http://localhost:8080
npx @orchestrator/mcp-bridge
```

### CLI Options

| Flag | Description | Default |
|------|-------------|---------|
| `--url <url>` | Orchestrator URL | `http://localhost:8080` |
| `--timeout <ms>` | Request timeout in ms | `30000` |
| `--no-reconnect` | Disable auto-reconnect | enabled |
| `--no-local-write` | Disable local stream_write_file | enabled |

### Environment Variables

| Variable | Description |
|----------|-------------|
| `ORCHESTRATOR_URL` | Orchestrator URL (fallback if --url not provided) |
| `BRIDGE_TIMEOUT` | Request timeout in ms |

## Architecture

```
IDE (Kiro/Cursor) <--stdio--> Bridge <--HTTP POST /mcp--> Orchestrator
```

The bridge exposes 2 meta-tools initially:
- `find_tools` — Search for available tools
- `execute_dynamic_tool` — Execute a discovered tool

Plus a local tool:
- `stream_write_file` — Write files to local disk

## Development

```bash
npm run dev    # Run with ts-node
npm run build  # Compile TypeScript
npm test       # Run tests
```
