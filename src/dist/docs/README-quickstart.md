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
| `Flyway migration failed` | Migration script error or checksum mismatch | Check logs for details; see Database Migration section below |
| `Embedding service unavailable` | Ollama not running | Start Ollama: `ollama serve` |
| `Required config 'kb.database.url' is empty` | Missing DB_URL env var | Set DB_URL or DB_HOST+DB_PORT+DB_NAME in .env |
| `java: command not found` | Java not installed | Install Java 21 |
| Port already in use | Another instance running | Change port via `--port` or kill existing process |

## Database Migration (Flyway)

Database schema is managed by **Flyway**. Migrations run automatically on application startup — no manual steps needed for normal operation.

### How it works

1. On first start with an **empty database**: Flyway creates all tables from scratch
2. On first start with an **existing database** (pre-Flyway): Flyway baselines at version 0, then applies any pending migrations
3. On subsequent starts: Flyway checks for pending migrations and applies them (typically a no-op)

### Migration scripts location

```
orchestrator-server/src/main/resources/db/migration/   ← Orchestrator DB
orchestrator-client/src/main/resources/db/migration/   ← Client/Bridge DB
kb-server/src/main/resources/db/migration/             ← KB Server DB
```

### Checking migration status

```bash
# Show applied and pending migrations
./gradlew :orchestrator-server:flywayInfo
./gradlew :kb-server:flywayInfo
./gradlew :orchestrator-client:flywayInfo
```

### Rolling back a migration

Flyway Community Edition does not support `flyway undo`. Use the custom rollback task:

```bash
# Rollback a specific version (e.g., V301)
./gradlew :orchestrator-server:flywayUndo -PundoVersion=301

# Verify rollback
./gradlew :orchestrator-server:flywayInfo
```

Rollback scripts (`U{version}__*.sql`) are located alongside forward migrations.

### Upgrading from pre-Flyway versions

If upgrading from a version that used `DatabaseInitializer` (v1.3.0 or earlier):

1. **No action needed** — Flyway detects existing tables and baselines automatically
2. All migration scripts use `CREATE TABLE IF NOT EXISTS` for safety
3. The `flyway_schema_history` table is created automatically on first startup

### Troubleshooting migrations

| Issue | Solution |
|-------|----------|
| `Validate failed: Migrations have been applied out of order` | Set `flyway.outOfOrder=true` temporarily or re-baseline |
| `Checksum mismatch` on existing migration | A committed migration was modified — revert the change or run `flyway repair` |
| Migration fails mid-way | Fix the SQL, delete the failed entry from `flyway_schema_history`, restart |
| `relation already exists` (without IF NOT EXISTS) | Migration script missing `IF NOT EXISTS` — fix and re-run |

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
