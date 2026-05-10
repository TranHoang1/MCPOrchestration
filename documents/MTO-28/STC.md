# Software Test Cases (STC)

## MTO-28: KB Refinery — LangChain4j Content Segmentation

| Field | Value |
|-------|-------|
| **Ticket** | MTO-28 |
| **Version** | 1.0 |
| **Author** | QA Agent |
| **Created** | 2026-05-10 |
| **Related STP** | STP-v1-MTO-28 |

---

## 1. Property-Based Tests (PBT)

### PBT-01: SegmentationResult Serialization Roundtrip

| Field | Value |
|-------|-------|
| **ID** | PBT-01 |
| **Component** | SegmentationResult |
| **Requirement** | AC-5.3 |
| **Technique** | Kotest Property Testing (Arb) |
| **Iterations** | 100 |

**Property:** For any valid SegmentationResult, `json.decodeFromString(json.encodeToString(result)) == result`

**Generators:**
- publicContent: Arb.string(0..500)
- technicalContent: Arb.string(0..500)
- businessRules: Arb.string(0..500)
- brSensitivityLevel: Arb.enum<BrSensitivityLevel>().orNull()
- processingTimeMs: Arb.long(0..60000)
- provider: Arb.element("openai", "ollama", "azure")
- degraded: Arb.boolean()

---

### PBT-02: Input Truncation Preserves Max Length

| Field | Value |
|-------|-------|
| **ID** | PBT-02 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | BR-10, EF-02 |
| **Technique** | Kotest Property Testing |
| **Iterations** | 100 |

**Property:** For any string with length > 10,000, after truncation the processed text length == 10,000

**Generators:**
- maskedText: Arb.string(10001..20000)

---

### PBT-03: Config Temperature Bounds

| Field | Value |
|-------|-------|
| **ID** | PBT-03 |
| **Component** | SegmentationConfig |
| **Requirement** | TDD §7.4 |
| **Technique** | Kotest Property Testing |
| **Iterations** | 100 |

**Property:** SegmentationConfig with any temperature value can be created (data class allows any Double), but ChatModelFactory should clamp to 0.0..1.0 range when building model.

**Generators:**
- temperature: Arb.double(-10.0..10.0)

---

### PBT-04: BrSensitivityLevel Enum Completeness

| Field | Value |
|-------|-------|
| **ID** | PBT-04 |
| **Component** | BrSensitivityLevel |
| **Requirement** | AC-3.1, AC-3.2, AC-3.3 |
| **Technique** | Kotest Property Testing |
| **Iterations** | 100 |

**Property:** For any BrSensitivityLevel value, serialization produces a valid JSON string that can be deserialized back to the same enum value.

**Generators:**
- level: Arb.enum<BrSensitivityLevel>()

---

## 2. Unit Tests (UT)

### UT-01: Segment Mixed Content Successfully

| Field | Value |
|-------|-------|
| **ID** | UT-01 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-1.1 |
| **Priority** | High |

**Preconditions:** AiService mocked to return valid JSON with all 3 sections populated

**Steps:**
1. Create mock SegmentationAiService returning: `{"publicContent":"ID: MTO-100","technicalContent":"NPE at line 42","businessRules":"rate = 12.5%","brSensitivityLevel":"LEVEL_1"}`
2. Create ContentSegmentationServiceImpl with default config and mocked aiService
3. Call `segment("mixed content text")`
4. Verify result.publicContent == "ID: MTO-100"
5. Verify result.technicalContent == "NPE at line 42"
6. Verify result.businessRules == "rate = 12.5%"
7. Verify result.brSensitivityLevel == LEVEL_1

**Expected:** All 3 content sections populated, sensitivity level assigned

---

### UT-02: Segment Text Without Business Rules

| Field | Value |
|-------|-------|
| **ID** | UT-02 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-1.2 |
| **Priority** | High |

**Preconditions:** AiService mocked to return JSON with empty businessRules

**Steps:**
1. Mock AiService returning: `{"publicContent":"Ticket info","technicalContent":"stack trace","businessRules":"","brSensitivityLevel":null}`
2. Call `segment("metadata and technical only")`
3. Verify result.businessRules == ""
4. Verify result.brSensitivityLevel == null

**Expected:** businessRules empty, brSensitivityLevel null

---

### UT-03: Detect BR Sensitivity Level 1

| Field | Value |
|-------|-------|
| **ID** | UT-03 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-1.3, AC-3.1 |
| **Priority** | High |

**Preconditions:** AiService mocked to return LEVEL_1 for interest rate content

**Steps:**
1. Mock AiService returning: `{"publicContent":"","technicalContent":"","businessRules":"lãi suất = base + 2.5%","brSensitivityLevel":"LEVEL_1"}`
2. Call `segment("lãi suất cho vay tiêu dùng: base + 2.5%/năm")`
3. Verify result.brSensitivityLevel == BrSensitivityLevel.LEVEL_1

**Expected:** LEVEL_1 (Confidential) assigned

---

### UT-04: Timeout Configuration Respected

| Field | Value |
|-------|-------|
| **ID** | UT-04 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-1.4, BR-10 |
| **Priority** | High |

**Preconditions:** Config with timeoutSeconds = 2

**Steps:**
1. Create config with timeoutSeconds = 2
2. Mock AiService with delay(3000) before returning
3. Call `segment("any text")`
4. Verify LlmTimeoutException is thrown

**Expected:** LlmTimeoutException thrown after 2 seconds

---

### UT-05: LLM Timeout Throws Correct Exception

| Field | Value |
|-------|-------|
| **ID** | UT-05 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-1.5 |
| **Priority** | High |

**Preconditions:** AiService throws TimeoutCancellationException

**Steps:**
1. Mock AiService to throw kotlinx.coroutines.TimeoutCancellationException
2. Call `segment("text")`
3. Verify SegmentationException.LlmTimeoutException is thrown
4. Verify exception message contains timeout duration

**Expected:** LlmTimeoutException with correct timeout value

---

### UT-06: ChatModelFactory Creates OpenAI Model

| Field | Value |
|-------|-------|
| **ID** | UT-06 |
| **Component** | ChatModelFactory |
| **Requirement** | AC-2.1 |
| **Priority** | High |

**Preconditions:** Config with provider = "openai", apiKey set

**Steps:**
1. Create SegmentationConfig(provider = "openai", apiKey = "test-key", modelName = "gpt-4o-mini")
2. Call ChatModelFactory().create(config)
3. Verify returned model is instance of OpenAiChatModel

**Expected:** OpenAiChatModel instance created

---

### UT-07: ChatModelFactory Creates Ollama Model

| Field | Value |
|-------|-------|
| **ID** | UT-07 |
| **Component** | ChatModelFactory |
| **Requirement** | AC-2.2 |
| **Priority** | High |

**Preconditions:** Config with provider = "ollama"

**Steps:**
1. Create SegmentationConfig(provider = "ollama", ollamaUrl = "http://localhost:11434")
2. Call ChatModelFactory().create(config)
3. Verify returned model is instance of OllamaChatModel

**Expected:** OllamaChatModel instance created

---

### UT-08: ChatModelFactory Rejects Invalid Provider

| Field | Value |
|-------|-------|
| **ID** | UT-08 |
| **Component** | ChatModelFactory |
| **Requirement** | AC-2.3 |
| **Priority** | High |

**Preconditions:** Config with provider = "invalid"

**Steps:**
1. Create SegmentationConfig(provider = "invalid")
2. Call ChatModelFactory().create(config)
3. Verify IllegalArgumentException is thrown
4. Verify message contains "Unsupported provider"

**Expected:** IllegalArgumentException with descriptive message

---

### UT-09: API Key From Environment Variable

| Field | Value |
|-------|-------|
| **ID** | UT-09 |
| **Component** | ChatModelFactory |
| **Requirement** | AC-2.4, BR-14 |
| **Priority** | Medium |

**Preconditions:** Config with apiKey = null, env var OPENAI_API_KEY set

**Steps:**
1. Create SegmentationConfig(provider = "openai", apiKey = null)
2. Verify ChatModelFactory falls back to System.getenv("OPENAI_API_KEY")

**Expected:** Factory uses environment variable when apiKey is null

---

### UT-10: BR Level 1 — Interest Rate Keywords

| Field | Value |
|-------|-------|
| **ID** | UT-10 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-3.1, BR-04 |
| **Priority** | High |

**Preconditions:** AiService returns LEVEL_1 for rate content

**Steps:**
1. Mock AiService to return LEVEL_1 for "phí trả nợ trước hạn: 3%"
2. Call segment with rate-related text
3. Verify brSensitivityLevel == LEVEL_1

**Expected:** LEVEL_1 for financial rate content

---

### UT-11: BR Level 2 — Approval Conditions

| Field | Value |
|-------|-------|
| **ID** | UT-11 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-3.2, BR-05 |
| **Priority** | High |

**Preconditions:** AiService returns LEVEL_2

**Steps:**
1. Mock AiService to return LEVEL_2 for "điều kiện duyệt vay: thu nhập >= 10 triệu"
2. Call segment
3. Verify brSensitivityLevel == LEVEL_2

**Expected:** LEVEL_2 for approval condition content

---

### UT-12: BR Level 3 — SLA/Process

| Field | Value |
|-------|-------|
| **ID** | UT-12 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-3.3, BR-06 |
| **Priority** | Medium |

**Preconditions:** AiService returns LEVEL_3

**Steps:**
1. Mock AiService to return LEVEL_3 for "SLA xử lý hồ sơ: 3 ngày làm việc"
2. Call segment
3. Verify brSensitivityLevel == LEVEL_3

**Expected:** LEVEL_3 for SLA/process content

---

### UT-13: Multiple BR Levels — Most Restrictive Wins

| Field | Value |
|-------|-------|
| **ID** | UT-13 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-3.4, BR-19 |
| **Priority** | High |

**Preconditions:** AiService returns LEVEL_1 (most restrictive) for mixed BR content

**Steps:**
1. Mock AiService to return LEVEL_1 for text containing both rate (L1) and SLA (L3)
2. Call segment
3. Verify brSensitivityLevel == LEVEL_1

**Expected:** Most restrictive level (LEVEL_1) selected

---

### UT-14: BR Local-Only — Re-process Via Ollama

| Field | Value |
|-------|-------|
| **ID** | UT-14 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-4.1, BR-07 |
| **Priority** | High |

**Preconditions:** Config brLocalOnly=true, provider="openai", localAiService provided

**Steps:**
1. Create config with brLocalOnly=true, provider="openai"
2. Mock primary AiService to return result with businessRules="rate = 5%"
3. Mock local AiService to return re-processed BR
4. Call segment
5. Verify localAiService.classify was called with BR content
6. Verify result.provider == "openai+ollama"

**Expected:** BR re-processed via local Ollama, provider shows combined

---

### UT-15: BR Local-Only — Skip When Already Ollama

| Field | Value |
|-------|-------|
| **ID** | UT-15 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-4.2 |
| **Priority** | Medium |

**Preconditions:** Config brLocalOnly=true, provider="ollama"

**Steps:**
1. Create config with brLocalOnly=true, provider="ollama"
2. Mock AiService to return result with businessRules
3. Call segment
4. Verify localAiService was NOT called
5. Verify result.provider == "ollama"

**Expected:** No re-processing when already using Ollama

---

### UT-16: BR Local-Only — Degraded When Ollama Unavailable

| Field | Value |
|-------|-------|
| **ID** | UT-16 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-4.3, NFR-1 |
| **Priority** | High |

**Preconditions:** Config brLocalOnly=true, localAiService throws exception

**Steps:**
1. Create config with brLocalOnly=true, provider="openai"
2. Mock primary AiService to return result with BR content
3. Mock local AiService to throw Exception("Ollama unavailable")
4. Call segment
5. Verify result.degraded == true
6. Verify result.businessRules still contains original BR (from cloud)

**Expected:** Degraded mode, original BR preserved, no crash

---

### UT-17: AiService Interface Annotations

| Field | Value |
|-------|-------|
| **ID** | UT-17 |
| **Component** | SegmentationAiService |
| **Requirement** | AC-5.1 |
| **Priority** | Medium |

**Steps:**
1. Use reflection to verify SegmentationAiService.classify has @SystemMessage annotation
2. Verify @UserMessage annotation present
3. Verify @V("maskedText") parameter annotation

**Expected:** All LangChain4j annotations present

---

### UT-18: Prompt Builder Includes Few-Shot Examples

| Field | Value |
|-------|-------|
| **ID** | UT-18 |
| **Component** | SegmentationPromptBuilder |
| **Requirement** | AC-5.2 |
| **Priority** | Medium |

**Steps:**
1. Create SegmentationPromptBuilder()
2. Call buildUserMessage("test text", includeFewShot = true)
3. Verify output contains "Examples:"
4. Verify output contains at least 3 example input/output pairs
5. Verify output contains "LEVEL_1" example

**Expected:** Few-shot examples included with all sensitivity levels

---

### UT-19: Parse Valid JSON Response

| Field | Value |
|-------|-------|
| **ID** | UT-19 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-5.3 |
| **Priority** | High |

**Preconditions:** AiService returns valid JSON (possibly with markdown code block wrapper)

**Steps:**
1. Mock AiService to return: `` ```json\n{"publicContent":"test","technicalContent":"","businessRules":"","brSensitivityLevel":null}\n``` ``
2. Call segment
3. Verify JSON is extracted and parsed correctly
4. Verify result.publicContent == "test"

**Expected:** JSON extracted from code block and parsed successfully

---

### UT-20: Token Estimation Within Budget

| Field | Value |
|-------|-------|
| **ID** | UT-20 |
| **Component** | SegmentationPromptBuilder |
| **Requirement** | NFR-2 |
| **Priority** | Low |

**Steps:**
1. Build full prompt with 10,000 char input
2. Estimate token count (chars / 4 approximation)
3. Verify total < 4000 tokens (system + user + response budget)

**Expected:** Prompt stays within token budget

---

### UT-21: Empty Input Throws InvalidInputException

| Field | Value |
|-------|-------|
| **ID** | UT-21 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | EF-01 |
| **Priority** | High |

**Steps:**
1. Call segment("")
2. Verify SegmentationException.InvalidInputException is thrown
3. Verify message contains "must not be blank"

**Expected:** InvalidInputException for empty input

---

### UT-22: Blank Input Throws InvalidInputException

| Field | Value |
|-------|-------|
| **ID** | UT-22 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | EF-01 |
| **Priority** | High |

**Steps:**
1. Call segment("   ") (whitespace only)
2. Verify SegmentationException.InvalidInputException is thrown

**Expected:** InvalidInputException for blank input

---

## 3. Integration Tests (IT)

### IT-01: Full Segmentation Flow — Mixed Content

| Field | Value |
|-------|-------|
| **ID** | IT-01 |
| **Component** | ContentSegmentationServiceImpl + AiService (mocked) |
| **Requirement** | AC-1.1, BR-01, BR-02, BR-03 |
| **Priority** | High |
| **Technique** | Koin DI with mocked ChatLanguageModel |

**Preconditions:** Koin module loaded with mocked ChatLanguageModel

**Steps:**
1. Set up Koin test module with mocked ChatLanguageModel that returns valid mixed-content JSON
2. Resolve ContentSegmentationService from Koin
3. Call segment with realistic mixed content (Vietnamese financial ticket)
4. Verify publicContent contains metadata
5. Verify technicalContent contains code/logs
6. Verify businessRules contains financial rules
7. Verify processingTimeMs > 0
8. Verify provider matches config

**Expected:** Full pipeline works end-to-end with DI

---

### IT-02: Segmentation With No BR Content

| Field | Value |
|-------|-------|
| **ID** | IT-02 |
| **Component** | ContentSegmentationServiceImpl + AiService |
| **Requirement** | AC-1.2 |
| **Priority** | High |

**Steps:**
1. Mock LLM to return result with empty businessRules
2. Call segment with technical-only content
3. Verify businessRules == ""
4. Verify brSensitivityLevel == null
5. Verify no local re-processing attempted

**Expected:** Clean handling of no-BR scenario

---

### IT-03: BR Level 1 Classification End-to-End

| Field | Value |
|-------|-------|
| **ID** | IT-03 |
| **Component** | Full service chain |
| **Requirement** | AC-1.3, AC-3.1 |
| **Priority** | High |

**Steps:**
1. Mock LLM to return LEVEL_1 for financial rate content
2. Call segment with "Lãi suất cho vay tiêu dùng: 12.5%/năm, phí trả nợ trước hạn: 3%"
3. Verify brSensitivityLevel == LEVEL_1
4. Verify businessRules contains the rate information

**Expected:** Level 1 correctly identified for rate content

---

### IT-04: Timeout Handling Integration

| Field | Value |
|-------|-------|
| **ID** | IT-04 |
| **Component** | ContentSegmentationServiceImpl with coroutines |
| **Requirement** | AC-1.4, BR-10 |
| **Priority** | High |
| **Technique** | kotlinx.coroutines.test with virtual time |

**Steps:**
1. Create config with timeoutSeconds = 1
2. Mock AiService with coEvery { classify(any()) } coAnswers { delay(2000); "response" }
3. Call segment within runTest
4. Verify LlmTimeoutException thrown
5. Verify exception thrown within ~1 second (virtual time)

**Expected:** Timeout fires correctly with coroutine integration

---

### IT-05: Provider Unavailable Integration

| Field | Value |
|-------|-------|
| **ID** | IT-05 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-1.5, EF-05 |
| **Priority** | High |

**Steps:**
1. Mock AiService to throw ConnectException("Connection refused")
2. Call segment
3. Verify SegmentationException.ProviderUnavailableException thrown
4. Verify exception.cause is ConnectException
5. Verify exception message contains provider name

**Expected:** ProviderUnavailableException wraps connection error

---

### IT-06: ChatModelFactory OpenAI Integration

| Field | Value |
|-------|-------|
| **ID** | IT-06 |
| **Component** | ChatModelFactory |
| **Requirement** | AC-2.1 |
| **Priority** | Medium |

**Steps:**
1. Create config with provider="openai", apiKey="sk-test", modelName="gpt-4o-mini"
2. Call factory.create(config)
3. Verify model is OpenAiChatModel
4. Verify model configuration matches (via reflection or toString)

**Expected:** OpenAI model created with correct parameters

---

### IT-07: BR Local-Only Full Flow

| Field | Value |
|-------|-------|
| **ID** | IT-07 |
| **Component** | ContentSegmentationServiceImpl + local AiService |
| **Requirement** | AC-4.1, BR-07 |
| **Priority** | High |

**Steps:**
1. Create config: brLocalOnly=true, provider="openai"
2. Mock primary AiService → returns result with businessRules="rate = 5%"
3. Mock local AiService → returns re-classified BR with LEVEL_1
4. Create service with both aiServices
5. Call segment
6. Verify primary AiService called once (full text)
7. Verify local AiService called once (BR text only)
8. Verify final result.provider == "openai+ollama"
9. Verify final result.brSensitivityLevel from local processing

**Expected:** Two-phase processing: cloud for full text, local for BR

---

### IT-08: Degraded Mode When Local Unavailable

| Field | Value |
|-------|-------|
| **ID** | IT-08 |
| **Component** | ContentSegmentationServiceImpl |
| **Requirement** | AC-4.3, NFR-1 |
| **Priority** | High |

**Steps:**
1. Create config: brLocalOnly=true, provider="openai"
2. Mock primary AiService → returns result with businessRules
3. Mock local AiService → throws RuntimeException("Ollama down")
4. Call segment
5. Verify result.degraded == true
6. Verify result.businessRules still has content (from primary)
7. Verify no exception propagated to caller

**Expected:** Graceful degradation, no crash

---

## 4. E2E-API Tests

### E2E-01: Full DI Container Segmentation

| Field | Value |
|-------|-------|
| **ID** | E2E-01 |
| **Component** | SegmentationModule + all components |
| **Requirement** | AC-1.1 |
| **Priority** | High |
| **Technique** | Koin Test with full module |

**Steps:**
1. Start Koin with segmentationModule (ChatLanguageModel mocked at DI level)
2. Resolve ContentSegmentationService
3. Call segment with realistic input
4. Verify SegmentationResult returned with all fields
5. Stop Koin

**Expected:** Full DI wiring works correctly

---

### E2E-02: Module Wiring Verification

| Field | Value |
|-------|-------|
| **ID** | E2E-02 |
| **Component** | SegmentationModule |
| **Requirement** | TDD §5.1 |
| **Priority** | Medium |

**Steps:**
1. Start Koin with segmentationModule
2. Verify get<SegmentationConfig>() resolves
3. Verify get<ChatModelFactory>() resolves
4. Verify get<SegmentationPromptBuilder>() resolves
5. Verify get<ContentSegmentationService>() resolves
6. Stop Koin

**Expected:** All DI bindings resolve without error

---

### E2E-03: BR Local-Only E2E

| Field | Value |
|-------|-------|
| **ID** | E2E-03 |
| **Component** | Full pipeline with local enforcement |
| **Requirement** | AC-4.1, BR-07 |
| **Priority** | High |

**Steps:**
1. Configure Koin with brLocalOnly=true, provider="openai"
2. Mock both primary and local ChatLanguageModels
3. Resolve service and call segment with BR-containing text
4. Verify local model was invoked for BR re-processing
5. Verify final result reflects local processing

**Expected:** End-to-end BR local-only enforcement works

---

### E2E-04: Graceful Degradation E2E

| Field | Value |
|-------|-------|
| **ID** | E2E-04 |
| **Component** | Full pipeline degraded mode |
| **Requirement** | NFR-1 |
| **Priority** | Medium |

**Steps:**
1. Configure Koin with brLocalOnly=true, local service = null
2. Resolve service and call segment with BR-containing text
3. Verify result.degraded == true
4. Verify service did not throw exception
5. Verify result still contains usable data

**Expected:** System degrades gracefully without crashing

---

## 5. Test Data Files

### 5.1 testdata/segmentation-mixed.csv

| id | input | expectedPublic | expectedTechnical | expectedBr | expectedLevel |
|----|-------|---------------|-------------------|------------|---------------|
| 1 | "Ticket MTO-100 assigned to [PII_1]. Error: NPE at PaymentService:42. Rate = 12.5%" | "Ticket MTO-100 assigned to [PII_1]" | "Error: NPE at PaymentService:42" | "Rate = 12.5%" | LEVEL_1 |
| 2 | "JIRA-200 Status: Open. SELECT * FROM loans WHERE rate > 5" | "JIRA-200 Status: Open" | "SELECT * FROM loans WHERE rate > 5" | "" | null |
| 3 | "Bug report: SLA xử lý 3 ngày. Stack: OOM at line 100" | "Bug report" | "Stack: OOM at line 100" | "SLA xử lý 3 ngày" | LEVEL_3 |

### 5.2 testdata/segmentation-br-levels.csv

| id | input | expectedLevel | reason |
|----|-------|---------------|--------|
| 1 | "lãi suất = base + 2.5%" | LEVEL_1 | Interest rate formula |
| 2 | "phí trả nợ trước hạn: 3%" | LEVEL_1 | Fee percentage |
| 3 | "commission rate: 1.5% per transaction" | LEVEL_1 | Commission rate |
| 4 | "điều kiện duyệt vay: thu nhập >= 10 triệu" | LEVEL_2 | Approval condition |
| 5 | "risk score threshold: 650" | LEVEL_2 | Risk threshold |
| 6 | "scoring criteria: age > 25 AND income > 8M" | LEVEL_2 | Scoring criteria |
| 7 | "SLA xử lý hồ sơ: 3 ngày làm việc" | LEVEL_3 | SLA definition |
| 8 | "quy trình duyệt: submit → review → approve" | LEVEL_3 | Process workflow |
| 9 | "No business rules here, just code" | null | No BR content |

### 5.3 testdata/llm-responses/valid-mixed.json

```json
{
  "publicContent": "Ticket MTO-100, Status: Open, Priority: High, Assignee: [PII_NAME_01]",
  "technicalContent": "NullPointerException at com.bank.PaymentService.process(PaymentService.kt:42)\n  at com.bank.LoanController.submit(LoanController.kt:15)",
  "businessRules": "Lãi suất cho vay tiêu dùng: base_rate + 2.5%/năm. Phí trả nợ trước hạn: 3% số dư còn lại.",
  "brSensitivityLevel": "LEVEL_1"
}
```

### 5.4 testdata/llm-responses/no-br.json

```json
{
  "publicContent": "Ticket PROJ-200, Status: In Progress",
  "technicalContent": "SELECT * FROM users WHERE active = true; -- Performance issue",
  "businessRules": "",
  "brSensitivityLevel": null
}
```

### 5.5 testdata/llm-responses/invalid.json

```
This is not valid JSON at all, the LLM hallucinated.
```
