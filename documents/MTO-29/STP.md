# Software Test Plan (STP)

## MTO-29: KB Refinery — MarkItDown MCP Integration for OCR

| Field | Value |
|-------|-------|
| **Ticket** | MTO-29 |
| **Version** | 1.0 |
| **Author** | QA Agent |
| **Created** | 2026-05-10 |
| **Related Docs** | BRD-v1-MTO-29, FSD-v1-MTO-29, TDD-v1-MTO-29 |

---

## 1. Test Scope

### 1.1 In Scope

| Component | Test Focus |
|-----------|-----------|
| OcrServiceImpl | MCP tool call, timeout handling, response parsing |
| ImageTextExtractor | ContentExtractor delegation, error handling |
| OcrConfig | Configuration defaults, validation |
| OcrException | Sealed class hierarchy |
| OcrModule | Koin DI wiring |
| ToolExecutionDispatcher integration | End-to-end MCP tool call |

### 1.2 Out of Scope

- MarkItDown MCP server internals (external tool)
- Actual OCR accuracy (depends on MarkItDown)
- Image pre-processing (rotation, deskew)
- AttachmentProcessor (tested in MTO-19)
- Performance/load testing

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Count | Automation | Framework |
|-------|-------|-----------|-----------|
| Property-Based Test (PBT) | 2 | 100% | Kotest Property Testing |
| Unit Test (UT) | 14 | 100% | Kotest + MockK |
| Integration Test (IT) | 6 | 100% | Kotest + MockK (dispatcher mocked) |
| E2E-API | 3 | 100% | Koin Test |
| **Total** | **25** | **100%** | — |

### 2.2 Test Level Definitions

| Level | What | How | Dependencies |
|-------|------|-----|-------------|
| **PBT** | URI validation, config bounds | Random input generation | Kotest Arb |
| **UT** | Individual class logic in isolation | Mock all dependencies | MockK |
| **IT** | OcrService + Dispatcher together | Mock MCP responses | MockK |
| **E2E-API** | Full DI container + service invocation | Koin test module | Koin Test |

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
| MCP Server | Mocked (no real MarkItDown in tests) |

---

## 4. Requirements Traceability Matrix (RTM)

| Requirement | Test Cases | Coverage |
|-------------|-----------|----------|
| AC-01.1: PNG text extraction | IT-01, E2E-01 | ✅ |
| AC-01.2: JPG mixed content | IT-02 | ✅ |
| AC-01.3: TIFF document scan | IT-03 | ✅ |
| AC-02.1: Route through dispatcher | UT-01, IT-01 | ✅ |
| AC-02.2: Server unavailable → graceful error | UT-02, IT-04 | ✅ |
| AC-03.1: Image MIME → ImageTextExtractor | UT-03, E2E-02 | ✅ |
| AC-03.2: Non-image → existing extractors | UT-04 | ✅ |
| AC-04.1: Output as markdown string | UT-05, IT-01 | ✅ |
| AC-04.2: Empty image → empty string | UT-06, IT-05 | ✅ |
| BR-02: MCP tool call format | UT-07, IT-01 | ✅ |
| NFR-1: < 30s timeout | UT-08, IT-06 | ✅ |
| NFR-2: Graceful degradation | UT-02, UT-09, IT-04 | ✅ |
| NFR-4: Interface-first design | E2E-03 | ✅ |

---

## 5. Entry/Exit Criteria

### 5.1 Entry Criteria

| # | Criterion | Verification |
|---|-----------|-------------|
| 1 | Source code committed | Git status clean |
| 2 | Build compiles | `./gradlew compileKotlin` passes |
| 3 | BRD, FSD, TDD approved | STATUS.json phases done |
| 4 | Test environment ready | Kotest + MockK available |

### 5.2 Exit Criteria

| # | Criterion | Target |
|---|-----------|--------|
| 1 | All test cases executed | 25/25 |
| 2 | Pass rate | ≥ 95% |
| 3 | Critical defects | 0 open |
| 4 | Code coverage (line) | ≥ 80% |

---

## 6. Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| ToolExecutionDispatcher API changes | High | Low | Mock at interface level |
| Coroutine/runBlocking interaction | Medium | Medium | Test with runTest |
| MCP response format changes | Medium | Low | Validate response structure |

---

## 7. Test Data Strategy

| Data Type | Source | Location |
|-----------|--------|----------|
| Mock MCP responses | Hand-crafted JSON | `testdata/ocr-responses/` |
| Image URIs | Synthetic | Test constants |
| Error scenarios | Hand-crafted | Test code |
