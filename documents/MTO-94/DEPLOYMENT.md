# Deployment Guide — MTO-94: Authentication & Authorization Epic

## 1. Prerequisites

| Requirement | Version | Purpose |
|-------------|---------|---------|
| JDK | 17+ | Runtime |
| PostgreSQL | 14+ | Database |
| MCP Orchestrator JAR | Latest build | Application |
| Network access | HTTPS outbound | SSO IdP communication |

### Pre-deployment Checklist

- [ ] PostgreSQL running and accessible
- [ ] Database `mcp_orchestrator` created
- [ ] Encryption key generated (32 bytes, base64-encoded)
- [ ] JWT secret generated (≥32 characters)
- [ ] SSO IdP configured (if using SSO)
- [ ] Backup of existing database taken

---

## 2. Environment Variables

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `JWT_SECRET` | HMAC-SHA256 signing key (≥32 chars) | `my-super-secret-key-at-least-32-chars-long` |
| `MCP_ENCRYPTION_KEY` | AES-256 key (32 bytes, base64) | `dGhpcyBpcyBhIDMyIGJ5dGUga2V5IGZvciBBRVM=` |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/mcp_orchestrator` |
| `DATABASE_USER` | DB username | `mcp_admin` |
| `DATABASE_PASSWORD` | DB password | `<secure-password>` |

### Optional Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_JWT_ALGORITHM` | `HS256` | JWT signing algorithm |
| `AUTH_SESSION_EXPIRY_HOURS` | `4` | Session token TTL |
| `AUTH_BRIDGE_TOKEN_EXPIRY_DAYS` | `30` | Bridge token TTL |
| `AUTH_MAX_BRIDGE_TOKEN_DAYS` | `365` | Max bridge token lifetime |
| `AUTH_JWT_ISSUER` | `mcp-orchestrator` | JWT issuer claim |
| `AUTH_LOCKOUT_MAX_ATTEMPTS` | `5` | Failed login attempts before lockout |
| `AUTH_LOCKOUT_MINUTES` | `15` | Lockout duration |
| `AUTH_ALLOW_EMAIL_HEADER` | `true` | Allow X-User-Email header auth |
| `AUTH_EMAIL_HEADER_NAME` | `X-User-Email` | Email header name |

### Generating Encryption Key

```bash
# Generate 32-byte key, base64-encode it
openssl rand -base64 32
```

### Generating JWT Secret

```bash
# Generate a secure random string
openssl rand -hex 32
```

---

## 3. Database Migrations

Migrations run automatically on startup. The following tables are created:

### Table 1: `users` (altered — adds auth columns)

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_mode TEXT NOT NULL DEFAULT 'local';
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TEXT;
```

### Table 2: `bridge_tokens`

```sql
CREATE TABLE IF NOT EXISTS bridge_tokens (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    token_hash TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TEXT NOT NULL
);
```

### Table 3: `credential_schemas`

```sql
CREATE TABLE IF NOT EXISTS credential_schemas (
    id TEXT PRIMARY KEY,
    server_name TEXT NOT NULL,
    display_name TEXT NOT NULL,
    fields_json TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

### Table 4: `user_credentials`

```sql
CREATE TABLE IF NOT EXISTS user_credentials (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    server_name TEXT NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(user_id, server_name)
);
```

### Table 5: `sso_config`

```sql
CREATE TABLE IF NOT EXISTS sso_config (
    id INTEGER PRIMARY KEY DEFAULT 1,
    config_json TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CONSTRAINT sso_config_singleton CHECK (id = 1)
);
```

### Manual Migration (if auto-migration disabled)

```bash
psql -h localhost -U mcp_admin -d mcp_orchestrator -f migrations/auth.sql
```

---

## 4. Configuration (application.yml)

Add the following to your `application.yml`:

```yaml
orchestrator:
  server:
    port: 8080
    mode: http-streamable

  auth:
    enabled: true
    # JWT settings read from env vars (see Section 2)

  user-management:
    enabled: true
    encryption_key_env: "MCP_ENCRYPTION_KEY"

  database:
    url: ${DATABASE_URL}
    user: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    pool_size: 10
```

---

## 5. Bridge Client Update

Bridge clients must pass authentication token via `--token` flag:

```bash
# Generate bridge token via API
curl -X POST http://localhost:8080/api/auth/bridge-token \
  -H "Authorization: Bearer <session-token>" \
  -H "Content-Type: application/json" \
  -d '{"expiry_days": 30}'

# Use bridge token in client
mcp-bridge --server http://localhost:8080 --token <bridge-token>
```

### Bridge Configuration File

```yaml
# ~/.mcp-bridge/config.yml
server_url: http://localhost:8080
token: <bridge-token-here>
```

---

## 6. SSO Configuration (Optional)

### 6.1 Configure IdP (Keycloak example)

1. Create a new client in your Keycloak realm
2. Set client type: `OpenID Connect`
3. Set access type: `Confidential`
4. Set redirect URI: `http://localhost:8080/api/auth/sso/callback`
5. Enable PKCE: `S256`
6. Note: client_id, client_secret, issuer URL

### 6.2 Configure SSO via Admin API

```bash
curl -X PUT http://localhost:8080/api/admin/sso/config \
  -H "X-User-Email: admin@company.com" \
  -H "Content-Type: application/json" \
  -d '{
    "enabled": true,
    "issuer_url": "https://keycloak.company.com/realms/mcp",
    "client_id": "mcp-orchestrator",
    "client_secret": "your-client-secret",
    "scopes": ["openid", "profile", "email"],
    "redirect_uri": "http://localhost:8080/api/auth/sso/callback",
    "default_role": "DEVELOPER",
    "claims_mapping": {
      "email": "email",
      "name": "name"
    },
    "auto_create_users": true
  }'
```

### 6.3 SSO Login Flow

```
Browser → GET /api/auth/sso/authorize → 302 → IdP login page
IdP → GET /api/auth/sso/callback?code=x&state=y → 302 → /portal?token=jwt
```

---

## 7. Rollback Procedures

### 7.1 Full Rollback (revert to pre-auth version)

```bash
# 1. Stop the application
systemctl stop mcp-orchestrator

# 2. Restore database backup
pg_restore -h localhost -U mcp_admin -d mcp_orchestrator backup.dump

# 3. Deploy previous JAR version
cp mcp-orchestrator-previous.jar /opt/mcp/mcp-orchestrator-all.jar

# 4. Remove auth env vars from systemd unit
systemctl daemon-reload

# 5. Start previous version
systemctl start mcp-orchestrator
```

### 7.2 Partial Rollback (disable SSO only)

```bash
# Disable SSO via API
curl -X PUT http://localhost:8080/api/admin/sso/config \
  -H "X-User-Email: admin@company.com" \
  -H "Content-Type: application/json" \
  -d '{"enabled": false, "issuer_url": "https://placeholder", "client_id": "x", "redirect_uri": "http://localhost:8080/api/auth/sso/callback"}'
```

### 7.3 Rollback Auth Columns (if needed)

```sql
-- WARNING: This removes auth data permanently
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;
ALTER TABLE users DROP COLUMN IF EXISTS auth_mode;
ALTER TABLE users DROP COLUMN IF EXISTS failed_login_attempts;
ALTER TABLE users DROP COLUMN IF EXISTS locked_until;
DROP TABLE IF EXISTS bridge_tokens;
DROP TABLE IF EXISTS sso_config;
```

---

## 8. Health Check & Verification

### 8.1 Health Endpoint

```bash
curl http://localhost:8080/health
# Expected: "OK" (HTTP 200)
```

### 8.2 Auth Verification

```bash
# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@company.com","password":"password"}'
# Expected: {"token":"...","expires_at":"...","user":{...}}

# Test token validation (use token from login)
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Authorization: Bearer <token>"
# Expected: {"token":"...","expires_at":"..."}
```

### 8.3 SSO Verification

```bash
# Check SSO config
curl http://localhost:8080/api/admin/sso/config \
  -H "X-User-Email: admin@company.com"
# Expected: {"enabled":true,...}

# Test SSO flow (browser)
# Navigate to: http://localhost:8080/api/auth/sso/authorize
# Should redirect to IdP login page
```

### 8.4 Database Verification

```sql
-- Verify tables exist
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public'
AND table_name IN ('bridge_tokens', 'credential_schemas', 'user_credentials', 'sso_config');

-- Verify auth columns on users
SELECT column_name FROM information_schema.columns
WHERE table_name = 'users' AND column_name IN ('password_hash', 'auth_mode');
```

---

## 9. Process Pool Tuning

### Thread Pool Configuration

The HTTP server uses a fixed thread pool (default: 8 threads).

| Deployment Size | Threads | Max Concurrent Users |
|----------------|---------|---------------------|
| Development | 4 | ~20 |
| Small team | 8 | ~50 |
| Medium team | 16 | ~100 |
| Large deployment | 32 | ~200 |

### Database Connection Pool

```yaml
orchestrator:
  database:
    pool_size: 10        # Default: 10 connections
    max_lifetime: 1800000  # 30 minutes
    idle_timeout: 600000   # 10 minutes
```

### Recommended Settings by Scale

| Users | DB Pool | Thread Pool | JVM Heap |
|-------|---------|-------------|----------|
| 1-20 | 5 | 4 | 512m |
| 20-50 | 10 | 8 | 1g |
| 50-100 | 20 | 16 | 2g |
| 100+ | 30 | 32 | 4g |

### JVM Options

```bash
java -Xmx2g -Xms512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -jar mcp-orchestrator-all.jar
```

---

## 10. Startup Command

```bash
# Production startup with all env vars
export JWT_SECRET="<your-jwt-secret>"
export MCP_ENCRYPTION_KEY="<your-base64-key>"
export DATABASE_URL="jdbc:postgresql://localhost:5432/mcp_orchestrator"
export DATABASE_USER="mcp_admin"
export DATABASE_PASSWORD="<db-password>"

java -Xmx2g -jar mcp-orchestrator-all.jar
```

### Systemd Service File

```ini
[Unit]
Description=MCP Orchestrator
After=postgresql.service

[Service]
Type=simple
User=mcp
WorkingDirectory=/opt/mcp
ExecStart=/usr/bin/java -Xmx2g -jar mcp-orchestrator-all.jar
EnvironmentFile=/opt/mcp/.env
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```
