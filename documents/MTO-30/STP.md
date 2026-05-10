# Software Test Plan (STP)

## MCPOrchestration — MTO-30: Business Rules Masking (AI-based)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-30 |
| Title | KB Refinery — Business Rules Masking (AI-based) |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-30.docx |
| Related FSD | FSD-v1-MTO-30.docx |
| Related TDD | TDD-v1-MTO-30.docx |

---

## 1. Test Scope

### 1.1 In Scope

| # | Component | Description |
|---|-----------|-------------|
| 1 | BrMaskingServiceImpl | Core masking logic with LLM integration |
| 2 | BrEncryptionService | AES-256-GCM encrypt/decrypt |
| 3 | BrIdentificationAiService | LLM prompt and response parsing |
| 4 | BrMaskingModule (DI) | Koin dependency injection wiring |
| 5 | Model classes | BrMaskingResult, BrPlaceholder, BrCategory, BrMaskingConfig |
| 6 | Error handling | Sealed exception hierarchy |

### 1.2 Out of Scope

- Key rotation mechanism
- Multi-tenant key management
- UI for BR management
- Performance/load testing (separate ticket)

---

## 2. Test Strategy

### 2.1 Test Levels

| Level | Abbreviation | Purpose | Automation | Framework |
|-------|-------------|---------|------------|-----------|
| Property-Based Testing | PBT | Verify invariants (encrypt↔decrypt roundtrip) | 100% | Kotest Property |
| Unit Testing | UT | Test individual classes in isolation | 100% | Kotest + MockK |
| Integration Testing | IT | Test component interactions (DI wiring, full flow) | 100% | Kotest + Koin Test |
| E2E API Testing | E2E-API | N/A (internal service, no HTTP API) | N/A | N/A |
| E2E UI Testing | E2E-UI | N/A (no UI component) | N/A | N/A |
| System Integration Testing | SIT | Verify with real LLM provider | Manual | Manual verification |

**Note:** MTO-30 is an internal service module (no HTTP endpoints, no UI). E2E-API and E2E-UI levels are not applicable. SIT verifies real LLM integration manually.

### 2.2 Test Approach by Component

| Component | Level | Technique | Mocking |
|-----------|-------|-----------|---------|
| BrEncryptionService | PBT + UT | Property tests for roundtrip, unit tests for edge cases | None (pure crypto) |
| BrMaskingServiceImpl | UT + IT | Mock LLM responses, verify masking logic | MockK for AiService |
| BrIdentificationAiService | SIT | Real LLM call verification | None (real provider) |
| BrMaskingModule | IT | Koin DI verification | Mock external deps |
| Model classes | UT | Serialization, enum coverage | None |

### 2.3 Entry/Exit Criteria

**Entry Criteria:**
- Source code compiled without errors
- All dependencies available (LangChain4j, javax.crypto)
- Test encryption key configured

**Exit Criteria:**
- All PBT properties pass (100 iterations minimum)
- All UT pass (100% of critical paths)
- All IT pass (DI wiring, full flow)
- No Critical/High severity bugs open
- Code coverage ≥ 80% for brmasking package

---

## 3. Test Environment

### 3.1 Environment Configuration

| Item | Value |
|------|-------|
| JDK | Java 21 |
| Kotlin | 2.0+ |
| Test Framework | Kotest 5.x (FunSpec style) |
| Mocking | MockK |
| DI Testing | Koin Test |
| Property Testing | Kotest Property |
| Build Tool | Gradle (./gradlew test) |

### 3.2 Test Data

| Data | Source | Location |
|------|--------|----------|
| BR content samples (Vietnamese) | Manual creation | Test fixtures in test class |
| Encryption key (test) | Generated 32-byte key | Test constant (Base64) |
| LLM response mocks | Based on FSD §3.5 prompt spec | MockK responses |

### 3.3 Test Encryption Key

```
Test key (32 bytes, Base64): dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==
```

---

## 4. Requirements Traceability Matrix (RTM)

| BRD Req | FSD UC | Test Cases | Level | Priority |
|---------|--------|------------|-------|----------|
| BR-01 (AI identification) | UC-01 Main Flow | PBT-01, UT-01, UT-02, UT-03, IT-01 | PBT+UT+IT | P0 |
| BR-02 (Categorization) | UC-01 Step 3 | UT-04, UT-05, UT-06, UT-07, UT-08 | UT | P0 |
| BR-03 (Placeholder format) | UC-01 Step 4 | PBT-02, UT-09, UT-10, UT-11 | PBT+UT | P0 |
| BR-04 (Encryption) | UC-01 Step 5 | PBT-03, PBT-04, UT-12, UT-13, UT-14, UT-15 | PBT+UT | P0 |
| BR-05 (Unmasking) | UC-02 | UT-16, UT-17, IT-02 | UT+IT | P0 |
| FSD BR-05 (Fail-safe) | UC-01 Exception | UT-18, UT-19 | UT | P0 |
| NFR-3 (Performance) | — | UT-20 | UT | P1 |
| Config validation | FSD §3.6 | UT-21, UT-22, IT-03 | UT+IT | P1 |

---

## 5. Test Case Summary

### 5.1 By Level

| Level | Count | Automated | Manual |
|-------|-------|-----------|--------|
| PBT | 4 | 4 | 0 |
| UT | 22 | 22 | 0 |
| IT | 4 | 4 | 0 |
| SIT | 2 | 0 | 2 |
| **Total** | **32** | **30** | **2** |

### 5.2 By Priority

| Priority | Count |
|----------|-------|
| P0 (Critical) | 24 |
| P1 (High) | 6 |
| P2 (Medium) | 2 |

---

## 6. Risk Assessment

| # | Risk | Impact | Mitigation |
|---|------|--------|-----------|
| 1 | LLM response format changes | Tests break | Mock responses based on contract, not implementation |
| 2 | Encryption key not set in CI | IT tests fail | Use test-specific key in test fixtures |
| 3 | Kotest property test flakiness | False failures | Use fixed seed for reproducibility |
| 4 | LLM timeout in SIT | SIT fails | Increase timeout, retry once |

---

## 7. Test Schedule

| Phase | Duration | Dependencies |
|-------|----------|-------------|
| PBT + UT implementation | 1 day | Source code complete |
| IT implementation | 0.5 day | UT passing |
| SIT execution | 0.5 day | IT passing, LLM API key |
| Bug fixes | 0.5 day | Test results |

---

## 8. Deliverables

| # | Deliverable | Format | Location |
|---|-------------|--------|----------|
| 1 | Test Plan (this document) | Markdown/DOCX | documents/MTO-30/STP.md |
| 2 | Test Cases | Markdown/DOCX | documents/MTO-30/STC.md |
| 3 | Test Source Code | Kotlin | orchestrator-server/src/test/kotlin/com/orchestrator/mcp/brmasking/ |
| 4 | Test Report | Markdown/DOCX | documents/MTO-30/TEST-REPORT-MTO-30.md |
