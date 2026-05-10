# Deployment Guide (DPG)

## MTO-28: KB Refinery — LangChain4j Content Segmentation

| Field | Value |
|-------|-------|
| **Ticket** | MTO-28 |
| **Version** | 1.0 |
| **Author** | DevOps Agent |
| **Created** | 2026-05-10 |
| **Related Docs** | TDD-v1-MTO-28, RLN-v1-MTO-28 |

---

## 1. Overview

This deployment adds the LangChain4j Content Segmentation module to the MCPOrchestration server. The module classifies PII-masked text into Public Metadata, Technical Content, and Business Rules using configurable LLM providers.

### 1.1 Change Summary

| Category | Change |
|----------|--------|
| New Dependencies | LangChain4j 1.0.0-beta1 (core + openai + ollama + azure) |
| New Package | `com.orchestrator.mcp.segmentation` (10 files) |
| Modified Files | build.gradle.kts, AppModule.kt, application.yml |
| Configuration | New `orchestrator.segmentation` YAML section |
| External Services | LLM provider (OpenAI/Ollama/Azure) |

### 1.2 Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| LLM provider unavailable | Segmentation fails (non-blocking) | Feature flag `segmentation.enabled` |
| API key misconfigured | Service fails to start | Fail-fast validation on startup |
| Increased JAR size (~15MB) | Longer deploy time | Acceptable for fat JAR |
| LangChain4j beta instability | Runtime errors | Pinned version, isolated module |

---

## 2. Pre-Deployment Checklist

| # | Check | Command/Action | Expected |
|---|-------|---------------|----------|
| 1 | Build passes | `./gradlew build` | BUILD SUCCESSFUL |
| 2 | Tests pass | `./gradlew test` | All tests pass |
| 3 | Shadow JAR created | `./gradlew shadowJar` | `mcp-orchestrator-all.jar` exists |
| 4 | LLM provider accessible | `curl https://api.openai.com/v1/models` | 200 OK (or Ollama: `curl http://localhost:11434/api/tags`) |
| 5 | API key configured | Check env var `OPENAI_API_KEY` | Non-empty |
| 6 | application.yml updated | Verify segmentation section exists | Config present |
| 7 | Disk space | Check available space | ≥ 100MB free |
| 8 | JDK version | `java -version` | JDK 21+ |

---

## 3. Deployment Steps

### 3.1 Build

```bash
# From project root
./gradlew clean shadowJar

# Verify JAR
ls -la orchestrator-server/build/libs/mcp-orchestrator-all.jar
```

### 3.2 Configuration

Add to `application.yml`:

```yaml
orchestrator:
  segmentation:
    enabled: true
    provider: "openai"              # openai | ollama | azure
    model-name: "gpt-4o-mini"
    temperature: 0.1
    max-tokens: 2000
    api-key: "${OPENAI_API_KEY}"
    timeout-seconds: 10
    br-local-only: false
    ollama-url: "http://localhost:11434"
    ollama-model: "llama3"
```

### 3.3 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `OPENAI_API_KEY` | Yes (if provider=openai) | OpenAI API key |
| `AZURE_OPENAI_KEY` | Yes (if provider=azure) | Azure OpenAI key |
| `SEGMENTATION_ENABLED` | No | Override enabled flag (true/false) |

### 3.4 Deploy

```bash
# Stop current instance
systemctl stop mcp-orchestrator

# Backup current JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak

# Deploy new JAR
cp orchestrator-server/build/libs/mcp-orchestrator-all.jar /opt/mcp-orchestrator/

# Start service
systemctl start mcp-orchestrator

# Check status
systemctl status mcp-orchestrator
```

### 3.5 For Development (Local)

```bash
# Run directly
java -jar orchestrator-server/build/libs/mcp-orchestrator-all.jar

# Or via Gradle
./gradlew :orchestrator-server:run
```

---

## 4. Post-Deployment Verification

### 4.1 Health Check

```bash
# Verify server starts without errors
curl http://localhost:8080/health
# Expected: 200 OK

# Check logs for segmentation module initialization
grep "SegmentationModule" /var/log/mcp-orchestrator/app.log
# Expected: "Segmentation module initialized with provider: openai"
```

### 4.2 Functional Verification

```bash
# Verify segmentation service is available via Koin DI
# (Internal service — verify via application logs on first pipeline run)
grep "segmentation" /var/log/mcp-orchestrator/app.log | tail -5
```

### 4.3 Smoke Test

Trigger a KB Refinery pipeline run with a test ticket. Verify:
1. Segmentation service is invoked
2. Result contains classified content
3. Processing time < 10 seconds
4. No errors in logs

---

## 5. Rollback Plan

### 5.1 Quick Disable (No Restart)

```yaml
# Set in application.yml or env var
orchestrator:
  segmentation:
    enabled: false
```

Restart service. Pipeline will skip segmentation and pass raw text through.

### 5.2 Full Rollback

```bash
# Stop service
systemctl stop mcp-orchestrator

# Restore backup JAR
cp /opt/mcp-orchestrator/mcp-orchestrator-all.jar.bak /opt/mcp-orchestrator/mcp-orchestrator-all.jar

# Start service
systemctl start mcp-orchestrator

# Verify rollback
curl http://localhost:8080/health
```

### 5.3 Rollback Criteria

| Condition | Action |
|-----------|--------|
| Service fails to start | Immediate rollback |
| Segmentation errors > 50% of requests | Disable via config |
| LLM costs exceed budget | Disable via config |
| Performance degradation (>30s response) | Disable via config |

---

## 6. Monitoring

### 6.1 Log Monitoring

| Log Pattern | Severity | Action |
|-------------|----------|--------|
| "Segmentation module initialized" | INFO | Expected on startup |
| "LLM timeout" | WARN | Monitor frequency |
| "Provider unavailable" | ERROR | Alert ops team |
| "BR local-only enforcement failed" | WARN | Security review |
| "Invalid LLM response" | WARN | Check prompt/model |

### 6.2 Alerts

| Alert | Threshold | Channel |
|-------|-----------|---------|
| Segmentation error rate | > 10% in 5 min | Slack #ops |
| LLM timeout rate | > 5% in 5 min | Slack #ops |
| Provider unavailable | Any occurrence | PagerDuty |

---

## 7. Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| langchain4j | 1.0.0-beta1 | Core LLM framework |
| langchain4j-open-ai | 1.0.0-beta1 | OpenAI provider |
| langchain4j-ollama | 1.0.0-beta1 | Ollama provider |
| langchain4j-azure-open-ai | 1.0.0-beta1 | Azure provider |

---

## 8. Appendix

### 8.1 File Changes Summary

| File | Change Type | Description |
|------|------------|-------------|
| orchestrator-server/build.gradle.kts | Modified | Added LangChain4j dependencies |
| orchestrator-server/src/main/kotlin/.../segmentation/ | New (10 files) | Segmentation module |
| orchestrator-server/src/main/kotlin/.../di/AppModule.kt | Modified | Include segmentationModule |
| application.yml | Modified | Added segmentation config section |
