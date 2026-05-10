# Technical Design Document (TDD)

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
| Related FSD | FSD-v1-MTO-30.docx |

---

## 1. Architecture Overview

The BR Masking module sits downstream of Content Segmentation (MTO-28). It receives `businessRules` text from SegmentationResult, uses LLM to identify individual rules, then masks and encrypts them.

```
SegmentationResult.businessRules → BrMaskingService → BrMaskingResult(maskedBr, placeholders)
```

## 2. Package Structure

```
com.orchestrator.mcp.brmasking/
├── BrMaskingService.kt              # Interface (public API)
├── BrMaskingServiceImpl.kt          # Implementation
├── model/
│   ├── BrMaskingResult.kt           # Result data class
│   ├── BrPlaceholder.kt             # Placeholder data class
│   ├── BrCategory.kt                # Category enum
│   ├── BrMaskingConfig.kt           # Configuration
│   └── BrMaskingException.kt        # Sealed exceptions
├── crypto/
│   └── BrEncryptionService.kt       # AES-256-GCM encryption/decryption
├── prompt/
│   └── BrIdentificationAiService.kt # LangChain4j AiService interface
└── di/
    └── BrMaskingModule.kt           # Koin DI module
```

## 3. Class Design

### 3.1 BrMaskingService Interface

```kotlin
interface BrMaskingService {
    suspend fun maskBusinessRules(brContent: String): BrMaskingResult
    fun unmask(placeholder: BrPlaceholder): String
}
```

### 3.2 BrMaskingServiceImpl

```kotlin
class BrMaskingServiceImpl(
    private val config: BrMaskingConfig,
    private val aiService: BrIdentificationAiService,
    private val encryptionService: BrEncryptionService
) : BrMaskingService {

    override suspend fun maskBusinessRules(brContent: String): BrMaskingResult
    override fun unmask(placeholder: BrPlaceholder): String
}
```

### 3.3 BrEncryptionService

```kotlin
class BrEncryptionService(private val keyBase64: String) {
    fun encrypt(plaintext: String): Pair<String, String>  // (ciphertext, iv) both Base64
    fun decrypt(ciphertext: String, iv: String): String
}
```

### 3.4 BrIdentificationAiService (LangChain4j)

```kotlin
interface BrIdentificationAiService {
    @SystemMessage("...")
    @UserMessage("Identify business rules in:\n\n{{content}}")
    fun identify(@V("content") content: String): String
}
```

## 4. Encryption Design

### 4.1 Algorithm: AES-256-GCM

| Parameter | Value |
|-----------|-------|
| Algorithm | AES/GCM/NoPadding |
| Key size | 256 bits (32 bytes) |
| IV size | 96 bits (12 bytes) |
| Tag size | 128 bits (16 bytes) |
| Key source | Env var `BR_ENCRYPTION_KEY` (Base64) |

### 4.2 Implementation

```kotlin
class BrEncryptionService(keyBase64: String) {
    private val secretKey: SecretKey = decodeKey(keyBase64)

    fun encrypt(plaintext: String): Pair<String, String> {
        val iv = generateIv()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(ciphertext) to
               Base64.getEncoder().encodeToString(iv)
    }

    fun decrypt(ciphertextB64: String, ivB64: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.getDecoder().decode(ivB64)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertextB64))
        return String(plaintext, Charsets.UTF_8)
    }

    private fun generateIv(): ByteArray {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun decodeKey(base64Key: String): SecretKey {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == 32) { "BR encryption key must be 32 bytes" }
        return SecretKeySpec(keyBytes, "AES")
    }
}
```

## 5. LLM Integration

### 5.1 Prompt Design

The LLM identifies BR boundaries and categories. Output is JSON array.

### 5.2 Response Parsing

```kotlin
@Serializable
data class IdentifiedBr(
    val text: String,
    val category: String,
    val summary: String
)
```

Parse LLM response → List<IdentifiedBr> → generate placeholders → encrypt originals.

### 5.3 Fail-Safe

If LLM fails or returns invalid JSON:
- Mask entire brContent as `[BR_UNKNOWN_01]`
- Encrypt entire content as single block
- Log warning

## 6. Implementation Checklist

### Files to Create

| # | File | Package | Lines (est.) | Priority |
|---|------|---------|-------------|----------|
| 1 | BrMaskingService.kt | brmasking | ~12 | P0 |
| 2 | BrMaskingServiceImpl.kt | brmasking | ~120 | P0 |
| 3 | BrMaskingResult.kt | brmasking.model | ~15 | P0 |
| 4 | BrPlaceholder.kt | brmasking.model | ~18 | P0 |
| 5 | BrCategory.kt | brmasking.model | ~15 | P0 |
| 6 | BrMaskingConfig.kt | brmasking.model | ~20 | P0 |
| 7 | BrMaskingException.kt | brmasking.model | ~20 | P0 |
| 8 | BrEncryptionService.kt | brmasking.crypto | ~60 | P0 |
| 9 | BrIdentificationAiService.kt | brmasking.prompt | ~40 | P0 |
| 10 | BrMaskingModule.kt | brmasking.di | ~25 | P0 |

### Files to Modify

| # | File | Change | Priority |
|---|------|--------|----------|
| 1 | AppModule.kt | Include brMaskingModule | P0 |
| 2 | application.yml | Add br-masking config section | P1 |

## 7. Security Considerations

- Encryption key NEVER logged or exposed in responses
- Original BR text only exists in memory during processing
- Decrypted text not cached
- Audit log for unmask operations (future)

## 8. Testing Strategy

| Level | What to Test | Technique |
|-------|-------------|-----------|
| Unit | BrEncryptionService encrypt/decrypt | JUnit (no mocks needed) |
| Unit | BrMaskingServiceImpl logic | MockK (mock AI service) |
| Unit | Placeholder generation | Pure function test |
| Integration | Full mask flow with mock LLM | Kotest |
| Property | Encrypt → decrypt roundtrip | Kotest Property |
