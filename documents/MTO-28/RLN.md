# Release Notes (RLN)

## MTO-28: KB Refinery — LangChain4j Content Segmentation

| Field | Value |
|-------|-------|
| **Ticket** | MTO-28 |
| **Version** | 1.0.0 |
| **Release Date** | 2026-05-10 |
| **Author** | DevOps Agent |
| **Type** | Feature |

---

## 1. Summary

Added LLM-based content segmentation to the KB Refinery pipeline. The service classifies PII-masked text into three content categories (Public Metadata, Technical Content, Business Rules) with sensitivity level classification for business rules.

---

## 2. New Features

### 2.1 Content Segmentation Service

- **LLM-based classification**: Uses LangChain4j to classify text into 3 categories
- **Multi-provider support**: OpenAI, Ollama (local), Azure OpenAI
- **BR Sensitivity Levels**: Automatic classification (Level 1-3) for business rules
- **Local-only enforcement**: Option to process sensitive BR content only via local LLM
- **Graceful degradation**: Service continues in degraded mode if local LLM unavailable

### 2.2 Configuration

- New YAML section: `orchestrator.segmentation`
- Feature flag: `segmentation.enabled` (default: true)
- Provider switchable without code change
- Environment variable support for API keys

---

## 3. Technical Changes

### 3.1 New Files (10)

| File | Purpose |
|------|---------|
| ContentSegmentationService.kt | Public API interface |
| ContentSegmentationServiceImpl.kt | Core implementation |
| SegmentationResult.kt | Result data class |
| BrSensitivityLevel.kt | Sensitivity enum |
| SegmentationException.kt | Error hierarchy |
| SegmentationConfig.kt | Configuration |
| SegmentationAiService.kt | LangChain4j interface |
| SegmentationPromptBuilder.kt | Prompt construction |
| ChatModelFactory.kt | Provider factory |
| SegmentationModule.kt | Koin DI module |

### 3.2 Modified Files

| File | Change |
|------|--------|
| build.gradle.kts | Added LangChain4j dependencies |
| AppModule.kt | Included segmentationModule |
| application.yml | Added segmentation config |

### 3.3 New Dependencies

| Dependency | Version |
|------------|---------|
| dev.langchain4j:langchain4j | 1.0.0-beta1 |
| dev.langchain4j:langchain4j-open-ai | 1.0.0-beta1 |
| dev.langchain4j:langchain4j-ollama | 1.0.0-beta1 |
| dev.langchain4j:langchain4j-azure-open-ai | 1.0.0-beta1 |

---

## 4. Configuration Changes

### 4.1 New Configuration (application.yml)

```yaml
orchestrator:
  segmentation:
    enabled: true
    provider: "openai"
    model-name: "gpt-4o-mini"
    temperature: 0.1
    max-tokens: 2000
    api-key: "${OPENAI_API_KEY}"
    timeout-seconds: 10
    br-local-only: false
    ollama-url: "http://localhost:11434"
    ollama-model: "llama3"
```

### 4.2 New Environment Variables

| Variable | Required | Default |
|----------|----------|---------|
| OPENAI_API_KEY | If provider=openai | — |
| AZURE_OPENAI_KEY | If provider=azure | — |

---

## 5. Breaking Changes

None. This is a new module with no impact on existing functionality.

---

## 6. Known Issues

| Issue | Severity | Workaround |
|-------|----------|------------|
| LangChain4j is beta (1.0.0-beta1) | Low | Version pinned, isolated module |
| Ollama must be running for br-local-only | Medium | Set br-local-only=false if no Ollama |
| No caching for repeated inputs | Low | Future enhancement |

---

## 7. Migration Guide

### From Previous Version

No migration needed. New module is additive.

### Required Actions

1. Set `OPENAI_API_KEY` environment variable (or configure alternative provider)
2. Review `application.yml` segmentation section
3. If using br-local-only mode, ensure Ollama is running

---

## 8. Testing

| Level | Count | Pass Rate |
|-------|-------|-----------|
| PBT | 4 | 100% |
| Unit | 20 | 100% |
| Integration | 8 | 100% |
| E2E-API | 4 | 100% |
| **Total** | **36** | **100%** |

---

## 9. Rollback

Set `orchestrator.segmentation.enabled: false` to disable without rollback. For full rollback, restore previous JAR from backup.
