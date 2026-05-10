# Business Requirements Document (BRD)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-35 |
| Title | KB Refinery — Semantic Entity Linking |
| Version | 1.0 |
| Date | 2026-05-09 |

---

## 1. Introduction

### 1.1 Scope

Automatically detect and create semantic links between Jira tickets that share common terminology, concepts, or domain entities. Uses embedding-based cosine similarity with HNSW index for efficient nearest-neighbor search.

### 1.2 Dependencies

| Dependency | Description |
|------------|-------------|
| MTO-26 | KB entries schema (content to embed) |
| Qdrant/PgVector | Vector DB for HNSW index |
| Embedding Service | Text → vector conversion |

---

## 2. User Stories

| # | Story | Priority |
|---|-------|----------|
| 1 | As a BA, I want to see related tickets automatically linked so I can understand feature dependencies | MUST HAVE |
| 2 | As a developer, I want semantic similarity scores between tickets so I can identify duplicate/overlapping work | MUST HAVE |
| 3 | As a PM, I want configurable similarity threshold so I can tune link sensitivity | SHOULD HAVE |
| 4 | As a system, I want batch linking on KB ingest so new entries are automatically linked | SHOULD HAVE |

---

## 3. Acceptance Criteria

1. Given two tickets with similar content, when linking runs, then a link with similarity score ≥ threshold is created
2. Given a new KB entry ingested, when auto-link triggers, then top-N similar entries are linked
3. Given similarity threshold = 0.75, when two tickets have cosine similarity 0.80, then they are linked
4. Given a query for related tickets, when called with issue_key, then returns ranked list by similarity

---

## 4. Non-Functional Requirements

| Category | Requirement |
|----------|-------------|
| Performance | Link query < 100ms for top-10 results |
| Accuracy | Cosine similarity on embeddings |
| Storage | HNSW index in vector DB |
| Scalability | Support 10,000+ KB entries |

