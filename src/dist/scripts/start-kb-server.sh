#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# KB Server — Start Script (Linux/Mac)
# Loads .env file, then starts the kb-server JAR.
# Usage: ./start-kb-server.sh [--port 9181] [--db-host localhost]
# ─────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# === Load .env file ===
ENV_FILE="${SCRIPT_DIR}/.env"
if [[ ! -f "$ENV_FILE" ]]; then
    if [[ -f "${SCRIPT_DIR}/.env.example" ]]; then
        echo "[kb-server] .env not found, copying from .env.example"
        cp "${SCRIPT_DIR}/.env.example" "$ENV_FILE"
    else
        echo "[kb-server] WARNING: No .env file found, using system environment only"
    fi
fi

if [[ -f "$ENV_FILE" ]]; then
    # Strip Windows CRLF if present
    ENV_CLEAN=$(mktemp)
    sed 's/\r$//' "$ENV_FILE" > "$ENV_CLEAN"
    set -a
    source "$ENV_CLEAN"
    set +a
    rm -f "$ENV_CLEAN"
    echo "[kb-server] Loaded environment from .env"
fi

# === Default values ===
KB_SERVER_PORT="${KB_SERVER_PORT:-9181}"
KB_SERVER_TRANSPORT="${KB_SERVER_TRANSPORT:-stdio}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-jira_assistant}"
DB_SCHEMA="${DB_SCHEMA:-public}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER:-ollama}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-nomic-embed-text}"
EMBEDDING_BASE_URL="${EMBEDDING_BASE_URL:-http://localhost:11434}"
EMBEDDING_DIMENSIONS="${EMBEDDING_DIMENSIONS:-768}"
VECTOR_DB_PROVIDER="${VECTOR_DB_PROVIDER:-pgvector}"
VECTOR_DB_COLLECTION="${VECTOR_DB_COLLECTION:-kb_entries}"
SEGMENTATION_PROVIDER="${SEGMENTATION_PROVIDER:-ollama}"
SEGMENTATION_MODEL="${SEGMENTATION_MODEL:-llama3}"
KB_ENCRYPTION_KEY="${KB_ENCRYPTION_KEY:-sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w=}"
KB_BR_ENCRYPTION_KEY="${KB_BR_ENCRYPTION_KEY:-sMARARO7oHOnD6W2bCPYNSk2F552azl2d1dyVHLG6+w=}"

# === Parse CLI arguments ===
CONFIG_FILE="${SCRIPT_DIR}/kb-server.yml"
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port)       KB_SERVER_PORT="$2"; shift 2 ;;
        --config)     CONFIG_FILE="$2"; shift 2 ;;
        --db-host)    DB_HOST="$2"; shift 2 ;;
        --db-port)    DB_PORT="$2"; shift 2 ;;
        --db-name)    DB_NAME="$2"; shift 2 ;;
        --db-schema)  DB_SCHEMA="$2"; shift 2 ;;
        --db-user)    DB_USERNAME="$2"; shift 2 ;;
        --db-pass)    DB_PASSWORD="$2"; shift 2 ;;
        --embedding-url) EMBEDDING_BASE_URL="$2"; shift 2 ;;
        --encryption-key) KB_ENCRYPTION_KEY="$2"; shift 2 ;;
        *)            echo "[kb-server] Unknown arg: $1"; shift ;;
    esac
done

# === Resolve composite vars ===
DB_URL="${DB_URL:-jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}}"
export DB_URL DB_HOST DB_PORT DB_NAME DB_SCHEMA DB_USERNAME DB_PASSWORD
export KB_SERVER_PORT KB_SERVER_TRANSPORT
export EMBEDDING_PROVIDER EMBEDDING_MODEL EMBEDDING_BASE_URL EMBEDDING_DIMENSIONS
export VECTOR_DB_PROVIDER VECTOR_DB_COLLECTION
export SEGMENTATION_PROVIDER SEGMENTATION_MODEL
export KB_ENCRYPTION_KEY KB_BR_ENCRYPTION_KEY

# === Display config ===
echo "[kb-server] ──────────────────────────────────────"
echo "[kb-server] Port:       ${KB_SERVER_PORT}"
echo "[kb-server] Transport:  ${KB_SERVER_TRANSPORT}"
echo "[kb-server] DB:         ${DB_HOST}:${DB_PORT}/${DB_NAME} (schema: ${DB_SCHEMA})"
echo "[kb-server] Embedding:  ${EMBEDDING_BASE_URL} (${EMBEDDING_MODEL})"
echo "[kb-server] Vector DB:  ${VECTOR_DB_PROVIDER} / ${VECTOR_DB_COLLECTION}"
echo "[kb-server] Config:     ${CONFIG_FILE}"
echo "[kb-server] ──────────────────────────────────────"

# === Start server ===
exec java -jar "${SCRIPT_DIR}/kb-server-all.jar" "--config=${CONFIG_FILE}"
