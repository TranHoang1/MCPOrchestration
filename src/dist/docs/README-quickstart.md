# MCP Orchestration Server — Quick Start

## Prerequisites

- **Java 21** — [Download Corretto](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html)
- **PostgreSQL 16+** with [pgvector](https://github.com/pgvector/pgvector) extension
- **Ollama** — [Install](https://ollama.com/download) (for embeddings + segmentation)

## Quick Start (3 steps)

### 1. Extract and configure

```bash
unzip mcp-orchestration-server-*.zip -d mcp-server
cd mcp-server
cp .env.example .env
```

Edit `.env` with your database credentials and Ollama URL.

### 2. Pull Ollama models

```bash
ollama pull nomic-embed-text
ollama pull llama3
```

### 3. Start servers

**Windows:**
```cmd
start-orchestrator.cmd
start-kb-server.cmd
```

**Linux/Mac:**
```bash
chmod +x *.sh
./start-orchestrator.sh
./start-kb-server.sh
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | jira_assistant | Database name |
| `DB_SCHEMA` | public | Database schema |
| `DB_USERNAME` | postgres | Database user |
| `DB_PASSWORD` | postgres | Database password |
| `DB_URL` | (auto-composed) | Full JDBC URL (overrides host/port/name) |
| `ORCHESTRATOR_PORT` | 9180 | Orchestrator server port |
| `KB_SERVER_PORT` | 9181 | KB server port |
| `EMBEDDING_PROVIDER` | ollama | Embedding provider |
| `EMBEDDING_MODEL` | nomic-embed-text:latest | Embedding model name |
| `EMBEDDING_BASE_URL` | http://localhost:11434 | Ollama API URL |
| `KB_ENCRYPTION_KEY` | (dev default) | PII encryption key (base64) |
| `KB_BR_ENCRYPTION_KEY` | (dev default) | Business rule encryption key |

## CLI Arguments

Start scripts accept CLI args that override env vars:

```bash
./start-orchestrator.sh --port 9280 --db-host 192.168.1.100 --db-name mydb
./start-kb-server.sh --port 9281 --embedding-url http://gpu-server:11434
```

| Argument | Applies to | Description |
|----------|-----------|-------------|
| `--port` | Both | Server port |
| `--config` | Both | Custom YAML config path |
| `--db-host` | Both | Database host |
| `--db-port` | Both | Database port |
| `--db-name` | Both | Database name |
| `--db-user` | Both | Database username |
| `--db-pass` | Both | Database password |
| `--db-schema` | KB only | Database schema |
| `--embedding-url` | Both | Ollama API URL |
| `--encryption-key` | KB only | PII encryption key |

## Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `Connection refused :5432` | PostgreSQL not running | Start PostgreSQL service |
| `relation does not exist` | Database not initialized | Server auto-creates tables on first start |
| `Embedding service unavailable` | Ollama not running | Start Ollama: `ollama serve` |
| `Required config 'kb.database.url' is empty` | Missing DB_URL env var | Set DB_URL or DB_HOST+DB_PORT+DB_NAME in .env |
| `java: command not found` | Java not installed | Install Java 21 |
| Port already in use | Another instance running | Change port via `--port` or kill existing process |

## Architecture

```
┌─────────────────────┐     ┌─────────────────┐
│  MCP Orchestrator   │────▶│   KB Server     │
│  (port 9180)        │     │   (port 9181)   │
└────────┬────────────┘     └────────┬────────┘
         │                           │
         ▼                           ▼
┌─────────────────────┐     ┌─────────────────┐
│   PostgreSQL        │     │     Ollama      │
│   + pgvector        │     │  (embeddings)   │
└─────────────────────┘     └─────────────────┘
```
