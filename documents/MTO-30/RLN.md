# Release Notes (RLN)

## MTO-30: KB Refinery — Business Rules Masking (AI-based)

| Field | Value |
|-------|-------|
| **Version** | 1.2.0 |
| **Date** | 2026-05-09 |
| **Type** | Feature |
| **Priority** | High |
| **Epic** | MTO-24 (Knowledge Base Refinery) |

---

## Summary

Added AI-based Business Rules Masking to the KB Refinery pipeline. The system identifies individual business rules in segmented text, categorizes them (RATE, APPROVAL, THRESHOLD, PROCESS, SLA), replaces with descriptive placeholders, and encrypts original content for secure storage.

---

## New Features

### Business Rules Identification via LLM

- Uses LangChain4j to identify individual business rules in segmented text
- Categorizes each rule: RATE, APPROVAL, THRESHOLD, PROCESS, SLA
- Assigns sensitivity level (LEVEL_1/LEVEL_2/LEVEL_3) per rule

### Placeholder Generation

- Format: `[BR_{CATEGORY}_{NN}: {summary}]`
- Example: `[BR_APPROVAL_01: Điều kiện duyệt hạn mức tự động]`
- Summary describes the rule without revealing specific numbers/thresholds

### Encryption at Rest

- Original BR content encrypted with AES-256-GCM before storage
- Separate encryption key from PII mapping (defense in depth)
- Only BA/Admin roles can decrypt and view original content

### Graceful Degradation

- If LLM unavailable: returns original text unmasked (logged as warning)
- If encryption key missing: fails fast at startup
- Configurable timeout (default 15s)

---

## Files Added (10 source + 3 test)

| Package | Files | Description |
|---------|-------|-------------|
| `brmasking/` | 2 | Service interface + implementation |
| `brmasking/model/` | 5 | Result, Placeholder, Category, Config, Exception |
| `brmasking/crypto/` | 1 | AES-256-GCM encryption for BR content |
| `brmasking/prompt/` | 1 | LangChain4j AiService interface |
| `brmasking/di/` | 1 | Koin DI module |

---

## Configuration

New `orchestrator.brmasking` section in `application.yml`:
- `enabled`: Feature flag (default: true)
- `use-segmentation-provider`: Reuse MTO-28 LLM config (default: true)
- `timeout-seconds`: LLM call timeout (default: 15)
- `max-rules-per-text`: Max BR items per text (default: 20)
- `encryption-key`: AES-256 key via env var `BR_ENCRYPTION_KEY`

---

## Dependencies

No new external dependencies. Uses:
- LangChain4j (already added by MTO-28)
- javax.crypto (JDK built-in)
- Koin DI (existing)

---

## Testing

| Level | Count | Pass Rate |
|-------|-------|-----------|
| Unit | 15 | 100% |
| Integration | 8 | 100% |
| E2E | 4 | 100% |
| **Total** | **27** | **100%** |

---

## Breaking Changes

None. Additive feature integrated into existing pipeline.

---

## Known Limitations

1. LLM accuracy for BR identification depends on prompt quality and model capability
2. Vietnamese-specific BR patterns may need prompt tuning for different financial products
3. Max 20 BR items per text (configurable) — texts with more rules are truncated
4. No feedback loop for incorrect BR identification (planned for MTO-37)

---

## Upgrade Instructions

1. Set `BR_ENCRYPTION_KEY` environment variable
2. Add `orchestrator.brmasking` section to `application.yml`
3. Deploy new JAR
4. Verify via logs: "BrMaskingModule initialized"
