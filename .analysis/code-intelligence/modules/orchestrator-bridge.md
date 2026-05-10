# Module Analysis — orchestrator-bridge

**Last Updated:** 2026-07-06
**Language:** Kotlin 2.3.20 | **Platform:** JVM 21

## Overview

Bridge process that connects IDE (Kiro/VS Code) to the orchestrator server. Runs as a local MCP server that the IDE connects to via stdio, and forwards requests to the orchestrator via HTTP Streamable.

## Key Files

| File | Purpose |
|------|---------|
| `BridgeApplication.kt` | Entry point |
| `BridgeConfig.kt` | Bridge configuration |
| `BridgeServer.kt` | MCP server for IDE communication |
| `BridgeToolPromoter.kt` | Promote frequently-used tools to reduce latency |
| `FileTransferHandler.kt` | File transfer between IDE and server |
| `HealthCheckConfig.kt` | Health check configuration |
| `HealthCheckManager.kt` | Monitor orchestrator health |
| `HttpStreamableClient.kt` | HTTP Streamable client to orchestrator |
| `ImageEmbedder.kt` | Embed images in markdown as base64 |
| `LocalEmbedImagesTool.kt` | MCP tool: embed_images (local execution) |
| `LocalStreamWriteTool.kt` | MCP tool: stream_write_file (local execution) |
| `ReconnectionManager.kt` | Auto-reconnect logic |
| `WorkspaceContext.kt` | IDE workspace context |

## Architecture

```
IDE (Kiro/VS Code) ──stdio──> Bridge ──HTTP Streamable──> Orchestrator Server
                                │
                                ├── Local tools (embed_images, stream_write_file)
                                ├── Tool promotion (cache hot tools)
                                └── Health monitoring + auto-reconnect
```

## Local Tools (executed without server roundtrip)

| Tool | Purpose |
|------|---------|
| `embed_images` | Replace image references with base64 data URIs |
| `stream_write_file` | Write content directly to local filesystem |
| `export_drawio` | Export draw.io XML to PNG (calls draw.io desktop) |
