# Deployment Guide (DPG)

## MTO-30: KB Refinery — Business Rules Masking (AI-based)

| Field | Value |
|-------|-------|
| **Ticket** | MTO-30 |
| **Version** | 1.0 |
| **Date** | 2026-05-08 |
| **Author** | DevOps Agent |

---

## 1. Overview

This deployment adds AI-based Business Rules Masking to the KB Refinery pipeline. The module identifies individual business rules in segmented text, replaces them with categorized placeholders, and encrypts the original content.

### 1.1 Components Affected

| Component | Change Type | Risk |
|-----------|-------------|------|
| orchestrator-server | New module (brmasking/) | Low |
| AppModule.kt | Import brMaskingModule | Low |
| application.yml | New config section | Low |

### 1.2 Dependencies

| Dependency | Required | Notes |
|------------|----------|-------|
| LangChain4j | Yes | Already added by MTO-28 |
| LLM Provider (OpenAI/Ollama) | Yes | Same provider as MTO-28 segmentation |
| MTO-28 (Segmentation) | Yes | Provides businessRules text input |
| MTO-26 (Encryption) | Yes | Provides EncryptionService for BR storage |

---

## 2. Pre-Deployment Checklist

- [ ] MTO-28 (Content Segmentation) deployed and working
- [ ] MTO-26 (KB Entries Schema + Encryption) deployed
- [ ] LLM provider configured and accessible
- [ ] All automated tests pass
- [ ] Shadow JAR built successfully

---

## 3. Configuration

Add to `application.yml`:

```yaml
orchestrator:
  brmasking:
    enabled: true
    use-segmentation-provider: true    # Reuse MTO-28 LLM config
    timeout-seconds: 15
    max-rules-per-text: 20
    encryption-key: "${BR_ENCRYPTION_KEY}"  # 32-byte base64 key
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `BR_ENCRYPTION_KEY` | Yes | Base64-encoded 32-byte AES key for BR encryption |

---

## 4. Deployment Steps

### Step 1: Set Environment Variables

```bash
export BR_ENCRYPTION_KEY="<base64-encoded-32-byte-key>"
# Generate key: openssl rand -base64 32
```

### Step 2: Build and Deploy

```bash
./gradlew :orchestrator-server:shadowJar
cp orchestrator-server/build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/
```

### Step 3: Restart Application

```bash
systemctl restart mcp-orchestrator
```

### Step 4: Verify

```bash
# Check logs for BR masking module startup
grep -i "BrMasking" /var/log/mcp-orchestrator/app.log

# Expected: "BrMaskingModule initialized"
```

---

## 5. Post-Deployment Verification

| # | Check | Expected |
|---|-------|----------|
| 1 | Module loads | Log: "BrMaskingModule initialized" |
| 2 | LLM connection | No "ProviderUnavailable" errors |
| 3 | Encryption key valid | No "ConfigException" at startup |
| 4 | End-to-end flow | Segmented BR text → masked with placeholders |

---

## 6. Rollback Plan

### Quick Rollback

```yaml
orchestrator:
  brmasking:
    enabled: false
```

Restart application. BR masking will be skipped in pipeline.

### Full Rollback

1. Stop application
2. Restore previous JAR
3. Remove `brmasking` config section
4. Restart

---

## 7. Monitoring

### Log Patterns

| Pattern | Level | Action |
|---------|-------|--------|
| "BR masking completed: N rules identified" | INFO | Normal |
| "BR masking timeout" | WARN | Check LLM latency |
| "BR encryption failed" | ERROR | Check encryption key |
| "No BR rules identified" | DEBUG | Normal for non-BR text |
