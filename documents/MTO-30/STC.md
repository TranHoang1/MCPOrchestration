# Software Test Cases (STC)

## MCPOrchestration — MTO-30: Business Rules Masking (AI-based)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-30 |
| Version | 1.0 |
| Date | 2026-05-09 |
| Related STP | STP-v1-MTO-30.docx |

---

## 1. Property-Based Tests (PBT)

### PBT-01: Encrypt-Decrypt Roundtrip

| Field | Value |
|-------|-------|
| ID | PBT-01 |
| Title | Any plaintext encrypted then decrypted returns original |
| Priority | P0 |
| Requirement | BR-04 |
| Iterations | 1000 |

**Property:** `∀ plaintext ∈ String (non-empty): decrypt(encrypt(plaintext)) == plaintext`

**Generator:** Kotest Arb.string(1..5000) — random Unicode strings including Vietnamese characters

**Test Code Pattern:**
```kotlin
test("PBT-01: encrypt-decrypt roundtrip preserves original text") {
    checkAll(1000, Arb.string(1..5000)) { plaintext ->
        val (ciphertext, iv) = encryptionService.encrypt(plaintext)
        encryptionService.decrypt(ciphertext, iv) shouldBe plaintext
    }
}
```

---

### PBT-02: Placeholder Format Invariant

| Field | Value |
|-------|-------|
| ID | PBT-02 |
| Title | All generated placeholders match format [BR_{CATEGORY}_{NN}] |
| Priority | P0 |
| Requirement | BR-03 |
| Iterations | 500 |

**Property:** `∀ result ∈ maskBusinessRules(*): result.brPlaceholders.all { it.id matches Regex("\\[BR_[A-Z]+_\\d{2}\\]") }`

**Generator:** Arb.string(10..2000) with mock LLM returning 1-5 random categories

---

### PBT-03: Unique IV Per Encryption

| Field | Value |
|-------|-------|
| ID | PBT-03 |
| Title | Each encryption produces unique IV |
| Priority | P0 |
| Requirement | BR-04 (AC-04.3) |
| Iterations | 100 |

**Property:** `∀ n ∈ [2..10]: encrypt(same_text) called n times → n distinct IVs`

---

### PBT-04: Masking Preserves Non-BR Content

| Field | Value |
|-------|-------|
| ID | PBT-04 |
| Title | Text outside identified BRs remains unchanged in masked output |
| Priority | P0 |
| Requirement | BR-01 |
| Iterations | 200 |

**Property:** For each identified BR replaced by placeholder, the surrounding text is preserved.

---

## 2. Unit Tests (UT)

### 2.1 BrMaskingServiceImpl Tests

#### UT-01: Mask Single Business Rule

| Field | Value |
|-------|-------|
| ID | UT-01 |
| Title | Single BR in content produces one placeholder |
| Priority | P0 |
| Requirement | BR-01 (AC-01.1) |
| Precondition | Mock LLM returns 1 identified BR |

**Steps:**
1. Configure mock AiService to return: `[{"text":"Lãi suất 12.5%/năm","category":"RATE","summary":"Annual interest rate"}]`
2. Call `maskBusinessRules("Lãi suất 12.5%/năm cho vay tiêu dùng")`
3. Verify result.maskedBr contains `[BR_RATE_01]`
4. Verify result.brPlaceholders has size 1
5. Verify placeholder.category == BrCategory.RATE
6. Verify placeholder.summary == "Annual interest rate"

**Expected:** maskedBr = `"[BR_RATE_01] cho vay tiêu dùng"`, 1 placeholder with RATE category

---

#### UT-02: Mask Multiple Business Rules

| Field | Value |
|-------|-------|
| ID | UT-02 |
| Title | Multiple BRs produce multiple placeholders with correct numbering |
| Priority | P0 |
| Requirement | BR-01 (AC-01.1), BR-03 |

**Steps:**
1. Mock LLM returns 3 BRs: RATE, APPROVAL, RATE
2. Call maskBusinessRules with text containing all 3
3. Verify 3 placeholders: [BR_RATE_01], [BR_APPROVAL_01], [BR_RATE_02]
4. Verify sequential numbering per category

**Expected:** 3 placeholders with correct category-specific numbering

---

#### UT-03: Empty Content Returns Unchanged

| Field | Value |
|-------|-------|
| ID | UT-03 |
| Title | Blank input returns empty result without calling LLM |
| Priority | P0 |
| Requirement | BR-01 (AC-01.2) |

**Steps:**
1. Call `maskBusinessRules("")`
2. Verify result.maskedBr == ""
3. Verify result.brPlaceholders is empty
4. Verify AiService.identify() was NOT called

**Expected:** Empty result, no LLM invocation

---

#### UT-04: Category RATE Detection

| Field | Value |
|-------|-------|
| ID | UT-04 |
| Title | LLM response with category "RATE" maps to BrCategory.RATE |
| Priority | P0 |
| Requirement | BR-02 (AC-02.1) |

**Steps:**
1. Mock LLM returns: `[{"text":"Lãi suất 12.5%/năm","category":"RATE","summary":"Interest rate"}]`
2. Call maskBusinessRules
3. Verify placeholder.category == BrCategory.RATE

---

#### UT-05: Category APPROVAL Detection

| Field | Value |
|-------|-------|
| ID | UT-05 |
| Title | LLM response with category "APPROVAL" maps to BrCategory.APPROVAL |
| Priority | P0 |
| Requirement | BR-02 (AC-02.2) |

**Steps:**
1. Mock LLM returns: `[{"text":"Duyệt khi điểm tín dụng ≥ 700","category":"APPROVAL","summary":"Credit score approval"}]`
2. Call maskBusinessRules
3. Verify placeholder.category == BrCategory.APPROVAL

---

#### UT-06: Category THRESHOLD Detection

| Field | Value |
|-------|-------|
| ID | UT-06 |
| Title | LLM response with category "THRESHOLD" maps to BrCategory.THRESHOLD |
| Priority | P0 |
| Requirement | BR-02 (AC-02.3) |

**Steps:**
1. Mock LLM returns: `[{"text":"Ngưỡng rủi ro cao: NPL > 5%","category":"THRESHOLD","summary":"NPL risk threshold"}]`
2. Call maskBusinessRules
3. Verify placeholder.category == BrCategory.THRESHOLD

---

#### UT-07: Category PROCESS Detection

| Field | Value |
|-------|-------|
| ID | UT-07 |
| Title | LLM response with category "PROCESS" maps to BrCategory.PROCESS |
| Priority | P0 |
| Requirement | BR-02 (AC-02.4) |

**Steps:**
1. Mock LLM returns: `[{"text":"Quy trình xét duyệt 3 cấp","category":"PROCESS","summary":"3-level approval process"}]`
2. Call maskBusinessRules
3. Verify placeholder.category == BrCategory.PROCESS

---

#### UT-08: Category COMMISSION Detection

| Field | Value |
|-------|-------|
| ID | UT-08 |
| Title | LLM response with category "COMMISSION" maps to BrCategory.COMMISSION |
| Priority | P0 |
| Requirement | BR-02 (AC-02.5) |

**Steps:**
1. Mock LLM returns: `[{"text":"Hoa hồng đại lý: 2.5%","category":"COMMISSION","summary":"Agent commission rate"}]`
2. Call maskBusinessRules
3. Verify placeholder.category == BrCategory.COMMISSION

---

#### UT-09: Placeholder Format First Rule

| Field | Value |
|-------|-------|
| ID | UT-09 |
| Title | First RATE rule gets placeholder [BR_RATE_01] |
| Priority | P0 |
| Requirement | BR-03 (AC-03.1) |

**Steps:**
1. Mock LLM returns 1 RATE rule
2. Call maskBusinessRules
3. Verify placeholder.id == "[BR_RATE_01]"

---

#### UT-10: Placeholder Format Sequential Numbering

| Field | Value |
|-------|-------|
| ID | UT-10 |
| Title | Second RATE rule gets placeholder [BR_RATE_02] |
| Priority | P0 |
| Requirement | BR-03 (AC-03.2) |

**Steps:**
1. Mock LLM returns 2 RATE rules
2. Call maskBusinessRules
3. Verify placeholders: [BR_RATE_01], [BR_RATE_02]

---

#### UT-11: Placeholder Uniqueness

| Field | Value |
|-------|-------|
| ID | UT-11 |
| Title | All placeholders in a single operation are unique |
| Priority | P0 |
| Requirement | BR-03 (AC-03.3) |

**Steps:**
1. Mock LLM returns 5 rules across different categories
2. Call maskBusinessRules
3. Verify all placeholder IDs are distinct

---

#### UT-12: Unknown Category Falls Back to UNKNOWN

| Field | Value |
|-------|-------|
| ID | UT-12 |
| Title | Unrecognized category string maps to BrCategory.UNKNOWN |
| Priority | P1 |
| Requirement | BR-02 |

**Steps:**
1. Mock LLM returns: `[{"text":"some rule","category":"INVALID_CAT","summary":"test"}]`
2. Call maskBusinessRules
3. Verify placeholder.category == BrCategory.UNKNOWN

---

### 2.2 BrEncryptionService Tests

#### UT-13: Encrypt Returns Non-Empty Ciphertext

| Field | Value |
|-------|-------|
| ID | UT-13 |
| Title | Encrypt produces non-empty Base64 ciphertext and IV |
| Priority | P0 |
| Requirement | BR-04 |

**Steps:**
1. Create BrEncryptionService with valid test key
2. Call encrypt("Lãi suất 12.5%/năm")
3. Verify ciphertext is non-empty Base64
4. Verify IV is non-empty Base64

---

#### UT-14: Decrypt Returns Original Text

| Field | Value |
|-------|-------|
| ID | UT-14 |
| Title | Decrypt with correct key and IV returns original plaintext |
| Priority | P0 |
| Requirement | BR-04, BR-05 (AC-05.1) |

**Steps:**
1. Encrypt "Ngưỡng rủi ro: NPL > 5%"
2. Decrypt with returned ciphertext and IV
3. Verify result == "Ngưỡng rủi ro: NPL > 5%"

---

#### UT-15: Decrypt With Wrong Key Throws Exception

| Field | Value |
|-------|-------|
| ID | UT-15 |
| Title | Decryption with wrong key throws DecryptionException |
| Priority | P0 |
| Requirement | BR-04 |

**Steps:**
1. Encrypt with key A
2. Create new BrEncryptionService with key B
3. Attempt decrypt
4. Verify BrMaskingException.DecryptionException is thrown

---

#### UT-16: Invalid Key Length Throws Exception

| Field | Value |
|-------|-------|
| ID | UT-16 |
| Title | Key shorter than 32 bytes throws InvalidKeyException |
| Priority | P1 |
| Requirement | FSD §5.2 |

**Steps:**
1. Attempt to create BrEncryptionService with 16-byte key
2. Verify IllegalArgumentException or InvalidKeyException is thrown

---

#### UT-17: Blank Key Throws InvalidKeyException

| Field | Value |
|-------|-------|
| ID | UT-17 |
| Title | Empty/blank encryption key throws InvalidKeyException |
| Priority | P1 |
| Requirement | FSD §5.2 |

**Steps:**
1. Attempt to create BrEncryptionService with ""
2. Verify BrMaskingException.InvalidKeyException is thrown

---

### 2.3 Unmask Tests

#### UT-18: Unmask Returns Original Text

| Field | Value |
|-------|-------|
| ID | UT-18 |
| Title | Unmask with valid placeholder returns decrypted original |
| Priority | P0 |
| Requirement | BR-05 (AC-05.1) |

**Steps:**
1. Mask content to get BrMaskingResult
2. Take first placeholder from result
3. Call unmask(placeholder)
4. Verify returned text matches original BR content

---

#### UT-19: Unmask With Corrupted Data Throws DecryptionException

| Field | Value |
|-------|-------|
| ID | UT-19 |
| Title | Unmask with corrupted encrypted data throws DecryptionException |
| Priority | P0 |
| Requirement | BR-05 |

**Steps:**
1. Create BrPlaceholder with invalid encryptedOriginal ("corrupted_data")
2. Call unmask(placeholder)
3. Verify BrMaskingException.DecryptionException is thrown

---

### 2.4 Fail-Safe Tests

#### UT-20: LLM Failure Triggers Fail-Safe Masking

| Field | Value |
|-------|-------|
| ID | UT-20 |
| Title | When LLM throws exception, entire content masked as [BR_UNKNOWN_01] |
| Priority | P0 |
| Requirement | FSD BR-05 |

**Steps:**
1. Mock AiService.identify() to throw RuntimeException
2. Call maskBusinessRules("some BR content")
3. Verify result.maskedBr == "[BR_UNKNOWN_01]"
4. Verify result.brPlaceholders has 1 entry with category UNKNOWN

---

#### UT-21: LLM Returns Invalid JSON Triggers Fail-Safe

| Field | Value |
|-------|-------|
| ID | UT-21 |
| Title | When LLM returns non-JSON, fail-safe masks entire content |
| Priority | P0 |
| Requirement | FSD BR-05, TDD §5.3 |

**Steps:**
1. Mock AiService.identify() to return "This is not JSON"
2. Call maskBusinessRules("BR content here")
3. Verify fail-safe behavior (single UNKNOWN placeholder)

---

### 2.5 Performance & Config Tests

#### UT-22: Processing Time Recorded

| Field | Value |
|-------|-------|
| ID | UT-22 |
| Title | BrMaskingResult.processingTimeMs is > 0 |
| Priority | P1 |
| Requirement | NFR-3 |

**Steps:**
1. Mock LLM with small delay
2. Call maskBusinessRules
3. Verify result.processingTimeMs > 0

---

#### UT-23: BrMaskingConfig Default Values

| Field | Value |
|-------|-------|
| ID | UT-23 |
| Title | Default config has correct values |
| Priority | P1 |
| Requirement | FSD §3.6 |

**Steps:**
1. Create BrMaskingConfig()
2. Verify enabled == true
3. Verify provider == "openai"
4. Verify modelName == "gpt-4o-mini"
5. Verify temperature == 0.0
6. Verify timeoutSeconds == 15

---

#### UT-24: BrCategory Enum Coverage

| Field | Value |
|-------|-------|
| ID | UT-24 |
| Title | BrCategory has exactly 6 values with correct labels |
| Priority | P1 |
| Requirement | BR-02 |

**Steps:**
1. Verify BrCategory.values().size == 6
2. Verify each enum has non-empty label

---

## 3. Integration Tests (IT)

### IT-01: Full Masking Flow With Mock LLM

| Field | Value |
|-------|-------|
| ID | IT-01 |
| Title | End-to-end masking flow: identify → mask → encrypt |
| Priority | P0 |
| Requirement | UC-01 Main Flow |
| Technique | Koin DI with mock AiService |

**Steps:**
1. Start Koin with brMaskingModule (override AiService with mock)
2. Inject BrMaskingService
3. Call maskBusinessRules with multi-BR Vietnamese text
4. Verify: masked text has placeholders, no original BR text visible
5. Verify: each placeholder has encrypted data
6. Verify: unmask each placeholder returns original text

**Test Data:**
```
Input: "Lãi suất cho vay tiêu dùng: 12.5%/năm. Điều kiện duyệt: thu nhập ≥ 15 triệu/tháng. Ngưỡng NPL: 3%."
Mock LLM Response: [
  {"text":"Lãi suất cho vay tiêu dùng: 12.5%/năm","category":"RATE","summary":"Consumer loan interest rate"},
  {"text":"Điều kiện duyệt: thu nhập ≥ 15 triệu/tháng","category":"APPROVAL","summary":"Income approval condition"},
  {"text":"Ngưỡng NPL: 3%","category":"THRESHOLD","summary":"NPL threshold"}
]
Expected masked: "[BR_RATE_01]. [BR_APPROVAL_01]. [BR_THRESHOLD_01]."
```

---

### IT-02: Full Unmask Flow

| Field | Value |
|-------|-------|
| ID | IT-02 |
| Title | Mask then unmask returns original content for each BR |
| Priority | P0 |
| Requirement | UC-02 Main Flow |
| Technique | Koin DI with mock AiService |

**Steps:**
1. Mask content (same as IT-01)
2. For each placeholder in result:
   - Call unmask(placeholder)
   - Verify returned text == original BR text from mock

---

### IT-03: Koin Module Wiring

| Field | Value |
|-------|-------|
| ID | IT-03 |
| Title | brMaskingModule resolves all dependencies correctly |
| Priority | P1 |
| Requirement | TDD §2 |
| Technique | Koin checkModules |

**Steps:**
1. Load brMaskingModule with test overrides (mock ChatModelFactory)
2. Verify BrMaskingService resolves
3. Verify BrEncryptionService resolves
4. Verify BrIdentificationAiService resolves

---

### IT-04: Encryption Key From Environment

| Field | Value |
|-------|-------|
| ID | IT-04 |
| Title | Service reads encryption key from env var when config is blank |
| Priority | P1 |
| Requirement | FSD §5.2, BR-04 (AC-04.2) |
| Technique | System property override |

**Steps:**
1. Set system env BR_ENCRYPTION_KEY to valid test key
2. Create BrMaskingConfig with blank encryptionKey
3. Start Koin module
4. Verify BrEncryptionService initializes without error
5. Verify encrypt/decrypt works

---

## 4. System Integration Tests (SIT) — Manual

### SIT-01: Real LLM BR Identification

| Field | Value |
|-------|-------|
| ID | SIT-01 |
| Title | Real OpenAI call identifies BRs correctly |
| Priority | P2 |
| Requirement | BR-01, BR-02 |
| Type | Manual |

**Steps:**
1. Configure real OpenAI API key
2. Call maskBusinessRules with Vietnamese financial text
3. Verify LLM identifies distinct BRs
4. Verify categories are reasonable
5. Verify summaries are non-sensitive

**Test Data:**
```
"Quy định cho vay mua nhà: Lãi suất ưu đãi 6.5%/năm trong 12 tháng đầu, sau đó thả nổi theo lãi suất cơ sở + 3%. Điều kiện: thu nhập tối thiểu 20 triệu/tháng, không có nợ xấu nhóm 3-5. Hạn mức tối đa 70% giá trị tài sản thế chấp."
```

---

### SIT-02: Real LLM Edge Case — No BRs

| Field | Value |
|-------|-------|
| ID | SIT-02 |
| Title | Real LLM correctly identifies text with no BRs |
| Priority | P2 |
| Requirement | BR-01 (AC-01.2) |
| Type | Manual |

**Steps:**
1. Call maskBusinessRules with non-BR text: "Hệ thống đã được cập nhật phiên bản mới."
2. Verify LLM returns empty array
3. Verify original text unchanged

---

## 5. Test Data Files

### 5.1 Vietnamese BR Samples

| # | Category | Sample Text | Expected Category |
|---|----------|-------------|-------------------|
| 1 | RATE | "Lãi suất cho vay tiêu dùng: 12.5%/năm" | RATE |
| 2 | RATE | "Phí quản lý tài khoản: 50,000 VND/tháng" | RATE |
| 3 | APPROVAL | "Điều kiện duyệt: thu nhập ≥ 15 triệu/tháng" | APPROVAL |
| 4 | APPROVAL | "Duyệt tự động khi điểm tín dụng ≥ 750" | APPROVAL |
| 5 | THRESHOLD | "Ngưỡng rủi ro cao: NPL > 5%" | THRESHOLD |
| 6 | THRESHOLD | "Giới hạn exposure: 500 tỷ VND/khách hàng" | THRESHOLD |
| 7 | PROCESS | "Quy trình xét duyệt 3 cấp: chi nhánh → vùng → hội sở" | PROCESS |
| 8 | PROCESS | "SLA xử lý hồ sơ: 3 ngày làm việc" | PROCESS |
| 9 | COMMISSION | "Hoa hồng đại lý: 2.5% giá trị hợp đồng" | COMMISSION |
| 10 | COMMISSION | "Thưởng doanh số: 0.5% khi đạt target" | COMMISSION |

### 5.2 Edge Cases

| # | Scenario | Input | Expected Behavior |
|---|----------|-------|-------------------|
| 1 | Empty string | "" | Return unchanged, no LLM call |
| 2 | Blank string | "   " | Return unchanged, no LLM call |
| 3 | No BRs in text | "Hệ thống hoạt động bình thường" | Return unchanged, empty placeholders |
| 4 | Single character | "x" | Return unchanged (LLM finds no BR) |
| 5 | Very long text | 5000+ chars | Should complete within timeout |
| 6 | Special characters | "Lãi suất: 12.5% → 15%" | Correctly mask with special chars |
| 7 | Mixed languages | "Rate = 12.5% per annum, lãi suất" | Identify BR regardless of language |
