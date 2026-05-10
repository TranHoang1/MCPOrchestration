# Deployment Guide (DPG)

## MCPOrchestration — MTO-39: User Management & Document Approval

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-39 |
| Title | Deployment Guide — User Management & Document Approval |
| Author | DevOps Agent |
| Version | 1.0 |
| Date | 2026-05-10 |

---

## 1. Prerequisites

### 1.1 Infrastructure Requirements

| Component | Requirement | Notes |
|-----------|-------------|-------|
| JDK | 21+ | Already deployed |
| PostgreSQL | 16+ | Existing instance (KB store) |
| MCP Orchestrator Server | HTTP standalone mode | Existing deployment |
| Environment Variable | `USER_MGMT_ENCRYPTION_KEY` | **NEW** — must be set before deploy |

### 1.2 Generate Encryption Key

```bash
# Generate a 32-byte random key, base64-encoded
openssl rand -base64 32
# Example output: dGhpcyBpcyBhIDMyIGJ5dGUga2V5IGZvciBhZXM=
```

**Store this key securely** — it encrypts all Jira API tokens at rest.

---

## 2. Deployment Steps

### Step 1: Set Environment Variable

```bash
# Linux/macOS
export USER_MGMT_ENCRYPTION_KEY="<your-generated-key>"

# Windows (PowerShell)
$env:USER_MGMT_ENCRYPTION_KEY = "<your-generated-key>"

# Docker
docker run -e USER_MGMT_ENCRYPTION_KEY="<key>" ...

# systemd service file
Environment=USER_MGMT_ENCRYPTION_KEY=<key>
```

### Step 2: Build Fat JAR

```bash
./gradlew :orchestrator-server:shadowJar
# Output: orchestrator-server/build/libs/mcp-orchestrator-all.jar
```

### Step 3: Deploy JAR

```bash
# Stop existing server
systemctl stop mcp-orchestrator

# Replace JAR
cp orchestrator-server/build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/

# Start server
systemctl start mcp-orchestrator
```

### Step 4: Verify Deployment

```bash
# Check health endpoint
curl http://localhost:8080/health
# Expected: OK

# Check admin endpoint (requires admin user in DB)
curl -H "X-User-Email: admin@company.com" http://localhost:8080/admin/roles
# Expected: JSON array of role permissions (42 entries)

# Check logs for migration
grep "User Management migration complete" /var/log/mcp-orchestrator.log
grep "Seeding default permission matrix" /var/log/mcp-orchestrator.log
```

### Step 5: Create Initial Admin User

The first user must be created directly in the database (bootstrap):

```sql
-- Generate encryption key first, then encrypt a Jira token manually
-- Or use the application's encryption endpoint (if available)

INSERT INTO users (email, jira_token_encrypted, role, display_name, active)
VALUES (
    'admin@company.com',
    '<encrypted-token>',  -- Use application to encrypt
    'SYSTEM_OWNER',
    'System Admin',
    true
);
```

**Alternative:** Use the MCP tool `approve_document` with the shared service account to bootstrap the first admin.

---

## 3. Configuration

### 3.1 application.yml Changes

```yaml
orchestrator:
  user_management:
    enabled: true
    encryption_key_env: "USER_MGMT_ENCRYPTION_KEY"
    jira_token_validation: true
    max_users_per_project: 50
    admin_header_name: "X-User-Email"
```

### 3.2 Database Tables Created Automatically

| Table | Records | Notes |
|-------|---------|-------|
| users | 0 (empty) | Populated via Admin API |
| user_projects | 0 (empty) | Populated via Admin API |
| role_permissions | 42 | Auto-seeded with default matrix |
| approval_log | 0 (empty) | Populated by approval actions |

---

## 4. Rollback Plan

### 4.1 Quick Rollback

```bash
# Stop server
systemctl stop mcp-orchestrator

# Restore previous JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Start server (old version ignores new tables)
systemctl start mcp-orchestrator
```

### 4.2 Full Rollback (Remove Tables)

```sql
-- Only if you need to completely remove the feature
DROP TABLE IF EXISTS approval_log CASCADE;
DROP TABLE IF EXISTS user_projects CASCADE;
DROP TABLE IF EXISTS role_permissions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
```

**Warning:** This deletes all user data and approval history permanently.

---

## 5. Post-Deployment Verification

| Check | Command | Expected |
|-------|---------|----------|
| Server starts | `curl localhost:8080/health` | "OK" |
| Tables created | `psql -c "\dt users"` | Table exists |
| Permissions seeded | `psql -c "SELECT count(*) FROM role_permissions"` | 42 |
| Admin API accessible | `curl -H "X-User-Email: admin@co.com" localhost:8080/admin/roles` | 200 JSON |
| MCP tools registered | Check server logs | "Registered user management tools" |
| No token in logs | `grep -i "token" /var/log/mcp-orchestrator.log` | No plaintext tokens |

---

## 6. Security Checklist

- [ ] `USER_MGMT_ENCRYPTION_KEY` set and not in version control
- [ ] Key stored in secrets manager (Vault, AWS Secrets Manager, etc.)
- [ ] PostgreSQL connection uses SSL in production
- [ ] Admin API not exposed to public internet (internal network only)
- [ ] Logs do not contain Jira API tokens
- [ ] Database backups include users table (encrypted tokens)

---

## 7. Monitoring

### 7.1 Key Metrics

| Metric | Alert Threshold | Action |
|--------|----------------|--------|
| Admin API 5xx errors | > 5 per minute | Check DB connectivity |
| Approval failures (permission) | > 20 per hour | Check permission matrix config |
| Jira sync pending | > 10 entries | Check Jira API connectivity |
| DB connection pool exhaustion | > 80% | Scale connection pool |

### 7.2 Log Patterns to Monitor

```
# Successful operations
"User Management startup complete"
"Creating user: email=..."
"Approving ... for ... by user ..."

# Warnings
"User Management startup failed (non-critical)"
"Jira token validation failed"

# Errors (require attention)
"Encryption key env '...' not set"
"Cannot deactivate last system_owner"
```
