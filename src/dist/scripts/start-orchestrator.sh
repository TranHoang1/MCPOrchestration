#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# MCP Orchestrator Server — Start Script (Linux/Mac)
# Loads .env file, then starts the orchestrator JAR.
# Usage: ./start-orchestrator.sh [--port 9180] [--db-host localhost]
# ─────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# === Load .env file ===
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ ! -f "$ENV_FILE" ]]; then
    if [[ -f "${SCRIPT_DIR}/.env.example" ]]; then
        echo "[orchestrator] .env not found, copying from .env.example"
        cp "${SCRIPT_DIR}/.env.example" "$ENV_FILE"
    else
        echo "[orchestrator] WARNING: No .env file found, using system environment only"
    fi
fi

if [[ -f "$ENV_FILE" ]]; then
    set -a
    source "$ENV_FILE"
    set +a
    echo "[orchestrator] Loaded environment from .env"
fi

# === Default values ===
ORCHESTRATOR_PORT="${ORCHESTRATOR_PORT:-9180}"
ORCHESTRATOR_TRANSPORT="${ORCHESTRATOR_TRANSPORT:-http-streamable}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-jira_assistant}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER:-ollama}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-nomic-embed-text:latest}"
EMBEDDING_BASE_URL="${EMBEDDING_BASE_URL:-http://localhost:11434}"
EMBEDDING_DIMENSIONS="${EMBEDDING_DIMENSIONS:-768}"
EMBEDDING_API_KEY="${EMBEDDING_API_KEY:-unused}"

# === Parse CLI arguments ===
CONFIG_FILE="${SCRIPT_DIR}/application.yml"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)       ORCHESTRATOR_PORT="$2"; shift 2 ;;
        --config)     CONFIG_FILE="$2"; shift 2 ;;
        --db-host)    DB_HOST="$2"; shift 2 ;;
        --db-port)    DB_PORT="$2"; shift 2 ;;
        --db-name)    DB_NAME="$2"; shift 2 ;;
        --db-user)    DB_USERNAME="$2"; shift 2 ;;
        --db-pass)    DB_PASSWORD="$2"; shift 2 ;;
        --embedding-url) EMBEDDING_BASE_URL="$2"; shift 2 ;;
        *)            echo "[orchestrator] Unknown arg: $1"; shift ;;
    esac
done

# === Resolve composite vars ===
DB_URL="${DB_URL:-jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}}"
export DB_URL DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD
export ORCHESTRATOR_PORT ORCHESTRATOR_TRANSPORT
export EMBEDDING_PROVIDER EMBEDDING_MODEL EMBEDDING_BASE_URL EMBEDDING_DIMENSIONS EMBEDDING_API_KEY

# === Display config ===
echo "[orchestrator] ──────────────────────────────────────"
echo "[orchestrator] Port:      ${ORCHESTRATOR_PORT}"
echo "[orchestrator] Transport: ${ORCHESTRATOR_TRANSPORT}"
echo "[orchestrator] DB:        ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "[orchestrator] Embedding: ${EMBEDDING_BASE_URL} (${EMBEDDING_MODEL})"
echo "[orchestrator] Config:    ${CONFIG_FILE}"
echo "[orchestrator] ──────────────────────────────────────"

# === Start server ===
exec java -jar "${SCRIPT_DIR}/mcp-orchestrator-all.jar" --config "${CONFIG_FILE}"
