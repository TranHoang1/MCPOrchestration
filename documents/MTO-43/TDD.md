# Technical Design Document (TDD)

## MCPOrchestration — MTO-43: Bash Bridge Client — MCP Orchestrator Connector

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-43 |
| Title | Bash Bridge Client — MCP Orchestrator Connector |
| Author | SA Agent |
| Version | 1.0 |
| Date | 2026-05-10 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-43.docx |
| Related FSD | FSD-v1-MTO-43.docx |

---

## 1. Introduction

### 1.1 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Bash | 4.0+ |
| HTTP | curl | 7.x+ |
| JSON | jq | 1.6+ |
| Deployment | Single file | mcp-bridge.sh |

### 1.2 Design Principles

- Single file deployment (< 500 lines)
- No compilation, no package manager
- Background process for health check (not threads)
- State communication via temp files (PID-namespaced)
- Portable across Linux distros and macOS

---

## 2. Architecture

### 2.1 File Structure

```
mcp-bridge-bash/
├── mcp-bridge.sh          # Single deployable script (all-in-one)
├── tests/
│   ├── test-bridge.sh     # Integration tests
│   └── mock-server.py     # Mock Orchestrator for testing
└── README.md
```

### 2.2 Script Structure (mcp-bridge.sh)

```bash
#!/usr/bin/env bash
set -euo pipefail

# === Configuration ===
# parse_args(), load env vars, set defaults

# === State Management ===
# STATE_FILE="/tmp/mcp-bridge-$$.state"
# transition_state(), get_state()

# === HTTP Client ===
# http_post(), initialize_session(), send_ping()

# === Health Check (Background) ===
# health_check_loop() — runs as background process

# === Reconnection ===
# reconnect_loop() — exponential backoff

# === Tool Handlers ===
# handle_initialize(), handle_tools_list(), handle_tool_call()
# handle_find_tools(), handle_execute_dynamic_tool()
# handle_stream_write_file()

# === JSON-RPC Helpers ===
# json_response(), json_error()

# === Main Loop ===
# main_loop() — read stdin, route, write stdout

# === Entry Point ===
# parse_args "$@"
# initialize_session
# health_check_loop &
# main_loop
```

---

## 3. Implementation Details

### 3.1 State Management

```bash
STATE_FILE="/tmp/mcp-bridge-$$.state"
SESSION_FILE="/tmp/mcp-bridge-$$.session"

transition_state() {
    local new_state="$1" reason="${2:-}"
    local old_state=$(get_state)
    echo "$new_state" > "$STATE_FILE"
    echo "[mcp-bridge] State: $old_state → $new_state (reason: $reason)" >&2
}

get_state() {
    cat "$STATE_FILE" 2>/dev/null || echo "DISCONNECTED"
}
```

### 3.2 HTTP Client

```bash
http_post() {
    local body="$1"
    local session_id=$(cat "$SESSION_FILE" 2>/dev/null || echo "")
    local headers=(-H "Content-Type: application/json")
    [ -n "$session_id" ] && headers+=(-H "Mcp-Session-Id: $session_id")
    
    curl -s -m "$TIMEOUT" -X POST "${headers[@]}" \
        -d "$body" "$ORCHESTRATOR_URL" 2>/dev/null
}

initialize_session() {
    local body='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"mcp-bridge-bash","version":"1.0.0"}}}'
    local response=$(http_post "$body")
    
    if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
        # Extract session ID from response or use default
        transition_state "CONNECTED" "initialized"
        return 0
    fi
    return 1
}
```

### 3.3 Health Check (Background Process)

```bash
health_check_loop() {
    local ping_id=0
    while true; do
        sleep "$PING_INTERVAL"
        
        local state=$(get_state)
        [ "$state" != "CONNECTED" ] && continue
        
        ((ping_id++))
        local body="{\"jsonrpc\":\"2.0\",\"id\":$ping_id,\"method\":\"ping\"}"
        local response=$(http_post "$body")
        
        if [ $? -ne 0 ] || ! echo "$response" | jq -e '.jsonrpc' >/dev/null 2>&1; then
            transition_state "DISCONNECTED" "ping failed"
            reconnect_loop
        fi
    done
}
```

### 3.4 Reconnection

```bash
reconnect_loop() {
    local attempt=0
    transition_state "RECONNECTING" "starting reconnect"
    
    while true; do
        local delay=$((BASE_DELAY * (2 ** attempt)))
        [ $delay -gt $MAX_DELAY ] && delay=$MAX_DELAY
        
        echo "[mcp-bridge] Reconnecting in ${delay}s (attempt $attempt)" >&2
        sleep "$delay"
        
        if initialize_session; then
            echo "[mcp-bridge] State: RECONNECTING → CONNECTED (after $attempt attempts)" >&2
            return 0
        fi
        ((attempt++))
    done
}
```

### 3.5 Main Loop

```bash
main_loop() {
    while IFS= read -r line; do
        [ -z "$line" ] && continue
        
        local method=$(echo "$line" | jq -r '.method // empty')
        local id=$(echo "$line" | jq -r '.id // empty')
        
        case "$method" in
            "initialize")
                handle_initialize "$id" ;;
            "tools/list")
                handle_tools_list "$id" ;;
            "tools/call")
                local tool_name=$(echo "$line" | jq -r '.params.name')
                local tool_args=$(echo "$line" | jq -c '.params.arguments // {}')
                handle_tool_call "$id" "$tool_name" "$tool_args" ;;
            "notifications/"*)
                ;; # Ignore notifications
            *)
                json_error "$id" -32601 "Method not found: $method" ;;
        esac
    done
}
```

### 3.6 Tool Routing

```bash
handle_tool_call() {
    local id="$1" tool_name="$2" tool_args="$3"
    
    local state=$(get_state)
    if [ "$state" != "CONNECTED" ] && [ "$tool_name" != "stream_write_file" ]; then
        json_error "$id" -1 "Bridge is reconnecting to Orchestrator"
        return
    fi
    
    case "$tool_name" in
        "stream_write_file")
            handle_stream_write_file "$id" "$tool_args" ;;
        "find_tools"|"execute_dynamic_tool"|"toggle_tool"|"reset_tools"|"manage_auto_approve"|"agent_log")
            proxy_to_orchestrator "$id" "$tool_name" "$tool_args" ;;
        *)
            json_error "$id" -1 "Unknown tool: $tool_name" ;;
    esac
}
```

---

## 4. Deployment

### 4.1 Installation

```bash
# Download
curl -o /usr/local/bin/mcp-bridge.sh https://raw.githubusercontent.com/.../mcp-bridge.sh
chmod +x /usr/local/bin/mcp-bridge.sh

# Or copy directly
scp mcp-bridge.sh user@server:/usr/local/bin/
```

### 4.2 IDE Configuration (mcp.json)

```json
{
  "mcpServers": {
    "orchestrator": {
      "command": "/usr/local/bin/mcp-bridge.sh",
      "args": ["--url", "http://localhost:8080/mcp"]
    }
  }
}
```

---

## 5. Implementation Checklist

| # | File | Description |
|---|------|-------------|
| 1 | `mcp-bridge-bash/mcp-bridge.sh` | Complete bridge script |
| 2 | `mcp-bridge-bash/tests/test-bridge.sh` | Integration tests |
| 3 | `mcp-bridge-bash/README.md` | Usage documentation |

---

## 6. Appendix

### Diagram Index

| # | Diagram | Image | Source |
|---|---------|-------|--------|
| 1 | Architecture | [architecture.png](diagrams/architecture.png) | [architecture.drawio](diagrams/architecture.drawio) |
| 2 | Component | [component.png](diagrams/component.png) | [component.drawio](diagrams/component.drawio) |
