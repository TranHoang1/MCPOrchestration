# Software Test Plan (STP)

## MTO-28: KB Refinery — LangChain4j Content Segmentation

| Field | Value |
|-------|-------|
| **Ticket** | MTO-28 |
| **Version** | 1.0 |
| **Author** | QA Agent |
| **Created** | 2026-05-10 |
| **Related Docs** | BRD-v1-MTO-28, FSD-v1-MTO-28, TDD-v1-MTO-28 |

---

## 1. Test Scope

### 1.1 In Scope

| Component | Test Focus |
|-----------|-----------|
| ContentSegmentationServiceImpl | Core segmentation logic, input validation, timeout handling |
| SegmentationResult | Data class serialization/deserialization |
| BrSensitivityLevel | Enum values, serialization |
| SegmentationException | Sealed class hierarchy, error messages |
| SegmentationConfig | Configuration defaults, validation |
| SegmentationAiService | LangChain4j AiService interface contract |
| SegmentationPromptBuilder | Prompt construction, few-shot examples |
| ChatModelFactory | Provider-specific model creation |
| SegmentationModule | Koin DI wiring |
| BR Local-Only Enforcement | Re-processing logic, degraded mode |

### 1.2 Out of Scope

- Actual LLM model accuracy (depends on external provider)
- PII Masking Engine (MTO-27 — upstream)
- KB Storage (downstream)
- UI testing (no UI in this ticket)
- Performance/load testing (separate concern)
- LLM model training or fine-tuning

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Count | Automation | Framework |
|-------|-------|-----------|-----------|
| Property-Based Test (PBT) | 4 | 100% | Kotest Property Testing |
| Unit Test (UT) | 20 | 100% | Kotest + MockK |
| Integration Test (IT) | 8 | 100% | Kotest + MockK (LangChain4j mocked) |
| E2E-API | 4 | 100% | Kotest + Koin Test |
| **Total** | **36** | **100%** | — |

### 2.2 Test Level Definitions

| Level | What | How | Dependencies |
|-------|------|-----|-------------|
| **PBT** | Input boundaries, serialization roundtrip | Random input generation | Kotest Arb |
| **UT** | Individual class logic in isolation | Mock all dependencies | MockK |
| **IT** | Service + AiService + Config together | Mock LLM responses, real parsing | MockK, Koin |
| **E2E-API** | Full DI container + service invocation | Koin test module with mocked LLM | Koin Test |

### 2.3 Test Approach per Component

| Component | UT | IT | PBT | E2E |
|-----------|----|----|-----|-----|
| ContentSegmentationServiceImpl | ✅ | ✅ | — | ✅ |
| SegmentationResult | ✅ | — | ✅ | — |
| BrSensitivityLevel | ✅ | — | — | — |
| SegmentationException | ✅ | — | — | — |
| SegmentationConfig | ✅ | — | ✅ | — |
| SegmentationPromptBuilder | ✅ | — | — | — |
| ChatModelFactory | ✅ | ✅ | — | — |
| SegmentationModule | — | — | — | ✅ |
| BR Local-Only | ✅ | ✅ | — | ✅ |

---

## 3. Test Environment

| Component | Specification |
|-----------|--------------|
| JDK | 21 |
| Kotlin | 2.3.20 |
| Test Framework | Kotest 5.9.1 |
| Mocking | MockK 1.14.2 |
| DI Testing | Koin Test |
| Coroutines | kotlinx.coroutines.test |
| Build | Gradle (`./gradlew test`) |
| LLM Provider | Mocked (no real LLM calls in tests) |

---

## 4. Requirements Traceability Matrix (RTM)

| Requirement ID | Requirement | Test Cases | Coverage |
|----------------|-------------|-----------|----------|
| AC-1.1 | Mixed content → 3 sections populated | UT-01, IT-01, E2E-01 | ✅ |
| AC-1.2 | No BR → businessRules empty, level null | UT-02, IT-02 | ✅ |
| AC-1.3 | Interest rate → Level 1 | UT-03, IT-03 | ✅ |
| AC-1.4 | Segmentation < 10 seconds | UT-04, IT-04 | ✅ |
| AC-1.5 | LLM timeout → graceful error | UT-05, IT-05 | ✅ |
| AC-2.1 | OpenAI configured → requests to OpenAI | UT-06, IT-06 | ✅ |
| AC-2.2 | Ollama configured → requests to Ollama | UT-07 | ✅ |
| AC-2.3 | Invalid provider → fail fast | UT-08 | ✅ |
| AC-2.4 | Env var substitution for API keys | UT-09 | ✅ |
| AC-3.1 | Level 1 keywords detected | UT-10, IT-03 | ✅ |
| AC-3.2 | Level 2 keywords detected | UT-11 | ✅ |
| AC-3.3 | Level 3 keywords detected | UT-12 | ✅ |
| AC-3.4 | Multiple levels → most restrictive | UT-13 | ✅ |
| AC-4.1 | brLocalOnly + cloud + BR → re-process | UT-14, IT-07, E2E-03 | ✅ |
| AC-4.2 | brLocalOnly + Ollama → no re-process | UT-15 | ✅ |
| AC-4.3 | Ollama unavailable → degraded mode | UT-16, IT-08 | ✅ |
| AC-5.1 | AiService annotated correctly | UT-17 | ✅ |
| AC-5.2 | Few-shot examples in prompt | UT-18 | ✅ |
| AC-5.3 | JSON output parsed correctly | UT-19, PBT-01 | ✅ |
| BR-01 | publicContent = metadata | IT-01 | ✅ |
| BR-02 | technicalContent = code/logs | IT-01 | ✅ |
| BR-03 | businessRules = rates/conditions | IT-01 | ✅ |
| BR-07 | BR never sent to cloud when local-only | IT-07, E2E-03 | ✅ |
| BR-10 | Max 10s timeout | UT-04, UT-05 | ✅ |
| BR-14 | API keys from env vars | UT-09 | ✅ |
| NFR-1 | Graceful degradation | UT-16, IT-08, E2E-04 | ✅ |
| NFR-2 | Token efficiency < 4000 | UT-20 | ✅ |

---

## 5. Entry/Exit Criteria

### 5.1 Entry Criteria

| # | Criterion | Verification |
|---|-----------|-------------|
| 1 | All source code committed | Git status clean |
| 2 | Build compiles without errors | `./gradlew compileKotlin` passes |
| 3 | BRD, FSD, TDD reviewed and approved | STATUS.json phases done |
| 4 | Test environment configured | Kotest + MockK available |

### 5.2 Exit Criteria

| # | Criterion | Target |
|---|-----------|--------|
| 1 | All test cases executed | 36/36 |
| 2 | Pass rate | ≥ 95% (34/36 minimum) |
| 3 | Critical defects | 0 open |
| 4 | Code coverage (line) | ≥ 80% |
| 5 | All PBT pass with 100 iterations | 100% |

---

## 6. Test Schedule

| Phase | Duration | Activities |
|-------|----------|-----------|
| Test Design | 1 day | Write test cases, prepare test data |
| Test Implementation | 2 days | Code unit tests, integration tests |
| Test Execution | 1 day | Run all tests, fix failures |
| Defect Resolution | 1 day | Fix bugs, re-test |

---

## 7. Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| LangChain4j API changes in beta | High | Medium | Pin version, mock in tests |
| Flaky tests due to coroutine timing | Medium | Low | Use runTest{}, deterministic delays |
| MockK limitations with Java interfaces | Medium | Low | Use relaxed mocks, verify behavior |
| Test isolation issues with Koin | Low | Medium | Use koinApplication per test |

---

## 8. Test Data Strategy

| Data Type | Source | Location |
|-----------|--------|----------|
| Mixed content samples | Hand-crafted | `testdata/segmentation-mixed.csv` |
| Metadata-only samples | Hand-crafted | `testdata/segmentation-metadata.csv` |
| BR sensitivity samples | Hand-crafted | `testdata/segmentation-br-levels.csv` |
| LLM response mocks | Hand-crafted JSON | `testdata/llm-responses/` |
| Edge cases | PBT-generated | Runtime |

---

## 9. Defect Management

| Severity | Response Time | Resolution Time |
|----------|--------------|-----------------|
| Critical (test blocked) | Immediate | Same day |
| High (wrong behavior) | 4 hours | 1 day |
| Medium (edge case) | 1 day | 2 days |
| Low (cosmetic) | 2 days | Next sprint |

---

## 10. Appendix

### 10.1 Test Case Summary by Level

| Level | IDs | Count |
|-------|-----|-------|
| PBT | PBT-01 to PBT-04 | 4 |
| UT | UT-01 to UT-20 | 20 |
| IT | IT-01 to IT-08 | 8 |
| E2E-API | E2E-01 to E2E-04 | 4 |

### 10.2 Diagram Index

| # | Diagram | Image | Source (editable) |
|---|---------|-------|-------------------|
| 1 | Test Coverage Overview | [test-coverage.png](diagrams/test-coverage.png) | [test-coverage.drawio](diagrams/test-coverage.drawio) |
| 2 | Test Execution Flow | [test-execution-flow.png](diagrams/test-execution-flow.png) | [test-execution-flow.drawio](diagrams/test-execution-flow.drawio) |
