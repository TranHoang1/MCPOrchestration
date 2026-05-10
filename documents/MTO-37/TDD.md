# Technical Design Document (TDD)

## MCPOrchestration — MTO-37: KB Refinery — Feedback & Correction UI

---

## 1. Architecture Overview

### 1.1 Package Structure

```
com.orchestrator.mcp.feedback/
├── FeedbackService.kt                (interface)
├── FeedbackServiceImpl.kt            (implementation)
├── model/
│   ├── Feedback.kt                   (feedback data class)
│   ├── FeedbackStatus.kt             (enum)
│   ├── FeedbackType.kt               (enum)
│   ├── FeedbackConfig.kt             (configuration)
│   └── FeedbackStats.kt              (analytics data class)
├── repository/
│   ├── FeedbackRepository.kt         (interface)
│   └── FeedbackRepositoryImpl.kt     (JDBC implementation)
└── di/
    └── FeedbackModule.kt             (Koin module)
```

---

## 2. Detailed Design

### 2.1 FeedbackService Interface

```kotlin
interface FeedbackService {
    suspend fun submit(feedback: Feedback): Feedback
    suspend fun approve(feedbackId: Long, reviewerId: String): Feedback?
    suspend fun reject(feedbackId: Long, reviewerId: String, reason: String): Feedback?
    suspend fun getByIssueKey(issueKey: String): List<Feedback>
    suspend fun getByStatus(status: FeedbackStatus, limit: Int = 50): List<Feedback>
    suspend fun getStats(): FeedbackStats
}
```

### 2.2 Feedback Model

```kotlin
data class Feedback(
    val id: Long = 0,
    val issueKey: String,
    val userId: String,
    val type: FeedbackType,
    val content: String,
    val suggestedCorrection: String? = null,
    val status: FeedbackStatus = FeedbackStatus.PENDING,
    val reviewerId: String? = null,
    val rejectionReason: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val resolvedAt: Instant? = null
)
```

---

## 3. Database Design

```sql
CREATE TABLE IF NOT EXISTS kb_feedback (
    id BIGSERIAL PRIMARY KEY,
    issue_key VARCHAR(50) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    feedback_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    suggested_correction TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id VARCHAR(100),
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_feedback_issue ON kb_feedback (issue_key);
CREATE INDEX idx_feedback_status ON kb_feedback (status);
CREATE INDEX idx_feedback_user ON kb_feedback (user_id);
```

---

## 4. Implementation Checklist

| # | File | Lines (est.) |
|---|------|-------------|
| 1 | FeedbackService.kt | ~18 |
| 2 | FeedbackServiceImpl.kt | ~75 |
| 3 | Feedback.kt | ~20 |
| 4 | FeedbackStatus.kt | ~8 |
| 5 | FeedbackType.kt | ~10 |
| 6 | FeedbackConfig.kt | ~8 |
| 7 | FeedbackStats.kt | ~12 |
| 8 | FeedbackRepository.kt | ~15 |
| 9 | FeedbackRepositoryImpl.kt | ~90 |
| 10 | FeedbackModule.kt | ~15 |

