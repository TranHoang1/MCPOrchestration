# Functional Specification Document (FSD)

## MCPOrchestration — MTO-43: Bash Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-43 |
| Title | Bash Bridge Client — MCP Orchestrator Connector |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-43.docx |

---

## 1. Introduction

### 1.1 Purpose

Specifies the functional behavior of the Bash MCP Bridge Client — a single shell script that acts as an MCP server (stdio) proxying tool calls to the Orchestrator (HTTP).

### 1.2 Key Constraints

- Pure Bash (no Python, Node.js, or compiled binaries)
- JSON handling via `jq` (external dependency)
- HTTP via `curl` (pre-installed)
- Background processes for health check (no threads)
- Single-threaded main loop (read stdin → process → write stdout)

---

## 2. Functional Requirements

### 2.1 Feature: stdio MCP Server

**UC-1: Process Tool Call**

| Step | Description |
|------|-------------|
| 1 | Read line from stdin |
| 2 | Parse JSON-RPC with jq |
| 3 | Extract method and params |
| 4 | Route: local tool or proxy to Orchestrator |
| 5 | Format JSON-RPC response |
| 6 | Write to stdout |

**Tools Exposed:**

| # | Tool | Type | Implementation |
|---|------|------|---------------|
| 1 | find_tools | Proxy | curl POST to Orchestrator |
| 2 | execute_dynamic_tool | Proxy | curl POST to Orchestrator |
| 3 | toggle_tool | Proxy | curl POST to Orchestrator |
| 4 | reset_tools | Proxy | curl POST to Orchestrator |
| 5 | manage_auto_approve | Proxy | curl POST to Orchestrator |
| 6 | agent_log | Proxy | curl POST to Orchestrator |
| 7 | stream_write_file | Local | Bash file I/O |

### 2.2 Feature: HTTP Client (curl)

**Request format:**
```bash
curl -s -m "$TIMEOUT" -X POST \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: $SESSION_ID" \
  -d "$REQUEST_BODY" \
  "$ORCHESTRATOR_URL"
```

**Session management:**
- On initialize: extract Mcp-Session-Id from response headers
- Include session ID in all subsequent requests

### 2.3 Feature: Health Check (Background)

Per MTO-46 specification:
- Background function running in subshell (`health_check_loop &`)
- Sends ping via curl every 30s
- On failure: sets shared state file to "DISCONNECTED"
- Main loop checks state file before proxying

**State communication (between main process and background):**
- State file: `/tmp/mcp-bridge-$$.state` (PID-specific)
- Main loop reads state before each request
- Health check writes state on transitions

### 2.4 Feature: Auto-Reconnect

- Exponential backoff: sleep 1, 2, 4, 8, 15, 15...
- Reconnect = re-run initialize via curl
- On success: update state file to "CONNECTED"

### 2.5 Feature: Local Tool — stream_write_file

```bash
stream_write_file() {
    local file_path="$1" content="$2" mode="${3:-write}"
    
    case "$mode" in
        write)   printf '%s' "$content" > "$file_path" ;;
        append)  printf '%s' "$content" >> "$file_path" ;;
        create)  
            [ -f "$file_path" ] && echo "File exists" && return 1
            printf '%s' "$content" > "$file_path" ;;
    esac
    
    local size=$(wc -c < "$file_path")
    echo "{\"file_path\":\"$file_path\",\"bytes_written\":${#content},\"total_size\":$size}"
}
```

### 2.6 Feature: Configuration

| Parameter | CLI | Env | Default |
|-----------|-----|-----|---------|
| URL | --url | ORCHESTRATOR_URL | http://localhost:8080/mcp |
| Timeout | --timeout | BRIDGE_TIMEOUT | 30 (seconds) |
| Ping interval | --ping-interval | PING_INTERVAL | 30 (seconds) |

---

## 3. Processing Logic

### 3.1 Main Loop

```bash
main_loop() {
    while IFS= read -r line; do
        # Parse request
        method=$(echo "$line" | jq -r '.method // .params.name // empty')
        id=$(echo "$line" | jq -r '.id')
        
        # Check state
        state=$(cat "$STATE_FILE" 2>/dev/null || echo "DISCONNECTED")
        if [ "$state" != "CONNECTED" ] && [ "$method" != "initialize" ]; then
            echo_error "$id" "Bridge is reconnecting"
            continue
        fi
        
        # Route
        case "$method" in
            "initialize") handle_initialize "$line" ;;
            "tools/list") handle_tools_list "$id" ;;
            "tools/call") handle_tool_call "$line" ;;
            *) echo_error "$id" "Unknown method: $method" ;;
        esac
    done
}
```

---

## 4. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | System Context | [system-context.png](diagrams/system-context.png) | [system-context.drawio](diagrams/system-context.drawio) |
| 2 | Sequence — Tool Call | [sequence-tool-call.png](diagrams/sequence-tool-call.png) | [sequence-tool-call.drawio](diagrams/sequence-tool-call.drawio) |
| 3 | State Diagram | [state-bridge.png](diagrams/state-bridge.png) | [state-bridge.drawio](diagrams/state-bridge.drawio) |
