# Bridge Clients — Configuration Guide

## Overview

MCP Bridge Clients connect AI agents (Kiro, Claude, Cursor, etc.) to the MCP Orchestrator Server via HTTP Streamable transport. Each bridge runs as a stdio MCP server locally and proxies tool calls to the remote Orchestrator.

**Available bridge clients:**

| Client | Language | Use Case |
|--------|----------|----------|
| `orchestrator-bridge` | Kotlin (JVM) | Primary — full features, shadow JAR |
| `mcp-client-bridge` | Node.js (TypeScript) | npm package, cross-platform |
| `mcp-bridge-python` | Python | pip package, minimal dependencies |
| `mcp-bridge-powershell` | PowerShell | Windows-native, no build step |
| `mcp-bridge-bash` | Bash | Linux/macOS, requires `jq` + `curl` |

All clients share identical configuration interface (CLI args + env vars) and expose the same MCP tools.

---

## Feature Comparison & Selection Guide

### Feature Matrix

| Feature | Kotlin (JVM) | Node.js | Python | PowerShell | Bash | CMD |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| FTS5 Code Search | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ollama Embeddings (Layer 2) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| AI Summarization (Layer 3) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Background File Watcher | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Multi-URL Failover | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Auto-Reconnect + Backoff | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ basic |
| Health Check (Ping) | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ polling |
| JWT Authentication | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `stream_write_file` (local) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `embed_images` (local) | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Shadow JAR (single file deploy) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Single file (no install) | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| Zero external dependencies | ❌ | ❌ | ✅ | ✅ | ✅ | ⚠️ jq + sqlite3 |
| No build step required | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| Async/concurrent operations | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |

> ⚠️ = Partial support with limitations

### Pros & Cons

| Client | Pros | Cons | Best For |
|--------|------|------|----------|
| **Kotlin** | Fastest performance; full 3-layer code intelligence; single JAR deployment; coroutine-based concurrency; production-grade | Requires JDK 21+; largest binary (~30MB JAR); slower cold start (~3s) | Production environments; large workspaces (10K+ files); teams needing semantic search |
| **Node.js** | Cross-platform; npm ecosystem; fast startup (~1s); TypeScript type safety; easy to extend | Requires Node.js 20+; `better-sqlite3` needs native compilation; `node_modules` size | Kiro/Cursor integration; frontend-heavy teams; rapid prototyping |
| **Python** | Zero external deps (stdlib sqlite3); simple codebase; easy to modify; pip installable | Slower than Kotlin/Node for large indexes; GIL limits concurrency; no native file watcher | Python-centric teams; environments where pip is standard; lightweight setups |
| **PowerShell** | No build step; native Windows integration; runs anywhere PowerShell 7+ exists; script-level customization | FTS5 only (no embeddings); requires `sqlite3` CLI; slower JSON parsing; no background indexing | Windows sysadmins; quick setup on Windows; environments without JDK/Node |
| **Bash** | No build step; minimal footprint; runs on any Linux/macOS; easy to audit (single file) | FTS5 only (no embeddings); requires `jq` + `curl` + `sqlite3`; no background indexing; no Windows | Linux servers; Docker containers; CI/CD pipelines; minimal environments |
| **CMD** | Absolute minimum — single .cmd file (~8KB); works on any Windows 10+; no install needed; curl.exe bundled; FTS5 code search with sqlite3.exe | No async, no embed_images, no background indexing; needs jq.exe + sqlite3.exe for full functionality; health check is synchronous polling | Legacy Windows where PowerShell is restricted; ultra-minimal setups; quick smoke testing |

### Decision Flowchart

```
Need semantic search (Ollama embeddings)?
├── Yes → Need best performance?
│         ├── Yes → Kotlin
│         └── No  → Already have Node.js? → Node.js
│                   Already have Python?  → Python
└── No (FTS5 keyword search is enough)
    ├── Need code intelligence at all?
    │   ├── Yes → Windows? → PowerShell
    │   │         Linux/macOS? → Bash
    │   │         Want npm? → Node.js
    │   └── No (just tool proxy)
    │       ├── Legacy Windows (no PowerShell)? → CMD
    │       ├── Windows? → PowerShell
    │       └── Linux/macOS? → Bash
    └── Want npm package? → Node.js
```

### Performance Benchmarks (approximate)

| Metric | Kotlin | Node.js | Python | PowerShell | Bash | CMD |
|--------|--------|---------|--------|------------|------|-----|
| Cold start | ~3s | ~1s | ~0.5s | ~1s | ~0.2s | ~0.1s |
| Index 5000 files | ~45s | ~55s | ~70s | ~120s | ~150s | ~180s |
| FTS5 search (100K symbols) | <100ms | <150ms | <200ms | <500ms | <800ms | <1000ms |
| Memory (idle) | ~80MB | ~50MB | ~30MB | ~40MB | ~5MB | ~2MB |
| Memory (10K files indexed) | ~200MB | ~150MB | ~120MB | N/A | N/A | N/A |
| Binary/install size | ~30MB | ~60MB (node_modules) | ~1MB | ~50KB | ~15KB | ~12KB |

---

## Quick Start

### Node.js (recommended for Kiro/Cursor)

```bash
cd mcp-client-bridge
npm ci && npm run build
node dist/index.js --url http://localhost:8080 --token <JWT>
```

### Kotlin (recommended for production)

```bash
java -jar mcp-bridge-all.jar --url http://localhost:8080 --token <JWT>
```

### Python

```bash
pip install -e mcp-bridge-python/
python -m mcp_bridge --url http://localhost:8080 --token <JWT>
```

### PowerShell

```powershell
pwsh mcp-bridge-powershell/mcp-bridge.ps1 -Url http://localhost:8080 -Token <JWT>
```

### Bash

```bash
chmod +x mcp-bridge-bash/mcp-bridge.sh
./mcp-bridge-bash/mcp-bridge.sh --url http://localhost:8080 --token <JWT>
```

### CMD (Windows minimal)

```cmd
mcp-bridge-cmd\mcp-bridge.cmd --url http://localhost:8080
```

---

## Configuration Reference

### Priority Order

```
CLI arguments  >  Environment variables  >  Defaults
```

### Common Parameters (All Clients)

| Parameter | CLI | Env Var | Default | Description |
|-----------|-----|---------|---------|-------------|
| Orchestrator URL(s) | `--url` | `ORCHESTRATOR_URLS` or `ORCHESTRATOR_URL` | `http://localhost:8080` | Comma-separated list of URLs |
| JWT Token | `--token` | `MCP_BRIDGE_TOKEN` | (none) | Authentication token |
| Request Timeout | `--timeout` | `BRIDGE_TIMEOUT` | 30000ms | HTTP request timeout |
| Ping Interval | `--ping-interval` | `BRIDGE_PING_INTERVAL` | 30000ms | Health check interval (0 = disabled) |
| Ping Timeout | `--ping-timeout` | `BRIDGE_PING_TIMEOUT` | 5000ms | Ping request timeout |
| Disable Reconnect | `--no-reconnect` | — | false | Disable auto-reconnection |
| Disable Local Tools | `--no-local-write` / `--no-local-tools` | — | false | Disable stream_write_file |

### URL Configuration

URLs support multi-endpoint failover. Provide comma-separated list:

```bash
# Single URL
--url http://localhost:8080

# Multi-URL failover
--url "http://primary:8080,http://secondary:8080,http://tertiary:8080"
```

**Rules:**
- Only `http://` and `https://` protocols accepted
- Maximum 10 URLs (excess truncated with warning)
- Empty strings and invalid protocols silently filtered
- At least 1 valid URL required (exits with error otherwise)

### Authentication

Token must be a valid JWT (3 base64url parts separated by `.`):

```bash
--token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.abc123
```

Bridge validates format on startup and exits immediately if invalid. Running without token is allowed but logs a warning.

---

## Failover & Reconnection

All bridge clients implement identical 3-phase reconnection logic:

### Phase 1 — Retry Active URL

- Retries current URL up to 3 times (`maxRetryBeforeRotate`)
- Exponential backoff: 1s → 2s → 4s (capped at 15s)

### Phase 2 — Rotate URLs

- If multiple URLs configured, tries each remaining URL once
- Marks failed URLs and skips them
- Reports all errors if all URLs exhausted

### Phase 3 — Infinite Backoff

- Resets to first URL
- Retries indefinitely with exponential backoff (max 15s)
- Reconnects automatically when server becomes available

### Health Check

- Periodic ping to verify connectivity (default: every 30s)
- On ping failure → triggers reconnection loop
- Set `--ping-interval 0` to disable

---

## Kiro MCP Configuration

Add bridge to `~/.kiro/settings/mcp.json` or workspace `.kiro/settings/mcp.json`:

### Node.js Bridge

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "node",
      "args": [
        "/path/to/mcp-client-bridge/dist/index.js",
        "--url", "http://localhost:8080",
        "--token", "eyJ..."
      ],
      "disabled": false,
      "autoApprove": ["find_tools", "execute_dynamic_tool", "agent_log"]
    }
  }
}
```

### Kotlin Bridge

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "java",
      "args": [
        "-jar", "/path/to/mcp-bridge-all.jar",
        "--url", "http://localhost:8080,http://backup:8080",
        "--token", "eyJ..."
      ],
      "disabled": false,
      "autoApprove": ["find_tools", "execute_dynamic_tool", "agent_log"]
    }
  }
}
```

### Python Bridge

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "python",
      "args": [
        "-m", "mcp_bridge",
        "--url", "http://localhost:8080",
        "--token", "eyJ..."
      ],
      "disabled": false,
      "autoApprove": ["find_tools", "execute_dynamic_tool", "agent_log"]
    }
  }
}
```

### PowerShell Bridge

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "pwsh",
      "args": [
        "/path/to/mcp-bridge.ps1",
        "-Url", "http://localhost:8080",
        "-Token", "eyJ..."
      ],
      "disabled": false,
      "autoApprove": ["find_tools", "execute_dynamic_tool"]
    }
  }
}
```

### Bash Bridge

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "bash",
      "args": [
        "/path/to/mcp-bridge.sh",
        "--url", "http://localhost:8080",
        "--token", "eyJ..."
      ],
      "disabled": false,
      "autoApprove": ["find_tools", "execute_dynamic_tool"]
    }
  }
}
```

---

## Exposed MCP Tools

All bridge clients expose these tools to connected agents:

### Core Tools (All Clients)

| Tool | Description | Local/Remote |
|------|-------------|--------------|
| `find_tools` | Semantic search for available tools on orchestrator | Remote |
| `execute_dynamic_tool` | Execute any tool on upstream MCP servers | Remote |
| `toggle_tool` | Enable/disable a tool or server for current session | Remote |
| `reset_tools` | Reset all toggle states to defaults | Remote |
| `manage_auto_approve` | Add/remove tools from auto-approve list | Remote |
| `agent_log` | Write execution log entry for agent tracking | Remote |
| `stream_write_file` | Write content directly to local file | Local |
| `embed_images` | Embed images as base64 in markdown files | Local |

### Code Intelligence Tools (Tier 1 + Tier 2)

| Tool | Description | Tier 1 | Tier 2 | CMD |
|------|-------------|:---:|:---:|:---:|
| `code_search` | FTS5 keyword search across indexed symbols (BM25 ranking). Filters: language, module, limit | ✅ | ✅ | ✅ |
| `code_symbols` | List all symbols (classes, functions, interfaces) in a specific file with line numbers | ✅ | ✅ | ✅ |
| `code_context` | Semantic search via Ollama embeddings; falls back to FTS5 if Ollama unavailable | ✅ | ❌ | ❌ |
| `code_modules` | List detected project modules with file/symbol counts and optional AI summaries | ✅ | ✅ | ✅ |
| `code_index_status` | Real-time index health: files indexed, symbols, available layers, DB size, progress | ✅ | ✅ | ✅ |

**Tier 1** (Kotlin, Node.js, Python): Full 3-layer code intelligence — FTS5 + embeddings + summarization.
**Tier 2** (PowerShell, Bash, CMD): FTS5 keyword search only — no embeddings, no `code_context` semantic mode.
**CMD requirement:** `sqlite3.exe` must be on PATH. If not found, code intelligence tools are not registered.

**Local tools** execute on the machine running the bridge (no network call).
**Remote tools** proxy to the Orchestrator server.
**Code Intelligence tools** query the local per-workspace SQLite index (`.bridge/code-index.db`).

---

## Client-Specific Notes

### Kotlin Bridge

- Requires JDK 21+
- Distributed as shadow JAR (`mcp-bridge-all.jar`)
- Includes Code Intelligence module (SQLite + Ollama)
- Best performance for large workspaces

### Node.js Bridge

- Requires Node.js 20+
- Install: `npm ci && npm run build`
- Published to npm as `mcp-orchestrator-bridge`
- Includes Code Intelligence module (better-sqlite3)

### Python Bridge

- Requires Python 3.11+
- Zero external dependencies for core (stdlib only)
- Install: `pip install -e mcp-bridge-python/`
- Includes Code Intelligence module (sqlite3 stdlib)

### PowerShell Bridge

- Requires PowerShell 7+ (pwsh)
- No build step — run `.ps1` directly
- Tier 2: FTS5 code search only (no embeddings)
- Requires `sqlite3` CLI for code intelligence

### Bash Bridge

- Requires Bash 4+, `jq`, `curl`
- No build step — run `.sh` directly
- Tier 2: FTS5 code search only (no embeddings)
- Requires `sqlite3` CLI for code intelligence

### CMD Bridge

- Requires Windows 10+ (curl.exe bundled)
- No build step — single `.cmd` file (~8KB) + `code-intel.cmd` module
- Tier 2: FTS5 code search (requires `sqlite3.exe` on PATH)
- Requires `jq.exe` for full JSON parsing (basic proxy mode without it)
- No `code_context` (semantic search not supported)

#### Full Mode (with jq + sqlite3)

All features work: argument parsing, `stream_write_file`, proper JSON-RPC routing, **code intelligence** (FTS5 search).

**Install jq (pick one):**

```cmd
:: Option 1: winget (Windows 11 / Windows 10 with App Installer)
winget install jqlang.jq

:: Option 2: scoop
scoop install jq

:: Option 3: chocolatey
choco install jq

:: Option 4: Manual download
:: Download from https://jqlang.github.io/jq/download/
:: Place jq.exe in a directory on your PATH (e.g., C:\Windows or C:\tools)
```

**Install sqlite3 (pick one):**

```cmd
:: Option 1: winget
winget install SQLite.SQLite

:: Option 2: scoop
scoop install sqlite

:: Option 3: chocolatey
choco install sqlite

:: Option 4: Manual download
:: Download "sqlite-tools-win-x64" from https://sqlite.org/download.html
:: Extract sqlite3.exe to a directory on your PATH
```

**Verify installation:**
```cmd
jq --version
:: Expected: jq-1.7.1 (or similar)

sqlite3 --version
:: Expected: 3.46.1 (or similar)
```

#### Basic Mode (without jq)

If `jq.exe` is not found on PATH, the CMD bridge runs in **BASIC mode** with limitations:
- Tool calls are proxied raw to orchestrator (no argument parsing)
- `stream_write_file` does not work
- Code intelligence tools do not work (even if sqlite3 is available)
- Request IDs are not properly tracked
- Bridge logs a warning on startup

#### Code Intelligence Availability

| Dependency | Available | Code Intelligence |
|------------|:---------:|:-----------------:|
| jq ✅ + sqlite3 ✅ | Full Mode | ✅ FTS5 search (4 tools) |
| jq ✅ + sqlite3 ❌ | Full Mode | ❌ Disabled (logged warning) |
| jq ❌ + sqlite3 ✅ | Basic Mode | ❌ Cannot parse tool args |
| jq ❌ + sqlite3 ❌ | Basic Mode | ❌ Proxy-only |

#### CMD Configuration Parameters

| Parameter | CLI | Env Var | Default | Notes |
|-----------|-----|---------|---------|-------|
| Orchestrator URL(s) | `--url` | `ORCHESTRATOR_URL` | `http://localhost:8080` | Comma-separated |
| Request Timeout | `--timeout` | `BRIDGE_TIMEOUT` | 30s | In seconds (not ms) |
| Ping Interval | `--ping-interval` | `BRIDGE_PING_INTERVAL` | 30s | Synchronous polling |
| Token | `--token` | `MCP_BRIDGE_TOKEN` | (none) | Passed as Bearer header |

**CMD-specific limitations vs other clients:**
- No async operations — health check runs between stdin reads (polling)
- No `embed_images` tool (CMD cannot do base64 encoding efficiently)
- No `code_context` (semantic search requires Ollama HTTP client — too complex for batch)
- No background file watcher (no async capability)
- `stream_write_file` and code intelligence only work in Full Mode (requires jq + sqlite3)

#### Kiro MCP Configuration for CMD

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "cmd",
      "args": [
        "/c",
        "C:\\path\\to\\mcp-bridge.cmd",
        "--url", "http://localhost:8080",
        "--token", "eyJ..."
      ],
      "disabled": false,
      "autoApprove": ["find_tools", "execute_dynamic_tool"]
    }
  }
}
```

> **Tip:** Place `mcp-bridge.cmd` and `jq.exe` in the same directory, or ensure both are on your system PATH.

---

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `No valid URLs configured` | Empty or invalid URL | Check `--url` or `ORCHESTRATOR_URL` env var |
| `Invalid token format` | Token not valid JWT | Ensure 3-part base64url format |
| Bridge exits immediately | Config validation failed | Check stderr for error message |
| `Connection refused` | Orchestrator not running | Start orchestrator server first |
| Repeated reconnection | Network instability | Check firewall, increase `--timeout` |
| `ping failed` in logs | Orchestrator unresponsive | Check orchestrator health, restart if needed |
| Tools not appearing | Bridge disconnected | Check connection state in logs |
| `stream_write_file` missing | Local tools disabled | Remove `--no-local-write` flag |

---

## Environment Variable Summary

```bash
# Required
export ORCHESTRATOR_URL="http://localhost:8080"
export MCP_BRIDGE_TOKEN="eyJhbGciOiJIUzI1NiJ9..."

# Optional — multi-URL failover
export ORCHESTRATOR_URLS="http://primary:8080,http://secondary:8080"

# Optional — tuning
export BRIDGE_TIMEOUT="30000"
export BRIDGE_PING_INTERVAL="30000"
export BRIDGE_PING_TIMEOUT="5000"
```
