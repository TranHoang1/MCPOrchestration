#!/usr/bin/env bash
set -uo pipefail

# ============================================================
# MCP Bridge Client — Bash
# Connects to MCP Orchestrator via HTTP Streamable transport.
# Exposes tools locally via stdio MCP server.
# ============================================================

VERSION="1.0.0"

# === Configuration ===
ORCHESTRATOR_URL="${ORCHESTRATOR_URL:-http://localhost:8080}"
TIMEOUT="${BRIDGE_TIMEOUT:-30}"
PING_INTERVAL="${BRIDGE_PING_INTERVAL:-30}"
PING_TIMEOUT="${BRIDGE_PING_TIMEOUT:-5}"
BASE_DELAY=1
MAX_DELAY=15
MAX_RETRY_BEFORE_ROTATE=3
ENABLE_LOCAL_TOOLS=true

# URL management state
declare -a URLS=()
URL_INDEX=0
URL_COUNT=0
ACTIVE_URL=""
declare -a URL_ERRORS=()

# State files (PID-namespaced)
STATE_FILE="/tmp/mcp-bridge-$$.state"
SESSION_FILE="/tmp/mcp-bridge-$$.session"
HEALTH_PID_FILE="/tmp/mcp-bridge-$$.health-pid"

# === Argument Parsing ===
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --url) ORCHESTRATOR_URL="$2"; shift 2 ;;
            --token) BRIDGE_TOKEN="$2"; shift 2 ;;
            --timeout) TIMEOUT="$2"; shift 2 ;;
            --ping-interval) PING_INTERVAL="$2"; shift 2 ;;
            --ping-timeout) PING_TIMEOUT="$2"; shift 2 ;;
            --no-reconnect) NO_RECONNECT=true; shift ;;
            --no-local-tools) ENABLE_LOCAL_TOOLS=false; shift ;;
            --help) usage; exit 0 ;;
            *) echo "[mcp-bridge] Unknown arg: $1" >&2; shift ;;
        esac
    done
    # Env fallback for token
    BRIDGE_TOKEN="${BRIDGE_TOKEN:-${MCP_BRIDGE_TOKEN:-}}"
}

usage() {
    cat >&2 <<EOF
MCP Bridge Client (Bash) v$VERSION
Usage: mcp-bridge.sh [OPTIONS]
Options:
  --url URL           Orchestrator URL(s), comma-separated (default: http://localhost:8080)
  --timeout SEC       Request timeout in seconds (default: 30)
  --ping-interval SEC Health check interval in seconds (default: 30, 0=disabled)
  --ping-timeout SEC  Ping timeout in seconds (default: 5)
  --no-reconnect      Disable auto-reconnect
  --no-local-tools    Disable local tools (stream_write_file)
  --help              Show this help
EOF
}

# === URL Parsing ===
parse_urls() {
    local raw=""
    if [[ -n "${ORCHESTRATOR_URL_RAW:-}" ]]; then
        raw="$ORCHESTRATOR_URL_RAW"
    elif [[ -n "${ORCHESTRATOR_URLS:-}" ]]; then
        raw="$ORCHESTRATOR_URLS"
    elif [[ -n "$ORCHESTRATOR_URL" ]]; then
        raw="$ORCHESTRATOR_URL"
    else
        raw="http://localhost:8080"
    fi

    IFS=',' read -ra URL_LIST <<< "$raw"
    URLS=()
    for url in "${URL_LIST[@]}"; do
        url=$(echo "$url" | xargs)
        [[ -z "$url" ]] && continue
        [[ "$url" != http://* && "$url" != https://* ]] && continue
        URLS+=("$url")
    done

    if [[ ${#URLS[@]} -eq 0 ]]; then
        log "ERROR: No valid URLs configured"
        exit 1
    fi
    if [[ ${#URLS[@]} -gt 10 ]]; then
        log "WARN: URL list truncated to 10"
        URLS=("${URLS[@]:0:10}")
    fi
    URL_COUNT=${#URLS[@]}
    URL_INDEX=0
    ACTIVE_URL="${URLS[0]}"
}

# === URL Manager Functions ===
url_advance() {
    URL_INDEX=$(( (URL_INDEX + 1) % URL_COUNT ))
    ACTIVE_URL="${URLS[$URL_INDEX]}"
}

url_mark_failed() {
    URL_ERRORS+=("$1: $2")
}

url_has_next() {
    [[ ${#URL_ERRORS[@]} -lt $URL_COUNT ]]
}

url_clear_errors() {
    URL_ERRORS=()
}

url_reset() {
    URL_INDEX=0
    ACTIVE_URL="${URLS[0]}"
    URL_ERRORS=()
}

url_report_errors() {
    log "All URLs failed:"
    for err in "${URL_ERRORS[@]}"; do
        log "  - $err"
    done
}

# === Logging ===
log() { echo "[mcp-bridge] $*" >&2; }

# === State Management ===
transition_state() {
    local new_state="$1" reason="${2:-}"
    local old_state
    old_state=$(get_state)
    echo "$new_state" > "$STATE_FILE"
    log "State: $old_state → $new_state (reason: $reason)"
}

get_state() { cat "$STATE_FILE" 2>/dev/null || echo "DISCONNECTED"; }

# === HTTP Client ===
http_post() {
    local body="$1" timeout_override="${2:-$TIMEOUT}"
    local session_id
    session_id=$(cat "$SESSION_FILE" 2>/dev/null || echo "")
    local -a headers=(-H "Content-Type: application/json")
    [[ -n "$BRIDGE_TOKEN" ]] && headers+=(-H "Authorization: Bearer $BRIDGE_TOKEN")
    [[ -n "$session_id" ]] && headers+=(-H "Mcp-Session-Id: $session_id")

    curl -s -m "$timeout_override" -X POST "${headers[@]}" \
        -d "$body" "${ACTIVE_URL}/mcp" 2>/dev/null
}

initialize_session() {
    local body='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"mcp-bridge-bash","version":"1.0.0"}}}'
    local response
    response=$(http_post "$body")

    if echo "$response" | jq -e '.result' >/dev/null 2>&1; then
        # Try to extract session ID from headers (curl -D) or use placeholder
        transition_state "CONNECTED" "initialized"
        return 0
    fi
    return 1
}

# === Health Check (Background) ===
health_check_loop() {
    [[ "$PING_INTERVAL" == "0" ]] && { log "Health check disabled (interval=0)"; return; }
    log "Health check started (interval=${PING_INTERVAL}s)"

    local ping_id=0
    while true; do
        sleep "$PING_INTERVAL"

        local state
        state=$(get_state)
        [[ "$state" != "CONNECTED" ]] && continue

        ((ping_id++))
        local body="{\"jsonrpc\":\"2.0\",\"id\":$ping_id,\"method\":\"ping\"}"
        local response
        response=$(http_post "$body" "$PING_TIMEOUT")

        if [[ $? -ne 0 ]] || ! echo "$response" | jq -e '.jsonrpc' >/dev/null 2>&1; then
            log "Ping failed"
            transition_state "DISCONNECTED" "ping timeout"
            reconnect_loop
        fi
    done
}

# === Reconnection ===
reconnect_loop() {
    [[ "${NO_RECONNECT:-}" == "true" ]] && return
    transition_state "RECONNECTING" "starting reconnect"

    # Phase 1: Retry active URL
    local i=0
    while [[ $i -lt $MAX_RETRY_BEFORE_ROTATE ]]; do
        local delay=$((BASE_DELAY * (2 ** i)))
        [[ $delay -gt $MAX_DELAY ]] && delay=$MAX_DELAY
        log "Retry $((i+1))/$MAX_RETRY_BEFORE_ROTATE for $ACTIVE_URL in ${delay}s"
        sleep "$delay"
        if initialize_session; then
            return 0
        fi
        ((i++))
    done

    # Phase 2: Rotate to other URLs
    if [[ $URL_COUNT -gt 1 ]]; then
        url_clear_errors
        url_mark_failed "$ACTIVE_URL" "Exhausted retries"

        while url_has_next; do
            url_advance
            log "Switching to URL $((URL_INDEX+1))/$URL_COUNT: $ACTIVE_URL"
            if initialize_session; then
                return 0
            fi
            url_mark_failed "$ACTIVE_URL" "Connection failed"
        done

        url_report_errors
        url_reset
    fi

    # Phase 3: Infinite backoff
    local attempt=0
    while true; do
        local delay=$((BASE_DELAY * (2 ** attempt)))
        [[ $delay -gt $MAX_DELAY ]] && delay=$MAX_DELAY
        log "Reconnecting in ${delay}s (attempt $attempt)"
        sleep "$delay"
        if initialize_session; then
            return 0
        fi
        ((attempt++))
    done
}

# === JSON-RPC Helpers ===
json_response() {
    local id="$1" result="$2"
    printf '{"jsonrpc":"2.0","id":%s,"result":%s}\n' "$id" "$result"
}

json_error() {
    local id="$1" code="$2" message="$3"
    printf '{"jsonrpc":"2.0","id":%s,"error":{"code":%s,"message":"%s"}}\n' "$id" "$code" "$message"
}

json_tool_result() {
    local id="$1" text="$2"
    local escaped
    escaped=$(echo "$text" | jq -Rs '.')
    printf '{"jsonrpc":"2.0","id":%s,"result":{"content":[{"type":"text","text":%s}]}}\n' "$id" "$escaped"
}

# === Tool Handlers ===
handle_initialize() {
    local id="$1"
    json_response "$id" '{"protocolVersion":"2025-03-26","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"mcp-bridge-bash","version":"1.0.0"}}'
}

handle_tools_list() {
    local id="$1"
    local tools='[{"name":"find_tools","description":"Search for available tools","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}},{"name":"execute_dynamic_tool","description":"Execute a tool on an upstream MCP server","inputSchema":{"type":"object","properties":{"tool_name":{"type":"string"},"arguments":{"type":"object"}},"required":["tool_name"]}},{"name":"toggle_tool","description":"Enable or disable a tool or server","inputSchema":{"type":"object","properties":{"tool_name":{"type":"string"},"server_name":{"type":"string"},"enabled":{"type":"boolean"}},"required":["enabled"]}},{"name":"reset_tools","description":"Reset all toggle states","inputSchema":{"type":"object","properties":{"server_name":{"type":"string"}},"required":[]}},{"name":"manage_auto_approve","description":"Manage auto-approve list","inputSchema":{"type":"object","properties":{"tool_name":{"type":"string"},"server_name":{"type":"string"},"auto_approve":{"type":"boolean"}},"required":["auto_approve"]}},{"name":"agent_log","description":"Write execution log entry","inputSchema":{"type":"object","properties":{"ticket_key":{"type":"string"},"agent_name":{"type":"string"},"step":{"type":"string"},"status":{"type":"string"},"message":{"type":"string"}},"required":["ticket_key","agent_name","step","status","message"]}}'

    if [[ "$ENABLE_LOCAL_TOOLS" == "true" ]]; then
        tools+=',{"name":"stream_write_file","description":"Write content to a file on disk","inputSchema":{"type":"object","properties":{"file_path":{"type":"string"},"content":{"type":"string"},"mode":{"type":"string"}},"required":["file_path"]}}'
    fi
    tools+=']'

    json_response "$id" "{\"tools\":$tools}"
}

handle_tool_call() {
    local id="$1" tool_name="$2" tool_args="$3"

    case "$tool_name" in
        "stream_write_file")
            if [[ "$ENABLE_LOCAL_TOOLS" == "true" ]]; then
                handle_stream_write_file "$id" "$tool_args"
            else
                json_error "$id" -1 "Local tools disabled"
            fi
            ;;
        "find_tools"|"execute_dynamic_tool"|"toggle_tool"|"reset_tools"|"manage_auto_approve"|"agent_log")
            proxy_to_orchestrator "$id" "$tool_name" "$tool_args"
            ;;
        *)
            json_error "$id" -1 "Unknown tool: $tool_name"
            ;;
    esac
}

handle_stream_write_file() {
    local id="$1" args="$2"
    local file_path mode content

    file_path=$(echo "$args" | jq -r '.file_path // empty')
    mode=$(echo "$args" | jq -r '.mode // "write"')
    content=$(echo "$args" | jq -r '.content // ""')

    if [[ -z "$file_path" ]]; then
        json_error "$id" -1 "file_path is required"
        return
    fi

    # Create parent directories
    mkdir -p "$(dirname "$file_path")"

    case "$mode" in
        "write") printf '%s' "$content" > "$file_path" ;;
        "append") printf '\n%s' "$content" >> "$file_path" ;;
        "create")
            if [[ -f "$file_path" ]]; then
                json_error "$id" -1 "File already exists: $file_path"
                return
            fi
            printf '%s' "$content" > "$file_path"
            ;;
    esac

    local bytes=${#content}
    json_tool_result "$id" "{\"status\":\"ok\",\"path\":\"$file_path\",\"bytes_written\":$bytes}"
}

proxy_to_orchestrator() {
    local id="$1" tool_name="$2" tool_args="$3"

    local state
    state=$(get_state)
    if [[ "$state" != "CONNECTED" ]]; then
        json_error "$id" -1 "Bridge is reconnecting to Orchestrator"
        return
    fi

    local body
    body=$(jq -nc --arg name "$tool_name" --argjson args "$tool_args" \
        '{"jsonrpc":"2.0","id":99,"method":"tools/call","params":{"name":$name,"arguments":$args}}')

    local response
    response=$(http_post "$body")

    if [[ $? -ne 0 ]] || [[ -z "$response" ]]; then
        json_error "$id" -1 "$tool_name failed: no response from orchestrator"
        return
    fi

    # Extract result and re-wrap with correct id
    local result
    result=$(echo "$response" | jq -c '.result // .error')
    if echo "$response" | jq -e '.error' >/dev/null 2>&1; then
        local err_msg
        err_msg=$(echo "$response" | jq -r '.error.message // "Unknown error"')
        json_error "$id" -1 "$err_msg"
    else
        json_response "$id" "$result"
    fi
}

# === Main Loop ===
main_loop() {
    while IFS= read -r line; do
        [[ -z "$line" ]] && continue

        local method id
        method=$(echo "$line" | jq -r '.method // empty')
        id=$(echo "$line" | jq -r '.id // empty')

        case "$method" in
            "initialize")
                handle_initialize "$id" ;;
            "initialized")
                ;; # notification, no response
            "tools/list")
                handle_tools_list "$id" ;;
            "tools/call")
                local tool_name tool_args
                tool_name=$(echo "$line" | jq -r '.params.name')
                tool_args=$(echo "$line" | jq -c '.params.arguments // {}')
                handle_tool_call "$id" "$tool_name" "$tool_args"
                ;;
            "notifications/"*)
                ;; # Ignore notifications
            *)
                [[ -n "$id" ]] && json_error "$id" -32601 "Method not found: $method"
                ;;
        esac
    done
}

# === Cleanup ===
cleanup() {
    local health_pid
    health_pid=$(cat "$HEALTH_PID_FILE" 2>/dev/null || echo "")
    [[ -n "$health_pid" ]] && kill "$health_pid" 2>/dev/null
    rm -f "$STATE_FILE" "$SESSION_FILE" "$HEALTH_PID_FILE"
    log "Shutting down..."
}

# === Entry Point ===
trap cleanup EXIT INT TERM

parse_args "$@"
# Set ORCHESTRATOR_URL_RAW from --url arg if provided
ORCHESTRATOR_URL_RAW="$ORCHESTRATOR_URL"
parse_urls

log "MCP Bridge Client (Bash) v$VERSION starting..."
if [[ $URL_COUNT -gt 1 ]]; then
    log "Configured $URL_COUNT URLs (failover enabled)"
    for i in "${!URLS[@]}"; do
        log "  [$((i+1))] ${URLS[$i]}"
    done
else
    log "Connecting to orchestrator at: $ACTIVE_URL"
fi

# Initial connection — try all URLs
connected=false
url_clear_errors
for (( i=0; i<URL_COUNT; i++ )); do
    log "Trying URL $((URL_INDEX+1))/$URL_COUNT: $ACTIVE_URL"
    if initialize_session; then
        connected=true
        break
    fi
    url_mark_failed "$ACTIVE_URL" "Connection failed"
    if url_has_next; then
        url_advance
    fi
done

if [[ "$connected" != "true" ]]; then
    url_report_errors
    url_reset
    log "Failed initial connection, will retry in background"
    reconnect_loop &
fi

# Start health check in background
health_check_loop &
echo $! > "$HEALTH_PID_FILE"

log "Bridge MCP server ready (stdio transport)"

# Run main loop
main_loop
