# Functional Specification Document (FSD)

## MCPOrchestration — MTO-30: Business Rules Masking (AI-based)

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-30 |
| Title | KB Refinery — Business Rules Masking (AI-based) |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Approved |
| Related BRD | BRD-v1-MTO-30.docx |

---

## 1. Use Cases

### UC-01: Mask Business Rules

| Field | Value |
|-------|-------|
| ID | UC-01 |
| Name | Mask Business Rules in Content |
| Actor | KB Refinery Pipeline (automated) |
| Precondition | SegmentationResult with non-empty businessRules |
| Postcondition | BrMaskingResult with masked text and encrypted originals |

**Main Flow:**
1. Pipeline calls `BrMaskingService.maskBusinessRules(brContent)`
2. Service sends content to LLM for BR boundary identification
3. LLM returns structured list of individual BRs with categories
4. Service generates placeholder for each BR
5. Service encrypts each original BR with AES-256-GCM
6. Service returns BrMaskingResult with masked text and placeholder list

**Alternative Flow — No BRs Found:**
1. At step 3, LLM identifies no distinct business rules
2. Service returns result with original text unchanged, empty placeholder list

**Exception Flow — LLM Failure:**
1. At step 2, LLM call fails
2. Service masks entire content as single `[BR_UNKNOWN_01]` placeholder (fail-safe)

### UC-02: Unmask Business Rule

| Field | Value |
|-------|-------|
| ID | UC-02 |
| Name | Unmask Specific Business Rule |
| Actor | BA / Admin |
| Precondition | Valid placeholder ID, authorized role |
| Postcondition | Original BR text decrypted and returned |

**Main Flow:**
1. User calls `BrMaskingService.unmask(placeholder, encryptedData)`
2. Service verifies caller authorization
3. Service decrypts BR content using AES-256-GCM
4. Service returns original BR text

**Exception Flow — Unauthorized:**
1. At step 2, caller role is not BA/Admin
2. Service throws UnauthorizedAccessException

## 2. Business Rules

| ID | Rule | Source |
|----|------|--------|
| BR-01 | Categories: RATE, APPROVAL, THRESHOLD, PROCESS, COMMISSION | BRD BR-02 |
| BR-02 | Placeholder format: [BR_{CATEGORY}_{NN}] | BRD BR-03 |
| BR-03 | Encryption: AES-256-GCM with unique IV per BR | BRD BR-04 |
| BR-04 | Key source: env var BR_ENCRYPTION_KEY (32 bytes base64) | BRD BR-04 |
| BR-05 | Fail-safe: mask entire content if LLM fails | UC-01 Exception |

## 3. Data Specifications

### 3.1 BrMaskingService Interface

```kotlin
interface BrMaskingService {
    suspend fun maskBusinessRules(brContent: String): BrMaskingResult
    fun unmask(placeholder: BrPlaceholder): String
}
```

### 3.2 BrMaskingResult

```kotlin
@Serializable
data class BrMaskingResult(
    val maskedBr: String,
    val brPlaceholders: List<BrPlaceholder>,
    val processingTimeMs: Long = 0
)
```

### 3.3 BrPlaceholder

```kotlin
@Serializable
data class BrPlaceholder(
    val id: String,              // e.g., "[BR_RATE_01]"
    val category: BrCategory,
    val encryptedOriginal: String,  // Base64-encoded AES-256-GCM ciphertext
    val iv: String,                 // Base64-encoded IV
    val summary: String             // LLM-generated summary (non-sensitive)
)
```

### 3.4 BrCategory Enum

```kotlin
enum class BrCategory(val label: String) {
    RATE("Interest rates, fees, pricing"),
    APPROVAL("Approval conditions, criteria"),
    THRESHOLD("Risk thresholds, limits"),
    PROCESS("Business processes, workflows"),
    COMMISSION("Commissions, agent fees")
}
```

### 3.5 LLM Prompt for BR Identification

**System Message:**
```
You are a Business Rules Identifier for financial documents.
Analyze the text and identify individual business rules.

Output MUST be valid JSON array:
[
  {"text": "original BR text", "category": "RATE|APPROVAL|THRESHOLD|PROCESS|COMMISSION", "summary": "1-line non-sensitive summary"}
]

Categories:
- RATE: Interest rates, fees, commissions, pricing formulas
- APPROVAL: Conditions for approval/rejection, scoring criteria
- THRESHOLD: Risk limits, NPL thresholds, exposure limits
- PROCESS: Workflow steps, SLA definitions, escalation rules
- COMMISSION: Agent/broker commissions, referral fees
```

### 3.6 Configuration

```yaml
orchestrator:
  br-masking:
    enabled: true
    encryption-key: "${BR_ENCRYPTION_KEY}"
    provider: "openai"
    model-name: "gpt-4o-mini"
    temperature: 0.0
    timeout-seconds: 15
```

## 4. API Contracts

### 4.1 Internal API: BrMaskingService

| Method | Input | Output | Errors |
|--------|-------|--------|--------|
| `maskBusinessRules(brContent)` | BR text string | BrMaskingResult | BrMaskingException |
| `unmask(placeholder)` | BrPlaceholder | Original text | UnauthorizedAccessException, DecryptionException |

## 5. Security Design

### 5.1 Encryption Flow

```
Original BR → AES-256-GCM encrypt(key, iv) → Base64 encode → store in BrPlaceholder.encryptedOriginal
```

### 5.2 Key Management

- Key stored in env var: `BR_ENCRYPTION_KEY`
- Key format: 32 bytes, Base64-encoded
- Key validation at startup (fail-fast if missing/invalid)

### 5.3 IV Generation

- Unique 12-byte IV per BR (SecureRandom)
- IV stored alongside ciphertext in BrPlaceholder

## 6. Error Handling

| Error | Code | Recovery |
|-------|------|----------|
| LLM failure | BR_LLM_FAILED | Mask entire content as single placeholder |
| Invalid encryption key | BR_INVALID_KEY | Fail at startup (ConfigException) |
| Decryption failure | BR_DECRYPT_FAILED | Throw DecryptionException |
| Unauthorized unmask | BR_UNAUTHORIZED | Throw UnauthorizedAccessException |
