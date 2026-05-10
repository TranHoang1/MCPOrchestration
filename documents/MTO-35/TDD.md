# Technical Design Document (TDD)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## 1. Architecture Overview

### 1.1 Package Structure

```
com.orchestrator.mcp.linking/
├── EntityLinkingService.kt           (interface)
├── EntityLinkingServiceImpl.kt       (implementation)
├── model/
│   ├── EntityLink.kt                 (link data class)
│   ├── LinkingConfig.kt              (configuration)
│   └── LinkingResult.kt              (result wrapper)
├── repository/
│   ├── EntityLinkRepository.kt       (interface)
│   └── EntityLinkRepositoryImpl.kt   (JDBC implementation)
└── di/
    └── LinkingModule.kt              (Koin module)
```

### 1.2 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Similarity metric | Cosine similarity | Standard for text embeddings |
| Index type | HNSW (via vector DB) | Fast approximate NN search |
| Link storage | PostgreSQL table | Queryable, joinable |
| Threshold | Configurable (default 0.75) | Tunable per deployment |

---

## 2. Detailed Design

### 2.1 EntityLinkingService Interface

```kotlin
interface EntityLinkingService {
    suspend fun findSimilar(issueKey: String, topK: Int = 10): List<EntityLink>
    suspend fun linkEntry(issueKey: String, content: String): LinkingResult
    suspend fun batchLink(entries: List<Pair<String, String>>): List<LinkingResult>
    suspend fun getLinks(issueKey: String): List<EntityLink>
}
```

### 2.2 EntityLink Model

```kotlin
data class EntityLink(
    val sourceIssueKey: String,
    val targetIssueKey: String,
    val similarityScore: Double,
    val linkType: LinkType = LinkType.SEMANTIC,
    val createdAt: Instant = Clock.System.now()
)

enum class LinkType { SEMANTIC, MANUAL, JIRA_LINKED }
```

### 2.3 Core Algorithm

```
fun linkEntry(issueKey, content):
    1. Generate embedding for content
    2. Search vector DB for top-K similar (excluding self)
    3. Filter by threshold (>= config.similarityThreshold)
    4. Store links in entity_links table
    5. Return LinkingResult with links created
```

---

## 3. Database Design

```sql
CREATE TABLE IF NOT EXISTS entity_links (
    id BIGSERIAL PRIMARY KEY,
    source_issue_key VARCHAR(50) NOT NULL,
    target_issue_key VARCHAR(50) NOT NULL,
    similarity_score DOUBLE PRECISION NOT NULL,
    link_type VARCHAR(20) NOT NULL DEFAULT 'SEMANTIC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(source_issue_key, target_issue_key)
);

CREATE INDEX idx_entity_links_source ON entity_links (source_issue_key);
CREATE INDEX idx_entity_links_target ON entity_links (target_issue_key);
CREATE INDEX idx_entity_links_score ON entity_links (similarity_score DESC);
```

---

## 4. Implementation Checklist

| # | File | Lines (est.) |
|---|------|-------------|
| 1 | EntityLinkingService.kt | ~15 |
| 2 | EntityLinkingServiceImpl.kt | ~80 |
| 3 | EntityLink.kt | ~18 |
| 4 | LinkingConfig.kt | ~12 |
| 5 | LinkingResult.kt | ~15 |
| 6 | EntityLinkRepository.kt | ~15 |
| 7 | EntityLinkRepositoryImpl.kt | ~75 |
| 8 | LinkingModule.kt | ~20 |

