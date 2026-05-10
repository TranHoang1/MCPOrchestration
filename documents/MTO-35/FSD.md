# Functional Specification Document (FSD)

## MCPOrchestration — MTO-35: KB Refinery — Semantic Entity Linking

---

## Document Information

| Field | Value |
|-------|-------|
| Jira Ticket | MTO-35 |
| Title | KB Refinery — Semantic Entity Linking |
| Author | BA Agent + TA Agent |
| Version | 1.0 |
| Date | 2026-05-09 |
| Status | Draft |
| Related BRD | BRD-v1-MTO-35.docx |
| Related TDD | TDD-v1-MTO-35.docx |

---

## Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-09 | BA + TA Agent | Initial FSD — full functional specification |

---

## 1. Introduction

### 1.1 Purpose

This FSD specifies the complete functional design for the **Semantic Entity Linking** feature of the KB Refinery module. The system automatically detects and creates semantic links between Jira tickets that share common terminology, domain entities, or feature concepts using embedding-based cosine similarity with HNSW index for efficient nearest-neighbor search.

### 1.2 Scope

**In Scope:**
- Automatic semantic link detection between KB entries via cosine similarity
- Configurable similarity threshold for link sensitivity tuning
- Batch linking on KB entry ingest (auto-link new entries)
- Query API for retrieving related tickets ranked by similarity
- HNSW index in vector DB for fast approximate nearest-neighbor search
- Link persistence in PostgreSQL with bidirectional querying
- Link type classification (SEMANTIC, MANUAL, JIRA_LINKED)

**Out of Scope:**
- UI for visualizing links (covered by MTO-36)
- Manual link creation/deletion UI
- Cross-project linking policies
- Link notification/alerting system
- NLP entity extraction (uses embeddings only)

### 1.3 Definitions & Acronyms

| Term | Definition |
|------|------------|
| Entity Link | A relationship between two KB entries based on semantic similarity |
| Cosine Similarity | Metric measuring angle between two vectors (0.0–1.0) |
| HNSW | Hierarchical Navigable Small World — approximate nearest-neighbor algorithm |
| KB Entry | A knowledge base record (from MTO-26) containing ticket content |
| Embedding | Dense vector representation of text content |
| Top-K | Number of most similar results to return |

### 1.4 References

| Document | Location |
|----------|----------|
| BRD — MTO-35 | documents/MTO-35/BRD.md |
| TDD — MTO-35 | documents/MTO-35/TDD.md |
| Project Structure | .analysis/code-intelligence/project-structure.md |
| MTO-26 (KB Entries) | Dependency — KB entry schema |

---

## 2. System Overview

### 2.1 System Context

The Semantic Entity Linking service operates within the MCP Orchestrator, consuming embeddings from the existing `EmbeddingService` and storing/querying vectors via `VectorDbClient`. It creates persistent links in PostgreSQL and exposes query APIs for downstream consumers (MTO-36 Network Mapping).

**External Dependencies:**
- **EmbeddingService** (existing): Converts text content to vector embeddings
- **VectorDbClient** (existing): HNSW-indexed vector storage and similarity search (Qdrant)
- **PostgreSQL**: Stores entity_links table for persistent link records
- **KB Entry Store** (MTO-26): Source of content to embed and link

### 2.2 Integration Points

| System | Direction | Protocol | Purpose |
|--------|-----------|----------|---------|
| EmbeddingService | Outbound | Internal API | Generate embeddings for content |
| VectorDbClient (Qdrant) | Outbound | REST API | Store vectors, search similar |
| PostgreSQL | Outbound | JDBC | Persist entity links |
| KB Ingest Pipeline | Inbound (event) | Internal call | Trigger auto-linking on new entry |
| MTO-36 NetworkService | Inbound | Internal API | Query links for graph building |

---

## 3. Functional Requirements

### 3.1 Feature: Find Similar Entries

**Source:** [Implements: Story #2, Story #1]

#### 3.1.1 Use Case

**Use Case ID:** UC-01
**Actor:** Developer / BA (via API)
**Preconditions:**
- Target issue key exists in KB with an embedding in vector DB
- At least one other KB entry exists in vector DB

**Postconditions:**
- Ranked list of similar entries returned with similarity scores

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User | | Calls `findSimilar(issueKey, topK)` |
| 2 | | EntityLinkingService | Retrieve embedding for issueKey from vector DB |
| 3 | | VectorDbClient | Search HNSW index for top-K nearest neighbors (excluding self) |
| 4 | | EntityLinkingService | Filter results by similarity threshold |
| 5 | | EntityLinkingService | Map results to `EntityLink` objects with scores |
| 6 | | EntityLinkingService | Return sorted list (descending by similarity) |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | topK exceeds available entries | Return all available entries (< topK) |
| AF-02 | All results below threshold | Return empty list |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Issue key not found in vector DB | Return empty list, log WARN |
| EF-02 | Vector DB unavailable | Throw VectorDbUnavailableException |
| EF-03 | Embedding service timeout | Throw EmbeddingServiceException |

---

### 3.2 Feature: Link Entry on Ingest

**Source:** [Implements: Story #4, Story #1]

#### 3.2.1 Use Case

**Use Case ID:** UC-02
**Actor:** System (automatic on KB ingest)
**Preconditions:**
- New KB entry has been ingested with content
- EmbeddingService is available
- VectorDbClient is available

**Postconditions:**
- Embedding generated and stored in vector DB
- Top-N similar entries identified and links persisted in entity_links table

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | KB Ingest | | Calls `linkEntry(issueKey, content)` |
| 2 | | EntityLinkingService | Generate embedding via EmbeddingService |
| 3 | | VectorDbClient | Upsert embedding into HNSW collection |
| 4 | | VectorDbClient | Search for top-K similar (excluding self) |
| 5 | | EntityLinkingService | Filter by threshold (>= config.similarityThreshold) |
| 6 | | EntityLinkRepository | Persist each link as bidirectional pair |
| 7 | | EntityLinkingService | Return LinkingResult with count and links |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | Entry already exists in vector DB | Upsert (update) embedding, re-run similarity search, update links |
| AF-02 | No similar entries found above threshold | Store embedding but create no links; return LinkingResult(linksCreated=0) |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Embedding generation fails | Log ERROR, return LinkingResult(success=false, error=message) |
| EF-02 | Vector DB upsert fails | Log ERROR, return failure result |
| EF-03 | DB persist fails (duplicate key) | Log WARN, skip duplicate, continue with remaining links |

---

### 3.3 Feature: Batch Link Multiple Entries

**Source:** [Implements: Story #4]

#### 3.3.1 Use Case

**Use Case ID:** UC-03
**Actor:** System (bulk operation / migration)
**Preconditions:**
- Multiple KB entries available for linking
- Services available (Embedding, VectorDB, PostgreSQL)

**Postconditions:**
- All entries have embeddings in vector DB
- All cross-links above threshold are persisted

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin/System | | Calls `batchLink(entries)` with list of (issueKey, content) pairs |
| 2 | | EntityLinkingService | For each entry: generate embedding |
| 3 | | VectorDbClient | Batch upsert all embeddings |
| 4 | | EntityLinkingService | For each entry: search similar, filter by threshold |
| 5 | | EntityLinkRepository | Batch insert all links (skip duplicates) |
| 6 | | EntityLinkingService | Return list of LinkingResult per entry |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | Some entries fail embedding | Continue with successful ones, report partial results |
| AF-02 | Batch size > 100 | Process in chunks of 50 to avoid memory pressure |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Embedding service rate-limited | Implement exponential backoff, retry up to 3 times |
| EF-02 | Transaction timeout on batch insert | Commit in smaller batches, report partial success |

---

### 3.4 Feature: Get Existing Links

**Source:** [Implements: Story #1]

#### 3.4.1 Use Case

**Use Case ID:** UC-04
**Actor:** Developer / BA / MTO-36 NetworkService
**Preconditions:**
- Issue key exists in entity_links table

**Postconditions:**
- All persisted links for the issue key returned

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | User/System | | Calls `getLinks(issueKey)` |
| 2 | | EntityLinkRepository | Query entity_links WHERE source = issueKey OR target = issueKey |
| 3 | | EntityLinkingService | Return list sorted by similarity score descending |

**Alternative Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| AF-01 | No links exist for issue key | Return empty list |

**Exception Flows:**

| ID | Condition | Steps |
|----|-----------|-------|
| EF-01 | Database connection failure | Throw ServerUnavailableException |

---

### 3.5 Feature: Configure Similarity Threshold

**Source:** [Implements: Story #3]

#### 3.5.1 Use Case

**Use Case ID:** UC-05
**Actor:** PM / Admin
**Preconditions:**
- System is running with default or custom configuration

**Postconditions:**
- Threshold applied to all subsequent link operations

**Main Flow:**

| Step | Actor | System | Description |
|------|-------|--------|-------------|
| 1 | Admin | | Sets `linking.similarity-threshold` in configuration |
| 2 | | ConfigurationManager | Loads new threshold value |
| 3 | | EntityLinkingService | Uses updated threshold for filtering |

**Business Rules:**

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Similarity threshold must be between 0.0 and 1.0 | Story #3 |
| BR-02 | Default threshold = 0.75 | Story #3 |
| BR-03 | Threshold change does NOT retroactively update existing links | Design decision |

---

## 4. Business Rules

| Rule ID | Rule | Source |
|---------|------|--------|
| BR-01 | Similarity threshold must be between 0.0 and 1.0 (inclusive) | Story #3 |
| BR-02 | Default similarity threshold = 0.75 | Story #3 |
| BR-03 | Links are bidirectional: if A→B exists, B→A is implied | Design |
| BR-04 | Self-links are never created (issueKey cannot link to itself) | Design |
| BR-05 | Duplicate links (same source+target) are prevented by UNIQUE constraint | Design |
| BR-06 | Link type defaults to SEMANTIC for auto-generated links | Design |
| BR-07 | Top-K default = 10 for similarity queries | Story #2 |
| BR-08 | Batch operations process in chunks of 50 to limit memory | NFR |
| BR-09 | Links persist across server restarts (PostgreSQL storage) | NFR |
| BR-10 | Embedding dimension must match vector DB collection dimension | Technical |

---

## 5. Data Specifications

### 5.1 Input Data

#### 5.1.1 linkEntry Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| issueKey | String | Yes | Non-empty, matches `[A-Z]+-\d+` | Jira issue key |
| content | String | Yes | Non-empty, max 10,000 chars | Text content to embed |

#### 5.1.2 findSimilar Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| issueKey | String | Yes | Non-empty, matches `[A-Z]+-\d+` | Target issue key |
| topK | Int | No | 1–100, default 10 | Max results to return |

#### 5.1.3 batchLink Input

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| entries | List<Pair<String, String>> | Yes | Non-empty, max 500 entries | List of (issueKey, content) pairs |

### 5.2 Output Data

#### 5.2.1 EntityLink

| Field | Type | Description |
|-------|------|-------------|
| sourceIssueKey | String | Source ticket key |
| targetIssueKey | String | Target ticket key |
| similarityScore | Double | Cosine similarity (0.0–1.0) |
| linkType | LinkType | SEMANTIC / MANUAL / JIRA_LINKED |
| createdAt | Instant | Link creation timestamp |

#### 5.2.2 LinkingResult

| Field | Type | Description |
|-------|------|-------------|
| issueKey | String | The entry that was linked |
| success | Boolean | Whether operation succeeded |
| linksCreated | Int | Number of new links created |
| links | List<EntityLink> | The created links |
| error | String? | Error message if failed |

---

## 6. API Contracts

### 6.1 EntityLinkingService Interface

```kotlin
interface EntityLinkingService {
    /**
     * Find entries similar to the given issue key.
     * Queries vector DB for nearest neighbors, filters by threshold.
     * @param issueKey Target issue key to find similar entries for
     * @param topK Maximum number of results (default 10)
     * @return List of EntityLink sorted by similarity descending
     */
    suspend fun findSimilar(issueKey: String, topK: Int = 10): List<EntityLink>

    /**
     * Generate embedding for content, store in vector DB, and create links.
     * Called on KB entry ingest.
     * @param issueKey Issue key for the new entry
     * @param content Text content to embed
     * @return LinkingResult with created links
     */
    suspend fun linkEntry(issueKey: String, content: String): LinkingResult

    /**
     * Batch link multiple entries. Processes in chunks of 50.
     * @param entries List of (issueKey, content) pairs
     * @return List of LinkingResult per entry
     */
    suspend fun batchLink(entries: List<Pair<String, String>>): List<LinkingResult>

    /**
     * Get all persisted links for an issue key (both directions).
     * @param issueKey Issue key to query
     * @return List of EntityLink sorted by similarity descending
     */
    suspend fun getLinks(issueKey: String): List<EntityLink>
}
```

### 6.2 EntityLinkRepository Interface

```kotlin
interface EntityLinkRepository {
    suspend fun save(link: EntityLink): EntityLink
    suspend fun saveBatch(links: List<EntityLink>): Int
    suspend fun findByIssueKey(issueKey: String): List<EntityLink>
    suspend fun findBySourceAndTarget(source: String, target: String): EntityLink?
    suspend fun deleteByIssueKey(issueKey: String): Int
    suspend fun countAll(): Long
}
```

### 6.3 MCP Tool Exposure (if applicable)

The linking service may be exposed as an MCP tool for AI agent consumption:

```json
{
  "name": "find_related_tickets",
  "description": "Find tickets semantically related to a given issue key",
  "inputSchema": {
    "type": "object",
    "properties": {
      "issue_key": { "type": "string", "description": "Jira issue key (e.g., MTO-35)" },
      "top_k": { "type": "integer", "description": "Max results (default 10)", "default": 10 }
    },
    "required": ["issue_key"]
  }
}
```

---

## 7. Integration Specifications

### 7.1 EmbeddingService Integration

| Aspect | Detail |
|--------|--------|
| Service | `EmbeddingService` (existing, `com.orchestrator.mcp.embedding`) |
| Method | `suspend fun embed(text: String): FloatArray` |
| Model | OpenAI text-embedding-3-small |
| Dimensions | 768 |
| Rate Limit | Handled by existing service (exponential backoff) |
| Error Handling | Catch `EmbeddingServiceException`, propagate as LinkingResult failure |

### 7.2 VectorDbClient Integration

| Aspect | Detail |
|--------|--------|
| Service | `VectorDbClient` (existing, `com.orchestrator.mcp.vectordb`) |
| Collection | `kb_entity_embeddings` (separate from `mcp_tools`) |
| Operations | `upsert(id, vector, metadata)`, `search(vector, topK, filter)` |
| Index | HNSW (configured in Qdrant collection) |
| Metadata | `{ "issue_key": "MTO-35", "project": "MTO" }` |

### 7.3 PostgreSQL Integration

| Aspect | Detail |
|--------|--------|
| Table | `entity_links` |
| Connection | Existing HikariCP pool |
| Transactions | Each batch insert in single transaction |
| Indexes | source_issue_key, target_issue_key, similarity_score DESC |

---

## 8. Processing Logic

### 8.1 linkEntry Pseudocode

```
suspend fun linkEntry(issueKey: String, content: String): LinkingResult {
    // Step 1: Generate embedding
    val embedding = try {
        embeddingService.embed(content)
    } catch (e: EmbeddingServiceException) {
        return LinkingResult(issueKey, success = false, error = e.message)
    }

    // Step 2: Upsert into vector DB
    vectorDbClient.upsert(
        id = issueKey,
        vector = embedding,
        metadata = mapOf("issue_key" to issueKey, "project" to issueKey.substringBefore("-"))
    )

    // Step 3: Search for similar entries (exclude self)
    val searchResults = vectorDbClient.search(
        vector = embedding,
        topK = config.topK + 1,  // +1 to account for self-match
        filter = { it.id != issueKey }
    )

    // Step 4: Filter by threshold
    val aboveThreshold = searchResults.filter { it.score >= config.similarityThreshold }

    // Step 5: Create links
    val links = aboveThreshold.map { result ->
        EntityLink(
            sourceIssueKey = issueKey,
            targetIssueKey = result.id,
            similarityScore = result.score,
            linkType = LinkType.SEMANTIC
        )
    }

    // Step 6: Persist (skip duplicates)
    val saved = entityLinkRepository.saveBatch(links)

    return LinkingResult(
        issueKey = issueKey,
        success = true,
        linksCreated = saved,
        links = links
    )
}
```

### 8.2 findSimilar Pseudocode

```
suspend fun findSimilar(issueKey: String, topK: Int): List<EntityLink> {
    // Step 1: Get existing embedding from vector DB
    val embedding = vectorDbClient.getVector(issueKey)
        ?: return emptyList()  // Not found in vector DB

    // Step 2: Search for nearest neighbors
    val results = vectorDbClient.search(
        vector = embedding,
        topK = topK + 1,
        filter = { it.id != issueKey }
    )

    // Step 3: Filter by threshold and map to EntityLink
    return results
        .filter { it.score >= config.similarityThreshold }
        .take(topK)
        .map { result ->
            EntityLink(
                sourceIssueKey = issueKey,
                targetIssueKey = result.id,
                similarityScore = result.score,
                linkType = LinkType.SEMANTIC
            )
        }
}
```

### 8.3 batchLink Pseudocode

```
suspend fun batchLink(entries: List<Pair<String, String>>): List<LinkingResult> {
    val results = mutableListOf<LinkingResult>()

    // Process in chunks of 50
    entries.chunked(50).forEach { chunk ->
        // Generate embeddings for chunk
        val embeddings = chunk.map { (key, content) ->
            key to embeddingService.embed(content)
        }

        // Batch upsert to vector DB
        vectorDbClient.batchUpsert(embeddings.map { (key, vec) ->
            VectorPoint(id = key, vector = vec, metadata = mapOf("issue_key" to key))
        })

        // For each entry, find similar and create links
        embeddings.forEach { (key, vec) ->
            val similar = vectorDbClient.search(vec, config.topK + 1, filter = { it.id != key })
            val links = similar
                .filter { it.score >= config.similarityThreshold }
                .map { EntityLink(key, it.id, it.score, LinkType.SEMANTIC) }

            val saved = entityLinkRepository.saveBatch(links)
            results.add(LinkingResult(key, true, saved, links))
        }
    }

    return results
}
```

---

## 9. State Diagram

### 9.1 Entity Link Lifecycle

```
[New Content Ingested]
        │
        ▼
┌─────────────────┐
│ EMBEDDING       │ ← Generate vector via EmbeddingService
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ INDEXED         │ ← Upsert into vector DB (HNSW)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ SEARCHING       │ ← Query top-K nearest neighbors
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ FILTERING       │ ← Apply similarity threshold
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
[LINKED]   [NO MATCH]
    │
    ▼
┌─────────────────┐
│ PERSISTED       │ ← Save to entity_links table
└─────────────────┘
```

### 9.2 Link States

| State | Description | Transitions |
|-------|-------------|-------------|
| ACTIVE | Link exists and is valid | → DELETED (if entry removed) |
| DELETED | Link removed (entry deleted from KB) | Terminal |

---

## 10. Non-Functional Requirements

| Category | Requirement | Target |
|----------|-------------|--------|
| Performance | findSimilar query latency | < 100ms for top-10 |
| Performance | linkEntry (single) latency | < 500ms (includes embedding generation) |
| Performance | batchLink throughput | ≥ 50 entries/second |
| Scalability | Total KB entries supported | 10,000+ |
| Scalability | Total links supported | 100,000+ |
| Availability | Graceful degradation if vector DB down | Return empty results, log ERROR |
| Accuracy | Cosine similarity precision | IEEE 754 double precision |
| Storage | Vector DB collection size | ~768 floats × N entries |
| Storage | PostgreSQL entity_links rows | ~10× N entries (avg 10 links per entry) |

---

## 11. Error Handling

| Error Condition | Response | Recovery |
|-----------------|----------|----------|
| EmbeddingService unavailable | LinkingResult(success=false) | Retry with backoff |
| VectorDB unavailable | Throw VectorDbUnavailableException | Caller handles gracefully |
| PostgreSQL connection failure | Throw ServerUnavailableException | Connection pool auto-reconnect |
| Duplicate link (UNIQUE violation) | Skip silently, continue | No action needed |
| Invalid issue key format | Throw InvalidParamsException | Caller validates input |
| Content too long (>10,000 chars) | Truncate to 10,000 chars before embedding | Log WARN |
| Empty content | Throw InvalidParamsException | Caller validates input |

---

## 12. Open Issues

| # | Issue | Status | Decision Needed By |
|---|-------|--------|-------------------|
| 1 | Should links be updated when content changes? (re-embed + re-link) | Open | SA/PM |
| 2 | Should old links be deleted when re-linking? Or keep historical? | Open | PM |
| 3 | Collection name for entity embeddings — shared with mcp_tools or separate? | Decided: Separate (`kb_entity_embeddings`) | — |
